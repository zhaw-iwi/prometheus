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
import ch.zhaw.prometheus.definition.component.builtin.ExactTextPolicyComponent;
import ch.zhaw.prometheus.definition.component.builtin.ExactTextSupport;
import ch.zhaw.prometheus.definition.component.builtin.ExtractionActionComponent;
import ch.zhaw.prometheus.definition.component.builtin.IncrementActionComponent;
import ch.zhaw.prometheus.definition.component.builtin.LatestEventTypeDecisionComponent;
import ch.zhaw.prometheus.definition.component.builtin.NoOpPolicyComponent;
import ch.zhaw.prometheus.definition.component.builtin.PromptDecisionComponent;
import ch.zhaw.prometheus.definition.component.builtin.PromptBehaviourActionComponent;
import ch.zhaw.prometheus.definition.component.builtin.PromptPolicyComponent;
import ch.zhaw.prometheus.definition.component.builtin.RpsEvaluateRoundActionComponent;
import ch.zhaw.prometheus.definition.component.builtin.RpsResultPolicyComponent;
import ch.zhaw.prometheus.definition.component.builtin.RpsRevealPolicyComponent;
import ch.zhaw.prometheus.definition.component.builtin.RpsSelectSignActionComponent;
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
            RuntimeInvocation bound = bound(invocation, prompt.storageBindings());
            return this.modelGateway.decide(resolveBindings(prompt.decisionPrompt(), bound.storage()), bound);
        }
        throw unsupported(decision);
    }

    @Override
    public RuntimeBehaviour execute(CompiledAction action, RuntimeInvocation invocation, RuntimeStorage storage) {
        if (action instanceof IncrementActionComponent increment) {
            JsonNode current = storage.get(increment.targetStorageKey());
            if (current == null || !current.isIntegralNumber()) {
                throw new IllegalStateException("increment target is not an initialized integer: "
                        + increment.targetStorageKey());
            }
            storage.put(increment.targetStorageKey(), JsonNodeFactory.instance.numberNode(current.longValue() + 1));
            return null;
        }
        if (action instanceof ExtractionActionComponent extraction) {
            RuntimeInvocation bound = bound(invocation, extraction.storageBindings());
            JsonNode value = this.modelGateway.extract(resolveBindings(extraction.extractionPrompt(), bound.storage()),
                    extraction.outputSchema() == null ? null : extraction.outputSchema().value(),
                    bound);
            if (value == null) {
                throw new IllegalStateException("extraction returned no value for " + extraction.targetStorageKey());
            }
            storage.put(extraction.targetStorageKey(), value);
            return null;
        }
        if (action instanceof PromptBehaviourActionComponent prompt) {
            return generateWithPolicies(List.of(prompt.policy()), invocation, true);
        }
        if (action instanceof RpsSelectSignActionComponent select) {
            RpsRuntimeSupport.select(select, storage);
            return null;
        }
        if (action instanceof RpsEvaluateRoundActionComponent evaluate) {
            RpsRuntimeSupport.evaluate(evaluate, invocation, storage);
            return null;
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
        DeterministicPolicyResult deterministic = deterministicPolicy(policies, invocation);
        if (deterministic.handled()) {
            return deterministic.behaviour();
        }
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
        List<CompiledStorageBinding> bindings = prompts.stream().flatMap(prompt -> prompt.storageBindings().stream())
                .toList();
        RuntimeInvocation bound = bound(invocation, bindings);
        RuntimePromptBundle promptBundle = new RuntimePromptBundle(resolveBindings(responsePrompt, bound.storage()),
                starting ? resolveBindings(deepest(prompts, PromptPolicyComponent::starterPrompt), bound.storage()) : "",
                resolveBindings(deepest(prompts, PromptPolicyComponent::summaryPrompt), bound.storage()),
                resolveBindings(deepest(prompts, PromptPolicyComponent::nonverbalPlanPrompt), bound.storage()),
                resolveBindings(deepest(prompts, PromptPolicyComponent::gesturePrompt), bound.storage()), starting);
        RuntimeBehaviour behaviour = this.modelGateway.generate(promptBundle, bound);
        return behaviour == null || behaviour.isEmpty() ? null : behaviour;
    }

    private static DeterministicPolicyResult deterministicPolicy(List<CompiledPolicy> policies,
            RuntimeInvocation invocation) {
        for (int index = policies.size() - 1; index >= 0; index--) {
            CompiledPolicy policy = policies.get(index);
            if (policy instanceof ExactTextPolicyComponent exact) {
                return new DeterministicPolicyResult(true, exactText(exact, invocation));
            }
            if (policy instanceof RpsRevealPolicyComponent reveal) {
                return new DeterministicPolicyResult(true, RpsRuntimeSupport.reveal(reveal, invocation.storage()));
            }
            if (policy instanceof RpsResultPolicyComponent result) {
                return new DeterministicPolicyResult(true, RpsRuntimeSupport.result(result, invocation.storage()));
            }
        }
        return new DeterministicPolicyResult(false, null);
    }

    private static RuntimeBehaviour exactText(ExactTextPolicyComponent component, RuntimeInvocation invocation) {
        for (int index = invocation.history().size() - 1; index >= 0; index--) {
            RuntimeEvent event = invocation.history().get(index);
            if (!component.eventType().equals(event.type()) || !component.actor().equals(event.actor())
                    || !component.eventKind().equals(event.kind())) {
                continue;
            }
            String text = ExactTextSupport.acceptedText(event.payload(), component.maxTextCodePoints());
            return text == null ? null : RuntimeBehaviour.speechOnly(text);
        }
        return null;
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

    private static String resolveBindings(String prompt, Map<String, ImmutableJson> storage) {
        String resolved = prompt == null ? "" : prompt;
        for (Map.Entry<String, ImmutableJson> entry : storage.entrySet()) {
            resolved = resolved.replace("${" + entry.getKey() + "}", entry.getValue().toString());
        }
        return resolved;
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

    private record DeterministicPolicyResult(boolean handled, RuntimeBehaviour behaviour) {
    }
}
