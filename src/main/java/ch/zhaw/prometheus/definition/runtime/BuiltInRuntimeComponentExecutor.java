package ch.zhaw.prometheus.definition.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import ch.zhaw.prometheus.definition.compiled.CompiledStorageBinding;
import ch.zhaw.prometheus.definition.compiled.ImmutableJson;
import ch.zhaw.prometheus.definition.component.CompiledAction;
import ch.zhaw.prometheus.definition.component.CompiledDecision;
import ch.zhaw.prometheus.definition.component.CompiledPolicy;
import ch.zhaw.prometheus.definition.component.CompiledSelector;
import ch.zhaw.prometheus.definition.component.builtin.CompositeSelectorComponent;
import ch.zhaw.prometheus.definition.component.builtin.ExtractionActionComponent;
import ch.zhaw.prometheus.definition.component.builtin.IncrementActionComponent;
import ch.zhaw.prometheus.definition.component.builtin.LatestEventTypeDecisionComponent;
import ch.zhaw.prometheus.definition.component.builtin.NoOpPolicyComponent;
import ch.zhaw.prometheus.definition.component.builtin.PromptDecisionComponent;
import ch.zhaw.prometheus.definition.component.builtin.PromptPolicyComponent;
import ch.zhaw.prometheus.definition.component.builtin.SelectorComponent;

/** Runtime semantics for the framework-owned compiled component records. */
public final class BuiltInRuntimeComponentExecutor implements RuntimeComponentExecutor {
    private static final String PROMPT_SEPARATOR = "\n\n";
    private final RuntimeModelGateway modelGateway;

    public BuiltInRuntimeComponentExecutor(RuntimeModelGateway modelGateway) {
        if (modelGateway == null) {
            throw new IllegalArgumentException("modelGateway must not be null");
        }
        this.modelGateway = modelGateway;
    }

    @Override
    public RuntimeBehaviour start(List<CompiledPolicy> policies, RuntimeInvocation invocation) {
        return generateWithPolicies(policies, invocation, true);
    }

    @Override
    public RuntimeBehaviour generate(List<CompiledPolicy> policies, RuntimeInvocation invocation) {
        return generateWithPolicies(policies, invocation, false);
    }

    @Override
    public boolean decide(CompiledDecision decision, RuntimeInvocation invocation) {
        if (decision instanceof LatestEventTypeDecisionComponent latest) {
            return !invocation.history().isEmpty()
                    && latest.eventType().equals(invocation.history().getLast().type());
        }
        if (decision instanceof PromptDecisionComponent prompt) {
            if (prompt.decisionPrompt() == null || prompt.decisionPrompt().isBlank()) {
                return false;
            }
            return this.modelGateway.decide(prompt.decisionPrompt(), bound(invocation, prompt.storageBindings()));
        }
        throw unsupported(decision);
    }

    @Override
    public void execute(CompiledAction action, RuntimeInvocation invocation, RuntimeStorage storage) {
        if (action instanceof IncrementActionComponent increment) {
            JsonNode current = storage.get(increment.targetStorageKey());
            if (current == null || !current.isIntegralNumber()) {
                throw new IllegalStateException("increment target is not an initialized integer: "
                        + increment.targetStorageKey());
            }
            storage.put(increment.targetStorageKey(), JsonNodeFactory.instance.numberNode(current.longValue() + 1));
            return;
        }
        if (action instanceof ExtractionActionComponent extraction) {
            JsonNode value = this.modelGateway.extract(extraction.extractionPrompt(),
                    extraction.outputSchema() == null ? null : extraction.outputSchema().value(),
                    bound(invocation, extraction.storageBindings()));
            if (value == null) {
                throw new IllegalStateException("extraction returned no value for " + extraction.targetStorageKey());
            }
            storage.put(extraction.targetStorageKey(), value);
            return;
        }
        throw unsupported(action);
    }

    @Override
    public boolean selects(CompiledSelector selector, RuntimeEvent event, String evaluatingStateId) {
        if (selector instanceof SelectorComponent simple) {
            return switch (simple.selectorKind()) {
                case ANY -> true;
                case ACTIVE_STATE_PATH -> event.statePath().contains(evaluatingStateId);
                case EVENT_TYPE -> simple.values().contains(event.type());
                case ACTOR -> simple.values().contains(event.actor());
                case EVENT_KIND -> simple.values().contains(event.kind());
                case STATE_ID -> simple.values().stream().anyMatch(event.statePath()::contains);
            };
        }
        if (selector instanceof CompositeSelectorComponent composite) {
            return switch (composite.mode()) {
                case ALL -> composite.selectors().stream().allMatch(child -> selects(child, event, evaluatingStateId));
                case ANY -> composite.selectors().stream().anyMatch(child -> selects(child, event, evaluatingStateId));
            };
        }
        throw unsupported(selector);
    }

    private RuntimeBehaviour generateWithPolicies(List<CompiledPolicy> policies, RuntimeInvocation invocation,
            boolean starting) {
        List<PromptPolicyComponent> prompts = new ArrayList<>();
        for (CompiledPolicy policy : policies) {
            if (policy instanceof PromptPolicyComponent prompt) {
                prompts.add(prompt);
            } else if (!(policy instanceof NoOpPolicyComponent)) {
                throw unsupported(policy);
            }
        }
        String responsePrompt = join(prompts.stream().map(PromptPolicyComponent::responsePrompt).toList());
        if (responsePrompt.isBlank()) {
            return null;
        }
        RuntimePromptBundle promptBundle = new RuntimePromptBundle(responsePrompt,
                starting ? deepest(prompts, PromptPolicyComponent::starterPrompt) : "",
                deepest(prompts, PromptPolicyComponent::summaryPrompt),
                deepest(prompts, PromptPolicyComponent::nonverbalPlanPrompt),
                deepest(prompts, PromptPolicyComponent::gesturePrompt), starting);
        List<CompiledStorageBinding> bindings = prompts.stream().flatMap(prompt -> prompt.storageBindings().stream())
                .toList();
        RuntimeBehaviour behaviour = this.modelGateway.generate(promptBundle, bound(invocation, bindings));
        return behaviour == null || behaviour.isEmpty() ? null : behaviour;
    }

    private static RuntimeInvocation bound(RuntimeInvocation invocation, List<CompiledStorageBinding> bindings) {
        Map<String, ImmutableJson> storage = new LinkedHashMap<>();
        for (CompiledStorageBinding binding : bindings) {
            ImmutableJson value = invocation.storage().get(binding.key());
            if (value != null) {
                storage.put(binding.key(), value);
            }
        }
        return new RuntimeInvocation(invocation.stateId(), invocation.activeStatePath(), invocation.history(), storage);
    }

    private static String join(List<String> prompts) {
        return prompts.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).collect(java.util.stream.Collectors.joining(PROMPT_SEPARATOR));
    }

    private static String deepest(List<PromptPolicyComponent> prompts,
            java.util.function.Function<PromptPolicyComponent, String> extractor) {
        for (int index = prompts.size() - 1; index >= 0; index--) {
            String value = extractor.apply(prompts.get(index));
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static UnsupportedOperationException unsupported(Object component) {
        return new UnsupportedOperationException("No runtime executor for compiled component "
                + component.getClass().getName());
    }
}
