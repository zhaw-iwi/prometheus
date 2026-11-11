package ch.zhaw.prometheus.model.policy;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Application-approved captured speech awaiting client-observed playback settling.
 * Empty receiptIds require evidence for the complete segment; otherwise they select
 * a contiguous, ordered span of its native caption receipts.
 * Neither this cue nor its acknowledgment proves physical speaker audibility.
 */
public record LivePlaybackCue(UUID id, String segmentId, Instant expiresAt, List<String> receiptIds) {
    public static final int MAX_RECEIPT_IDS = 32;
    public LivePlaybackCue(UUID id, String segmentId, Instant expiresAt) { this(id, segmentId, expiresAt, List.of()); }
    public LivePlaybackCue {
        Objects.requireNonNull(id); Objects.requireNonNull(expiresAt);
        if (segmentId != null && !segmentId.matches("[A-Za-z0-9._:-]{1,160}"))
            throw new IllegalArgumentException("Invalid captured segment identity");
        receiptIds = receiptIds == null ? List.of() : List.copyOf(receiptIds);
        if (receiptIds.size() > MAX_RECEIPT_IDS || new HashSet<>(receiptIds).size() != receiptIds.size()
                || receiptIds.stream().anyMatch(value -> !value.matches("[A-Za-z0-9_:.\\-]{1,200}"))
                || !receiptIds.isEmpty() && segmentId == null)
            throw new IllegalArgumentException("Invalid captured receipt selection");
    }
    public boolean expired(Instant now) { return !now.isBefore(expiresAt); }
}
