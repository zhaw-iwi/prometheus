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

import ch.zhaw.prometheus.controllers.AgentMetaType;
import ch.zhaw.prometheus.controllers.dto.SingleStateAgentCreateDTO;
import ch.zhaw.prometheus.controllers.views.AgentInfoView;
import ch.zhaw.prometheus.controllers.views.AgentStateInfoView;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.controllers.views.PolicyResponseView;
import ch.zhaw.prometheus.controllers.views.ResponseView;
import ch.zhaw.prometheus.controllers.views.StorageEntryView;
import ch.zhaw.prometheus.logging.AgentBehaviourBroadcaster;
import ch.zhaw.prometheus.logging.AgentMonitorBroadcaster;
import ch.zhaw.prometheus.model.Action;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Decision;
import ch.zhaw.prometheus.model.Final;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.OutputProfile;
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

    public AgentApplicationService(AgentRepository repository, AgentMonitorBroadcaster monitorBroadcaster,
            AgentBehaviourBroadcaster behaviourBroadcaster,
            PromptMessageAssembler promptMessageAssembler, LanguageModelGateway languageModelGateway) {
        this.repository = repository;
        this.monitorBroadcaster = monitorBroadcaster;
        this.behaviourBroadcaster = behaviourBroadcaster;
        this.promptMessageAssembler = promptMessageAssembler;
        this.languageModelGateway = languageModelGateway;
    }

    public List<AgentInfoView> listAgents() {
        List<Agent> agents = this.repository.findAll();
        List<AgentInfoView> result = new ArrayList<>();
        for (Agent current : agents) {
            result.add(new AgentInfoView(current.getId(), current.getName(), current.getDescription(), current.isActive()));
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
                agent.getDescription(), agent.isActive()));
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
        return Optional.of(new AgentStateInfoView(stateName, innerName, innerNames));
    }

    public Optional<List<String>> getAgentStates(UUID agentID) {
        return this.findAgent(agentID).map(Agent::listStates);
    }

    public Optional<List<StorageEntryView>> getAgentStorage(UUID agentID) {
        return this.findAgent(agentID).map(agent -> agent.getStorage().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map((entry) -> new StorageEntryView(entry.getKey(),
                        entry.getValue() == null ? "null" : entry.getValue().toString()))
                .toList());
    }

    public Optional<List<Event>> getAgentEventHistory(UUID agentID) {
        return this.findAgent(agentID).map(agent -> agent.getEventHistory().toList());
    }

    public Optional<ResponseView> start(UUID agentID) {
        Optional<Agent> agentMaybe = this.findAgent(agentID);
        if (agentMaybe.isEmpty()) {
            return Optional.empty();
        }
        Agent agent = agentMaybe.get();
        Event starter = agent.start(this.runtime());
        Agent saved = this.persistAndPublishMonitor(agent);
        this.publishBehaviour(saved, starter);
        return Optional.of(new ResponseView(starter, agent.isActive()));
    }

    public BehaviourGenerationOutcome generate(UUID agentID, List<String> omitModalities) {
        return this.generate(agentID, omitModalities, OutputProfile.FULL_PLAN);
    }

    public BehaviourGenerationOutcome generate(UUID agentID, List<String> omitModalities, OutputProfile outputProfile) {
        Optional<Agent> agentMaybe = this.findAgent(agentID);
        if (agentMaybe.isEmpty()) {
            return BehaviourGenerationOutcome.AGENT_NOT_FOUND;
        }
        Agent agent = agentMaybe.get();
        OutputProfile resolvedProfile = outputProfile == null ? OutputProfile.FULL_PLAN : outputProfile;
        Event response = agent.generate(this.runtime(resolvedProfile));
        if (response == null) {
            return BehaviourGenerationOutcome.NO_BEHAVIOUR_GENERATED;
        }
        this.applyOmittedModalities(response, omitModalities);
        Agent saved = this.persistAndPublishMonitor(agent);
        this.publishBehaviour(saved, response);
        return BehaviourGenerationOutcome.GENERATED;
    }

    public boolean acknowledge(UUID agentID, EventRequest request) {
        Optional<Agent> agentMaybe = this.findAgent(agentID);
        if (agentMaybe.isEmpty()) {
            return false;
        }
        Agent agent = agentMaybe.get();
        Event event = new Event(request.getType(), request.getActor(), request.getKind(), request.getPayload());
        Event response = agent.acknowledge(event, this.runtime());
        Agent saved = this.persistAndPublishMonitor(agent);
        this.publishBehaviour(saved, response);
        return true;
    }

    public Optional<ResponseView> reset(UUID agentID) {
        Optional<Agent> agentMaybe = this.findAgent(agentID);
        if (agentMaybe.isEmpty()) {
            return Optional.empty();
        }
        Agent agent = agentMaybe.get();
        agent.reset();
        Event response = agent.start(this.runtime());
        Agent saved = this.persistAndPublishMonitor(agent);
        this.publishBehaviour(saved, response);
        return Optional.of(new ResponseView(response, agent.isActive()));
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
                new PolicyResponseView(agent.getTotalPolicy(this.promptMessageAssembler, resolvedProfile), agent.isActive()));
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
        Optional<Agent> agentMaybe = this.findAgent(agentID);
        if (agentMaybe.isEmpty()) {
            return Optional.empty();
        }
        SseEmitter emitter = this.behaviourBroadcaster.subscribe(agentID, () -> this.findAgent(agentID));
        return Optional.of(emitter);
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
        Event starter = agent.start(this.runtime());
        Agent saved = this.repository.save(agent);
        safePublishMonitor(saved);
        this.publishBehaviour(saved, starter);
        return Optional.of(new AgentInfoView(saved.getId(), saved.getName(), saved.getDescription(), saved.isActive()));
    }

    private Optional<Agent> findAgent(UUID agentID) {
        return this.repository.findById(agentID);
    }

    private Agent persistAndPublishMonitor(Agent agent) {
        Agent saved = this.repository.save(agent);
        safePublishMonitor(saved);
        return saved;
    }

    private void publishBehaviour(Agent agent, Event responseEvent) {
        if (agent == null || responseEvent == null) {
            return;
        }
        Event eventToPublish = responseEvent;
        List<Event> events = agent.getEventHistory().toList();
        for (int i = events.size() - 1; i >= 0; i--) {
            Event candidate = events.get(i);
            if (!Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(candidate.getType())) {
                continue;
            }
            if (!java.util.Objects.equals(candidate.getPayload(), responseEvent.getPayload())) {
                continue;
            }
            eventToPublish = candidate;
            break;
        }
        safePublishBehaviour(agent.getId(), eventToPublish);
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

    public PolicyRuntime runtime() {
        return this.runtime(OutputProfile.FULL_PLAN);
    }

    public PolicyRuntime runtime(OutputProfile outputProfile) {
        OutputProfile resolved = outputProfile == null ? OutputProfile.FULL_PLAN : outputProfile;
        return new PolicyRuntime(this.promptMessageAssembler, this.languageModelGateway, resolved);
    }
}

