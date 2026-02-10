package ch.zhaw.prometheus.model.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EventSelectorSpecUnitTest {

    @Test
    void compositeSpecJsonRoundTripBuildsEquivalentSelector() {
        EventSelectorSpec spec = EventSelectorSpec.and(
                EventSelectorSpec.actor(Event.ACTOR_USER),
                EventSelectorSpec.type(Event.TYPE_USER_UTTERANCE));

        EventSelector rebuilt = EventSelectorSpec.fromJson(spec.toJson()).toEventSelector();
        assertTrue(rebuilt.test(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "ok")));
        assertFalse(rebuilt.test(Event.observation(Event.TYPE_FACE_EMOTION, Event.ACTOR_USER, "{}")));
        assertFalse(rebuilt.test(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_ASSISTANT, "no")));
    }
}
