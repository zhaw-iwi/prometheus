package ch.zhaw.prometheus.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.JsonElement;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.event.EventSelector;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PolicyResult;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.regulation.ModulationBundle;
import ch.zhaw.prometheus.model.regulation.NoOpRegulationSystem;
import ch.zhaw.prometheus.model.regulation.PersistableRegulationSystem;
import ch.zhaw.prometheus.model.regulation.RegulationContext;
import ch.zhaw.prometheus.model.regulation.RegulationResult;
import ch.zhaw.prometheus.model.regulation.RegulationSystem;
import ch.zhaw.prometheus.model.regulation.RegulationSystemSpec;
import ch.zhaw.prometheus.model.snapshot.SnapshotAggregator;
import ch.zhaw.prometheus.model.snapshot.SnapshotAggregatorType;
import ch.zhaw.prometheus.spi.ContenFilterException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Transient;

@Entity
public class Agent {
    private UUID executionEpoch;

    public UUID executionEpoch() {
        if (executionEpoch == null) executionEpoch = UUID.randomUUID();
        return executionEpoch;
    }

    @Id
    @GeneratedValue
    private UUID id;

    public UUID getId() {
        return this.id;
    }

    protected Agent() {

    }

    private String name;
    private String description;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private State initialState;
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private State currentState;
    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Storage storage;
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private EventHistory eventHistory;
    @Column(length = 10000)
    private String regulationSystemSpecJson;
    @Enumerated(EnumType.STRING)
    private SnapshotAggregatorType regulationSnapshotAggregatorType;
    @Column(length = 4096)
    private String interactionProfileJson;
    @Column(length = 16)
    private String languageCode;

    @Transient
    private RegulationSystem regulationSystem;
    @Transient
    private ModulationBundle latestModulation;

    public Agent(String name, String description, State initialState) {
        this(name, description, initialState, null);
    }

