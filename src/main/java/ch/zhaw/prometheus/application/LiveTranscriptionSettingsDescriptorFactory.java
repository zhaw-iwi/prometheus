package ch.zhaw.prometheus.application;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import ch.zhaw.prometheus.application.LiveTranscriptionSettings.InputLanguage;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.NoiseReduction;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.TranscriptionDelay;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.TurnMode;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.WireValue;
import ch.zhaw.prometheus.controllers.views.LiveTranscriptionCapabilitiesView;
import ch.zhaw.prometheus.controllers.views.LiveTranscriptionCapabilitiesView.Capabilities;
import ch.zhaw.prometheus.controllers.views.LiveTranscriptionCapabilitiesView.Setting;
import ch.zhaw.prometheus.spi.LiveTranscriptionSessionClient;

@Component
public class LiveTranscriptionSettingsDescriptorFactory {

    public static final int SCHEMA_VERSION = 1;

    public LiveTranscriptionCapabilitiesView descriptor(String agentLanguageCode) {
        String defaultLanguage = InputLanguage.fromAgentLanguage(agentLanguageCode).wireValue();
        List<Setting> settings = List.of(
                select("noiseReduction", LiveTranscriptionSettingsNormalizer.DEFAULT_NOISE_REDUCTION.wireValue(),
                        wireValues(NoiseReduction.values()), "live-input-boundary", null, false),
                select("turnDetection.type", LiveTranscriptionSettingsNormalizer.DEFAULT_TURN_MODE.wireValue(),
                        wireValues(TurnMode.values()), "local-input-boundary", null, false),
                number("turnDetection.silenceDurationSeconds",
                        LiveTranscriptionSettingsNormalizer.DEFAULT_LOCAL_SILENCE_DURATION_SECONDS,
                        LiveTranscriptionSettingsNormalizer.LOCAL_SILENCE_DURATION_MIN_SECONDS,
                        LiveTranscriptionSettingsNormalizer.LOCAL_SILENCE_DURATION_MAX_SECONDS,
                        0.1, "local-input-boundary", "turnDetection.type=local_vad"),
                new Setting("transcriptionPrompt", "text", "", List.of(), null, null, null,
                        LiveTranscriptionSettingsNormalizer.PROMPT_MAX_LENGTH, null, null, null,
                        "live-input-boundary", null, true),
                new Setting("transcriptionKeywords", "string-list", List.of(), List.of(), null, null, null,
                        LiveTranscriptionSettingsNormalizer.KEYWORD_MAX_LENGTH,
                        LiveTranscriptionSettingsNormalizer.KEYWORD_MAX_ITEMS, 0,
                        LiveTranscriptionSettingsNormalizer.KEYWORD_ITEM_PATTERN,
                        "live-input-boundary", null, true),
                new Setting("languages", "multi-select", List.of(defaultLanguage), wireValues(InputLanguage.values()),
                        null, null, null, null, 2, 1, null, "live-input-boundary", null, false),
                select("transcriptionDelay", LiveTranscriptionSettingsNormalizer.DEFAULT_DELAY.wireValue(),
                        wireValues(TranscriptionDelay.values()), "live-input-boundary", null, false));
        return new LiveTranscriptionCapabilitiesView(
                SCHEMA_VERSION,
                LiveTranscriptionSessionClient.SESSION_TYPE,
                LiveTranscriptionSessionClient.MODEL,
                new Capabilities(false, true),
                settings);
    }

    private static Setting select(String key, Object defaultValue, List<String> allowedValues,
            String behavior, String visibleWhen, boolean sensitive) {
        return new Setting(key, "select", defaultValue, allowedValues, null, null, null, null, null, null, null,
                behavior, visibleWhen, sensitive);
    }

    private static Setting number(String key, Number defaultValue, Number minimum, Number maximum, Number step,
            String behavior, String visibleWhen) {
        return new Setting(key, "number", defaultValue, List.of(), minimum, maximum, step, null, null, null, null,
                behavior, visibleWhen, false);
    }

    private static List<String> wireValues(WireValue[] values) {
        return Arrays.stream(values).map(WireValue::wireValue).toList();
    }
}
