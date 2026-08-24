package ch.zhaw.prometheus.controllers.views;

import java.util.List;

public record LiveTranscriptionCapabilitiesView(
        int schemaVersion,
        String sessionType,
        String model,
        Capabilities capabilities,
        List<Setting> settings) {

    public record Capabilities(boolean assistantOutput, boolean inputTranscription) {
    }

    public record Setting(
            String key,
            String control,
            Object defaultValue,
            List<String> allowedValues,
            Number minimum,
            Number maximum,
            Number step,
            Integer maxLength,
            Integer maxItems,
            Integer minItems,
            String itemPattern,
            String activeSessionBehavior,
            String visibleWhen,
            boolean sensitive) {
    }
}