    public Agent(String name, String description, State initialState, Storage storage) {
        this.name = name;
        this.description = description;
        this.initialState = initialState;
        this.storage = storage;
        this.currentState = this.initialState;
        this.eventHistory = new EventHistory();
        this.regulationSystem = new NoOpRegulationSystem();
        this.regulationSystemSpecJson = RegulationSystemSpec.noOp().toJson();
        this.regulationSnapshotAggregatorType = SnapshotAggregatorType.DEFAULT_OBSERVATION;
        this.interactionProfileJson = AgentInteractionProfile.empty().toJson();
        this.latestModulation = ModulationBundle.neutral();
        this.attachEventHistory();
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public String getLanguageCode() {
        return normalizeLanguageCode(this.languageCode);
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = normalizeLanguageCode(languageCode);
    }

    public AgentInteractionProfile getInteractionProfile() {
        if (this.interactionProfileJson == null || this.interactionProfileJson.isBlank()) {
            this.interactionProfileJson = AgentInteractionProfile.empty().toJson();
        }
        return AgentInteractionProfile.fromJson(this.interactionProfileJson);
    }

    public void setInteractionProfile(AgentInteractionProfile interactionProfile) {
        AgentInteractionProfile resolved = interactionProfile == null ? AgentInteractionProfile.empty()
                : interactionProfile;
        this.interactionProfileJson = resolved.toJson();
    }

    public State getCurrentState() {
        return this.currentState;
    }

    public EventHistory getEventHistory() {
        return this.eventHistory;
    }

    public List<Event> getEventsForState(String stateName) {
        if (this.eventHistory == null) {
            return List.of();
        }
        return this.eventHistory.selectList(EventSelector.stateName(stateName));
    }

    public List<Event> getCurrentStateEventHistory() {
        if (this.currentState == null) {
            return List.of();
        }
        return this.currentState.getEventHistory().toList();
    }

    @JsonIgnore
    public Map<String, JsonElement> getStorage() {
        if (this.storage == null) {
            return java.util.Map.of();
        }
        return this.storage.toMap();
    }

    public com.google.gson.JsonObject handScene() {
        var result=ch.zhaw.prometheus.model.hand.HandScene.get(storage);
        if(storage.containsKey(ch.zhaw.prometheus.model.hand.ObservedTargets.KEY))result.add("observed",new com.google.gson.Gson().toJsonTree(ch.zhaw.prometheus.model.hand.ObservedTargets.fresh(storage,java.time.Instant.now())));
        return result;
    }
    public com.google.gson.JsonObject handExecution(com.google.gson.JsonObject request,ch.zhaw.prometheus.model.policy.ExternalSpeech owner,java.time.Instant now){
        if(!ch.zhaw.prometheus.model.hand.HandFeatures.outputSequences(getInteractionProfile()))throw new IllegalArgumentException("Output sequences not declared");
        return request==null?ch.zhaw.prometheus.model.hand.HandExecution.view(storage,now):ch.zhaw.prometheus.model.hand.HandExecution.receipt(storage,getEventHistory(),executionEpoch(),owner,request,now);
    }
    public void interruptHandExecution(String reason){ch.zhaw.prometheus.model.hand.HandExecution.cancel(storage,reason,java.time.Instant.now());}
    public com.google.gson.JsonObject bindHandTarget(com.google.gson.JsonObject request,java.time.Instant now){
        if(!ch.zhaw.prometheus.model.hand.HandFeatures.dynamicTargets(getInteractionProfile()))throw new IllegalArgumentException("Detected targets not declared");
        var result=ch.zhaw.prometheus.model.hand.ObservedTargets.bind(storage,request,now);
        interruptHandExecution("target_binding_changed");
        if(ch.zhaw.prometheus.model.task.TaskMemory.active(storage))ch.zhaw.prometheus.model.coordination.CoordinationMemory.phase(storage,"PAUSED");
        ch.zhaw.prometheus.model.coordination.CoordinationMemory.revise(storage);
        if(storage.containsKey(ch.zhaw.prometheus.model.coordination.CoordinationMemory.DRAFT))ch.zhaw.prometheus.model.coordination.CoordinationMemory.resetPreparation(storage,now);
        return result;
    }
    public boolean admitSceneTargets(Event event,java.time.Instant now){
        if(!ch.zhaw.prometheus.model.hand.HandFeatures.dynamicTargets(getInteractionProfile())||!getInteractionProfile().supportsObservation(Event.TYPE_SCENE_TARGETS)||!Event.KIND_OBSERVATION.equals(event.getKind()))throw new IllegalArgumentException("Detected targets not declared");
        return ch.zhaw.prometheus.model.hand.ObservedTargets.admit(storage,getId(),event,now);
    }
    public com.google.gson.JsonObject configureHandScene(com.google.gson.JsonObject request,java.time.Instant now) {
        if(!ch.zhaw.prometheus.model.hand.HandFeatures.targets(getInteractionProfile()))throw new IllegalArgumentException("Create a target-capable instance");
        var result=ch.zhaw.prometheus.model.hand.HandScene.configure(storage,getId(),request,getEventHistory(),now);
        interruptHandExecution("scene_changed");
        // Keep accepted terms intact, but never silently re-arm them against new destinations.
        if(ch.zhaw.prometheus.model.task.TaskMemory.active(storage))ch.zhaw.prometheus.model.coordination.CoordinationMemory.phase(storage,"PAUSED");
        ch.zhaw.prometheus.model.coordination.CoordinationMemory.revise(storage);
        if(storage.containsKey(ch.zhaw.prometheus.model.coordination.CoordinationMemory.DRAFT))ch.zhaw.prometheus.model.coordination.CoordinationMemory.resetPreparation(storage,now);
        return result;
    }
    public void resolveHandPointing(Event event,java.time.Instant now) {
        if(!ch.zhaw.prometheus.model.event.Event.TYPE_HAND_POINTING.equals(event.getType())||!ch.zhaw.prometheus.model.hand.HandFeatures.targets(getInteractionProfile()))return;
        var observation=ch.zhaw.prometheus.model.hand.HandPointObservation.parse(event.getPayload());
        var json=com.google.gson.JsonParser.parseString(event.getPayload()).getAsJsonObject();
        if(ch.zhaw.prometheus.model.hand.HandFeatures.dynamicTargets(getInteractionProfile()))json.add("sourceAssociation",ch.zhaw.prometheus.model.hand.ObservedTargets.source(storage,observation,now));
        json.add("resolution",ch.zhaw.prometheus.model.hand.HandScene.resolve(storage,observation,now));event.setPayload(json.toString());
        if(ch.zhaw.prometheus.model.hand.HandFeatures.dynamicTargets(getInteractionProfile()))storage.put("hand.scene.pointing.v1",json.deepCopy());
    }

    public boolean isActive() {
        return this.currentState.isActive();
    }

    public Event start(PolicyRuntime runtime) {
        runtime = runtime.forCapabilities(this.getInteractionProfile());
        try {
            Event response = this.currentState.start(runtime);
            return this.recordEvent(response,runtime);
        } catch (ContenFilterException e) {
            throw e;
        }
    }

    public Event tick(PolicyRuntime runtime) {
        if (!this.isActive() || this.currentState == null) {
            return null;
        }
        Event tickEvent = Event.systemTick();
        return this.acknowledge(tickEvent, runtime);
    }

    public Event generate(PolicyRuntime runtime) {
        runtime = runtime.forCapabilities(this.getInteractionProfile());
        if (this.currentState == null) {
            return null;
        }
        Event response = this.currentState.generate(runtime);
        return this.recordEvent(response,runtime);
    }

    public Event acknowledge(Event event, PolicyRuntime runtime) {
        runtime = runtime.forCapabilities(this.getInteractionProfile());
        Event response = this.acknowledgeWithoutRegulation(event, true, runtime);
        Event responseFromRegulation = this.applyRegulation(event, runtime);
        return responseFromRegulation != null ? responseFromRegulation : response;
    }

    /** Records externally generated assistant speech without evaluating guards, actions or regulation. */
    public Event recordExternalSpeech(String text, ch.zhaw.prometheus.model.event.SpeechProvenance provenance) {
        if (text == null || text.isBlank() || text.length() > 3000 || provenance == null
                || !provenance.realized())
            throw new IllegalArgumentException("Invalid realized speech");
        Event event = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                ch.zhaw.prometheus.model.behaviour.BehaviourPlan.speechOnly(text).toJson());
        if (event.getPayload().length() > 4096) throw new IllegalArgumentException("Native speech payload too large");
        event.speechProvenance(provenance);
        return recordEvent(event);
    }

