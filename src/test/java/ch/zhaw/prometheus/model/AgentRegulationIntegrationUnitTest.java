package ch.zhaw.prometheus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.event.EventSelector;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.regulation.ZurichRegulationSystem;

class AgentRegulationIntegrationUnitTest {

    @Test
    void regulationInternalOpportunityCanTriggerTransitionOnTick() {
        State finalState = new InactiveState("final", new NoOpPolicy());
        Decision decision = new HasEventsDecision();
        decision.setEventSelector(EventSelector.type(Event.TYPE_INTERNAL_REGULATION_OPPORTUNITY));
        Transition toFinal = new Transition(List.of(decision), List.of(), finalState);
        State start = new State("start", new NoOpPolicy(), List.of(toFinal));

        Agent agent = new Agent("regulated", "test", start);
        agent.setRegulationSystem(new ZurichRegulationSystem(
                0.0d, 0.0d, 0.0d,
                0.0d, 0.6d, 0.2d, 0.5d));

        agent.tick();

        assertEquals("final", agent.getCurrentState().getName());
        assertTrue(!agent.isActive());
        assertTrue(agent.getEventHistory().toList().stream()
                .anyMatch(event -> Event.TYPE_INTERNAL_REGULATION_OPPORTUNITY.equals(event.getType())));
    }

    private static class HasEventsDecision extends Decision {
        HasEventsDecision() {
            super(new NoOpPolicy());
        }

        @Override
        public boolean decide(EventHistory events) {
            return !events.isEmpty();
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

        @Override
        public Event start() {
            return null;
        }

        @Override
        public Event start(Policy outerPolicy) {
            return null;
        }
    }
}
