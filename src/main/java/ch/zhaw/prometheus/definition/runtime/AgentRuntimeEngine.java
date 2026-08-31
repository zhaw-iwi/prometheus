package ch.zhaw.prometheus.definition.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import ch.zhaw.prometheus.definition.compiled.CompiledAgentDefinition;
import ch.zhaw.prometheus.definition.compiled.CompiledAtomicState;
import ch.zhaw.prometheus.definition.compiled.CompiledCompositeState;
import ch.zhaw.prometheus.definition.compiled.CompiledState;
import ch.zhaw.prometheus.definition.compiled.CompiledTransition;
import ch.zhaw.prometheus.definition.compiled.ImmutableJson;
import ch.zhaw.prometheus.definition.component.CompiledInitializer;
import ch.zhaw.prometheus.definition.component.CompiledPolicy;
import ch.zhaw.prometheus.definition.component.CompiledSelector;

/** Pure state-machine orchestration over one compiled graph and one mutable instance. */
public final class AgentRuntimeEngine {
    public static final String BEHAVIOUR_EVENT_TYPE = "resp.behaviour_plan";
    public static final String BEHAVIOUR_EVENT_ACTOR = "assistant";
    public static final String BEHAVIOUR_EVENT_KIND = "response";
    private static final int MAX_REPROCESS_TRANSITIONS = 1_024;

    public AgentRuntimeCreation create(long definitionRevisionId, CompiledAgentDefinition definition,
            AgentRuntimeContext context) {
        return create(definitionRevisionId, definition, context, Map.of());
    }

    public AgentRuntimeCreation create(long definitionRevisionId, CompiledAgentDefinition definition,
            AgentRuntimeContext context, Map<String, JsonNode> initialStorageOverrides) {
        requireContext(context);
        if (initialStorageOverrides == null) {
            throw new IllegalArgumentException("initialStorageOverrides must not be null");
        }
        Map<String, ImmutableJson> initialStorage = new LinkedHashMap<>();
        definition.storage().forEach(declaration -> {
            if (declaration.initialValue() != null) {
                initialStorage.put(declaration.key(), declaration.initialValue());
            }
        });
        for (CompiledInitializer initializer : definition.lifecycle().initializers()) {
            initialStorage.put(initializer.targetStorageKey(), new ImmutableJson(initializer.initialize(context.random())));
        }
        for (var override : initialStorageOverrides.entrySet()) {
            boolean declared = definition.storage().stream()
                    .anyMatch(declaration -> declaration.key().equals(override.getKey()));
            if (!declared) {
                throw new IllegalArgumentException("Unknown initial storage key " + override.getKey());
            }
            initialStorage.put(override.getKey(), new ImmutableJson(override.getValue()));
        }
        String initialLeaf = resolveInitialLeaf(definition.lifecycle().initialState()).id();
        AgentRuntimeInstance instance = new AgentRuntimeInstance(definitionRevisionId, definition, initialLeaf,
                initialStorage);
        AgentInstanceSnapshot created = instance.snapshot();
        AgentRuntimeResult startup = definition.lifecycle().startOnCreation()
                ? start(instance, context)
                : result(created, instance.snapshot(), List.of(), List.of(), List.of(), null);
        return new AgentRuntimeCreation(instance, startup);
    }

    public AgentRuntimeInstance restore(long definitionRevisionId, CompiledAgentDefinition definition,
            String activeLeafStateId, Map<String, ImmutableJson> initialStorage,
            Map<String, ImmutableJson> storage, List<RuntimeEvent> history, boolean started) {
        if (initialStorage == null || storage == null || history == null) {
            throw new IllegalArgumentException("Persisted runtime collections must not be null");
        }
        return new AgentRuntimeInstance(definitionRevisionId, definition, activeLeafStateId,
                initialStorage, storage, history, started);
    }

    public AgentRuntimeResult start(AgentRuntimeInstance instance, AgentRuntimeContext context) {
        require(instance, context);
        AgentInstanceSnapshot before = instance.snapshot();
        List<RuntimeEvent> removed = enterActivePath(instance);
        instance.setStarted(true);
        RuntimeBehaviour behaviour = instance.isActive() ? executePolicy(instance, context, true) : null;
        List<RuntimeEvent> appended = appendBehaviour(instance, behaviour);
        return result(before, instance.snapshot(), appended, removed, List.of(), behaviour);
    }

