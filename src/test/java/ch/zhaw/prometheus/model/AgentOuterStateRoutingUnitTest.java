package ch.zhaw.prometheus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.Policy;

class AgentOuterStateRoutingUnitTest {

    @Test
    void recordsEventsOnceWithFullOuterToLeafPath() {
        State inner = new State("Inner", new FixedSpeechPolicy("inner-start", "inner-respond"), List.of());
        OuterState outer = new OuterState("outer policy", "Outer", List.of(), inner);
        Agent agent = new Agent("a", "d", outer);
        var runtime = TestPolicyRuntime.runtime();

        Event starter = agent.start(runtime);
        Event response = agent.respond(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "hello"), runtime);

        List<Event> history = agent.getEventHistory().toList();
        assertEquals(3, history.size());
        assertTrue(starter.getPayload().contains("\"speech\":\"inner-start\""));
        assertTrue(response.getPayload().contains("\"speech\":\"inner-respond\""));
        assertEquals(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, history.get(0).getType());
        assertEquals(Event.TYPE_USER_UTTERANCE, history.get(1).getType());
        assertEquals(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, history.get(2).getType());

        for (Event event : history) {
            assertIterableEquals(List.of("Outer", "Inner"), event.getStatePath());
        }
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
            return "fixed";
        }
    }
}

