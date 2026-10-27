package ch.zhaw.prometheus.model.event;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.SensoryRemarks;

class EventSelectionTimingUnitTest {
    @Test void selectedDialoguePreservesTimingAndSuppressesAnOtherwiseStableSensorComment() {
        var now = Instant.parse("2026-10-26T12:00:00Z"); var history = new EventHistory();
        var user = history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Please explain that"));
        user.recordedAt(now.minusSeconds(1));
        for (var at : java.util.List.of(now.minusSeconds(3), now)) history.appendEvent(Event.observation(
                Event.TYPE_SOCIAL_GROUPING, "sensor", "{\"humanCount\":2,\"ts\":\"" + at + "\"}"));
        var selected = history.select(EventSelector.any());
        assertNotSame(user, selected.toList().getFirst()); assertNull(selected.toList().getFirst().getId());
        assertEquals(user.getCreatedDate(), selected.toList().getFirst().getCreatedDate());
        assertTrue(SensoryRemarks.change(selected, new Storage(), now, 2000, 4000, 15000).isEmpty());
        assertTrue(SensoryRemarks.change(selected, new Storage(), now, 2000, 0, 15000).isPresent());
    }
}
