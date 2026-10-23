package ch.zhaw.prometheus.model.policy;

import java.time.Instant;
import java.util.UUID;

/** Transcript evidence under the current speech owner; never proof of physical playback. */
public record LiveSpeech(UUID id, String text, Long startMs, Long endMs, Instant receivedAt, boolean complete) {
    public enum Admission { DEFAULT, ADMIT, WAIT, REJECT }
}
