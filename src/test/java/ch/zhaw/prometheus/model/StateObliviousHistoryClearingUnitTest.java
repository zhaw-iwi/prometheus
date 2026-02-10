package ch.zhaw.prometheus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;

class StateObliviousHistoryClearingUnitTest {

    @Test
    void obliviousStateClearsOnlyEventsWhosePathContainsThatState() {
        State oblivious = new State("Oblivious", new NoOpPolicy(), List.of(), true, true);
        State sibling = new State("Sibling", new NoOpPolicy(), List.of());
        EventHistory history = new EventHistory();
        oblivious.setEventHistory(history);
        sibling.setEventHistory(history);

        history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "o1")
                .withStatePath("Outer", "Oblivious"));
        history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "s1")
                .withStatePath("Outer", "Sibling"));

        oblivious.enter();

        List<Event> remaining = history.toList();
        assertEquals(1, remaining.size());
        assertEquals("s1", remaining.get(0).getPayload());
    }
}

