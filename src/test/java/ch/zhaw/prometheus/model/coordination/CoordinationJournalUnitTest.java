package ch.zhaw.prometheus.model.coordination;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.*;

class CoordinationJournalUnitTest {
    @Test void journalRetainsBoundedContentFreeSourceReferencesAndVisibleTruncation() {
        var storage = new Storage(); var history = new EventHistory();
        for (int i = 0; i < 30; i++) {
            history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "private transcript"));
            CoordinationMemory.record(storage, "PROPOSE", "CONVERSATION", history.select(EventSelector.any()).toList().getLast(), Instant.EPOCH);
        }
        var log = storage.get(CoordinationMemory.LOG).getAsJsonArray();
        assertTrue(log.size() <= 12); assertTrue(log.toString().getBytes(StandardCharsets.UTF_8).length <= 6000);
        assertEquals(30 - log.size(), storage.get(CoordinationMemory.LOG_DROPPED).getAsInt());
        assertEquals("history:29", log.get(log.size()-1).getAsJsonObject().get("source").getAsString());
        assertFalse(log.toString().contains("private")); assertFalse(log.toString().contains("unpersisted"));
        CoordinationMemory.clear(storage); assertFalse(storage.containsKey(CoordinationMemory.LOG_DROPPED));
    }
}
