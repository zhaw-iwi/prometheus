package ch.zhaw.prometheus.model.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class EventSelectorUnitTest {

    @Test
    void composedSelectorFiltersByActorKindAndState() {
        EventHistory history = new EventHistory();

        history.appendEvent(Event.observation("obs.user_utterance", "user", "u1").withStatePath("A"));
        history.appendEvent(
                Event.response("resp.behaviour_plan", "assistant", "{\"speech\":\"a1\"}").withStatePath("A"));
        history.appendEvent(Event.observation("obs.sensor", "device", "d1").withStatePath("A"));
        history.appendEvent(Event.observation("obs.user_utterance", "user", "u2").withStatePath("B"));

        EventSelector selector = EventSelector.actor("user")
                .and(EventSelector.kind(Event.KIND_OBSERVATION))
                .and(EventSelector.stateName("A"));

        List<Event> selected = history.selectList(selector);

        assertEquals(1, selected.size());
        assertEquals("u1", selected.get(0).getPayload());
    }

    @Test
    void anyOrTypeSelectorsWorkForSimpleCases() {
        EventHistory history = new EventHistory();
        history.appendEvent(Event.observation("obs.user_utterance", "user", "hello").withStatePath("S"));
        history.appendEvent(
                Event.response("resp.behaviour_plan", "assistant", "{\"speech\":\"ok\"}").withStatePath("S"));

        assertEquals(2, history.selectList(EventSelector.any()).size());
        assertEquals(1, history.selectList(EventSelector.type("resp.behaviour_plan")).size());
        assertEquals(2, history.selectList(EventSelector.type("obs.user_utterance", "resp.behaviour_plan")).size());
    }
}
