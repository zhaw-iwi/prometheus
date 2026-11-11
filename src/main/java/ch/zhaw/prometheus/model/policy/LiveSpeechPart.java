package ch.zhaw.prometheus.model.policy;

import java.time.Instant;

/** Trusted captured delta and backend receipt time, retained only during speech recording. */
public record LiveSpeechPart(String text, Instant receivedAt, String receiptId) {
    public LiveSpeechPart(String text, Instant receivedAt) { this(text, receivedAt, null); }
    public LiveSpeechPart {
        if (receiptId != null && !receiptId.matches("[A-Za-z0-9_:.\\-]{1,200}"))
            throw new IllegalArgumentException("Invalid captured receipt identity");
    }
}
