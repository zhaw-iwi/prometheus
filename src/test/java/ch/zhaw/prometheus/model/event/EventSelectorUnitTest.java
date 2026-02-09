package ch.zhaw.prometheus.model.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.model.State;

class EventSelectorUnitTest {

    @Test
    void composedSelectorFiltersByActorKindAndState() {
        State stateA = new State("A", new NoOpPolicy(), List.of());
        State stateB = new State("B", new NoOpPolicy(), List.of());
        EventHistory history = new EventHistory();

        history.appendEvent(Event.observation("obs.user_utterance", "user", "u1", null, "A"), stateA);
        history.appendEvent(Event.response("resp.behaviour_plan", "assistant", "a1", "{\"speech\":\"a1\"}", "A"),
                stateA);
        history.appendEvent(Event.observation("obs.sensor", "device", "d1", null, "A"), stateA);
        history.appendEvent(Event.observation("obs.user_utterance", "user", "u2", null, "B"), stateB);

        EventSelector selector = EventSelector.actor("user")
                .and(EventSelector.kind(Event.KIND_OBSERVATION))
                .and(EventSelector.stateName("A"));

        List<Event> selected = history.selectList(selector);

        assertEquals(1, selected.size());
        assertEquals("u1", selected.get(0).getContent());
    }

    @Test
    void anyOrTypeSelectorsWorkForSimpleCases() {
        State state = new State("S", new NoOpPolicy(), List.of());
        EventHistory history = new EventHistory();
        history.appendEvent(Event.observation("obs.user_utterance", "user", "hello", null, "S"), state);
        history.appendEvent(Event.response("resp.behaviour_plan", "assistant", "ok", "{\"speech\":\"ok\"}", "S"),
                state);

        assertEquals(2, history.selectList(EventSelector.any()).size());
        assertEquals(1, history.selectList(EventSelector.type("resp.behaviour_plan")).size());
        assertEquals(2, history.selectList(EventSelector.type("obs.user_utterance", "resp.behaviour_plan")).size());
    }
}