    public AgentRuntimeResult generate(AgentRuntimeInstance instance, AgentRuntimeContext context) {
        require(instance, context);
        AgentInstanceSnapshot before = instance.snapshot();
        RuntimeBehaviour behaviour = instance.isActive() ? executePolicy(instance, context, false) : null;
        List<RuntimeEvent> appended = appendBehaviour(instance, behaviour);
        return result(before, instance.snapshot(), appended, List.of(), List.of(), behaviour);
    }

    public AgentRuntimeResult acknowledge(AgentRuntimeInstance instance, RuntimeEvent event,
            AgentRuntimeContext context) {
        require(instance, context);
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        AgentInstanceSnapshot before = instance.snapshot();
        List<RuntimeEvent> appended = new ArrayList<>();
        List<RuntimeEvent> removed = new ArrayList<>();
        List<String> transitions = new ArrayList<>();
        appended.add(instance.append(event));
        RuntimeBehaviour behaviour = null;

        if (instance.isActive()) {
            int transitionCount = 0;
            while (true) {
                AcceptedTransition accepted = firstAccepted(instance, context);
                if (accepted == null) {
                    break;
                }
                if (++transitionCount > MAX_REPROCESS_TRANSITIONS) {
                    throw new IllegalStateException("event reprocessing exceeded " + MAX_REPROCESS_TRANSITIONS
                            + " transitions");
                }
                RuntimeBehaviour actionBehaviour = executeActions(instance, accepted, context);
                transitions.add(accepted.transition().id());
                CompiledState target = accepted.transition().targetState();
                instance.setActiveLeafStateId(resolveInitialLeaf(target).id());
                removed.addAll(enterActivePath(instance));
                if (isStarting(target)) {
                    instance.setStarted(true);
                    behaviour = instance.isActive() ? executePolicy(instance, context, true) : actionBehaviour;
                    if (behaviour == null) {
                        behaviour = actionBehaviour;
                    }
                    appended.addAll(appendBehaviour(instance, behaviour));
                    break;
                }
                if (!instance.isActive()) {
                    break;
                }
            }
        }
        return result(before, instance.snapshot(), appended, removed, transitions, behaviour);
    }

    public AgentRuntimeResult reset(AgentRuntimeInstance instance, AgentRuntimeContext context) {
        require(instance, context);
        AgentInstanceSnapshot before = instance.snapshot();
        RuntimeStorage storage = instance.mutableStorage();
        for (var declaration : instance.definition().storage()) {
            switch (declaration.reset()) {
                case "preserve" -> {
                }
                case "remove" -> storage.remove(declaration.key());
                case "initial" -> {
                    ImmutableJson initial = instance.initialStorage().get(declaration.key());
                    if (initial == null) {
                        storage.remove(declaration.key());
                    } else {
                        storage.put(declaration.key(), initial.value());
                    }
                }
                default -> throw new IllegalStateException("Unsupported storage reset mode " + declaration.reset());
            }
        }
        List<RuntimeEvent> removed = instance.clearHistory();
        instance.setActiveLeafStateId(resolveInitialLeaf(instance.definition().lifecycle().initialState()).id());
        instance.setStarted(false);
        return result(before, instance.snapshot(), List.of(), removed, List.of(), null);
    }

    private static AcceptedTransition firstAccepted(AgentRuntimeInstance instance, AgentRuntimeContext context) {
        for (CompiledState state : instance.definition().pathTo(instance.activeLeafStateId())) {
            for (CompiledTransition transition : instance.definition().transitionsFrom(state.id())) {
                RuntimeInvocation invocation = invocation(instance, state, context.components());
                boolean accepted = true;
                for (var decision : transition.decisions()) {
                    if (!context.components().decide(decision, invocation)) {
                        accepted = false;
                        break;
                    }
                }
                if (accepted) {
                    return new AcceptedTransition(state, transition);
                }
            }
        }
        return null;
    }

    private static RuntimeBehaviour executeActions(AgentRuntimeInstance instance, AcceptedTransition accepted,
            AgentRuntimeContext context) {
        RuntimeBehaviour behaviour = null;
        for (var action : accepted.transition().actions()) {
            RuntimeBehaviour emitted = context.components().execute(action,
                    invocation(instance, accepted.sourceState(), context.components()), instance.mutableStorage());
            if (emitted != null) {
                behaviour = emitted;
            }
        }
        return behaviour;
    }

