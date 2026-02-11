package ch.zhaw.prometheus.model.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;

class DefaultObservationSnapshotAggregatorUnitTest {

    @Test
    void aggregatesStableFactsFromSelectedEvents() {
        EventHistory events = new EventHistory();
        events.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "hello")
                .withStatePath("S"));
        events.appendEvent(Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT, "{\"speech\":\"hi\"}").withStatePath("S"));
        events.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "need help")
                .withStatePath("S"));
        events.appendEvent(Event.observation(Event.TYPE_FACE_EMOTION, Event.ACTOR_USER,
                "{\"emotion\":\"sad\",\"confidence\":0.72,\"valence\":-0.5,\"arousal\":0.3}")
                .withStatePath("S"));
        events.appendEvent(Event.observation(Event.TYPE_FACE_EMOTION, Event.ACTOR_USER,
                "{\"emotion\":\"sad\",\"confidence\":0.77,\"valence\":-0.6,\"arousal\":0.4}")
                .withStatePath("S"));
        events.appendEvent(Event.observation(Event.TYPE_FACE_EMOTION, Event.ACTOR_USER,
                "{\"emotion\":\"neutral\",\"confidence\":0.95,\"valence\":0.0,\"arousal\":0.2}")
                .withStatePath("S"));
        events.appendEvent(Event.observation(Event.TYPE_FACE_EMOTION, Event.ACTOR_USER,
                "{\"emotion\":\"happy\",\"confidence\":0.92,\"valence\":0.6,\"arousal\":0.6}")
                .withStatePath("S"));
        events.appendEvent(Event.observation(Event.TYPE_HUMAN_PRESENCE, Event.ACTOR_USER,
                "{\"humanCount\":3,\"trackedCount\":3,\"ts\":\"2026-02-11T10:00:00Z\"}")
                .withStatePath("S"));
        events.appendEvent(Event.observation(Event.TYPE_SOCIAL_GROUPING, Event.ACTOR_USER,
                "{\"humanCount\":3,\"groupCount\":1,\"singletonCount\":1,\"largestGroupSize\":2,\"ts\":\"2026-02-11T10:00:00Z\"}")
                .withStatePath("S"));
        events.appendEvent(Event.observation(Event.TYPE_SOCIAL_GROUPING, Event.ACTOR_USER,
                "{\"humanCount\":4,\"groupCount\":2,\"singletonCount\":0,\"largestGroupSize\":2,\"ts\":\"2026-02-11T10:00:03Z\"}")
                .withStatePath("S"));

        ObservationSnapshot snapshot = DefaultObservationSnapshotAggregator.INSTANCE.aggregate(events);

        assertEquals(10, snapshot.getSourceEventCount());
        assertEquals(10, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_EVENT_COUNT));
        assertEquals(2, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_USER_UTTERANCE_COUNT));
        assertEquals(1, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_ASSISTANT_BEHAVIOUR_COUNT));
        assertEquals(4, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_FACE_EMOTION_TOTAL_COUNT));
        assertEquals(1, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_HUMAN_PRESENCE_TOTAL_COUNT));
        assertEquals(2, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_SOCIAL_GROUPING_TOTAL_COUNT));
        assertEquals("need help", snapshot.getString(DefaultObservationSnapshotAggregator.FACT_LAST_USER_UTTERANCE));
        assertEquals("happy", snapshot.getString(DefaultObservationSnapshotAggregator.FACT_FACE_EMOTION_CURRENT));
        assertEquals(0.92d,
                snapshot.getDouble(DefaultObservationSnapshotAggregator.FACT_FACE_EMOTION_CURRENT_CONFIDENCE));
        assertEquals("sad",
                snapshot.getString(DefaultObservationSnapshotAggregator.FACT_FACE_EMOTION_MAJORITY_LAST_WINDOW));
        assertEquals(0, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_FACE_EMOTION_NEGATIVE_STREAK));
        assertEquals("improving", snapshot.getString(DefaultObservationSnapshotAggregator.FACT_FACE_EMOTION_VALENCE_TREND));
        assertEquals(0.433d,
                snapshot.getDouble(DefaultObservationSnapshotAggregator.FACT_FACE_EMOTION_VALENCE_VOLATILITY), 0.001d);
        assertEquals(1, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_FACE_EMOTION_EVENTS_SINCE_CHANGE));
        assertEquals(4, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_SOCIAL_CURRENT_HUMAN_COUNT));
        assertEquals(2, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_SOCIAL_CURRENT_GROUP_COUNT));
        assertEquals(0, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_SOCIAL_CURRENT_SINGLETON_COUNT));
        assertEquals(2, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_SOCIAL_CURRENT_LARGEST_GROUP_SIZE));
        assertEquals("increasing", snapshot.getString(DefaultObservationSnapshotAggregator.FACT_SOCIAL_GROUP_COUNT_TREND));
        assertEquals(Event.TYPE_SOCIAL_GROUPING, snapshot.getString(DefaultObservationSnapshotAggregator.FACT_LAST_EVENT_TYPE));
        assertEquals(Event.ACTOR_USER, snapshot.getString(DefaultObservationSnapshotAggregator.FACT_LAST_EVENT_ACTOR));
    }

    @Test
    void keepsFactsMissingWhenNoEvidenceExists() {
        ObservationSnapshot snapshot = DefaultObservationSnapshotAggregator.INSTANCE.aggregate(new EventHistory());

        assertEquals(0, snapshot.getSourceEventCount());
        assertEquals(0, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_EVENT_COUNT));
        assertEquals(0, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_USER_UTTERANCE_COUNT));
        assertEquals(0, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_FACE_EMOTION_TOTAL_COUNT));
        assertEquals(0, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_HUMAN_PRESENCE_TOTAL_COUNT));
        assertEquals(0, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_SOCIAL_GROUPING_TOTAL_COUNT));
        assertNull(snapshot.getString(DefaultObservationSnapshotAggregator.FACT_LAST_USER_UTTERANCE));
        assertNull(snapshot.getString(DefaultObservationSnapshotAggregator.FACT_FACE_EMOTION_CURRENT));
        assertNull(snapshot.getDouble(DefaultObservationSnapshotAggregator.FACT_FACE_EMOTION_CURRENT_CONFIDENCE));
        assertNull(snapshot.getString(DefaultObservationSnapshotAggregator.FACT_FACE_EMOTION_MAJORITY_LAST_WINDOW));
        assertNull(snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_SOCIAL_CURRENT_HUMAN_COUNT));
    }
}
