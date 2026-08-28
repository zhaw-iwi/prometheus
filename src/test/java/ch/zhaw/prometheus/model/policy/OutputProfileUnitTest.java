package ch.zhaw.prometheus.model.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class OutputProfileUnitTest {

    @Test
    void fullPlanIsTheOnlySupportedOutputProfile() {
        assertEquals(1, OutputProfile.values().length);
        assertEquals(OutputProfile.FULL_PLAN, OutputProfile.fromNullable(null));
        assertEquals(OutputProfile.FULL_PLAN, OutputProfile.fromNullable(""));
        assertEquals(OutputProfile.FULL_PLAN, OutputProfile.fromNullable("full-plan"));
        assertNull(OutputProfile.fromNullable("realtime_speech"));
        assertNull(OutputProfile.fromNullable("backend_complement"));
        assertNull(OutputProfile.fromNullable("speech_only"));
    }
}