    private Event acknowledgeWithoutRegulation(Event event, boolean recordInput, PolicyRuntime runtime) {
        GuardEvaluation evaluation = null;
        try {
            if (recordInput) {
                this.recordEvent(event);
            }
            if (runtime.behaviourSpeculation() != null) runtime.behaviourSpeculation().prepare(this.currentState, event, runtime);
            evaluation = GuardEvaluation.prepare(this.currentState, runtime);
            PolicyRuntime turnRuntime = runtime.withGuardEvaluation(evaluation);
            Event response = this.currentState.acknowledge(event, turnRuntime);
            return this.recordEvent(response,runtime);
        } catch (TransitionException e) {
            this.currentState = e.getSubsequentState();
            if (this.currentState.isStarting()) {
                Event response = this.currentState.start(runtime);
                return this.recordEvent(response,runtime);
            }
            this.currentState.enter();
            return this.acknowledgeWithoutRegulation(event, false, runtime);
        } finally {
            if (evaluation != null) evaluation.invalidate();
        }
    }

    public RegulationSystem getRegulationSystem() {
        if (this.regulationSystem == null) {
            this.regulationSystem = this.resolveRegulationSystemFromSpec();
        }
        return this.regulationSystem;
    }

    public void setRegulationSystem(RegulationSystem regulationSystem) {
        if (regulationSystem == null) {
            this.regulationSystem = new NoOpRegulationSystem();
            this.regulationSystemSpecJson = RegulationSystemSpec.noOp().toJson();
            return;
        }
        if (!(regulationSystem instanceof PersistableRegulationSystem persistable)) {
            throw new IllegalArgumentException(
                    "regulation system must implement PersistableRegulationSystem: "
                            + regulationSystem.getClass().getName());
        }
        this.regulationSystem = regulationSystem;
        this.regulationSystemSpecJson = persistable.toSpec().toJson();
    }

