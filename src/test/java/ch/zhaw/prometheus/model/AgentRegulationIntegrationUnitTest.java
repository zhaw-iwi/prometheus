package ch.zhaw.prometheus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.event.EventSelectorSpec;
import ch.zhaw.prometheus.model.commons.regulation.ZurichRegulationSystem;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.model.policy.Policy;

class AgentRegulationIntegrationUnitTest {

    @Test
    void regulationInternalOpportunityCanTriggerTransitionOnTick() {
        State finalState = new InactiveState("final", new NoOpPolicy());
        Decision decision = new HasEventsDecision();
        decision.setEventSelectorSpec(EventSelectorSpec.type(Event.TYPE_INTERNAL_REGULATION_OPPORTUNITY));
        Transition toFinal = new Transition(List.of(decision), List.of(), finalState);
        State start = new State("start", new NoOpPolicy(), List.of(toFinal));

        Agent agent = new Agent("regulated", "test", start);
        agent.setRegulationSystem(new ZurichRegulationSystem(
                0.0d, 0.0d, 0.0d,
                0.0d, 0.6d, 0.2d, 0.5d));
        var runtime = TestPolicyRuntime.runtime();

        agent.tick(runtime);

        assertEquals("final", agent.getCurrentState().getName());
        assertTrue(!agent.isActive());
        assertTrue(agent.getEventHistory().toList().stream()
                .anyMatch(event -> Event.TYPE_INTERNAL_REGULATION_OPPORTUNITY.equals(event.getType())));
    }

    @Test
    void regulationInternalOpportunityEventIsRecordedWithCurrentStatePath() {
        State start = new State("start", new NoOpPolicy(), List.of());
        Agent agent = new Agent("regulated", "test", start);
        agent.setRegulationSystem(new ZurichRegulationSystem(
                0.0d, 0.0d, 0.0d,
                0.0d, 0.6d, 0.2d, 0.5d));
        var runtime = TestPolicyRuntime.runtime();

        agent.tick(runtime);

        Event internal = agent.getEventHistory().toList().stream()
                .filter(event -> Event.TYPE_INTERNAL_REGULATION_OPPORTUNITY.equals(event.getType()))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("start"), internal.getStatePath());
    }

    private static class HasEventsDecision extends Decision {
        HasEventsDecision() {
            super(new NoOpPolicy());
        }

        @Override
        public boolean decide(EventHistory events, ch.zhaw.prometheus.model.policy.PolicyRuntime runtime) {
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
        public Event start(ch.zhaw.prometheus.model.policy.PolicyRuntime runtime) {
            return null;
        }

        @Override
        public Event start(Policy outerPolicy, ch.zhaw.prometheus.model.policy.PolicyRuntime runtime) {
            return null;
        }
    }
}

