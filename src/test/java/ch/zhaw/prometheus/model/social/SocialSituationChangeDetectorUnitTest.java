package ch.zhaw.prometheus.model.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;

class SocialSituationChangeDetectorUnitTest {
    private final SocialSituationChangeDetector detector = SocialSituationChangeDetector.defaultThresholds();

    @Test
    void detectsArrivalFromNoOneToOnePerson() {
        EventHistory history = new EventHistory();
        history.appendEvent(grouping(0, 0, 0, 0));
        appendComputedIfPresent(history);
        history.appendEvent(grouping(1, 0, 1, 1));

        Event change = this.detector.detect(history).orElseThrow();

        assertEquals(Event.TYPE_SOCIAL_SITUATION_CHANGE, change.getType());
        assertEquals(Event.ACTOR_SYSTEM, change.getActor());
        assertEquals(Event.KIND_OBSERVATION, change.getKind());
        JsonObject payload = payload(change);
        assertEquals(SocialSituationChangeDetector.CHANGE_ARRIVAL, payload.get("changeType").getAsString());
        assertEquals(0, payload.get("previousHumanCount").getAsInt());
        assertEquals(1, payload.get("currentHumanCount").getAsInt());
    }

    @Test
    void detectsDepartureFromOnePersonToNoOne() {
        EventHistory history = new EventHistory();
        history.appendEvent(grouping(1, 0, 1, 1));
        appendComputedIfPresent(history);
        history.appendEvent(grouping(0, 0, 0, 0));

        Event change = this.detector.detect(history).orElseThrow();

        JsonObject payload = payload(change);
        assertEquals(SocialSituationChangeDetector.CHANGE_DEPARTURE, payload.get("changeType").getAsString());
        assertEquals(1, payload.get("previousHumanCount").getAsInt());
        assertEquals(0, payload.get("currentHumanCount").getAsInt());
    }

    @Test
    void detectsCrowdWhenThresholdIsReached() {
        EventHistory history = new EventHistory();
        history.appendEvent(grouping(2, 1, 0, 2));
        history.appendEvent(grouping(3, 1, 0, 3));

        Event change = this.detector.detect(history).orElseThrow();

        JsonObject payload = payload(change);
        assertEquals(SocialSituationChangeDetector.CHANGE_CROWD_DETECTED, payload.get("changeType").getAsString());
        assertEquals(2, payload.get("previousHumanCount").getAsInt());
        assertEquals(3, payload.get("currentHumanCount").getAsInt());
    }

    @Test
    void detectsInitialAloneAndInitialSinglePersonSituations() {
        EventHistory alone = new EventHistory();
        alone.appendEvent(grouping(0, 0, 0, 0));

        Event aloneChange = this.detector.detect(alone).orElseThrow();
        assertEquals(SocialSituationChangeDetector.CHANGE_NOW_ALONE,
                payload(aloneChange).get("changeType").getAsString());

        EventHistory single = new EventHistory();
        single.appendEvent(grouping(1, 0, 1, 1));

        Event singleChange = this.detector.detect(single).orElseThrow();
        assertEquals(SocialSituationChangeDetector.CHANGE_SINGLE_PERSON_NEARBY,
                payload(singleChange).get("changeType").getAsString());
    }

    @Test
    void detectsGroupSizeChangeWithoutHumanCountChange() {
        EventHistory history = new EventHistory();
        history.appendEvent(grouping(2, 0, 2, 1));
        history.appendEvent(grouping(2, 1, 0, 2));

        Event change = this.detector.detect(history).orElseThrow();

        JsonObject payload = payload(change);
        assertEquals(SocialSituationChangeDetector.CHANGE_GROUP_SIZE_CHANGED,
                payload.get("changeType").getAsString());
        assertEquals(0, payload.get("previousGroupCount").getAsInt());
        assertEquals(1, payload.get("currentGroupCount").getAsInt());
    }

    @Test
    void suppressesRepeatedSameSocialStateAfterComputedEvent() {
        EventHistory history = new EventHistory();
        history.appendEvent(grouping(1, 0, 1, 1));
        appendComputedIfPresent(history);
        history.appendEvent(grouping(1, 0, 1, 1));

        Optional<Event> change = this.detector.detect(history);

        assertTrue(change.isEmpty());
    }

    @Test
    void returnsEmptyForMalformedOrPartialCurrentGroupingPayload() {
        EventHistory malformed = new EventHistory();
        malformed.appendEvent(Event.observation(Event.TYPE_SOCIAL_GROUPING, Event.ACTOR_USER, "{not-json"));

        assertTrue(this.detector.detect(malformed).isEmpty());

        EventHistory partial = new EventHistory();
        partial.appendEvent(Event.observation(Event.TYPE_SOCIAL_GROUPING, Event.ACTOR_USER,
                "{\"humanCount\":1}"));

        assertTrue(this.detector.detect(partial).isEmpty());
    }

    @Test
    void carriesLatestPresenceConfidenceWhenAvailable() {
        EventHistory history = new EventHistory();
        history.appendEvent(Event.observation(Event.TYPE_HUMAN_PRESENCE, Event.ACTOR_USER,
                "{\"humanCount\":1,\"trackedCount\":1,\"avgDetectionConfidence\":0.74}"));
        history.appendEvent(grouping(1, 0, 1, 1));

        Event change = this.detector.detect(history).orElseThrow();

        assertEquals(0.74d, payload(change).get("confidence").getAsDouble(), 0.001d);
    }

    private void appendComputedIfPresent(EventHistory history) {
        this.detector.detect(history).ifPresent(history::appendEvent);
    }

    private static Event grouping(int humanCount, int groupCount, int singletonCount, int largestGroupSize) {
        return Event.observation(Event.TYPE_SOCIAL_GROUPING, Event.ACTOR_USER,
                "{\"humanCount\":" + humanCount
                        + ",\"groupCount\":" + groupCount
                        + ",\"singletonCount\":" + singletonCount
                        + ",\"largestGroupSize\":" + largestGroupSize + "}");
    }

    private static JsonObject payload(Event event) {
        return JsonParser.parseString(event.getPayload()).getAsJsonObject();
    }
}
