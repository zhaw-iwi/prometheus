package ch.zhaw.prometheus.application;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public record LiveTranscriptionSettings(
        TurnDetection turnDetection,
        NoiseReduction noiseReduction,
        String transcriptionPrompt,
        List<String> transcriptionKeywords,
        List<InputLanguage> languages,
        TranscriptionDelay transcriptionDelay) {

    public LiveTranscriptionSettings {
        transcriptionKeywords = List.copyOf(transcriptionKeywords);
        languages = List.copyOf(languages);
    }

    public interface WireValue {
        @JsonValue
        String wireValue();
    }

    public enum NoiseReduction implements WireValue {
        @JsonProperty("near_field")
        NEAR_FIELD("near_field"),
        @JsonProperty("far_field")
        FAR_FIELD("far_field"),
        @JsonProperty("off")
        OFF("off");

        private final String wireValue;

        NoiseReduction(String wireValue) {
            this.wireValue = wireValue;
        }

        @Override
        public String wireValue() {
            return this.wireValue;
        }
    }

    public enum TurnMode implements WireValue {
        @JsonProperty("local_vad")
        LOCAL_VAD("local_vad"),
        @JsonProperty("manual")
        MANUAL("manual");

        private final String wireValue;

        TurnMode(String wireValue) {
            this.wireValue = wireValue;
        }

        @Override
        public String wireValue() {
            return this.wireValue;
        }
    }

    public enum InputLanguage implements WireValue {
        @JsonProperty("ar")
        AR("ar"),
        @JsonProperty("de")
        DE("de"),
        @JsonProperty("en")
        EN("en");

        private final String wireValue;

        InputLanguage(String wireValue) {
            this.wireValue = wireValue;
        }

        @Override
        public String wireValue() {
            return this.wireValue;
        }

        public static InputLanguage fromAgentLanguage(String languageCode) {
            if (languageCode == null) {
                return EN;
            }
            return switch (languageCode.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "ar" -> AR;
                case "de" -> DE;
                default -> EN;
            };
        }
    }

    public enum TranscriptionDelay implements WireValue {
        @JsonProperty("minimal")
        MINIMAL("minimal"),
        @JsonProperty("low")
        LOW("low"),
        @JsonProperty("medium")
        MEDIUM("medium"),
        @JsonProperty("high")
        HIGH("high"),
        @JsonProperty("xhigh")
        XHIGH("xhigh");

        private final String wireValue;

        TranscriptionDelay(String wireValue) {
            this.wireValue = wireValue;
        }

        @Override
        public String wireValue() {
            return this.wireValue;
        }
    }

    public record TurnDetection(TurnMode type, Double silenceDurationSeconds) {
    }
}
