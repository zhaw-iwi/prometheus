package ch.zhaw.prometheus.model.hand;

import java.time.Instant;
import java.util.List;
import ch.zhaw.prometheus.model.event.Event;

/** Bound new hand ingress before persistence; legacy event contracts are unchanged. */
public final class HandObservationAdmission {
    private HandObservationAdmission() {}
    public static void validate(String payload, List<Event> history, Instant receivedAt) {
        HandSignObservation.parse(payload);
        int count = 0; Instant cutoff = receivedAt.minusSeconds(1);
        for (int i = history.size() - 1; i >= 0; i--) {
            var event = history.get(i);
            if (!Event.TYPE_HAND_SIGNS.equals(event.getType())) continue;
            if (event.getCreatedDate() == null) continue;
            if (!event.getCreatedDate().isAfter(cutoff)) break;
            if (++count >= 5)
                throw new IllegalArgumentException("Hand observation rate exceeds five frames per second");
        }
    }
}
