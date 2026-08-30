package ch.zhaw.prometheus.application;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.JsonNode;

import ch.zhaw.prometheus.controllers.views.AgentInfoView;
import ch.zhaw.prometheus.controllers.views.AgentStateInfoView;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.controllers.views.PolicyResponseView;
import ch.zhaw.prometheus.controllers.views.ResponseView;
import ch.zhaw.prometheus.controllers.views.StorageEntryView;
import ch.zhaw.prometheus.definition.compiled.CompiledAgentDefinition;
import ch.zhaw.prometheus.definition.compiled.CompiledAtomicState;
import ch.zhaw.prometheus.definition.compiled.CompiledCompositeState;
import ch.zhaw.prometheus.definition.compiled.CompiledState;
import ch.zhaw.prometheus.definition.compiled.ImmutableJson;
import ch.zhaw.prometheus.definition.component.CompiledPolicy;
import ch.zhaw.prometheus.definition.compiled.CompiledStorageBinding;
import ch.zhaw.prometheus.definition.component.builtin.PromptPolicyComponent;
import ch.zhaw.prometheus.definition.instance.DeclarativeAgentExecution;
import ch.zhaw.prometheus.definition.instance.DeclarativeAgentInstanceService;
import ch.zhaw.prometheus.definition.instance.DeclarativeAgentNotFoundException;
import ch.zhaw.prometheus.definition.instance.LoadedDeclarativeAgent;
import ch.zhaw.prometheus.definition.instance.PersistedDeclarativeAgent;
import ch.zhaw.prometheus.definition.instance.RuntimeInstanceStatus;
import ch.zhaw.prometheus.definition.runtime.AgentRuntimeContext;
import ch.zhaw.prometheus.definition.runtime.BuiltInRuntimeComponentExecutor;
import ch.zhaw.prometheus.definition.runtime.LanguageModelRuntimeGateway;
import ch.zhaw.prometheus.definition.runtime.RuntimeBehaviour;
import ch.zhaw.prometheus.definition.runtime.RuntimeComponentExecutor;
import ch.zhaw.prometheus.definition.runtime.RuntimeEvent;
import ch.zhaw.prometheus.definition.runtime.RuntimeInvocation;
import ch.zhaw.prometheus.definition.runtime.RuntimePromptBundle;
import ch.zhaw.prometheus.definition.runtime.RuntimeStorage;
import ch.zhaw.prometheus.logging.AgentBehaviourBroadcaster;
import ch.zhaw.prometheus.logging.AgentMonitorBroadcaster;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.model.social.SocialSituationChangeDetector;