    public ModulationBundle getLatestModulation() {
        if (this.latestModulation == null) {
            this.latestModulation = ModulationBundle.neutral();
        }
        return this.latestModulation;
    }

    public SnapshotAggregatorType getRegulationSnapshotAggregatorType() {
        if (this.regulationSnapshotAggregatorType == null) {
            this.regulationSnapshotAggregatorType = SnapshotAggregatorType.DEFAULT_OBSERVATION;
        }
        return this.regulationSnapshotAggregatorType;
    }

    public void setRegulationSnapshotAggregatorType(SnapshotAggregatorType regulationSnapshotAggregatorType) {
        if (regulationSnapshotAggregatorType == null) {
            this.regulationSnapshotAggregatorType = SnapshotAggregatorType.DEFAULT_OBSERVATION;
            return;
        }
        this.regulationSnapshotAggregatorType = regulationSnapshotAggregatorType;
    }

    public PolicyResult getTotalPolicy() {
        return this.getTotalPolicy(null);
    }

    public PolicyResult getTotalPolicy(PromptMessageAssembler promptMessageAssembler) {
        return this.getTotalPolicy(promptMessageAssembler, OutputProfile.FULL_PLAN);
    }

    public PolicyResult getTotalPolicy(PromptMessageAssembler promptMessageAssembler, OutputProfile outputProfile) {
        return this.currentState.getPolicyBundle(null, promptMessageAssembler, outputProfile);
    }

    public List<String> listStates() {
        return this.reachableStates().stream().map(State::getName).distinct().toList();
    }

    State initialState() { return this.initialState; }

    /** Read-only graph inspection, including inactive states; no transitions or policies execute. */
    public List<Action> reachableActions() {
        return reachableStates().stream().flatMap(state -> state.getTransitions().stream())
                .flatMap(transition -> transition.getActions().stream()).distinct().toList();
    }
    public List<State> reachableStates() {
        Set<State> visited = new HashSet<>();
        List<State> states = new ArrayList<>();
        this.initialState.collectStates(visited, states);
        this.currentState.collectStates(visited, states);
        return List.copyOf(states);
    }

    public void reset() {
        ch.zhaw.prometheus.model.hand.HandExecution.clear(storage);
        this.executionEpoch = UUID.randomUUID();
        this.currentState = this.initialState;
        this.currentState.reset();
        if (this.eventHistory != null) {
            this.eventHistory.reset();
        }
        this.getRegulationSystem().reset();
        this.latestModulation = ModulationBundle.neutral();
        this.syncRegulationSystemSpecFromRuntime();
    }

    public void resetCurrentState() {
        this.currentState.reset();
    }

    @Override
    public String toString() {
        return "Agent with current state " + this.currentState;
    }

    @PostLoad
    private void postLoad() {
        if (this.eventHistory == null) {
            this.eventHistory = new EventHistory();
        }
        if (this.regulationSystem == null) {
            this.regulationSystem = this.resolveRegulationSystemFromSpec();
        }
        if (this.regulationSystemSpecJson == null || this.regulationSystemSpecJson.isBlank()) {
            this.regulationSystemSpecJson = RegulationSystemSpec.noOp().toJson();
        }
        if (this.regulationSnapshotAggregatorType == null) {
            this.regulationSnapshotAggregatorType = SnapshotAggregatorType.DEFAULT_OBSERVATION;
        }
        if (this.interactionProfileJson == null || this.interactionProfileJson.isBlank()) {
            this.interactionProfileJson = AgentInteractionProfile.empty().toJson();
        }
        this.languageCode = normalizeLanguageCode(this.languageCode);
        if (this.latestModulation == null) {
            this.latestModulation = ModulationBundle.neutral();
        }
        this.attachEventHistory();
    }

