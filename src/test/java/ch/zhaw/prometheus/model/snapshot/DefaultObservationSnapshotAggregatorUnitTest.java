package ch.zhaw.prometheus.model.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

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

        ObservationSnapshot snapshot = DefaultObservationSnapshotAggregator.INSTANCE.aggregate(events);

        assertEquals(4, snapshot.getSourceEventCount());
        assertEquals(4, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_EVENT_COUNT));
        assertEquals(2, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_USER_UTTERANCE_COUNT));
        assertEquals(1, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_ASSISTANT_BEHAVIOUR_COUNT));
        assertEquals(1, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_FACE_EMOTION_OBSERVATION_COUNT));
        assertEquals("need help", snapshot.getString(DefaultObservationSnapshotAggregator.FACT_LAST_USER_UTTERANCE));
        assertEquals("sad", snapshot.getString(DefaultObservationSnapshotAggregator.FACT_LAST_FACE_EMOTION));
        assertEquals(0.72d,
                snapshot.getDouble(DefaultObservationSnapshotAggregator.FACT_LAST_FACE_EMOTION_CONFIDENCE));
        assertEquals(Event.TYPE_FACE_EMOTION, snapshot.getString(DefaultObservationSnapshotAggregator.FACT_LAST_EVENT_TYPE));
        assertEquals(Event.ACTOR_USER, snapshot.getString(DefaultObservationSnapshotAggregator.FACT_LAST_EVENT_ACTOR));
    }

    @Test
    void keepsFactsMissingWhenNoEvidenceExists() {
        ObservationSnapshot snapshot = DefaultObservationSnapshotAggregator.INSTANCE.aggregate(new EventHistory());

        assertEquals(0, snapshot.getSourceEventCount());
        assertEquals(0, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_EVENT_COUNT));
        assertEquals(0, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_USER_UTTERANCE_COUNT));
        assertEquals(0, snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_FACE_EMOTION_OBSERVATION_COUNT));
        assertNull(snapshot.getString(DefaultObservationSnapshotAggregator.FACT_LAST_USER_UTTERANCE));
        assertNull(snapshot.getString(DefaultObservationSnapshotAggregator.FACT_LAST_FACE_EMOTION));
        assertNull(snapshot.getDouble(DefaultObservationSnapshotAggregator.FACT_LAST_FACE_EMOTION_CONFIDENCE));
    }
}
