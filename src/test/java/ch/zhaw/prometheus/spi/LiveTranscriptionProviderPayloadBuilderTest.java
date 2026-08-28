package ch.zhaw.prometheus.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.zhaw.prometheus.application.LiveTranscriptionSettings;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.InputLanguage;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.NoiseReduction;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.TranscriptionDelay;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.TurnDetection;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.TurnMode;

class LiveTranscriptionProviderPayloadBuilderTest {

    private final LiveTranscriptionProviderPayloadBuilder builder = new LiveTranscriptionProviderPayloadBuilder();

    @Test
    void customizedEnvelopeMatchesTranscriptionOnlyProviderShape() {
        LiveTranscriptionSettings settings = new LiveTranscriptionSettings(
                new TurnDetection(TurnMode.LOCAL_VAD, 2.5),
                NoiseReduction.FAR_FIELD,
                "meeting context",
                List.of("PROMETHEUS", "ZHAW"),
                List.of(InputLanguage.AR, InputLanguage.DE, InputLanguage.EN),
                TranscriptionDelay.XHIGH);

        JsonObject actual = this.builder.buildClientSecretEnvelope(settings, 60);
        JsonObject expected = JsonParser.parseString("""
                {
                  "expires_after":{"anchor":"created_at","seconds":60},
                  "session":{
                    "type":"transcription",
                    "audio":{"input":{
                      "transcription":{
                        "model":"gpt-live-transcribe",
                        "prompt":"meeting context",
                        "keywords":["PROMETHEUS","ZHAW"],
                        "languages":["ar","de","en"],
                        "delay":"xhigh"
                      },
                      "noise_reduction":{"type":"far_field"},
                      "turn_detection":null
                    }}
                  }
                }
                """).getAsJsonObject();

        assertEquals(expected, actual);
        String serialized = actual.toString();
        assertFalse(serialized.contains("output"));
        assertFalse(serialized.contains("voice"));
        assertFalse(serialized.contains("assistant"));
        assertFalse(serialized.contains("diarization"));
    }

    @Test
    void explicitNoiseReductionOffSerializesAsNull() {
        LiveTranscriptionSettings settings = new LiveTranscriptionSettings(
                new TurnDetection(TurnMode.MANUAL, null),
                NoiseReduction.OFF,
                "",
                List.of(),
                List.of(InputLanguage.EN),
                TranscriptionDelay.MEDIUM);

        JsonObject input = this.builder.buildClientSecretEnvelope(settings, 75)
                .getAsJsonObject("session").getAsJsonObject("audio").getAsJsonObject("input");
        assertTrue(input.get("noise_reduction").isJsonNull());
        assertTrue(input.get("turn_detection").isJsonNull());
        assertEquals("gpt-live-transcribe", input.getAsJsonObject("transcription").get("model").getAsString());
    }
}
