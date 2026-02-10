package ch.zhaw.prometheus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.event.EventSelector;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.model.policy.Policy;

class AgentNestedOuterStateRoutingUnitTest {

    @Test
    void nestedOuterStatesRouteEventsByPathContainmentAcrossLeafTransition() {
        State leaf2 = new State("Leaf2", new FixedSpeechPolicy("leaf2-start", "leaf2-respond"), List.of());
        Decision always = new AlwaysTrueDecision();
        Transition toLeaf2 = new Transition(List.of(always), List.of(), leaf2);
        State leaf1 = new State("Leaf1", new FixedSpeechPolicy("leaf1-start", "leaf1-respond"), List.of(toLeaf2));
        OuterState outerB = new OuterState("outer-b", "OuterB", List.of(), leaf1);
        OuterState outerA = new OuterState("outer-a", "OuterA", List.of(), outerB);
        Agent agent = new Agent("a", "d", outerA);

        agent.start();
        agent.respond(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "u1"));
        agent.respond(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "u2"));

        List<Event> all = agent.getEventHistory().toList();
        assertEquals(5, all.size());

        assertEquals(2, count(all, EventSelector.stateName("Leaf1")));
        assertEquals(3, count(all, EventSelector.stateName("Leaf2")));
        assertEquals(5, count(all, EventSelector.stateName("OuterB")));
        assertEquals(5, count(all, EventSelector.stateName("OuterA")));

        Event first = all.get(0);
        Event third = all.get(2);
        assertIterableEquals(List.of("OuterA", "OuterB", "Leaf1"), first.getStatePath());
        assertIterableEquals(List.of("OuterA", "OuterB", "Leaf2"), third.getStatePath());
    }

    private static long count(List<Event> events, EventSelector selector) {
        return events.stream().filter(selector::test).count();
    }

    private static class AlwaysTrueDecision extends Decision {
        AlwaysTrueDecision() {
            super(new NoOpPolicy());
        }

        @Override
        public boolean decide(EventHistory events, ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler, ch.zhaw.prometheus.spi.LanguageModelGateway languageModelGateway) {
            return true;
        }
    }

    private static class FixedSpeechPolicy extends Policy {
        private final String onStartSpeech;
        private final String onRespondSpeech;

        FixedSpeechPolicy(String onStartSpeech, String onRespondSpeech) {
            this.onStartSpeech = onStartSpeech;
            this.onRespondSpeech = onRespondSpeech;
        }

        @Override
        public BehaviourPlan onStart(State state, EventHistory events, ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler, ch.zhaw.prometheus.spi.LanguageModelGateway languageModelGateway) {
            return BehaviourPlan.speechOnly(this.onStartSpeech);
        }

        @Override
        public BehaviourPlan onRespond(State state, EventHistory events, ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler, ch.zhaw.prometheus.spi.LanguageModelGateway languageModelGateway) {
            return BehaviourPlan.speechOnly(this.onRespondSpeech);
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


