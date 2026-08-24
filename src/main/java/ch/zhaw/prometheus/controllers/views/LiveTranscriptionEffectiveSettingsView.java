package ch.zhaw.prometheus.controllers.views;

import java.util.List;

import ch.zhaw.prometheus.application.LiveTranscriptionSettings;

public record LiveTranscriptionEffectiveSettingsView(
        TurnDetection turnDetection,
        String noiseReduction,
        boolean transcriptionPromptConfigured,
        int transcriptionKeywordCount,
        List<String> languages,
        String transcriptionDelay) {

    public static LiveTranscriptionEffectiveSettingsView from(LiveTranscriptionSettings settings) {
        return new LiveTranscriptionEffectiveSettingsView(
                new TurnDetection(settings.turnDetection().type().wireValue(),
                        settings.turnDetection().silenceDurationSeconds()),
                settings.noiseReduction().wireValue(),
                !settings.transcriptionPrompt().isEmpty(),
                settings.transcriptionKeywords().size(),
                settings.languages().stream().map(LiveTranscriptionSettings.InputLanguage::wireValue).toList(),
                settings.transcriptionDelay().wireValue());
    }

    public record TurnDetection(String type, Double silenceDurationSeconds) {
    }
}
