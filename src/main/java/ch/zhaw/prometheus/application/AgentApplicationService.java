package ch.zhaw.prometheus.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.controllers.AgentMetaType;
import ch.zhaw.prometheus.controllers.dto.SingleStateAgentCreateDTO;
import ch.zhaw.prometheus.controllers.views.AgentInfoView;
import ch.zhaw.prometheus.controllers.views.AgentStateInfoView;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.controllers.views.PolicyResponseView;
import ch.zhaw.prometheus.controllers.views.ResponseView;
import ch.zhaw.prometheus.controllers.views.StorageEntryView;
import ch.zhaw.prometheus.logging.AgentBehaviourBroadcaster;
import ch.zhaw.prometheus.logging.LatencyTrace;
import ch.zhaw.prometheus.logging.AgentMonitorBroadcaster;
import ch.zhaw.prometheus.model.Action;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Decision;
import ch.zhaw.prometheus.model.Final;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.social.SocialSituationChangeDetector;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@Service
public class AgentApplicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentApplicationService.class);

    private final AgentRepository repository;
    private final AgentMonitorBroadcaster monitorBroadcaster;
    private final AgentBehaviourBroadcaster behaviourBroadcaster;
    private final PromptMessageAssembler promptMessageAssembler;
    private final LanguageModelGateway languageModelGateway;
    private final SocialSituationChangeDetector socialSituationChangeDetector;
    private final AgentTurnSerialiser turns = new AgentTurnSerialiser();
    private BackgroundActionExecutor backgroundActions;
    private ch.zhaw.prometheus.repositories.StorageRepository storageRepository;
    private org.springframework.transaction.support.TransactionTemplate backgroundTransaction;
    private BehaviourSpeculationService speculation;
    private ExternalSpeechOwnership speechOwnership;
    private AgentPersistenceContext persistenceContext;
    private SceneTargetCaptureService sceneCaptures;
    @org.springframework.beans.factory.annotation.Autowired
    void configureSceneCaptures(SceneTargetCaptureService captures) { this.sceneCaptures=captures; }
    private boolean refreshScene(Agent agent) { return sceneCaptures!=null&&sceneCaptures.refresh(agent); }
    @org.springframework.beans.factory.annotation.Autowired
    void configurePersistenceContext(AgentPersistenceContext context) { this.persistenceContext = context; }
    private <T> T persistenceTurn(java.util.function.Supplier<T> work) {
        return persistenceContext == null ? work.get() : persistenceContext.call(work);
    }
    private record LoadedTurn(Agent agent, PolicyRuntime runtime) {}
    private Optional<LoadedTurn> loadTurn(UUID id, OutputProfile profile) {
        java.util.function.Supplier<Optional<LoadedTurn>> load = () -> findAgent(id)
                .map(agent -> {
                    var runtime = runtimeFor(agent, profile);
                    var owner = runtime.externalSpeech();
                    ch.zhaw.prometheus.logging.ActivityTrace.bind(agent.executionEpoch(), owner == null ? null : owner.sessionId(), null);
                    return new LoadedTurn(agent, runtime);
                });
        return persistenceContext == null ? load.get() : persistenceContext.load(load);
    }
    private org.springframework.context.ApplicationEventPublisher applicationEvents;
    private AgentActivityService activities;
    @org.springframework.beans.factory.annotation.Autowired
    void configureActivities(AgentActivityService activities) { this.activities = activities; }
    <T> T activity(UUID id, String kind, boolean foreground, java.util.function.Supplier<T> work) {
        return activities == null ? work.get() : activities.call(id, kind, foreground, work);
    }

    @org.springframework.beans.factory.annotation.Autowired
    void configureApplicationEvents(org.springframework.context.ApplicationEventPublisher events) { this.applicationEvents = events; }

    private void committed(Agent agent) {
        if (applicationEvents == null || agent == null) return;
        var history = agent.getEventHistory().toList();
        Event latest = history.isEmpty() ? null : history.getLast();
        UUID narration = latest != null && ch.zhaw.prometheus.model.event.ConversationProjection.isIntent(latest)
                && BehaviourPlan.fromJson(latest.getPayload()).getSpeech() != null ? latest.getId() : null;
        UUID speechRequest = narration;
        if (narration == null && agent.getCurrentState().ownPolicy() instanceof ch.zhaw.prometheus.model.policy.ExternalVoicePolicy external) {
            var activity = external.liveActivityContext();
            if (activity != null && activity.continuation() != null) {
                narration = activity.continuation().id(); speechRequest = activity.continuation().responseId();
            }
        }
        var event = new AgentCommitted(agent.getId(), agent.executionEpoch(), narration, true, speechRequest);
        AfterCommit.run(() -> applicationEvents.publishEvent(event));
    }

    @org.springframework.beans.factory.annotation.Autowired
    void configureSpeechOwnership(ExternalSpeechOwnership ownership) { this.speechOwnership = ownership; }

    private PolicyRuntime runtimeFor(Agent agent, OutputProfile profile) {
        var owner = speechOwnership == null ? null : speechOwnership.current(agent);
        if (owner == null && agent.getCurrentState() != null
                && agent.getCurrentState().ownPolicy() instanceof ch.zhaw.prometheus.model.task.TaskPolicy task
                && !ch.zhaw.prometheus.model.task.TaskMemory.text(task.storage(), ch.zhaw.prometheus.model.task.TaskMemory.SESSION, "").isEmpty())
            ch.zhaw.prometheus.model.task.TaskMemory.pauseForSession(task.storage(), "live_session_ended");
        var runtime = runtime(profile).withExternalSpeech(owner);
        if(sceneCaptures!=null)runtime=runtime.withGateway(sceneCaptures.gateway(agent,runtime.languageModelGateway()));
        return owner == null ? runtime : runtime.withTaskContinuation(() -> speechOwnership.taskWorkAllowed(agent.getId(), owner));
    }

    @org.springframework.beans.factory.annotation.Autowired
    void configureSpeculation(BehaviourSpeculationService service) { this.speculation = service; }

    void discardSpeculation(UUID id, String reason) { if (speculation != null) speculation.discard(id, reason); }

    private static UUID lastEventId(Agent agent) {
        var events = agent.getEventHistory().toList();
        return events.isEmpty() ? null : events.getLast().getId();
    }

    @org.springframework.beans.factory.annotation.Autowired
    void configureBackgroundActions(BackgroundActionExecutor executor,
            ch.zhaw.prometheus.repositories.StorageRepository storage,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.backgroundActions = executor;
        this.storageRepository = storage;
        this.backgroundTransaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        this.backgroundTransaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private <T> T actionTurn(Agent agent, OutputProfile profile, java.util.function.Function<PolicyRuntime, T> work) {
        return actionTurn(agent, runtimeFor(agent, profile), work);
    }

    private <T> T actionTurn(Agent agent, PolicyRuntime runtime, java.util.function.Function<PolicyRuntime, T> work) {
        refreshScene(agent);
        if (backgroundActions == null) return work.apply(runtime);
        UUID id = agent.getId(), epoch = agent.executionEpoch();
        try (var turn = new BackgroundActionTurn(id, backgroundActions,
                (action, values, versions) -> applyBackgroundAction(id, epoch, action, values, versions))) {
            T result = work.apply(runtime.withActionExecution(turn));
            turn.commit();
            return result;
        }
    }

    private String applyBackgroundAction(UUID id, UUID epoch, ch.zhaw.prometheus.model.PreparedAction action,
            java.util.Map<String, String> values, BackgroundActionExecutor.Versions versions) {
        var parsed = new java.util.HashMap<String, com.google.gson.JsonElement>();
        values.forEach((key, value) -> parsed.put(key, ch.zhaw.prometheus.spi.InferenceResult.json(value)));
        return serialized(id, () -> {
            var before = new java.util.HashMap<String, String>();
            var after = new java.util.HashMap<String, String>();
            String status = backgroundTransaction.execute(transaction -> {
                Agent current = repository.findById(id).orElse(null);
                if (current == null || !epoch.equals(current.executionEpoch())) return "discarded_obsolete_agent";
                if (values.isEmpty()) return "completed";
                Storage storage = storageRepository.findById(action.storageId()).orElse(null);
                if (storage == null) return "discarded_missing_storage";
                for (var entry : action.expectedVersions().entrySet()) {
                    String actual = storage.writeVersion(entry.getKey());
                    if (!actual.equals(versions.expected(action.storageId(), entry.getKey(), entry.getValue())))
                        return "discarded_storage_conflict";
                    before.put(entry.getKey(), actual);
                }
                parsed.forEach(storage::put);
                parsed.keySet().forEach(key -> after.put(key, storage.writeVersion(key)));
                storageRepository.saveAndFlush(storage);
                return "completed";
            });
            if ("completed".equals(status)) {
                after.forEach((key, value) -> versions.applied(action.storageId(), key, before.get(key), value));
                repository.findById(id).ifPresent(agent -> { safePublishMonitor(agent); committed(agent); });
            }
            return status;
        });
    }

    <T> T serialized(UUID agentId, java.util.function.Supplier<T> work) { return turns.call(agentId, work); }

    public boolean tick(UUID agentId) {
        return activity(agentId, "tick", false, () -> {
        return serialized(agentId, () -> {
            discardSpeculation(agentId, "tick");
            Agent agent = findAgent(agentId).orElse(null);
            if (agent == null || !agent.isActive()) return false;
            return actionTurn(agent, OutputProfile.FULL_PLAN, runtime -> {
                Event response = agent.tick(runtime);
                Agent saved = persistAndPublishMonitor(agent);
                publishBehaviour(saved, response);
                return true;
            });
        });
        });
    }

    public AgentApplicationService(AgentRepository repository, AgentMonitorBroadcaster monitorBroadcaster,
            AgentBehaviourBroadcaster behaviourBroadcaster,
            PromptMessageAssembler promptMessageAssembler, LanguageModelGateway languageModelGateway) {
        this.repository = repository;
        this.monitorBroadcaster = monitorBroadcaster;
        this.behaviourBroadcaster = behaviourBroadcaster;
        this.promptMessageAssembler = promptMessageAssembler;
        this.languageModelGateway = languageModelGateway;
        this.socialSituationChangeDetector = SocialSituationChangeDetector.defaultThresholds();
    }

    public List<AgentInfoView> listAgents() {
        List<Agent> agents = this.repository.findAll();
        List<AgentInfoView> result = new ArrayList<>();
        for (Agent current : agents) {
            result.add(new AgentInfoView(current.getId(), current.getName(), current.getDescription(),
                    current.isActive(), current.getInteractionProfile(), current.getLanguageCode()));
        }
        return result;
    }

    public List<Agent> listAgentAggregates() {
        return this.repository.findAll();
    }

    public Optional<Agent> getAgentById(UUID agentID) {
        return this.findAgent(agentID);
    }

    public Optional<AgentInfoView> getAgentInfo(UUID agentID) {
        return this.findAgent(agentID).map(agent -> new AgentInfoView(agent.getId(), agent.getName(),
                agent.getDescription(), agent.isActive(), agent.getInteractionProfile(), agent.getLanguageCode()));
    }

    public Optional<String> getAgentLanguageCode(UUID agentID) {
        return this.findAgent(agentID)
                .map(Agent::getLanguageCode)
                .filter(AgentApplicationService::isPresent);
    }

    public Optional<AgentStateInfoView> getAgentState(UUID agentID) {
        Optional<Agent> agentMaybe = this.findAgent(agentID);
        if (agentMaybe.isEmpty()) {
            return Optional.empty();
        }
        Agent agent = agentMaybe.get();
        State currentState = agent.getCurrentState();
        if (currentState == null) {
            return Optional.empty();
        }
        String stateName = currentState.getName();
        String innerName = null;
        List<String> innerNames = List.of();
        if (currentState instanceof ch.zhaw.prometheus.model.OuterState outerState
                && outerState.getInnerCurrent() != null) {
            innerName = outerState.getInnerCurrent().getName();
            innerNames = outerState.getInnerCurrentChain();
        }
        return Optional.of(new AgentStateInfoView(stateName, innerName, innerNames)
                .withActiveStateIds(ch.zhaw.prometheus.model.StateGraph.activeIds(currentState)));
    }

    public Optional<List<String>> getAgentStates(UUID agentID) {
        return this.findAgent(agentID).map(Agent::listStates);
    }

    public Optional<ch.zhaw.prometheus.model.StateGraph> getAgentGraph(UUID agentID) {
        return this.findAgent(agentID).map(ch.zhaw.prometheus.model.StateGraph::of);
    }

    public Optional<List<StorageEntryView>> getAgentStorage(UUID agentID) {
        return this.findAgent(agentID).map(agent -> agent.getStorage().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map((entry) -> new StorageEntryView(entry.getKey(),
                        entry.getValue() == null ? "null" : entry.getValue().toString()))
                .toList());
    }

    public Optional<String> handScene(UUID agentID) {return findAgent(agentID).map(a->a.handScene().toString());}
    public Optional<String> handExecution(UUID agentID,String body){
        if(body!=null&&body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1024)throw new IllegalArgumentException("Receipt exceeds 1 KiB");
        return serialized(agentID,()->persistenceTurn(()->{
            var found=findAgent(agentID);if(found.isEmpty())return Optional.empty();var agent=found.get();
            var request = body == null ? null : com.google.gson.JsonParser.parseString(body).getAsJsonObject();
            if (request != null && new com.google.gson.JsonPrimitive("POSE_RENDERED").equals(request.get("operation"))) {
                if (!request.keySet().equals(java.util.Set.of("operation", "eventId")))
                    throw new IllegalArgumentException("Invalid pose rendering receipt");
                boolean changed = agent.renderedPose(UUID.fromString(ch.zhaw.prometheus.model.hand.HandSignObservation.text(request.get("eventId"))),
                        speechOwnership == null ? null : speechOwnership.current(agent), java.time.Instant.now());
                if (changed) persistAndPublishMonitor(agent);
                return Optional.of("{}");
            }
            boolean sceneChanged=refreshScene(agent);
            String before=java.util.Objects.toString(agent.getStorage().get(ch.zhaw.prometheus.model.hand.HandExecution.KEY));
            var result=agent.handExecution(request,speechOwnership==null?null:speechOwnership.current(agent),java.time.Instant.now());
            if(before.equals(java.util.Objects.toString(agent.getStorage().get(ch.zhaw.prometheus.model.hand.HandExecution.KEY)))){
                if(sceneChanged)persistAndPublishMonitor(agent);
                return Optional.of(result.toString());
            }
            var response=agent.acknowledge(Event.systemTick(),runtimeFor(agent,OutputProfile.FULL_PLAN));
            var saved=persistAndPublishMonitor(agent);if(response!=null)publishBehaviour(saved,response);return Optional.of(result.toString());
        }));
    }
    public Optional<String> configureHandScene(UUID agentID,String body) {
        if(body==null||body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32768)throw new IllegalArgumentException("Scene exceeds 32 KiB");
        return serialized(agentID,()->persistenceTurn(()->{
            var found=findAgent(agentID);if(found.isEmpty())return Optional.empty();
            var agent=found.get();var scene=agent.configureHandScene(ch.zhaw.prometheus.model.hand.HandScene.parseRequest(body),java.time.Instant.now());
            discardSpeculation(agentID,"scene_changed");persistAndPublishMonitor(agent);return Optional.of(scene.toString());
        }));
    }
    public Optional<String> bindHandTarget(UUID agentID,String body){return serialized(agentID,()->persistenceTurn(()->{
        var found=findAgent(agentID);if(found.isEmpty())return Optional.empty();var agent=found.get();
        refreshScene(agent);
        var binding=agent.bindHandTarget(ch.zhaw.prometheus.model.hand.HandScene.parseRequest(body),java.time.Instant.now());
        persistAndPublishMonitor(agent);return Optional.of(binding.toString());
    }));}

    public Optional<List<Event>> getAgentEventHistory(UUID agentID) {
        // History display needs durable events/paths, not the agent's policies,
        // transitions and storage. Scoped callers retain their fresh access check.
        return this.repository.findHistoryById(agentID).map(ch.zhaw.prometheus.model.event.EventHistory::toList);
    }

    public Optional<List<Event>> getAgentCurrentStateEventHistory(UUID agentID) {
        return this.findAgent(agentID).map(Agent::getCurrentStateEventHistory);
    }

    public Optional<ResponseView> start(UUID agentID) {
        return serialized(agentID, () -> {
            discardSpeculation(agentID, "start");
            Optional<Agent> agentMaybe = this.findAgent(agentID);
            if (agentMaybe.isEmpty()) {
                return Optional.empty();
            }
            Agent agent = agentMaybe.get();
            Event starter = agent.start(this.runtimeFor(agent, OutputProfile.FULL_PLAN));
            Agent saved = this.persistAndPublishMonitor(agent);
            this.publishBehaviour(saved, starter);
            return Optional.of(new ResponseView(starter, agent.isActive()));
        });
    }

    public BehaviourGenerationOutcome generate(UUID agentID, List<String> omitModalities) {
        return this.generate(agentID, omitModalities, OutputProfile.FULL_PLAN);
    }

    public BehaviourGenerationOutcome generate(UUID agentID, List<String> omitModalities, OutputProfile outputProfile) {
        return activity(agentID, "generate", true, () -> {
        return serialized(agentID, () -> persistenceTurn(() -> {
            OutputProfile resolvedProfile = outputProfile == null ? OutputProfile.FULL_PLAN : outputProfile;
            Optional<LoadedTurn> agentMaybe = this.loadTurn(agentID, resolvedProfile);
            if (agentMaybe.isEmpty()) {
                return BehaviourGenerationOutcome.AGENT_NOT_FOUND;
            }
            Agent agent = agentMaybe.get().agent();
            refreshScene(agent);
            PolicyRuntime generationRuntime = agentMaybe.get().runtime();
            if (speculation != null && generationRuntime.externalSpeech() == null) {
                var gateway=speculation.forGeneration(agentID,agent.executionEpoch(),lastEventId(agent),languageModelGateway);
                generationRuntime=generationRuntime.withGateway(sceneCaptures==null?gateway:sceneCaptures.gateway(agent,gateway));
            }
            PolicyRuntime preparedRuntime = generationRuntime;
            Event response;
            try { response = LatencyTrace.measure("generate", () -> agent.generate(preparedRuntime)); }
            finally { discardSpeculation(agentID, "unused_generation"); }
            if (response == null) {
                // A policy may record a silent rejection/handled-turn receipt without emitting behaviour.
                // Keep those decisions durable so a retry cannot reinterpret the same admitted turn.
                if (preparedRuntime.taskContinuation().getAsBoolean()) this.persistAndPublishMonitor(agent);
                return BehaviourGenerationOutcome.NO_BEHAVIOUR_GENERATED;
            }
            this.applyOmittedModalities(response, omitModalities);
            Agent saved = this.persistAndPublishMonitor(agent);
            this.publishBehaviour(saved, response);
            return BehaviourGenerationOutcome.GENERATED;
        }));
        });
    }

    public Optional<ResponseView> acknowledge(UUID agentID, EventRequest request) {
        return this.acknowledge(agentID, request, OutputProfile.FULL_PLAN);
    }

    public Optional<ResponseView> acknowledge(UUID agentID, EventRequest request, OutputProfile outputProfile) {
        if(sceneCaptures!=null&&Event.TYPE_SCENE_TARGETS.equals(request.getType()))return acknowledgeSceneCapture(agentID,request);
        return activity(agentID, "acknowledge", Event.TYPE_USER_UTTERANCE.equals(request.getType()), () -> {
        return serialized(agentID, () -> persistenceTurn(() -> {
            OutputProfile resolvedProfile = outputProfile == null ? OutputProfile.FULL_PLAN : outputProfile;
            Optional<LoadedTurn> agentMaybe = this.loadTurn(agentID, resolvedProfile);
            if (agentMaybe.isEmpty()) {
                return Optional.empty();
            }
            Agent agent = agentMaybe.get().agent();
            refreshScene(agent);
            Event event = new Event(request.getType(), request.getActor(), request.getKind(), request.getPayload());
            boolean modernHand = Event.TYPE_HAND_SIGNS.equals(event.getType()) || Event.TYPE_HAND_POINTING.equals(event.getType())
                    || Event.TYPE_SCENE_TARGETS.equals(event.getType()) || Event.TYPE_HAND_POSE.equals(event.getType())
                        && ch.zhaw.prometheus.model.hand.HandFeatures.poses(agent.getInteractionProfile());
            if (!modernHand && request.getPayload() != null && request.getPayload().length() > 4096)
                throw new IllegalArgumentException("Event payload exceeds 4096 characters");
            if(Event.TYPE_SCENE_TARGETS.equals(event.getType())&&!agent.admitSceneTargets(event,java.time.Instant.now()))return Optional.of(new ResponseView(null,agent.isActive()));
            if (Event.TYPE_HAND_SIGNS.equals(event.getType()) || Event.TYPE_HAND_POSE.equals(event.getType()) || Event.TYPE_HAND_POINTING.equals(event.getType())) {
                ch.zhaw.prometheus.model.hand.HandObservationAdmission.validate(event, agent.getInteractionProfile(), agent.getEventHistory().toList(), java.time.Instant.now());
                if (modernHand && !ch.zhaw.prometheus.model.hand.HandObservationAdmission.freshRepresentation(
                        event, agent.getEventHistory().toList(), java.time.Instant.now()))
                    return Optional.of(new ResponseView(null, agent.isActive()));
                agent.resolveHandPointing(event,java.time.Instant.now());
            }
            PolicyRuntime acknowledgementRuntime = agentMaybe.get().runtime();
            try (var preview = speculation == null || acknowledgementRuntime.externalSpeech() != null ? null : speculation.open(agentID, agent.executionEpoch(),
                    agent.getRegulationSystem().getClass() == ch.zhaw.prometheus.model.regulation.NoOpRegulationSystem.class)) {
                Optional<ResponseView> result = actionTurn(agent, acknowledgementRuntime, runtime -> {
                    PolicyRuntime turnRuntime = runtime.withBehaviourSpeculation(preview);
                    long sourcePosition = agent.getEventHistory().nextPosition();
                    Event response = LatencyTrace.measure("acknowledge", () -> agent.acknowledge(event, turnRuntime));
                    Event computedResponse = this.acknowledgeComputedSocialSituationChange(agent, event, turnRuntime);
                    Event responseToReturn = computedResponse == null ? response : computedResponse;
                    Agent saved = this.persistAndPublishMonitor(agent);
                    responseToReturn=committedResponse(saved,responseToReturn);
                    saved.getEventHistory().toList().stream().filter(input -> Long.valueOf(sourcePosition).equals(input.sourcePosition()))
                            .findFirst().ifPresent(input -> ch.zhaw.prometheus.logging.ActivityTrace.bind(null, null, input.getId()));
                    this.publishBehaviour(saved, responseToReturn);
                    if (preview != null) preview.result(lastEventId(saved), responseToReturn != null);
                    return Optional.of(new ResponseView(responseToReturn, agent.isActive()));
                });
                if (preview != null) preview.commit();
                return result;
            }
        }));
        });
    }

    private Optional<ResponseView> acknowledgeSceneCapture(UUID agentId,EventRequest request) {
        return activity(agentId,"scene_capture",false,()->{
            var admitted=sceneCaptures.admit(agentId,request);if(admitted.isEmpty())return Optional.empty();
            // Capture durability does not wait for inference. A busy turn assimilates
            // the latest validated snapshot before grounding its result; intermediate
            // passive scene frames coalesce, while tracking continuity is checked on every receipt.
            return turns.tryCall(agentId,()->persistenceTurn(()->{
                var found=findAgent(agentId);if(found.isEmpty())return Optional.empty();var agent=found.get();
                if(refreshScene(agent))persistAndPublishMonitor(agent);
                return Optional.of(new ResponseView(null,agent.isActive(),admitted.get().snapshot()));
            }),()->Optional.of(new ResponseView(null,admitted.get().active(),admitted.get().snapshot())));
        });
    }

    public Optional<ResponseView> reset(UUID agentID) {
        return activity(agentID, "reset", true, () -> {
        return serialized(agentID, () -> {
            discardSpeculation(agentID, "reset");
            Optional<Agent> agentMaybe = this.findAgent(agentID);
            if (agentMaybe.isEmpty()) {
                return Optional.empty();
            }
            Agent agent = agentMaybe.get();
            agent.reset();
            ch.zhaw.prometheus.logging.ActivityTrace.bind(agent.executionEpoch(), null, null);
            if (speechOwnership != null) speechOwnership.revoke(agentID);
            Event response = agent.start(this.runtime());
            Agent saved = this.persistAndPublishMonitor(agent);
            this.publishBehaviour(saved, response);
            return Optional.of(new ResponseView(response, agent.isActive()));
        });
        });
    }

    public Optional<PolicyResponseView> prompt(UUID agentID) {
        return this.prompt(agentID, OutputProfile.FULL_PLAN);
    }

    public Optional<PolicyResponseView> prompt(UUID agentID, OutputProfile outputProfile) {
        Optional<Agent> agentMaybe = this.findAgent(agentID);
        if (agentMaybe.isEmpty()) {
            return Optional.empty();
        }
        Agent agent = agentMaybe.get();
        OutputProfile resolvedProfile = outputProfile == null ? OutputProfile.FULL_PLAN : outputProfile;
        return Optional.of(
                new PolicyResponseView(agent.getTotalPolicy(this.promptMessageAssembler, resolvedProfile),
                        agent.isActive()));
    }

    public Optional<SseEmitter> subscribeMonitor(UUID agentID) {
        Optional<Agent> agentMaybe = this.findAgent(agentID);
        if (agentMaybe.isEmpty()) {
            return Optional.empty();
        }
        SseEmitter emitter = this.monitorBroadcaster.subscribe(agentID, () -> this.findAgent(agentID));
        return Optional.of(emitter);
    }

    public Optional<SseEmitter> subscribeBehaviour(UUID agentID) {
        return this.subscribeBehaviour(agentID, null);
    }

    public Optional<SseEmitter> subscribeBehaviour(UUID agentID, String lastEventId) {
        Optional<Agent> agentMaybe = this.findAgent(agentID);
        if (agentMaybe.isEmpty()) {
            return Optional.empty();
        }
        SseEmitter emitter = this.behaviourBroadcaster.subscribe(agentID, () -> this.findAgent(agentID), lastEventId);
        return Optional.of(emitter);
    }

    public Optional<SseEmitter> subscribeConversationBehaviour(UUID id, String lastEventId) {
        if (findAgent(id).isEmpty()) return Optional.empty();
        return Optional.of(behaviourBroadcaster.subscribe(id, () -> findAgent(id), lastEventId, true));
    }

    public Optional<AgentInfoView> createSingleStateAgent(SingleStateAgentCreateDTO data) {
        if (data == null || AgentMetaType.singleState.getValue() != data.getType()) {
            return Optional.empty();
        }

        Storage storage = new Storage();
        Decision trigger = new StaticDecision(data.getTriggerToFinalPrompt());
        Decision guard = new StaticDecision(data.getGuardToFinalPrompt());
        Action action = new StaticExtractionAction(data.getActionToFinalPrompt(), storage, "summary");
        Transition transition = new Transition(List.of(trigger, guard), List.of(action),
                new Final("User Exit Final"));
        PromptPolicy policy = new PromptPolicy(data.getStatePrompt(),
                data.getStateStarterPrompt(), PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        policy.setNonVerbalGesturePrompt(data.getStateNonVerbalGesturePrompt());
        State state = new State(data.getStateName(), policy, List.of(transition));

        Agent agent = new Agent(data.getAgentName(), data.getAgentDescription(), state, storage);
        agent.setLanguageCode(valueOrDefault(data.getLanguageCode(), AgentDefinition.LANGUAGE_ENGLISH));
        Event starter = agent.start(this.runtime());
        Agent saved = LatencyTrace.measure("persist", () -> this.repository.save(agent));
        LatencyTrace.measure("monitor_publish", () -> { safePublishMonitor(saved); return null; });
        this.publishBehaviour(saved, starter);
        return Optional.of(new AgentInfoView(saved.getId(), saved.getName(), saved.getDescription(), saved.isActive(),
                saved.getInteractionProfile(), saved.getLanguageCode()));
    }

    public Agent persistCreatedAgent(AgentCreationResult creation) {
        if (creation == null) {
            throw new IllegalArgumentException("agent creation result must not be null");
        }
        Agent saved = this.repository.save(creation.agent());
        AfterCommit.run(() -> safePublishMonitor(saved));
        this.publishBehaviour(saved, creation.starterEvent());
        return saved;
    }

    private Optional<Agent> findAgent(UUID agentID) {
        return this.repository.findById(agentID);
    }

    private Agent persistAndPublishMonitor(Agent agent) {
        if (ch.zhaw.prometheus.model.hand.HandFeatures.poses(agent.getInteractionProfile()))
            ch.zhaw.prometheus.model.hand.HandStateBudget.require(agent.getStorage());
        Agent saved = LatencyTrace.measure("persist", () -> this.repository.save(agent));
        LatencyTrace.measure("monitor_publish", () -> { safePublishMonitor(saved); return null; });
        committed(saved);
        return saved;
    }

    private void publishBehaviour(Agent agent, Event responseEvent) {
        if (agent == null || responseEvent == null) {
            return;
        }
        Event eventToPublish = committedResponse(agent,responseEvent);
        LatencyTrace.behaviour(eventToPublish.getId());
        Event published = eventToPublish;
        AfterCommit.run(() -> LatencyTrace.measure("behaviour_publish", () -> { safePublishBehaviour(agent.getId(), published); return null; }));
    }
    private static Event committedResponse(Agent agent,Event responseEvent){
        if(responseEvent==null)return null;
        List<Event> events = agent.getEventHistory().toList();
        for (int i = events.size() - 1; i >= 0; i--) {
            Event candidate = events.get(i);
            if (!Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(candidate.getType())) {
                continue;
            }
            if (responseEvent.sourcePosition()!=null&&!responseEvent.sourcePosition().equals(candidate.sourcePosition())
                    || !java.util.Objects.equals(candidate.getPayload(), responseEvent.getPayload())) {
                continue;
            }
            return candidate;
        }
        return responseEvent;
    }

    private Event acknowledgeComputedSocialSituationChange(Agent agent, Event sourceEvent, PolicyRuntime runtime) {
        if (agent == null || sourceEvent == null || runtime == null) {
            return null;
        }
        if (!Event.TYPE_SOCIAL_GROUPING.equals(sourceEvent.getType())) {
            return null;
        }
        return this.socialSituationChangeDetector.detect(agent.getEventHistory())
                .map(change -> agent.acknowledge(change, runtime))
                .orElse(null);
    }

    private void safePublishMonitor(Agent agent) {
        try {
            this.monitorBroadcaster.publish(agent);
        } catch (Throwable failure) {
            LOGGER.debug("SSE monitor publish failed in service boundary; agentId={}",
                    agent == null ? null : agent.getId(), failure);
        }
    }

    private void safePublishBehaviour(UUID agentId, Event event) {
        try {
            this.behaviourBroadcaster.publish(agentId, event);
        } catch (Throwable failure) {
            LOGGER.debug("SSE behaviour publish failed in service boundary; agentId={}", agentId, failure);
        }
    }

    private void applyOmittedModalities(Event responseEvent, List<String> omitModalities) {
        if (responseEvent == null || omitModalities == null || omitModalities.isEmpty()) {
            return;
        }
        if (!Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(responseEvent.getType())) {
            return;
        }
        BehaviourPlan plan = BehaviourPlan.fromJson(responseEvent.getPayload());
        if (plan == null) {
            return;
        }
        Set<String> normalized = new HashSet<>();
        for (String modality : omitModalities) {
            String key = normalizeModality(modality);
            if (key != null) {
                normalized.add(key);
            }
        }
        if (normalized.contains("speech")) {
            plan.setSpeech(null);
        }
        if (normalized.contains("nonverbal")) {
            plan.setNonVerbal(null);
        }
        if (normalized.contains("motion")) {
            plan.setMotion(null);
        }
        if (normalized.contains("display")) {
            plan.setDisplay(null);
        }
        responseEvent.setPayload(plan.toJson());
    }

    private static String normalizeModality(String modality) {
        if (modality == null || modality.isBlank()) {
            return null;
        }
        String value = modality.trim().toLowerCase().replace("-", "").replace("_", "");
        return switch (value) {
            case "speech" -> "speech";
            case "nonverbal" -> "nonverbal";
            case "motion" -> "motion";
            case "display" -> "display";
            default -> null;
        };
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String valueOrDefault(String value, String fallback) {
        return isPresent(value) ? value : fallback;
    }

    public PolicyRuntime runtime() {
        return this.runtime(OutputProfile.FULL_PLAN);
    }

    public PolicyRuntime runtime(OutputProfile outputProfile) {
        OutputProfile resolved = outputProfile == null ? OutputProfile.FULL_PLAN : outputProfile;
        return new PolicyRuntime(this.promptMessageAssembler, this.languageModelGateway, resolved);
    }
}
