package ch.zhaw.prometheus.model.policy;

import java.time.Instant;
import java.util.UUID;

/** A current speaking permission, separate from permission to execute a task. */
public record LiveSpeechRequest(UUID id, ExternalSpeech owner, int revision, Instant created,
        Instant expiresAt, String facts, Instant releaseBy) {
    public static final String ENABLED = "live.speechApproval";
    public LiveSpeechRequest(UUID id, ExternalSpeech owner, int revision, Instant created, Instant expiresAt, String facts) {
        this(id, owner, revision, created, expiresAt, facts, created.plusSeconds(60));
    }
    public LiveSpeechRequest {
        if (id == null || owner == null || created == null || expiresAt == null || !expiresAt.isAfter(created)
                || expiresAt.isAfter(created.plusSeconds(45)) || facts == null || facts.isBlank()
                || releaseBy == null || releaseBy.isBefore(expiresAt) || releaseBy.isAfter(created.plusSeconds(60))
                || facts.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 6000)
            throw new IllegalArgumentException("Invalid speech request");
    }
}
