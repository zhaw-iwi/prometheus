package ch.zhaw.prometheus.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.*;

class ParallelGuardEvaluationUnitTest {
    @Test void catalogContinuationComparesOrderedCombinedAndParallelRequestCounts() {
        record Example(ch.zhaw.prometheus.agentdefs.AgentDefinition definition, int ordered, int checks) {}
        for (var example : List.of(
                new Example(new ch.zhaw.prometheus.agentdefs.usecases.healthcare.SingleStateSmartGoalCoaching(), 3, 2),
                new Example(new ch.zhaw.prometheus.agentdefs.core.RoleClarificationGuessingGame(), 5, 5),
                new Example(new ch.zhaw.prometheus.agentdefs.core.RockScissorPaper(), 4, 4))) {
            for (var strategy : List.of(GuardInferenceOptions.Strategy.ORDERED, GuardInferenceOptions.Strategy.COMBINED, GuardInferenceOptions.Strategy.PARALLEL)) {
                var calls = new AtomicInteger(); var allStarted = new CountDownLatch(example.checks());
                try (var gateway = new Gateway(strategy, request -> {
                    calls.incrementAndGet();
                    if (request.purpose() != InferencePurpose.DECISION) return "{\"speech\":\"Ready.\",\"nonVerbal\":{}}";
                    if (request.output() == InferenceRequest.Output.BOOLEAN) {
                        allStarted.countDown(); await(allStarted); return "false";
                    }
                    var values = new com.google.gson.JsonObject();
                    request.schema().getAsJsonObject("properties").keySet().forEach(id -> values.addProperty(id, false));
                    return values.toString();
                }) {
                    @Override public GuardInferenceOptions guardInferenceOptions() { return new GuardInferenceOptions(strategy, 16, 65536); }
                    @Override public boolean decide(List<PromptMessage> messages) { calls.incrementAndGet(); return false; }
                }) {
                    Agent agent = example.definition().createAgent(); agent.start(runtime(gateway)); calls.set(0);
                    if (agent.acknowledge(input(), runtime(gateway)) == null) agent.generate(runtime(gateway));
                    int expected = strategy == GuardInferenceOptions.Strategy.ORDERED ? example.ordered()
                            : strategy == GuardInferenceOptions.Strategy.COMBINED ? 2 : example.checks() + 1;
                    assertEquals(expected, calls.get(), example.definition().key() + " " + strategy);
                }
            }
        }
    }

    @Test void customDecisionBarrierKeepsSubsequentChecksOnTheCaller() {
        Thread caller = Thread.currentThread(); var sequential = new AtomicInteger();
        State initial = state("initial");
        initial.addTransition(new Transition(new Decision(new NoOpPolicy()) {
            @Override public boolean decide(EventHistory history, PolicyRuntime runtime) { assertSame(caller, Thread.currentThread()); return false; }
        }, state("unused")));
        initial.addTransition(new Transition(new StaticDecision("later"), state("finish")));
        try (var gateway = new Gateway(GuardInferenceOptions.Strategy.PARALLEL, request -> { fail("crossed custom barrier"); return "false"; }) {
            @Override public boolean decide(List<PromptMessage> messages) { assertSame(caller, Thread.currentThread()); sequential.incrementAndGet(); return false; }
        }) {
            new Agent("a", "d", initial).acknowledge(input(), runtime(gateway));
            assertEquals(1, sequential.get());
        }
    }

