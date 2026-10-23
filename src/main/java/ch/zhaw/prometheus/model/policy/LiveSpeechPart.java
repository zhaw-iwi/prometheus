package ch.zhaw.prometheus.model.policy;

import java.time.Instant;

/** Trusted captured delta and backend receipt time, retained only during speech recording. */
public record LiveSpeechPart(String text, Instant receivedAt) {}
