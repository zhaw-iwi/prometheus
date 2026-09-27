package ch.zhaw.prometheus.model.policy;

import java.util.UUID;

/** Provider-neutral, application-owned speech execution context. Not a public output profile. */
public record ExternalSpeech(UUID sessionId, UUID epoch) {}
