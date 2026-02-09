package ch.zhaw.prometheus.model.commons.regulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.regulation.RegulationContext;
import ch.zhaw.prometheus.model.regulation.RegulationResult;
import ch.zhaw.prometheus.model.snapshot.ObservationSnapshot;

class ZurichRegulationSystemUnitTest {

    @Test
    void tickCrossingThresholdEmitsSingleOpportunityEvent() {
        ZurichRegulationSystem regulation = new ZurichRegulationSystem(
                0.0d, 0.0d, 0.0d,
                0.0d, 0.6d, 0.2d, 0.5d);

        RegulationResult first = regulation.update(context(Event.systemTick("S")));
        RegulationResult second = regulation.update(context(Event.systemTick("S")));

        assertEquals(1, first.internalEvents().size());
        assertEquals(Event.TYPE_INTERNAL_REGULATION_OPPORTUNITY, first.internalEvents().get(0).getType());
        assertTrue(first.modulation().get(ZurichRegulationSystem.MOD_AFFILIATION) >= 0.5d);
        assertTrue(second.internalEvents().isEmpty());
    }

    @Test
    void userUtteranceRelievesDependencyAndReducesAffiliationPressure() {
        ZurichRegulationSystem regulation = new ZurichRegulationSystem(
                0.8d, 0.0d, 0.0d,
                0.0d, 0.0d, 0.3d, 0.7d);

        RegulationResult result = regulation.update(context(
                Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "hello", null, "S")));

        assertTrue(regulation.getVariable(ZurichRegulationSystem.VAR_DEPENDENCY) < 0.8d);
        assertTrue(result.modulation().get(ZurichRegulationSystem.MOD_AFFILIATION) < 0.8d);
        assertTrue(result.internalEvents().isEmpty());
    }

    private static RegulationContext context(Event trigger) {
        return new RegulationContext(trigger, new EventHistory(), ObservationSnapshot.empty(), Instant.now());
    }
}