@Service
public class AgentApplicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentApplicationService.class);

    private final DeclarativeAgentInstanceService instances;
    private final AgentMonitorBroadcaster monitorBroadcaster;
    private final AgentBehaviourBroadcaster behaviourBroadcaster;
    private final LanguageModelRuntimeGateway modelGateway;
    private final SocialSituationChangeDetector socialSituationChangeDetector;

    public AgentApplicationService(DeclarativeAgentInstanceService instances,
            AgentMonitorBroadcaster monitorBroadcaster, AgentBehaviourBroadcaster behaviourBroadcaster,
            LanguageModelRuntimeGateway modelGateway) {
        this.instances = instances;
        this.monitorBroadcaster = monitorBroadcaster;
        this.behaviourBroadcaster = behaviourBroadcaster;
        this.modelGateway = modelGateway;
        this.socialSituationChangeDetector = SocialSituationChangeDetector.defaultThresholds();
    }

    public CreatedDeclarativeAgent create(String definitionKey) {
        var creation = this.instances.create(definitionKey, context(List.of()));
        LoadedDeclarativeAgent loaded = this.instances.find(creation.instance().id()).orElseThrow();
        Agent agent = toAgent(loaded);
        Event startup = lastBehaviour(creation.startup().appendedEvents());
        safePublishMonitor(agent);
        publishBehaviour(agent.getId(), startup);
        return new CreatedDeclarativeAgent(agent, startup);
    }

    public boolean delete(UUID agentId) {
        return this.instances.delete(agentId);
    }

    public List<AgentInfoView> listAgents() {
        return this.instances.findAll().stream().map(AgentApplicationService::toAgent)
                .map(AgentApplicationService::toInfo).toList();
    }

    public List<Agent> listAgentAggregates() {
        return this.instances.findAll().stream().map(AgentApplicationService::toAgent).toList();
    }

    public Optional<Agent> getAgentById(UUID agentId) {
        return this.instances.find(agentId).map(AgentApplicationService::toAgent);
    }

    public Optional<AgentInfoView> getAgentInfo(UUID agentId) {
        return getAgentById(agentId).map(AgentApplicationService::toInfo);
    }

    public Optional<String> getAgentLanguageCode(UUID agentId) {
        return getAgentById(agentId).map(Agent::getLanguageCode).filter(AgentApplicationService::present);
    }

    public Optional<AgentStateInfoView> getAgentState(UUID agentId) {
        return getAgentById(agentId).map(agent -> {
            List<String> path = agent.getActiveStateNames();
            String name = path.isEmpty() ? null : path.getFirst();
            String inner = path.size() < 2 ? null : path.getLast();
            List<String> innerNames = path.size() < 2 ? List.of() : path.subList(1, path.size());
            return new AgentStateInfoView(name, inner, innerNames);
        });
    }

    public Optional<List<String>> getAgentStates(UUID agentId) {
        return getAgentById(agentId).map(Agent::listStates);
    }

    public Optional<List<StorageEntryView>> getAgentStorage(UUID agentId) {
        return getAgentById(agentId).map(agent -> agent.getStorage().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new StorageEntryView(entry.getKey(), entry.getValue().toString())).toList());
    }

    public Optional<List<Event>> getAgentEventHistory(UUID agentId) {
        return getAgentById(agentId).map(Agent::getEventHistory);
    }

    public Optional<List<Event>> getAgentCurrentStateEventHistory(UUID agentId) {
        return getAgentById(agentId).map(Agent::getCurrentStateEventHistory);
    }

    public Optional<ResponseView> start(UUID agentId) {
        try {
            DeclarativeAgentExecution execution = this.instances.start(agentId, context(List.of()));
            return Optional.of(response(execution));
        } catch (DeclarativeAgentNotFoundException notFound) {
            return Optional.empty();
        }
    }

    public BehaviourGenerationOutcome generate(UUID agentId, List<String> omitModalities) {
        return generate(agentId, omitModalities, OutputProfile.FULL_PLAN);
    }

    public BehaviourGenerationOutcome generate(UUID agentId, List<String> omitModalities, OutputProfile profile) {
        try {
            DeclarativeAgentExecution execution = this.instances.generate(agentId, context(omitModalities));
            Event event = lastBehaviour(execution.result().appendedEvents());
            publish(execution.instance(), event);
            return event == null ? BehaviourGenerationOutcome.NO_BEHAVIOUR_GENERATED
                    : BehaviourGenerationOutcome.GENERATED;
        } catch (DeclarativeAgentNotFoundException notFound) {
            return BehaviourGenerationOutcome.AGENT_NOT_FOUND;
        }
    }

    public Optional<ResponseView> acknowledge(UUID agentId, EventRequest request) {
        return acknowledge(agentId, request, OutputProfile.FULL_PLAN);
    }

    public Optional<ResponseView> acknowledge(UUID agentId, EventRequest request, OutputProfile profile) {
        try {
            Event source = new Event(request.getType(), request.getActor(), request.getKind(), request.getPayload());
            DeclarativeAgentExecution execution = this.instances.acknowledge(agentId, source.toRuntime(),
                    context(List.of()));
            Event response = lastBehaviour(execution.result().appendedEvents());
            if (Event.TYPE_SOCIAL_GROUPING.equals(source.getType())) {
                EventHistory history = new EventHistory(execution.instance().history().stream()
                        .map(Event::fromRuntime).toList());
                Optional<Event> computed = this.socialSituationChangeDetector.detect(history);
                if (computed.isPresent()) {
                    execution = this.instances.acknowledge(agentId, computed.get().toRuntime(), context(List.of()));
                    Event computedResponse = lastBehaviour(execution.result().appendedEvents());
                    if (computedResponse != null) {
                        response = computedResponse;
                    }
                }
            }
            publish(execution.instance(), response);
            return Optional.of(new ResponseView(response, isActive(execution.instance())));
        } catch (DeclarativeAgentNotFoundException notFound) {
            return Optional.empty();
        }
    }

    public Optional<ResponseView> reset(UUID agentId) {
        try {
            return Optional.of(response(this.instances.resetAndStart(agentId, context(List.of()))));
        } catch (DeclarativeAgentNotFoundException notFound) {
            return Optional.empty();
        }
    }

    public Optional<PolicyResponseView> prompt(UUID agentId) {
        return prompt(agentId, OutputProfile.FULL_PLAN);
    }

    public Optional<PolicyResponseView> prompt(UUID agentId, OutputProfile profile) {
        return this.instances.find(agentId).map(loaded -> {
            PersistedDeclarativeAgent stored = loaded.instance();
            CompiledAgentDefinition definition = loaded.definition();
            List<CompiledState> path = definition.pathTo(stored.activeLeafStateId());
            List<PromptPolicyComponent> policies = path.stream().map(AgentApplicationService::policy)
                    .filter(PromptPolicyComponent.class::isInstance).map(PromptPolicyComponent.class::cast).toList();
            String responsePrompt = policies.stream().map(PromptPolicyComponent::responsePrompt)
                    .filter(AgentApplicationService::present).map(String::trim)
                    .collect(java.util.stream.Collectors.joining("\n\n"));
            Map<String, ImmutableJson> boundStorage = boundStorage(stored.storage(), policies);
            RuntimeInvocation invocation = new RuntimeInvocation(stored.activeLeafStateId(),
                    path.stream().map(CompiledState::id).toList(), stored.history(), boundStorage);
            RuntimePromptBundle prompts = new RuntimePromptBundle(resolve(responsePrompt, boundStorage),
                    resolve(deepest(policies, PromptPolicyComponent::starterPrompt), boundStorage),
                    resolve(deepest(policies, PromptPolicyComponent::summaryPrompt), boundStorage),
                    resolve(deepest(policies, PromptPolicyComponent::nonverbalPlanPrompt), boundStorage),
                    resolve(deepest(policies, PromptPolicyComponent::gesturePrompt), boundStorage), !stored.started());
            List<ch.zhaw.prometheus.model.policy.PromptMessage> messages = responsePrompt.isBlank()
                    ? List.of() : this.modelGateway.promptMessages(prompts, invocation);
            String stateName = path.isEmpty() ? null : path.getLast().name();
            return new PolicyResponseView(stateName, messages, isActive(stored),
                    !stored.started());
        });
    }

    public Optional<SseEmitter> subscribeMonitor(UUID agentId) {
        if (this.instances.find(agentId).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(this.monitorBroadcaster.subscribe(agentId, () -> getAgentById(agentId)));
    }

    public Optional<SseEmitter> subscribeBehaviour(UUID agentId) {
        return subscribeBehaviour(agentId, null);
    }

    public Optional<SseEmitter> subscribeBehaviour(UUID agentId, String lastEventId) {
        if (this.instances.find(agentId).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(this.behaviourBroadcaster.subscribe(agentId, () -> getAgentById(agentId), lastEventId));
    }

    private ResponseView response(DeclarativeAgentExecution execution) {
        Event event = lastBehaviour(execution.result().appendedEvents());
        publish(execution.instance(), event);
        return new ResponseView(event, isActive(execution.instance()));
    }

    private void publish(PersistedDeclarativeAgent stored, Event event) {
        this.instances.find(stored.id()).map(AgentApplicationService::toAgent).ifPresent(this::safePublishMonitor);
        publishBehaviour(stored.id(), event);
    }

    private void publishBehaviour(UUID agentId, Event event) {
        if (event == null) {
            return;
        }
        try {
            this.behaviourBroadcaster.publish(agentId, event);
        } catch (Throwable failure) {
            LOGGER.debug("SSE behaviour publish failed in service boundary; agentId={}", agentId, failure);
        }
    }

    private void safePublishMonitor(Agent agent) {
        try {
            this.monitorBroadcaster.publish(agent);
        } catch (Throwable failure) {
            LOGGER.debug("SSE monitor publish failed in service boundary; agentId={}",
                    agent == null ? null : agent.getId(), failure);
        }
    }

    private AgentRuntimeContext context(List<String> omitModalities) {
        RuntimeComponentExecutor executor = new BuiltInRuntimeComponentExecutor(this.modelGateway);
        Set<String> omitted = normalizedModalities(omitModalities);
        if (!omitted.isEmpty()) {
            executor = new FilteringExecutor(executor, omitted);
        }
        return new AgentRuntimeContext(executor, RandomGenerator.getDefault());
    }

    private static Agent toAgent(LoadedDeclarativeAgent loaded) {
        PersistedDeclarativeAgent stored = loaded.instance();
        CompiledAgentDefinition definition = loaded.definition();
        List<CompiledState> path = definition.pathTo(stored.activeLeafStateId());
        Map<String, JsonNode> storage = new LinkedHashMap<>();
        stored.storage().forEach((key, value) -> storage.put(key, value.value()));
        return new Agent(stored.id(), stored.definitionRevisionId(), definition.key(),
                definition.metadata().displayName(), definition.metadata().description(),
                isActive(stored),
                AgentInteractionProfile.of(definition.interaction().supportedObservations(),
                        definition.interaction().supportedBehaviourModalities(), definition.interaction().profileTags()),
                definition.metadata().languageCode(), path.stream().map(CompiledState::id).toList(),
                path.stream().map(CompiledState::name).toList(),
                definition.statesById().values().stream().map(CompiledState::name).distinct().toList(), storage,
                stored.history().stream().map(Event::fromRuntime).toList());
    }

    private static AgentInfoView toInfo(Agent agent) {
        return new AgentInfoView(agent.getId(), agent.getName(), agent.getDescription(), agent.isActive(),
                agent.getInteractionProfile(), agent.getLanguageCode());
    }

    private static boolean isActive(PersistedDeclarativeAgent agent) {
        return agent.status() == RuntimeInstanceStatus.ACTIVE;
    }

    private static Event lastBehaviour(List<RuntimeEvent> events) {
        for (int index = events.size() - 1; index >= 0; index--) {
            RuntimeEvent event = events.get(index);
            if (Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.type())) {
                return Event.fromRuntime(event);
            }
        }
        return null;
    }

    private static CompiledPolicy policy(CompiledState state) {
        if (state instanceof CompiledAtomicState atomic) {
            return atomic.policy();
        }
        if (state instanceof CompiledCompositeState composite) {
            return composite.policy();
        }
        return null;
    }

    private static Map<String, ImmutableJson> boundStorage(Map<String, ImmutableJson> storage,
            List<PromptPolicyComponent> policies) {
        Map<String, ImmutableJson> result = new LinkedHashMap<>();
        policies.stream().flatMap(policy -> policy.storageBindings().stream())
                .map(CompiledStorageBinding::key).distinct().forEach(key -> {
                    ImmutableJson value = storage.get(key);
                    if (value != null) {
                        result.put(key, value);
                    }
                });
        return result;
    }

    private static String resolve(String prompt, Map<String, ImmutableJson> storage) {
        String resolved = prompt == null ? "" : prompt;
        for (var entry : storage.entrySet()) {
            resolved = resolved.replace("${" + entry.getKey() + "}", entry.getValue().toString());
        }
        return resolved;
    }

    private static String deepest(List<PromptPolicyComponent> policies,
            java.util.function.Function<PromptPolicyComponent, String> value) {
        for (int index = policies.size() - 1; index >= 0; index--) {
            String candidate = value.apply(policies.get(index));
            if (present(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private static Set<String> normalizedModalities(List<String> omitModalities) {
        Set<String> result = new HashSet<>();
        if (omitModalities == null) {
            return result;
        }
        for (String value : omitModalities) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String normalized = value.trim().toLowerCase().replace("-", "").replace("_", "");
            if (Set.of("speech", "nonverbal", "motion", "display").contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static final class FilteringExecutor implements RuntimeComponentExecutor {
        private final RuntimeComponentExecutor delegate;
        private final Set<String> omitted;

        private FilteringExecutor(RuntimeComponentExecutor delegate, Set<String> omitted) {
            this.delegate = delegate;
            this.omitted = Set.copyOf(omitted);
        }

        @Override
        public RuntimeBehaviour start(List<ch.zhaw.prometheus.definition.component.CompiledPolicy> policies,
                RuntimeInvocation invocation) {
            return filter(this.delegate.start(policies, invocation));
        }

        @Override
        public RuntimeBehaviour generate(List<ch.zhaw.prometheus.definition.component.CompiledPolicy> policies,
                RuntimeInvocation invocation) {
            return filter(this.delegate.generate(policies, invocation));
        }

        @Override
        public boolean decide(ch.zhaw.prometheus.definition.component.CompiledDecision decision,
                RuntimeInvocation invocation) {
            return this.delegate.decide(decision, invocation);
        }

        @Override
        public RuntimeBehaviour execute(ch.zhaw.prometheus.definition.component.CompiledAction action,
                RuntimeInvocation invocation, RuntimeStorage storage) {
            return filter(this.delegate.execute(action, invocation, storage));
        }

        @Override
        public boolean selects(ch.zhaw.prometheus.definition.component.CompiledSelector selector, RuntimeEvent event,
                String evaluatingStateId) {
            return this.delegate.selects(selector, event, evaluatingStateId);
        }

        private RuntimeBehaviour filter(RuntimeBehaviour behaviour) {
            if (behaviour == null) {
                return null;
            }
            return new RuntimeBehaviour(this.omitted.contains("speech") ? null : behaviour.speech(),
                    this.omitted.contains("nonverbal") ? null : behaviour.nonVerbal(),
                    this.omitted.contains("motion") ? null : behaviour.motion(),
                    this.omitted.contains("display") ? null : behaviour.display());
        }
    }
}
