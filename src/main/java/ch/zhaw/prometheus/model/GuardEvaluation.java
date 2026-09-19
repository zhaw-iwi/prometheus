package ch.zhaw.prometheus.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ch.zhaw.prometheus.model.commons.decisions.LatestEventTypeDecision;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.spi.GuardInferenceOptions;
import ch.zhaw.prometheus.spi.GuardRequests;
import ch.zhaw.prometheus.spi.InferencePurpose;
import ch.zhaw.prometheus.spi.InferenceRequest;
import ch.zhaw.prometheus.spi.InferenceResult;
import ch.zhaw.prometheus.spi.GuardInferenceExecutor;

/** Per-acknowledgement optimization. Results are consumed by the original ordered transition machinery. */
public final class GuardEvaluation {
    private record Key(State state, Decision decision) {}
    private record TransitionKey(State state, Transition transition) {}
    private record Candidate(Key key, String id, InferenceRequest request) {}
    private final Map<Key, Candidate> candidates = new LinkedHashMap<>();
    private final Set<TransitionKey> locallyRejected = new HashSet<>();
    private final Map<Key, List<Candidate>> groups = new HashMap<>();
    private final Map<Key, Boolean> results = new HashMap<>();
    private boolean valid = true;
    private boolean parallel;
    private GuardInferenceExecutor.Session execution;
    private final List<List<Candidate>> orderedGroups = new ArrayList<>();

    public static GuardEvaluation prepare(State root, PolicyRuntime runtime) {
        if (!hasModelChecks(root)) return null;
        var options = runtime.languageModelGateway().guardInferenceOptions();
        if (options == null || options.strategy() == GuardInferenceOptions.Strategy.ORDERED
                || runtime.promptMessageAssembler().getClass() != PromptMessageAssembler.class) return null;
        GuardEvaluation evaluation = new GuardEvaluation();
        evaluation.parallel = options.strategy() == GuardInferenceOptions.Strategy.PARALLEL
                || options.strategy() == GuardInferenceOptions.Strategy.COMBINED_PARALLEL;
        if (evaluation.parallel && runtime.languageModelGateway().guardExecutor() == null) return null;
        evaluation.collect(root, runtime);
        evaluation.group(runtime, options);
        return evaluation;
    }

    private static boolean hasModelChecks(State state) {
        if (state == null || (state.getClass() != State.class && state.getClass() != OuterState.class)) return false;
        for (Transition transition : state.getTransitions()) {
            if (transition.getDecisions().stream().anyMatch(decision -> decision.getClass() == StaticDecision.class
                    && decision.getPolicy().getClass() == PromptPolicy.class)) return true;
        }
        return state instanceof OuterState outer && hasModelChecks(outer.getInnerCurrent());
    }

    private void collect(State state, PolicyRuntime runtime) {
        // Unknown state subclasses may execute arbitrary work before checking their guards.
        if (state.getClass() != State.class && state.getClass() != OuterState.class) return;
        for (Transition transition : state.getTransitions()) {
            if (transition.getDecisions().isEmpty()) return; // Unconditional transition is a barrier.
            if (transition.getDecisions().stream().anyMatch(decision -> !pure(decision))) return;
            List<Candidate> pending = new ArrayList<>();
            boolean rejected = false;
            try {
                // Known local filters can reject a pure conjunction without invoking any model.
                for (Decision decision : transition.getDecisions()) {
                    EventHistory selected = selected(state, decision);
                    if (decision.getClass() == LatestEventTypeDecision.class && !decision.decide(selected, runtime)) {
                        rejected = true; break;
                    }
                    if (decision.getClass() == StaticDecision.class && decision.getPolicy().describe().isBlank()) {
                        rejected = true; break;
                    }
                }
                if (rejected) { locallyRejected.add(new TransitionKey(state, transition)); continue; }
                for (Decision decision : transition.getDecisions()) {
                    if (decision.getClass() != StaticDecision.class) continue;
                    Key key = new Key(state, decision);
                    if (candidates.containsKey(key)) continue;
                    var request = new InferenceRequest(InferencePurpose.DECISION,
                            ((PromptPolicy) decision.getPolicy()).decisionMessages(selected(state, decision), runtime.promptMessageAssembler()),
                            InferenceRequest.Output.BOOLEAN);
                    pending.add(new Candidate(key, "g" + (candidates.size() + pending.size()), request));
                }
            } catch (RuntimeException unavailable) {
                // A later guard may have invalid inputs but never be reached. Let ordered execution decide.
                return;
            }
            if (candidates.size() + pending.size() > 64) return;
            for (Candidate candidate : pending) candidates.put(candidate.key(), candidate);
            if (candidates.size() >= 64) return; // Bound speculative work; later guards stay ordered.
        }
        if (state instanceof OuterState outer) collect(outer.getInnerCurrent(), runtime);
    }

