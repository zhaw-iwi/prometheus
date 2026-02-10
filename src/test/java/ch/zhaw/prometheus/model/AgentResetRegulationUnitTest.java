package ch.zhaw.prometheus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.commons.regulation.ZurichRegulationSystem;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;

class AgentResetRegulationUnitTest {

    @Test
    void resetClearsZurichLatentStateAndRearmsOpportunityEmission() {
        Agent agent = new Agent("a", "d", new State("start", new NoOpPolicy(), List.of()));
        ZurichRegulationSystem regulation = new ZurichRegulationSystem(
                0.0d, 0.0d, 0.0d,
                0.0d, 0.6d, 0.2d, 0.5d);
        agent.setRegulationSystem(regulation);
        var runtime = TestPolicyRuntime.runtime();

        agent.tick(runtime);
        assertEquals(0.6d, regulation.getVariable(ZurichRegulationSystem.VAR_DEPENDENCY), 1e-9);
        assertTrue(agent.getEventHistory().toList().stream()
                .anyMatch(event -> Event.TYPE_INTERNAL_REGULATION_OPPORTUNITY.equals(event.getType())));

        agent.reset();

        assertEquals(0.0d, regulation.getVariable(ZurichRegulationSystem.VAR_DEPENDENCY), 1e-9);
        assertTrue(agent.getEventHistory().isEmpty());

        agent.tick(runtime);

        assertTrue(agent.getEventHistory().toList().stream()
                .anyMatch(event -> Event.TYPE_INTERNAL_REGULATION_OPPORTUNITY.equals(event.getType())));
    }
}
