package ch.zhaw.prometheus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.snapshot.DefaultObservationSnapshotAggregator;
import ch.zhaw.prometheus.model.snapshot.ObservationSnapshot;

class AgentContinuousEvaluationUnitTest {

    @Test
    void tickCanDriveAssistantResponseWithoutExternalInput() {
        State state = new State("conversation", new TickResponsivePolicy(), List.of());
        Agent agent = new Agent("a", "d", state);

        Event response = agent.tick();

        assertNotNull(response);
        assertEquals(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, response.getType());
        assertEquals("Proactive check-in", response.getContent());

        List<Event> history = agent.getEventHistory().toList();
        assertEquals(2, history.size());
        assertEquals(Event.TYPE_SYSTEM_TICK, history.get(0).getType());
        assertEquals(Event.ACTOR_SYSTEM, history.get(0).getActor());
    }

    @Test
    void tickCanTriggerTransitionWithoutUserInput() {
        State finalState = new InactiveState("final", new FixedSpeechPolicy("Final reached"));
        Decision onTickDecision = new OnTickDecision();
        Transition transition = new Transition(List.of(onTickDecision), List.of(), finalState);
        State initial = new State("start", new NoOpPolicy(), List.of(transition));
        Agent agent = new Agent("a", "d", initial);

        Event response = agent.tick();

        assertNotNull(response);
        assertEquals("Final reached", response.getContent());
        assertTrue(!agent.isActive());
        assertEquals("final", agent.getCurrentState().getName());
    }

    @Test
    void tickDoesNothingForInactiveAgent() {
        Agent agent = new Agent("a", "d", new InactiveState("final", new NoOpPolicy()));

        Event response = agent.tick();

        assertNull(response);
        assertTrue(agent.getEventHistory().isEmpty());
    }

    private static class TickResponsivePolicy extends Policy {
        @Override
        public BehaviourPlan onStart(State state, EventHistory events) {
            return null;
        }

        @Override
        public BehaviourPlan onRespond(State state, EventHistory events) {
            List<Event> list = events.toList();
            if (!list.isEmpty() && Event.TYPE_SYSTEM_TICK.equals(list.get(list.size() - 1).getType())) {
                return BehaviourPlan.speechOnly("Proactive check-in");
            }
            return null;
        }

        @Override
        public String summarise(State state, EventHistory events) {
            return "";
        }

        @Override
        public String describe() {
            return "tick-responsive";
        }
    }

    private static class OnTickDecision extends Decision {
        OnTickDecision() {
            super(new NoOpPolicy());
        }

        @Override
        public boolean decide(EventHistory events, ObservationSnapshot snapshot) {
            String lastType = snapshot.getString(DefaultObservationSnapshotAggregator.FACT_LAST_EVENT_TYPE);
            return Event.TYPE_SYSTEM_TICK.equals(lastType);
        }
    }

    private static class FixedSpeechPolicy extends Policy {
        private final String speech;

        FixedSpeechPolicy(String speech) {
            this.speech = speech;
        }

        @Override
        public BehaviourPlan onStart(State state, EventHistory events) {
            return BehaviourPlan.speechOnly(this.speech);
        }

        @Override
        public BehaviourPlan onRespond(State state, EventHistory events) {
            return null;
        }

        @Override
        public String summarise(State state, EventHistory events) {
            return "";
        }

        @Override
        public String describe() {
            return "fixed";
        }
    }

    private static class InactiveState extends State {
        InactiveState(String name, Policy policy) {
            super(name, policy, List.of());
        }

        @Override
        public boolean isActive() {
            return false;
        }
    }
}
