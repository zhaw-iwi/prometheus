package ch.zhaw.prometheus.model.event;

import java.util.List;
import java.util.UUID;

/** Internal additive event metadata; absence denotes ordinary legacy backend speech. */
public record SpeechProvenance(Origin origin, UUID sessionId, UUID epoch, String segmentId,
        Association association, List<UUID> intentIds, boolean complete) {
    public enum Origin { BACKEND_INTENT, NATIVE }
    public enum Association { NONE, AMBIGUOUS, CONFIRMED }
    public SpeechProvenance {
        intentIds = intentIds == null ? List.of() : List.copyOf(intentIds);
        if (origin == null || sessionId == null || epoch == null || association == null || intentIds.size() > 64)
            throw new IllegalArgumentException("Invalid speech provenance");
    }
    public static SpeechProvenance intent(ch.zhaw.prometheus.model.policy.ExternalSpeech owner) {
        return new SpeechProvenance(Origin.BACKEND_INTENT, owner.sessionId(), owner.epoch(), null, Association.NONE, List.of(), true);
    }
}
