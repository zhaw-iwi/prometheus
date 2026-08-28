package ch.zhaw.prometheus.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import ch.zhaw.prometheus.application.LiveTranscriptionSettings.InputLanguage;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.NoiseReduction;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.TranscriptionDelay;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.TurnDetection;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.TurnMode;
import ch.zhaw.prometheus.controllers.dto.LiveTranscriptionSettingsRequest;

@Component
public class LiveTranscriptionSettingsNormalizer {

    public static final int PROMPT_MAX_LENGTH = 1024;
    public static final int KEYWORD_MAX_ITEMS = 100;
    public static final int KEYWORD_MAX_LENGTH = 100;
    public static final String KEYWORD_ITEM_PATTERN = "^[^<>\\r\\n]+$";
    public static final double LOCAL_SILENCE_DURATION_MIN_SECONDS = 0.5;
    public static final double LOCAL_SILENCE_DURATION_MAX_SECONDS = 10.0;
    public static final double DEFAULT_LOCAL_SILENCE_DURATION_SECONDS = 1.5;
    public static final NoiseReduction DEFAULT_NOISE_REDUCTION = NoiseReduction.FAR_FIELD;
    public static final TurnMode DEFAULT_TURN_MODE = TurnMode.LOCAL_VAD;
    public static final TranscriptionDelay DEFAULT_DELAY = TranscriptionDelay.MEDIUM;

    public LiveTranscriptionSettings normalize(LiveTranscriptionSettingsRequest requested,
            String agentLanguageCode) {
        LiveTranscriptionSettingsRequest source = requested == null
                ? new LiveTranscriptionSettingsRequest(null, null, null, null, null, null)
                : requested;
        TurnDetection turnDetection = normalizeTurnDetection(source.turnDetection());
        return new LiveTranscriptionSettings(
                turnDetection,
                source.noiseReduction() == null ? DEFAULT_NOISE_REDUCTION : source.noiseReduction(),
                normalizePrompt(source.transcriptionPrompt()),
                normalizeKeywords(source.transcriptionKeywords()),
                normalizeLanguages(source.languages(), agentLanguageCode),
                source.transcriptionDelay() == null ? DEFAULT_DELAY : source.transcriptionDelay());
    }

    private static TurnDetection normalizeTurnDetection(
            LiveTranscriptionSettingsRequest.TurnDetectionRequest requested) {
        TurnMode mode = requested == null || requested.type() == null ? DEFAULT_TURN_MODE : requested.type();
        Double silenceDuration = requested == null ? null : requested.silenceDurationSeconds();
        if (mode == TurnMode.MANUAL) {
            if (silenceDuration != null) {
                throw new IllegalArgumentException(
                        "turnDetection.silenceDurationSeconds applies only to local_vad");
            }
            return new TurnDetection(mode, null);
        }
        double normalized = silenceDuration == null ? DEFAULT_LOCAL_SILENCE_DURATION_SECONDS : silenceDuration;
        if (!Double.isFinite(normalized)
                || normalized < LOCAL_SILENCE_DURATION_MIN_SECONDS
                || normalized > LOCAL_SILENCE_DURATION_MAX_SECONDS) {
            throw new IllegalArgumentException(
                    "turnDetection.silenceDurationSeconds must be between 0.5 and 10.0");
        }
        return new TurnDetection(mode, normalized);
    }

    private static String normalizePrompt(String prompt) {
        if (prompt == null) {
            return "";
        }
        String normalized = prompt.trim();
        if (normalized.length() > PROMPT_MAX_LENGTH) {
            throw new IllegalArgumentException("transcriptionPrompt exceeds " + PROMPT_MAX_LENGTH + " characters");
        }
        return normalized;
    }

    private static List<String> normalizeKeywords(List<String> keywords) {
        if (keywords == null) {
            return List.of();
        }
        if (keywords.size() > KEYWORD_MAX_ITEMS) {
            throw new IllegalArgumentException("transcriptionKeywords exceeds " + KEYWORD_MAX_ITEMS + " items");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String keyword : keywords) {
            if (keyword == null) {
                throw new IllegalArgumentException("transcriptionKeywords must not contain null");
            }
            String trimmed = keyword.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > KEYWORD_MAX_LENGTH) {
                throw new IllegalArgumentException(
                        "transcription keyword exceeds " + KEYWORD_MAX_LENGTH + " characters");
            }
            if (trimmed.indexOf('<') >= 0 || trimmed.indexOf('>') >= 0
                    || trimmed.indexOf('\r') >= 0 || trimmed.indexOf('\n') >= 0) {
                throw new IllegalArgumentException("transcription keywords contain unsupported characters");
            }
            normalized.add(trimmed);
        }
        return List.copyOf(normalized);
    }

    private static List<InputLanguage> normalizeLanguages(List<InputLanguage> languages, String agentLanguageCode) {
        if (languages == null) {
            return List.of(InputLanguage.fromAgentLanguage(agentLanguageCode));
        }
        if (languages.isEmpty()) {
            throw new IllegalArgumentException("languages must contain at least one supported language");
        }
        Set<InputLanguage> normalized = new LinkedHashSet<>();
        for (InputLanguage language : languages) {
            if (language == null) {
                throw new IllegalArgumentException("languages must not contain null");
            }
            normalized.add(language);
        }
        return List.copyOf(new ArrayList<>(normalized));
    }
}