    private static boolean pure(Decision decision) {
        return decision.getClass() == LatestEventTypeDecision.class || (decision.getClass() == StaticDecision.class
                && decision.getPolicy().getClass() == PromptPolicy.class);
    }

    private static EventHistory selected(State state, Decision decision) {
        var selector = decision.getEventSelector();
        return state.getSharedEventHistory().select(selector == null ? state.getEventSelector() : selector);
    }

    private void group(PolicyRuntime runtime, GuardInferenceOptions options) {
        List<Candidate> group = new ArrayList<>();
        Object compatibility = null;
        for (Candidate candidate : candidates.values()) {
            if (options.strategy() == GuardInferenceOptions.Strategy.PARALLEL) {
                register(List.of(candidate)); continue;
            }
            Object next = runtime.languageModelGateway().guardCompatibilityKey(candidate.request());
            List<Candidate> proposed = new ArrayList<>(group); proposed.add(candidate);
            if (!group.isEmpty() && (!java.util.Objects.equals(compatibility, next)
                    || proposed.size() > options.maxBatchSize() || size(proposed) > options.maxCharacters())) {
                register(group); group = new ArrayList<>();
            }
            group.add(candidate); compatibility = next;
        }
        register(group);
    }

    private static int size(List<Candidate> candidates) {
        return GuardRequests.combine(requests(candidates)).messages().stream().mapToInt(message -> message.getContent().length()).sum();
    }
    private void register(List<Candidate> group) {
        if (group.isEmpty()) return;
        List<Candidate> snapshot = List.copyOf(group);
        orderedGroups.add(snapshot);
        for (Candidate candidate : group) groups.put(candidate.key(), snapshot);
    }
    private static Map<String, InferenceRequest> requests(List<Candidate> group) {
        Map<String, InferenceRequest> requests = new LinkedHashMap<>();
        for (Candidate candidate : group) requests.put(candidate.id(), candidate.request());
        return requests;
    }

    public boolean rejects(State state, Transition transition) {
        return valid && locallyRejected.contains(new TransitionKey(state, transition));
    }

    public Boolean result(State state, Decision decision, EventHistory selected, PolicyRuntime runtime) {
        if (!valid) return null;
        Key key = new Key(state, decision);
        Candidate candidate = candidates.get(key);
        if (candidate == null) return null;
        var current = ((PromptPolicy) decision.getPolicy()).decisionMessages(selected, runtime.promptMessageAssembler());
        if (!sameMessages(candidate.request().messages(), current)) {
            invalidate(); throw new IllegalStateException("Guard snapshot changed before evaluation");
        }
        List<Candidate> group = groups.get(key);
        if (group.size() < 2 && !parallel) return null; // Ordinary semantic call retains short-circuit behavior.
        if (!results.containsKey(key)) {
            Map<String, InferenceRequest> tasks = requests(group);
            String raw;
            if (parallel) {
                if (execution == null) execution = runtime.languageModelGateway().guardExecutor().start(
                        orderedGroups.stream().map(members -> members.size() == 1 ? members.getFirst().request()
                                : GuardRequests.combine(requests(members))).toList(), runtime.languageModelGateway()::infer);
                raw = execution.await(orderedGroups.indexOf(group));
            } else raw = runtime.languageModelGateway().infer(GuardRequests.combine(tasks));
            Map<String, Boolean> checked = group.size() == 1
                    ? Map.of(candidate.id(), InferenceResult.bool(raw)) : GuardRequests.validate(raw, tasks.keySet());
            var fresh = ((PromptPolicy) decision.getPolicy()).decisionMessages(selected(state, decision), runtime.promptMessageAssembler());
            if (!sameMessages(candidate.request().messages(), fresh)) {
                invalidate(); throw new IllegalStateException("Guard snapshot changed during inference; results discarded");
            }
            for (Candidate member : group) results.put(member.key(), checked.get(member.id()));
        }
        return results.get(key);
    }

    private static boolean sameMessages(List<PromptMessage> left, List<PromptMessage> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).getRole().equals(right.get(i).getRole())
                    || !left.get(i).getContent().equals(right.get(i).getContent())) return false;
        }
        return true;
    }

    public void invalidate() {
        valid = false; results.clear();
        if (execution != null) execution.close();
    }
}
