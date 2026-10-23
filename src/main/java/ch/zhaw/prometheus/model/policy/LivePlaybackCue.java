package ch.zhaw.prometheus.model.policy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Application-approved captured speech awaiting client-observed playback settling.
 * Neither this cue nor its acknowledgment proves physical speaker audibility.
 */
public record LivePlaybackCue(UUID id, String segmentId, Instant expiresAt) {
    public LivePlaybackCue {
        Objects.requireNonNull(id); Objects.requireNonNull(expiresAt);
        if (segmentId != null && !segmentId.matches("[A-Za-z0-9._:-]{1,160}"))
            throw new IllegalArgumentException("Invalid captured segment identity");
    }
    public boolean expired(Instant now) { return !now.isBefore(expiresAt); }
}
