package ch.zhaw.prometheus.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@Service
public class AgentApplicationService {
    private final AgentRepository repository;
    private final AgentMonitorBroadcaster monitorBroadcaster;
    private final PromptMessageAssembler promptMessageAssembler;
    private final LanguageModelGateway languageModelGateway;

    public AgentApplicationService(AgentRepository repository, AgentMonitorBroadcaster monitorBroadcaster,
            PromptMessageAssembler promptMessageAssembler, LanguageModelGateway languageModelGateway) {
        this.repository = repository;
        this.monitorBroadcaster = monitorBroadcaster;
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
        this.persistAndPublish(agent);
        return Optional.of(new ResponseView(starter, agent.isActive()));
    }

    public Optional<ResponseView> tick(UUID agentID) {
        Optional<Agent> agentMaybe = this.findAgent(agentID);
        if (agentMaybe.isEmpty()) {
            return Optional.empty();
        }
        Agent agent = agentMaybe.get();
        Event response = agent.tick(this.runtime());
        this.persistAndPublish(agent);
        return Optional.of(new ResponseView(response, agent.isActive()));
    }

    public Optional<ResponseView> respond(UUID agentID, EventRequest request) {
        Optional<Agent> agentMaybe = this.findAgent(agentID);
        if (agentMaybe.isEmpty()) {
            return Optional.empty();
        }
        Agent agent = agentMaybe.get();
        Event event = new Event(request.getType(), request.getActor(), request.getKind(), request.getPayload());
        Event response = agent.respond(event, this.runtime());
        this.persistAndPublish(agent);
        return Optional.of(new ResponseView(response, agent.isActive()));
    }

    public boolean acknowledge(UUID agentID, EventRequest request) {
        Optional<Agent> agentMaybe = this.findAgent(agentID);
        if (agentMaybe.isEmpty()) {
            return false;
        }
        Agent agent = agentMaybe.get();
        Event event = new Event(request.getType(), request.getActor(), request.getKind(), request.getPayload());
        agent.acknowledge(event, this.runtime());
        this.persistAndPublish(agent);
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
        this.persistAndPublish(agent);
        return Optional.of(new ResponseView(response, agent.isActive()));
    }

    public Optional<PolicyResponseView> prompt(UUID agentID) {
        Optional<Agent> agentMaybe = this.findAgent(agentID);
        if (agentMaybe.isEmpty()) {
            return Optional.empty();
        }
        Agent agent = agentMaybe.get();
        return Optional.of(new PolicyResponseView(agent.getTotalPolicy(), agent.isActive()));
    }

    public Optional<SseEmitter> subscribeMonitor(UUID agentID) {
        Optional<Agent> agentMaybe = this.findAgent(agentID);
        if (agentMaybe.isEmpty()) {
            return Optional.empty();
        }
        SseEmitter emitter = this.monitorBroadcaster.subscribe(agentID, () -> this.findAgent(agentID));
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
        Policy policy = new PromptPolicy(data.getStatePrompt(),
                data.getStateStarterPrompt(), PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        State state = new State(data.getStateName(), policy, List.of(transition));

        Agent agent = new Agent(data.getAgentName(), data.getAgentDescription(), state, storage);
        agent.start(this.runtime());
        Agent saved = this.repository.save(agent);
        this.monitorBroadcaster.publish(saved);
        return Optional.of(new AgentInfoView(saved.getId(), saved.getName(), saved.getDescription(), saved.isActive()));
    }

    private Optional<Agent> findAgent(UUID agentID) {
        return this.repository.findById(agentID);
    }

    private void persistAndPublish(Agent agent) {
        this.repository.save(agent);
        this.monitorBroadcaster.publish(agent);
    }

    public PolicyRuntime runtime() {
        return new PolicyRuntime(this.promptMessageAssembler, this.languageModelGateway);
    }
}