    private static RuntimeBehaviour executePolicy(AgentRuntimeInstance instance, AgentRuntimeContext context,
            boolean starting) {
        List<CompiledPolicy> policies = new ArrayList<>();
        for (CompiledState state : instance.definition().pathTo(instance.activeLeafStateId())) {
            CompiledPolicy policy = policy(state);
            if (policy != null) {
                policies.add(policy);
            }
        }
        RuntimeInvocation invocation = invocation(instance, instance.definition().state(instance.activeLeafStateId()),
                context.components());
        return starting ? context.components().start(policies, invocation)
                : context.components().generate(policies, invocation);
    }

    private static RuntimeInvocation invocation(AgentRuntimeInstance instance, CompiledState evaluatingState,
            RuntimeComponentExecutor components) {
        CompiledSelector selector = selector(evaluatingState);
        List<RuntimeEvent> selected = instance.history().stream()
                .filter(event -> selector == null
                        ? event.statePath().contains(evaluatingState.id())
                        : components.selects(selector, event, evaluatingState.id()))
                .toList();
        return new RuntimeInvocation(evaluatingState.id(), instance.activeStatePath(), selected,
                instance.storageSnapshot());
    }

    private static List<RuntimeEvent> enterActivePath(AgentRuntimeInstance instance) {
        List<RuntimeEvent> removed = new ArrayList<>();
        for (CompiledState state : instance.definition().pathTo(instance.activeLeafStateId())) {
            if (isOblivious(state)) {
                removed.addAll(instance.clearHistoryForState(state.id()));
            }
        }
        return removed;
    }

    private static List<RuntimeEvent> appendBehaviour(AgentRuntimeInstance instance, RuntimeBehaviour behaviour) {
        if (behaviour == null || behaviour.isEmpty()) {
            return List.of();
        }
        RuntimeEvent response = new RuntimeEvent(BEHAVIOUR_EVENT_TYPE, BEHAVIOUR_EVENT_ACTOR,
                BEHAVIOUR_EVENT_KIND, behaviour.toJson());
        return List.of(instance.append(response));
    }

    private static CompiledState resolveInitialLeaf(CompiledState state) {
        CompiledState current = state;
        while (current instanceof CompiledCompositeState composite) {
            current = composite.initialChildState();
        }
        return current;
    }

    private static boolean isStarting(CompiledState state) {
        if (state instanceof CompiledAtomicState atomic) {
            return "start".equals(atomic.entryMode());
        }
        if (state instanceof CompiledCompositeState composite) {
            return "start".equals(composite.entryMode());
        }
        return true;
    }

    private static boolean isOblivious(CompiledState state) {
        return state instanceof CompiledAtomicState atomic && atomic.oblivious()
                || state instanceof CompiledCompositeState composite && composite.oblivious();
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

    private static CompiledSelector selector(CompiledState state) {
        if (state instanceof CompiledAtomicState atomic) {
            return atomic.eventSelector();
        }
        if (state instanceof CompiledCompositeState composite) {
            return composite.eventSelector();
        }
        return null;
    }

    private static AgentRuntimeResult result(AgentInstanceSnapshot before, AgentInstanceSnapshot after,
            List<RuntimeEvent> appended, List<RuntimeEvent> removed, List<String> transitions,
            RuntimeBehaviour behaviour) {
        Set<String> keys = new LinkedHashSet<>(before.storage().keySet());
        keys.addAll(after.storage().keySet());
        Map<String, RuntimeStorageChange> storageChanges = new LinkedHashMap<>();
        for (String key : keys) {
            ImmutableJson previous = before.storage().get(key);
            ImmutableJson current = after.storage().get(key);
            if (!java.util.Objects.equals(previous, current)) {
                storageChanges.put(key, new RuntimeStorageChange(previous, current));
            }
        }
        return new AgentRuntimeResult(before, after, appended, removed, storageChanges, transitions, behaviour);
    }

    private static void require(AgentRuntimeInstance instance, AgentRuntimeContext context) {
        if (instance == null) {
            throw new IllegalArgumentException("instance must not be null");
        }
        requireContext(context);
    }

    private static void requireContext(AgentRuntimeContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
    }

    private record AcceptedTransition(CompiledState sourceState, CompiledTransition transition) {
    }

}
