package ch.zhaw.prometheus.model.behaviour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BehaviourPlanUnitTest {

    @Test
    void speechOnlyPlanRoundTripsViaJson() {
        BehaviourPlan original = BehaviourPlan.speechOnly("Hello world");
        String payload = original.toJson();

        BehaviourPlan restored = BehaviourPlan.fromJson(payload);

        assertNotNull(restored);
        assertEquals("Hello world", restored.getSpeech());
        assertNull(restored.getNonVerbal());
        assertNull(restored.getMotion());
        assertNull(restored.getDisplay());
        assertFalse(restored.isEmpty());
    }

    @Test
    void emptyAndBlankPayloadCases() {
        BehaviourPlan empty = new BehaviourPlan();

        assertTrue(empty.isEmpty());
        assertNull(BehaviourPlan.fromJson(null));
        assertNull(BehaviourPlan.fromJson("   "));
    }
}
