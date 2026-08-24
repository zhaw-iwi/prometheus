package ch.zhaw.prometheus.controllers.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import ch.zhaw.prometheus.application.LiveTranscriptionSettings.InputLanguage;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.NoiseReduction;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.TranscriptionDelay;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.TurnMode;

@JsonIgnoreProperties(ignoreUnknown = false)
public record LiveTranscriptionSettingsRequest(
        TurnDetectionRequest turnDetection,
        NoiseReduction noiseReduction,
        String transcriptionPrompt,
        List<String> transcriptionKeywords,
        List<InputLanguage> languages,
        TranscriptionDelay transcriptionDelay) {

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("unsupported live-transcription settings property");
    }

    @Override
    public String toString() {
        return "LiveTranscriptionSettingsRequest[turnDetection=" + this.turnDetection
                + ", noiseReduction=" + this.noiseReduction
                + ", transcriptionPromptConfigured="
                + (this.transcriptionPrompt != null && !this.transcriptionPrompt.isBlank())
                + ", transcriptionKeywordCount="
                + (this.transcriptionKeywords == null ? 0 : this.transcriptionKeywords.size())
                + ", languages=" + this.languages
                + ", transcriptionDelay=" + this.transcriptionDelay + "]";
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record TurnDetectionRequest(TurnMode type, Double silenceDurationSeconds) {

        @JsonAnySetter
        public void rejectUnknownProperty(String name, Object value) {
            throw new IllegalArgumentException("unsupported live-transcription turn-detection property");
        }
    }
}
