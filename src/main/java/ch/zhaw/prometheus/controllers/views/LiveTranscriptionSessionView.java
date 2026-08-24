package ch.zhaw.prometheus.controllers.views;

public record LiveTranscriptionSessionView(
        String clientSecret,
        String sessionType,
        String model,
        int settingsSchemaVersion,
        String webRtcUrl,
        LiveTranscriptionEffectiveSettingsView effectiveSettings) {
}