    @Test void laterInnerCompletionCannotBeatOuterPriorityAndActionsStayOnCaller() throws Exception {
        var started = new CountDownLatch(2); var releaseOuter = new CountDownLatch(1); var innerFinished = new CountDownLatch(1);
        var actions = new AtomicInteger(); var caller = new AtomicReference<Thread>();
        State inner = state("inner"), outerFinal = state("outer-final");
        Action action = new Action(new NoOpPolicy()) {
            @Override public void execute(EventHistory history, PolicyRuntime runtime) { assertSame(caller.get(), Thread.currentThread()); actions.incrementAndGet(); }
        };
        inner.addTransition(new Transition(new StaticDecision("inner"), action, state("inner-final")));
        var outer = new OuterState("outer", "outer", List.of(new Transition(new StaticDecision("outer"), action, outerFinal)), inner);
        Agent agent = new Agent("a", "d", outer);
        try (var gateway = new Gateway(GuardInferenceOptions.Strategy.PARALLEL, request -> {
            started.countDown();
            if (request.messages().getFirst().getContent().equals("outer")) await(releaseOuter);
            else innerFinished.countDown();
            return "true";
        }); var turns = Executors.newSingleThreadExecutor()) {
            var result = turns.submit(() -> { caller.set(Thread.currentThread()); return agent.acknowledge(input(), runtime(gateway)); });
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS)); assertTrue(innerFinished.await(5, TimeUnit.SECONDS));
                assertEquals(0, actions.get()); assertSame(outer, agent.getCurrentState());
            } finally { releaseOuter.countDown(); }
            result.get(5, TimeUnit.SECONDS);
            assertSame(outerFinal, agent.getCurrentState()); assertEquals(1, actions.get());
        }
    }

    @Test void boundedCombinedGroupsRunOnceInParallel() throws Exception {
        var started = new CountDownLatch(2); var release = new CountDownLatch(1); var calls = new AtomicInteger();
        State initial = state("initial");
        for (int i = 0; i < 4; i++) initial.addTransition(new Transition(new StaticDecision("g" + i), state("unused" + i)));
        try (var gateway = new Gateway(GuardInferenceOptions.Strategy.COMBINED_PARALLEL, request -> {
            calls.incrementAndGet(); started.countDown(); await(release);
            var values = new com.google.gson.JsonObject();
            assertEquals(2, request.schema().getAsJsonObject("properties").size());
            request.schema().getAsJsonObject("properties").keySet().forEach(id -> values.addProperty(id, false));
            return values.toString();
        }); var turns = Executors.newSingleThreadExecutor()) {
            var result = turns.submit(() -> new Agent("a", "d", initial).acknowledge(input(), runtime(gateway)));
            try { assertTrue(started.await(5, TimeUnit.SECONDS)); } finally { release.countDown(); }
            result.get(5, TimeUnit.SECONDS); assertEquals(2, calls.get());
        }
    }

    @Test void catalogRoleScenarioKeepsFirstRoleWhenBothParallelRoleChecksAreTrue() throws Exception {
        var started = new CountDownLatch(5); var release = new CountDownLatch(1); var calls = new AtomicInteger();
        Agent agent = new ch.zhaw.prometheus.agentdefs.core.RoleClarificationGuessingGame().createAgent();
        try (var gateway = new Gateway(GuardInferenceOptions.Strategy.PARALLEL, request -> {
            if (request.purpose() == InferencePurpose.BEHAVIOUR) return "{\"speech\":\"Ready.\",\"nonVerbal\":{}}";
            calls.incrementAndGet(); started.countDown(); await(release);
            String prompt = request.messages().getFirst().getContent();
            return Boolean.toString(prompt.contains("person thinks of, chooses, or imagines") || prompt.contains("Valerian thinks of, chooses, or imagines"));
        }); var turns = Executors.newSingleThreadExecutor()) {
            agent.start(runtime(gateway));
            var result = turns.submit(() -> agent.acknowledge(input(), runtime(gateway)));
            try { assertTrue(started.await(5, TimeUnit.SECONDS)); } finally { release.countDown(); }
            result.get(5, TimeUnit.SECONDS);
            assertEquals("Valerian Core guessing game - Valerian guesses", ((OuterState) agent.getCurrentState()).getInnerCurrent().getName());
            assertEquals(5, calls.get());
        }
    }

    @Test void changedSnapshotAndRequiredFailureNeverApplyAnAction() throws Exception {
        for (boolean changed : List.of(false, true)) {
            var actions = new AtomicInteger(); var started = new CountDownLatch(2); var release = new CountDownLatch(1);
            State initial = state("initial");
            for (int i = 0; i < 2; i++) initial.addTransition(new Transition(new StaticDecision("guard" + i), new Action(new NoOpPolicy()) {
                @Override public void execute(EventHistory history, PolicyRuntime runtime) { actions.incrementAndGet(); }
            }, state("finish")));
            Agent agent = new Agent("a", "d", initial);
            try (var gateway = new Gateway(GuardInferenceOptions.Strategy.PARALLEL, request -> {
                started.countDown(); await(release);
                if (!changed) throw new IllegalStateException("fixture failure"); return "true";
            }); var turns = Executors.newSingleThreadExecutor()) {
                var result = turns.submit(() -> agent.acknowledge(input(), runtime(gateway)));
                try {
                    assertTrue(started.await(5, TimeUnit.SECONDS));
                    if (changed) agent.reset();
                } finally { release.countDown(); }
                assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
                assertEquals(0, actions.get()); assertSame(initial, agent.getCurrentState());
            }
        }
    }

    private static State state(String name) { return new State(name, new NoOpPolicy(), List.of()); }
    private static Event input() { return Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Fixture"); }
    private static PolicyRuntime runtime(Gateway gateway) { return new PolicyRuntime(new PromptMessageAssembler(), gateway); }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("interrupted"); }
    }
    private static class Gateway extends NoOpLanguageModelGateway implements AutoCloseable {
        final GuardInferenceExecutor executor; final GuardInferenceOptions.Strategy strategy; final Function<InferenceRequest, String> work;
        Gateway(GuardInferenceOptions.Strategy strategy, Function<InferenceRequest, String> work) {
            var properties = new OpenAIProperties(); properties.setGuardParallelism(5); properties.setGuardPerTurnParallelism(5);
            executor = new GuardInferenceExecutor(properties); this.strategy = strategy; this.work = work;
        }
        @Override public GuardInferenceOptions guardInferenceOptions() { return new GuardInferenceOptions(strategy, 2, 65536); }
        @Override public GuardInferenceExecutor guardExecutor() { return executor; }
        @Override public String infer(InferenceRequest request) { return work.apply(request); }
        @Override public void close() { executor.close(); }
    }
}
