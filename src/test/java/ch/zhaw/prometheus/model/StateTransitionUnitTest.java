package ch.zhaw.prometheus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.event.EventSelectorSpec;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.model.policy.Policy;

class StateTransitionUnitTest {

    @Test
    void stateRespondEmitsBehaviourPlanEventWithSpeechPayload() {
        State state = new State("conversation", new FixedSpeechPolicy("hello", "response"), List.of());
        Agent agent = new Agent("a", "d", state);
        var runtime = TestPolicyRuntime.runtime();

        Event userEvent = Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "Hi");
        Event response = agent.respond(userEvent, runtime);

        assertNotNull(response);
        assertEquals(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, response.getType());
        assertEquals(Event.ACTOR_ASSISTANT, response.getActor());
        assertTrue(response.getPayload().contains("\"speech\":\"response\""));
    }

    @Test
    void transitionUsesStateDefaultSelectorWhenDecisionSelectorIsNull() {
        State state = new State("focus", new NoOpPolicy(), List.of());
        EventHistory sharedHistory = new EventHistory();
        state.setEventHistory(sharedHistory);

        sharedHistory.appendEvent(
                Event.observation("obs.user_utterance", "user", "from-focus").withStatePath("focus"));
        State other = new State("other", new NoOpPolicy(), List.of());
        sharedHistory.appendEvent(
                Event.observation("obs.user_utterance", "user", "from-other").withStatePath("other"));

        CapturingDecisionPolicy decisionPolicy = new CapturingDecisionPolicy(true);
        Decision decision = new TestDecision(decisionPolicy);
        Transition transition = new Transition(List.of(decision), List.of(), other);

        boolean decided = transition.decide(state, TestPolicyRuntime.runtime());

        assertTrue(decided);
        assertEquals(1, decisionPolicy.lastSeenCount);
        assertEquals("from-focus", decisionPolicy.lastSeenLastContent);
    }

    @Test
    void transitionActionUsesExplicitActionSelectorWhenProvided() {
        State state = new State("focus", new NoOpPolicy(), List.of());
        EventHistory sharedHistory = new EventHistory();
        state.setEventHistory(sharedHistory);

        sharedHistory.appendEvent(Event.observation("obs.user_utterance", "user", "u1").withStatePath("focus"));
        sharedHistory.appendEvent(
                Event.response("resp.behaviour_plan", "assistant", "{\"speech\":\"a1\"}").withStatePath("focus"));

        RecordingAction action = new RecordingAction(new NoOpPolicy(), EventSelectorSpec.actor("assistant"));
        Transition transition = new Transition(List.of(), List.of(action),
                new State("next", new NoOpPolicy(), List.of()));

        transition.action(state, TestPolicyRuntime.runtime());

        assertEquals(1, action.lastSeenCount);
        assertEquals("{\"speech\":\"a1\"}", action.lastSeenLastContent);
    }

    private static class FixedSpeechPolicy extends Policy {
        private final String startSpeech;
        private final String respondSpeech;

        FixedSpeechPolicy(String startSpeech, String respondSpeech) {
            this.startSpeech = startSpeech;
            this.respondSpeech = respondSpeech;
        }

        @Override
        public BehaviourPlan onStart(State state, EventHistory events, ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler, ch.zhaw.prometheus.spi.LanguageModelGateway languageModelGateway) {
            return BehaviourPlan.speechOnly(this.startSpeech);
        }

        @Override
        public BehaviourPlan onRespond(State state, EventHistory events, ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler, ch.zhaw.prometheus.spi.LanguageModelGateway languageModelGateway) {
            return BehaviourPlan.speechOnly(this.respondSpeech);
        }

        @Override
        public String summarise(State state, EventHistory events, ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler, ch.zhaw.prometheus.spi.LanguageModelGateway languageModelGateway) {
            return "";
        }

        @Override
        public String describe() {
            return "fixed-speech-policy";
        }
    }

    private static class CapturingDecisionPolicy extends Policy {
        private final boolean answer;
        private int lastSeenCount;
        private String lastSeenLastContent;

        CapturingDecisionPolicy(boolean answer) {
            this.answer = answer;
        }

        @Override
        public BehaviourPlan onStart(State state, EventHistory events, ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler, ch.zhaw.prometheus.spi.LanguageModelGateway languageModelGateway) {
            return null;
        }

        @Override
        public BehaviourPlan onRespond(State state, EventHistory events, ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler, ch.zhaw.prometheus.spi.LanguageModelGateway languageModelGateway) {
            return null;
        }

        @Override
        public String summarise(State state, EventHistory events, ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler, ch.zhaw.prometheus.spi.LanguageModelGateway languageModelGateway) {
            return "";
        }

        @Override
        public String describe() {
            return "capturing-decision-policy";
        }

        @Override
        public boolean decide(EventHistory events, ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler,
                ch.zhaw.prometheus.spi.LanguageModelGateway languageModelGateway) {
            List<Event> list = events.toList();
            this.lastSeenCount = list.size();
            this.lastSeenLastContent = list.isEmpty() ? null : list.get(list.size() - 1).getPayload();
            return this.answer;
        }

        @Override
        public JsonElement extract(EventHistory events, ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler,
                ch.zhaw.prometheus.spi.LanguageModelGateway languageModelGateway) {
            return new JsonPrimitive("unused");
        }
    }

    private static class TestDecision extends Decision {
        TestDecision(Policy policy) {
            super(policy);
        }
    }

    private static class RecordingAction extends Action {
        private int lastSeenCount;
        private String lastSeenLastContent;

        RecordingAction(Policy policy, EventSelectorSpec selector) {
            super(policy, selector);
        }

        @Override
        public void execute(EventHistory eventHistory, ch.zhaw.prometheus.model.policy.PolicyRuntime runtime) {
            List<Event> list = eventHistory.toList();
            this.lastSeenCount = list.size();
            this.lastSeenLastContent = list.isEmpty() ? null : list.get(list.size() - 1).getPayload();
        }
    }
}

