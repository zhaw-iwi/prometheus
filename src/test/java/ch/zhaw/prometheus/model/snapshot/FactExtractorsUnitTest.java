package ch.zhaw.prometheus.model.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.event.EventSelector;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;

class FactExtractorsUnitTest {

    @Test
    void selectorBasedExtractorsReuseEventSelectorComposition() {
        State state = new State("S", new NoOpPolicy(), List.of());
        EventHistory events = new EventHistory();
        events.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "u1", null, "S"), state);
        events.appendEvent(Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT, "a1",
                "{\"speech\":\"a1\"}", "S"), state);
        events.appendEvent(Event.observation("obs.sensor", "device", "s1", null, "S"), state);

        FactExtractor userCountExtractor = FactExtractors.count("user_obs_count",
                EventSelector.actor(Event.ACTOR_USER).and(EventSelector.kind(Event.KIND_OBSERVATION)));
        FactExtractor lastAssistantType = FactExtractors.last("last_assistant_type",
                EventSelector.actor(Event.ACTOR_ASSISTANT), Event::getType);

        Fact userCountFact = userCountExtractor.extract(events).orElseThrow();
        Fact lastAssistantTypeFact = lastAssistantType.extract(events).orElseThrow();

        assertEquals(1, userCountFact.getValue());
        assertEquals(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, lastAssistantTypeFact.getValue());
        assertTrue(lastAssistantTypeFact.getProvenance().get(0).contains(Event.ACTOR_ASSISTANT));
    }
}
