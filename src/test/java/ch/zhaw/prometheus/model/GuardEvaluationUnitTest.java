package ch.zhaw.prometheus.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ch.zhaw.prometheus.model.commons.decisions.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.*;

class GuardEvaluationUnitTest {
    @Test void outerPriorityWinsWhenOuterAndInnerAreBothTrue() {
        var calls = new AtomicInteger();
        State finish = state("outer-final");
        State inner = state("inner");
        inner.addTransition(new Transition(List.of(new StaticDecision("inner")), List.of(count(calls)), state("inner-final")));
        OuterState outer = new OuterState("outer", "outer", List.of(
                new Transition(List.of(new StaticDecision("outer")), List.of(count(calls)), finish)), inner);
        var gateway = new Gateway(true);
        Agent agent = new Agent("a", "d", outer);
        agent.acknowledge(input(), runtime(gateway));
        assertSame(finish, agent.getCurrentState()); assertEquals(1, calls.get()); assertEquals(1, gateway.batches);
        assertEquals(0, gateway.singles);
    }

    @Test void allFalseNestedChecksKeepOwnSelectedHistoriesAndExplicitSelectors() {
        State leaf = state("leaf");
        var explicit = new StaticDecision("leaf-check"); explicit.setEventSelectorSpec(EventSelectorSpec.actor("assistant"));
        leaf.addTransition(new Transition(explicit, state("leaf-final")));
        OuterState middle = new OuterState("middle", "middle", List.of(new Transition(new StaticDecision("middle-check"), state("middle-final"))), leaf);
        OuterState outer = new OuterState("outer", "outer", List.of(new Transition(new StaticDecision("outer-check"), state("outer-final"))), middle);
        Agent agent = new Agent("a", "d", outer);
        agent.getEventHistory().appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "outer-only").withStatePath("outer"));
        agent.getEventHistory().appendEvent(Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, "assistant", "assistant-only").withStatePath("outer", "middle", "leaf"));
        var gateway = new Gateway(false);
        agent.acknowledge(input(), runtime(gateway));
        assertSame(outer, agent.getCurrentState()); assertSame(leaf, middle.getInnerCurrent());
        JsonObject tasks = JsonParser.parseString(gateway.last.messages().get(1).getContent()).getAsJsonObject();
        assertTrue(tasks.get("g0").toString().contains("outer-only"));
        assertFalse(tasks.get("g1").toString().contains("outer-only"));
        assertTrue(tasks.get("g2").toString().contains("assistant-only"));
        assertFalse(tasks.get("g2").toString().contains("latest-user"));
        assertEquals(1, gateway.batches);
    }

    @Test void unknownDecisionIsABarrierAndRemainsOrdered() {
        State initial = state("initial");
        var unknownCalls = new AtomicInteger();
        initial.addTransition(new Transition(new Decision(new NoOpPolicy()) {
            @Override public boolean decide(EventHistory events, PolicyRuntime runtime) { unknownCalls.incrementAndGet(); return false; }
        }, state("unused")));
        initial.addTransition(new Transition(new StaticDecision("second"), state("second")));
        initial.addTransition(new Transition(new StaticDecision("third"), state("third")));
        var gateway = new Gateway(false);
        new Agent("a", "d", initial).acknowledge(input(), runtime(gateway));
        assertEquals(1, unknownCalls.get()); assertEquals(0, gateway.batches); assertEquals(2, gateway.singles);
    }

    @Test void localFilterRejectsPureConjunctionWithoutPayingForEarlierModelCheck() {
        State initial = state("initial");
        initial.addTransition(new Transition(List.of(new StaticDecision("expensive"),
                new LatestEventTypeDecision(Event.TYPE_FACE_EMOTION)), List.of(), state("unused")));
        var gateway = new Gateway(true);
        new Agent("a", "d", initial).acknowledge(input(), runtime(gateway));
        assertEquals(0, gateway.batches + gateway.singles);
    }

    @Test void malformedBatchExecutesNoActionOrTransition() {
        for (String invalid : List.of("{}", "{\"g0\":true}", "{\"g0\":true,\"g1\":\"false\"}", "{\"g0\":true,\"g1\":false,\"extra\":false}")) {
            var actions = new AtomicInteger(); State initial = twoChecks(actions);
            var gateway = new Gateway(true); gateway.override = invalid;
            Agent agent = new Agent("a", "d", initial);
            assertThrows(IllegalStateException.class, () -> agent.acknowledge(input(), runtime(gateway)));
            assertSame(initial, agent.getCurrentState()); assertEquals(0, actions.get()); assertEquals(1, gateway.batches);
        }
    }

    @Test void selectedActionInvalidatesBatchBeforeAFollowingNonStartingState() {
        var actions = new AtomicInteger();
        Storage storage = new Storage(); storage.put("mode", new com.google.gson.JsonPrimitive("before"));
        State following = new State("following", new NoOpPolicy(), List.of(), false, false);
        following.setEventSelectorSpec(EventSelectorSpec.any());
        var dependent = new StaticDecision("unused");
        dependent.setPolicy(new PromptPolicy("after-action ${mode}", null, null, storage, List.of("mode")));
        following.addTransition(new Transition(dependent, state("finish")));
        State initial = state("initial");
        initial.addTransition(new Transition(List.of(new StaticDecision("first")), List.of(new Action(new NoOpPolicy()) {
            @Override public void execute(EventHistory events, PolicyRuntime runtime) { actions.incrementAndGet(); storage.put("mode", new com.google.gson.JsonPrimitive("after")); }
        }), following));
        initial.addTransition(new Transition(new StaticDecision("unused"), state("unused")));
        var gateway = new Gateway(true) {
            @Override public boolean decide(List<PromptMessage> messages) {
                assertEquals(1, actions.get()); assertEquals("after-action after", messages.get(0).getContent());
                return super.decide(messages);
            }
        };
        Agent agent = new Agent("a", "d", initial);
        agent.acknowledge(input(), runtime(gateway));
        assertEquals("finish", agent.getCurrentState().getName());
        assertEquals(1, actions.get()); assertEquals(1, gateway.batches); assertEquals(1, gateway.singles);
    }

    @Test void sizeLimitAndIncompatibleRoutesUseOrdinaryOrderedCalls() {
        var gateway = new Gateway(false) {
            @Override public GuardInferenceOptions guardInferenceOptions() {
                return new GuardInferenceOptions(GuardInferenceOptions.Strategy.COMBINED, 1, 1024);
            }
        };
        new Agent("a", "d", twoChecks(new AtomicInteger())).acknowledge(input(), runtime(gateway));
        assertEquals(0, gateway.batches); assertEquals(2, gateway.singles);
        var incompatible = new Gateway(false) {
            @Override public Object guardCompatibilityKey(InferenceRequest request) { return request.requestId(); }
        };
        new Agent("b", "d", twoChecks(new AtomicInteger())).acknowledge(input(), runtime(incompatible));
        assertEquals(0, incompatible.batches); assertEquals(2, incompatible.singles);
        var bounded = new Gateway(false) {
            @Override public GuardInferenceOptions guardInferenceOptions() {
                return new GuardInferenceOptions(GuardInferenceOptions.Strategy.COMBINED, 16, 1024);
            }
        };
        State large = state("large");
        large.addTransition(new Transition(new StaticDecision("x".repeat(1024)), state("one")));
        large.addTransition(new Transition(new StaticDecision("y".repeat(1024)), state("two")));
        new Agent("c", "d", large).acknowledge(input(), runtime(bounded));
        assertEquals(0, bounded.batches); assertEquals(2, bounded.singles);
    }

    @Test void historyChangedDuringInferenceDiscardsResults() {
        State initial = twoChecks(new AtomicInteger()); Agent agent = new Agent("a", "d", initial);
        var gateway = new Gateway(true) {
            @Override public String infer(InferenceRequest request) {
                agent.getEventHistory().appendEvent(input().withStatePath("initial"));
                return super.infer(request);
            }
        };
        assertThrows(IllegalStateException.class, () -> agent.acknowledge(input(), runtime(gateway)));
        assertSame(initial, agent.getCurrentState());
    }

    @Test void catalogRoleSelectionKeepsFirstTransitionWhenBothRolesAreTrue() {
        Agent agent = new ch.zhaw.prometheus.agentdefs.core.RoleClarificationGuessingGame().createAgent();
        var gateway = new Gateway(false) {
            @Override public String infer(InferenceRequest request) {
                if (request.purpose() == InferencePurpose.BEHAVIOUR) return "{\"speech\":\"Ready.\",\"nonVerbal\":{}}";
                assertEquals(java.util.Set.of("g0", "g1", "g2", "g3", "g4"), request.schema().getAsJsonObject("properties").keySet());
                batches++; return "{\"g0\":false,\"g1\":false,\"g2\":false,\"g3\":true,\"g4\":true}";
            }
        };
        agent.start(runtime(gateway));
        agent.acknowledge(input(), runtime(gateway));
        assertEquals("Valerian Core guessing game - Valerian guesses", ((OuterState) agent.getCurrentState()).getInnerCurrent().getName());
        assertEquals(1, gateway.batches);
    }

    private static State twoChecks(AtomicInteger actions) {
        State initial = state("initial");
        for (String name : List.of("first", "second")) initial.addTransition(new Transition(
                List.of(new StaticDecision(name)), List.of(count(actions)), state(name)));
        return initial;
    }
    private static Action count(AtomicInteger count) {
        return new Action(new NoOpPolicy()) { @Override public void execute(EventHistory events, PolicyRuntime runtime) { count.incrementAndGet(); } };
    }
    private static State state(String name) { return new State(name, new NoOpPolicy(), List.of()); }
    private static Event input() { return Event.observation(Event.TYPE_USER_UTTERANCE, "user", "latest-user"); }
    private static PolicyRuntime runtime(LanguageModelGateway gateway) { return new PolicyRuntime(new PromptMessageAssembler(), gateway); }
    private static class Gateway extends NoOpLanguageModelGateway {
        int batches, singles; String override; InferenceRequest last; boolean answer;
        Gateway(boolean answer) { this.answer = answer; }
        @Override public GuardInferenceOptions guardInferenceOptions() { return new GuardInferenceOptions(GuardInferenceOptions.Strategy.COMBINED, 16, 65536); }
        @Override public boolean decide(List<PromptMessage> messages) { singles++; return answer; }
        @Override public String infer(InferenceRequest request) {
            batches++; last = request;
            if (override != null) return override;
            JsonObject result = new JsonObject();
            for (String key : request.schema().getAsJsonObject("properties").keySet()) result.addProperty(key, answer);
            return result.toString();
        }
    }
}
