package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.application.LiveTranscriptionSettings.InputLanguage;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.NoiseReduction;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.TranscriptionDelay;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.TurnMode;
import ch.zhaw.prometheus.controllers.dto.LiveTranscriptionSettingsRequest;
import ch.zhaw.prometheus.controllers.dto.LiveTranscriptionSettingsRequest.TurnDetectionRequest;

class LiveTranscriptionSettingsNormalizerTest {

    private final LiveTranscriptionSettingsNormalizer normalizer = new LiveTranscriptionSettingsNormalizer();

    @Test
    void defaultsAreFarFieldLocalVadMediumDelayAndAgentLanguage() {
        LiveTranscriptionSettings settings = this.normalizer.normalize(
                new LiveTranscriptionSettingsRequest(null, null, null, null, null, null), "de");

        assertEquals(NoiseReduction.FAR_FIELD, settings.noiseReduction());
        assertEquals(TurnMode.LOCAL_VAD, settings.turnDetection().type());
        assertEquals(1.5, settings.turnDetection().silenceDurationSeconds());
        assertEquals(List.of(InputLanguage.DE), settings.languages());
        assertEquals(TranscriptionDelay.MEDIUM, settings.transcriptionDelay());
        assertEquals("", settings.transcriptionPrompt());
        assertEquals(List.of(), settings.transcriptionKeywords());
    }

    @Test
    void explicitSettingsAreTrimmedDeduplicatedAndImmutable() {
        LiveTranscriptionSettings settings = this.normalizer.normalize(
                new LiveTranscriptionSettingsRequest(
                        new TurnDetectionRequest(TurnMode.LOCAL_VAD, 2.5),
                        NoiseReduction.NEAR_FIELD,
                        " meeting context ",
                        List.of(" PROMETHEUS ", "PROMETHEUS", "ZHAW"),
                        List.of(InputLanguage.EN, InputLanguage.DE, InputLanguage.EN),
                        TranscriptionDelay.HIGH),
                "de");

        assertEquals(2.5, settings.turnDetection().silenceDurationSeconds());
        assertEquals(NoiseReduction.NEAR_FIELD, settings.noiseReduction());
        assertEquals("meeting context", settings.transcriptionPrompt());
        assertEquals(List.of("PROMETHEUS", "ZHAW"), settings.transcriptionKeywords());
        assertEquals(List.of(InputLanguage.EN, InputLanguage.DE), settings.languages());
        assertEquals(TranscriptionDelay.HIGH, settings.transcriptionDelay());
        assertThrows(UnsupportedOperationException.class,
                () -> settings.transcriptionKeywords().add("other"));
    }

    @Test
    void manualModeRejectsLocalVadDuration() {
        LiveTranscriptionSettingsRequest request = new LiveTranscriptionSettingsRequest(
                new TurnDetectionRequest(TurnMode.MANUAL, 1.5), null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> this.normalizer.normalize(request, "en"));
    }

    @Test
    void unsafeAndOutOfRangeValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> this.normalizer.normalize(
                new LiveTranscriptionSettingsRequest(
                        new TurnDetectionRequest(TurnMode.LOCAL_VAD, 10.1), null, null, null, null, null),
                "en"));
        assertThrows(IllegalArgumentException.class, () -> this.normalizer.normalize(
                new LiveTranscriptionSettingsRequest(null, null, null, List.of("unsafe<keyword>"), null, null),
                "en"));
        assertThrows(IllegalArgumentException.class, () -> this.normalizer.normalize(
                new LiveTranscriptionSettingsRequest(null, null, null, null, List.of(), null), "en"));
    }

    @Test
    void unknownAgentLanguageFallsBackToEnglish() {
        LiveTranscriptionSettings settings = this.normalizer.normalize(null, "fr");
        assertEquals(List.of(InputLanguage.EN), settings.languages());
    }
}