    private static String normalizeLanguageCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void attachEventHistory() {
        if (this.initialState == null || this.eventHistory == null) {
            return;
        }
        Set<State> visited = new HashSet<>();
        List<State> states = new ArrayList<>();
        this.initialState.collectStates(visited, states);
        for (State state : states) {
            state.setEventHistory(this.eventHistory);
            if (state instanceof ch.zhaw.prometheus.model.commons.states.SmallTalkState
                    || state instanceof ch.zhaw.prometheus.model.commons.states.DynamicActionableCoachingState) {
                for (Transition transition : state.getTransitions()) for (Action action : transition.getActions())
                    if (!action.hasExplicitExecutionMode()
                            && action instanceof ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction)
                        action.nonBlocking();
            }
        }
    }

    private Event applyRegulation(Event triggerEvent, PolicyRuntime runtime) {
        if (triggerEvent == null || this.currentState == null) {
            return null;
        }
        if (isInternalRegulationEvent(triggerEvent)) {
            return null;
        }
        RegulationResult result = this.getRegulationSystem()
                .update(new RegulationContext(triggerEvent,
                        this.eventHistory,
                        this.getRegulationSnapshotAggregator().aggregate(this.eventHistory), Instant.now()));
        if (result == null) {
            return null;
        }
        this.latestModulation = result.modulation() == null ? ModulationBundle.neutral() : result.modulation();
        if (result.internalEvents() == null || result.internalEvents().isEmpty()) {
            this.syncRegulationSystemSpecFromRuntime();
            return null;
        }
        Event latestResponse = null;
        for (Event internal : result.internalEvents()) {
            if (internal == null) {
                continue;
            }
            Event normalized = new Event(
                    internal.getType(),
                    internal.getActor(),
                    internal.getKind(),
                    internal.getPayload());
            Event response = this.acknowledgeWithoutRegulation(normalized, true, runtime);
            if (response != null) {
                latestResponse = response;
            }
        }
        this.syncRegulationSystemSpecFromRuntime();
        return latestResponse;
    }

    private SnapshotAggregator getRegulationSnapshotAggregator() {
        if (this.regulationSnapshotAggregatorType == null) {
            this.regulationSnapshotAggregatorType = SnapshotAggregatorType.DEFAULT_OBSERVATION;
        }
        return this.regulationSnapshotAggregatorType.create();
    }

    private static boolean isInternalRegulationEvent(Event event) {
        String type = event.getType();
        return type != null && type.startsWith("int.");
    }

    private Event recordEvent(Event event) {
        return recordEvent(event,null);
    }
    private Event recordEvent(Event event,PolicyRuntime runtime) {
        if (event == null || this.eventHistory == null || this.currentState == null) {
            return null;
        }
        event.setStatePath(this.currentState.getActiveStatePath());
        event=this.eventHistory.appendEvent(event);
        if(ch.zhaw.prometheus.model.hand.HandFeatures.outputSequences(getInteractionProfile())){
            if(Event.TYPE_USER_UTTERANCE.equals(event.getType()))interruptHandExecution("new_user_turn");
            if(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType())){
                if(event.getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>4096)throw new IllegalArgumentException("Behaviour event exceeds 4 KiB");
                ch.zhaw.prometheus.model.hand.HandExecution.publish(storage,event,executionEpoch(),runtime==null?null:runtime.externalSpeech(),java.time.Instant.now());
            }
        }
        return event;
    }

    private RegulationSystem resolveRegulationSystemFromSpec() {
        if (this.regulationSystemSpecJson == null || this.regulationSystemSpecJson.isBlank()) {
            return new NoOpRegulationSystem();
        }
        RegulationSystemSpec spec = RegulationSystemSpec.fromJson(this.regulationSystemSpecJson);
        if (spec == null) {
            return new NoOpRegulationSystem();
        }
        return spec.toRegulationSystem();
    }

    private void syncRegulationSystemSpecFromRuntime() {
        if (!(this.regulationSystem instanceof PersistableRegulationSystem persistable)) {
            return;
        }
        this.regulationSystemSpecJson = persistable.toSpec().toJson();
    }
}

