package ch.zhaw.prometheus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ch.zhaw.prometheus.model.commons.regulation.ZurichRegulationSystem;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.repositories.AgentRepository;

@SpringBootTest
class RegulationSystemPersistenceUnitTest {

    @Autowired
    private AgentRepository repository;

    @Test
    void zurichRegulationStatePersistsAcrossSaveReload() {
        State start = new State("start", new NoOpPolicy(), List.of());
        Agent agent = new Agent("regulated", "persistence", start);
        agent.setRegulationSystem(new ZurichRegulationSystem(
                0.0d, 0.0d, 0.0d,
                0.0d, 0.30d, 0.0d, 0.50d));

        Agent saved = this.repository.save(agent);
        Agent cycle1 = this.repository.findById(saved.getId()).orElseThrow();
        assertTrue(cycle1.getRegulationSystem() instanceof ZurichRegulationSystem);
        cycle1.tick(TestPolicyRuntime.runtime());
        assertEquals(0.30d,
                ((ZurichRegulationSystem) cycle1.getRegulationSystem()).getVariable(ZurichRegulationSystem.VAR_DEPENDENCY),
                0.0001d);
        this.repository.save(cycle1);

        boolean firstCycleOpportunity = cycle1.getEventHistory().toList().stream()
                .anyMatch(event -> Event.TYPE_INTERNAL_REGULATION_OPPORTUNITY.equals(event.getType()));
        assertFalse(firstCycleOpportunity);

        Agent cycle2 = this.repository.findById(saved.getId()).orElseThrow();
        assertTrue(cycle2.getRegulationSystem() instanceof ZurichRegulationSystem);
        assertEquals(0.30d,
                ((ZurichRegulationSystem) cycle2.getRegulationSystem()).getVariable(ZurichRegulationSystem.VAR_DEPENDENCY),
                0.0001d);
        cycle2.tick(TestPolicyRuntime.runtime());
        assertEquals(0.60d,
                ((ZurichRegulationSystem) cycle2.getRegulationSystem()).getVariable(ZurichRegulationSystem.VAR_DEPENDENCY),
                0.0001d);

        boolean secondCycleOpportunity = cycle2.getEventHistory().toList().stream()
                .anyMatch(event -> Event.TYPE_INTERNAL_REGULATION_OPPORTUNITY.equals(event.getType()));
        assertTrue(secondCycleOpportunity);
    }
}
