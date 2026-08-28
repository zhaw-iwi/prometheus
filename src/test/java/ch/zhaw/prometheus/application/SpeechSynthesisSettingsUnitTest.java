package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SpeechSynthesisSettingsUnitTest {
    @Test
    void defaultsAndNormalizesSharedSpeechOptions() {
        SpeechSynthesisSettings defaults = new SpeechSynthesisSettings(null, null);
        assertEquals("alloy", defaults.getVoice());
        assertEquals(1.0, defaults.getSpeed(), 0.0001);

        SpeechSynthesisSettings selected = new SpeechSynthesisSettings(" Marin ", "1.25");
        assertEquals("marin", selected.getVoice());
        assertEquals(1.25, selected.getSpeed(), 0.0001);
    }

    @Test
    void rejectsUnsupportedVoiceAndInvalidSpeed() {
        assertThrows(IllegalArgumentException.class, () -> new SpeechSynthesisSettings("not-a-voice", "1"));
        assertThrows(IllegalArgumentException.class, () -> new SpeechSynthesisSettings("cedar", "0.24"));
        assertThrows(IllegalArgumentException.class, () -> new SpeechSynthesisSettings("cedar", "4.01"));
        assertThrows(IllegalArgumentException.class, () -> new SpeechSynthesisSettings("cedar", "NaN"));
    }
}
