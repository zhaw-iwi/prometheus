package ch.zhaw.prometheus.model.event;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.*;

@SpringBootTest
@Transactional
class EventHistoryOrderPersistenceTest {
    @PersistenceContext EntityManager entities;

    @Test void tiedDatabaseTimestampsKeepAppendOrderAfterReloadAndRemoval() throws Exception {
        EventHistory history = new EventHistory();
        history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "first"));
        history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "second"));
        history.appendEvent(Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, "assistant", "response"));
        entities.persist(history); entities.flush();
        entities.createQuery("update Event e set e.createdDate = :at where e.eventHistory.id = :id")
                .setParameter("at", Instant.parse("2020-01-01T00:00:00Z")).setParameter("id", history.getID()).executeUpdate();
        var id = history.getID(); entities.clear(); history = entities.find(EventHistory.class, id);
        assertEquals(List.of("first", "second", "response"), history.toList().stream().map(Event::getPayload).toList());
        assertEquals(1, history.toList().stream().map(Event::getCreatedDate).distinct().count());
        String publicJson = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(history.toList().getLast());
        assertFalse(publicJson.contains("historyPosition")); assertFalse(publicJson.contains("history_position"));
        history.removeLast(EventSelector.of(event -> event.getPayload().equals("second")));
        history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "next"));
        entities.flush(); entities.clear();
        assertEquals(List.of("first", "response", "next"), entities.find(EventHistory.class, id).toList().stream().map(Event::getPayload).toList());
    }

    @Test void legacyNullPositionsRetainTheirDatesAndIdsWithoutBackfill() {
        EventHistory history = new EventHistory();
        Event old = history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "legacy"));
        entities.persist(history); entities.flush();
        var id = history.getID(); var eventId = old.getId();
        var date = Instant.parse("2020-01-01T00:00:00Z");
        entities.createQuery("update Event e set e.historyPosition = null, e.createdDate = :at where e.id = :id")
                .setParameter("at", date).setParameter("id", eventId).executeUpdate();
        entities.clear(); history = entities.find(EventHistory.class, id);
        history.appendEvent(Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, "assistant", "new"));
        entities.flush(); entities.clear(); history = entities.find(EventHistory.class, id);
        assertEquals(List.of("legacy", "new"), history.toList().stream().map(Event::getPayload).toList());
        assertEquals(eventId, history.toList().getFirst().getId()); assertEquals(date, history.toList().getFirst().getCreatedDate());
        assertNull(history.toList().getFirst().historyPosition()); assertEquals(0L, history.toList().getLast().historyPosition());
    }
}
