package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.application.live.LiveDelegation;

class LiveDelegationUnitTest {
    @Test void notificationBeforeTranscriptWaitsForCommitAndDuplicateCannotRepeatWork() {
        var delegates = new LiveDelegation(); var current = LiveContextDeliveryUnitTest.snapshot("r1", "Guide");
        delegates.request("request1", 1200L, "r1", 0); assertTrue(delegates.resolve(current, 100).isEmpty());
        delegates.committed(new LiveTranscriptIngressService.Outcome(UUID.randomUUID(), "ASSISTANT", "output", "COMPLETE", "", null, 0L, 500L));
        assertTrue(delegates.resolve(current, 200).isEmpty());
        delegates.committed(new LiveTranscriptIngressService.Outcome(UUID.randomUUID(), "USER", "accepted", "COMPLETE", "", UUID.randomUUID(), 500L, 1000L));
        var replies = delegates.resolve(LiveContextDeliveryUnitTest.snapshot("r2", "New task"), 300);
        assertEquals(1, replies.size()); assertEquals("request1", replies.getFirst().id());
        assertTrue(replies.getFirst().text().contains("Context changed")); assertFalse(replies.getFirst().text().contains("accepted"));
        delegates.request("request1", 1200L, "r2", 400); assertTrue(delegates.resolve(current, 6000).isEmpty());
    }
    @Test void missingTranscriptTimesOutAsUnconfirmedAndPendingRequestsAreBounded() {
        var delegates = new LiveDelegation(); var current = LiveContextDeliveryUnitTest.snapshot("r1", "Guide");
        delegates.request("unknown", null, "r1", 0); assertTrue(delegates.resolve(current, 4999).isEmpty());
        assertTrue(delegates.resolve(current, 5000).getFirst().text().contains("unconfirmed"));
        for (int i = 0; i < 32; i++) delegates.request("request" + i, null, "r1", 5000);
        assertThrows(IllegalStateException.class, () -> delegates.request("overflow", null, "r1", 5000));
    }
}
