package ch.zhaw.prometheus.model.policy;

import java.util.UUID;

/** Backend-authored words for output-only synthesis, never a native transcript. */
public record LiveCommittedSpeech(LiveSpeechRequest request, String text, UUID synthesisId) {
    public static final String ENABLED = "live.committedSpeech";
    public LiveCommittedSpeech {
        if (request == null || text == null || text.isBlank() || text.length() > 3000)
            throw new IllegalArgumentException("Invalid committed speech");
    }
}
