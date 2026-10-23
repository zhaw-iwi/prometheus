package ch.zhaw.prometheus.model.policy;

import java.time.Instant;
import java.util.UUID;

/** Application-owned playback expectation, separate from provider response completion. */
public record LiveSpeechCue(UUID id, UUID roundId, Instant startedAt, Instant deadline, long minimumStartMs) {}
