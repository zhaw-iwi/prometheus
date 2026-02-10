package ch.zhaw.prometheus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.event.EventSelector;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.model.snapshot.DefaultObservationSnapshotAggregator;
import ch.zhaw.prometheus.model.snapshot.ObservationSnapshot;

class StateTransitionSnapshotUnitTest {

    @Test
    void decisionCanUseSnapshotFactsFromStateScopedSelection() {
        State focus = new State("focus", new NoOpPolicy(), List.of());
        State other = new State("other", new NoOpPolicy(), List.of());
        EventHistory sharedHistory = new EventHistory();
        focus.setEventHistory(sharedHistory);

        sharedHistory.appendEvent(
                Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "f1").withStatePath("focus"));
        sharedHistory.appendEvent(
                Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "o1").withStatePath("other"));
        sharedHistory.appendEvent(
                Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "f2").withStatePath("focus"));

        SnapshotThresholdDecision decision = new SnapshotThresholdDecision(2);
        Transition transition = new Transition(List.of(decision), List.of(), other);

        assertTrue(transition.decide(focus));
        assertEquals(2, decision.lastSeenUserUtteranceCount);
        assertEquals("f2", decision.lastSeenLastUtterance);
    }

    @Test
    void actionCanUseSnapshotFactsFromExplicitSelector() {
        State state = new State("focus", new NoOpPolicy(), List.of());
        EventHistory sharedHistory = new EventHistory();
        state.setEventHistory(sharedHistory);

        sharedHistory.appendEvent(
                Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "u1").withStatePath("focus"));
        sharedHistory.appendEvent(
                Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT, "{\"speech\":\"a1\"}").withStatePath("focus"));

        SnapshotRecordingAction action = new SnapshotRecordingAction();
        action.setEventSelector(EventSelector.actor(Event.ACTOR_ASSISTANT));
        Transition transition = new Transition(List.of(), List.of(action), new State("next", new NoOpPolicy(), List.of()));

        transition.action(state);

        assertEquals(1, action.lastSnapshotEventCount);
        assertEquals(1, action.lastAssistantBehaviourCount);
        assertEquals(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, action.lastEventType);
    }

    private static class SnapshotThresholdDecision extends Decision {
        private final int threshold;
        private int lastSeenUserUtteranceCount;
        private String lastSeenLastUtterance;

        SnapshotThresholdDecision(int threshold) {
            super(new NoOpPolicy());
            this.threshold = threshold;
        }

        @Override
        public boolean decide(EventHistory events) {
            return false;
        }

        @Override
        public boolean decide(EventHistory events, ObservationSnapshot snapshot) {
            Integer count = snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_USER_UTTERANCE_COUNT);
            this.lastSeenUserUtteranceCount = count == null ? 0 : count;
            this.lastSeenLastUtterance = snapshot.getString(DefaultObservationSnapshotAggregator.FACT_LAST_USER_UTTERANCE);
            return this.lastSeenUserUtteranceCount >= this.threshold;
        }
    }

    private static class SnapshotRecordingAction extends Action {
        private int lastSnapshotEventCount;
        private int lastAssistantBehaviourCount;
        private String lastEventType;

        SnapshotRecordingAction() {
            super(new NoOpPolicy());
        }

        @Override
        public void execute(EventHistory eventHistory) {
            throw new UnsupportedOperationException("snapshot overload should be used");
        }

        @Override
        public void execute(EventHistory eventHistory, ObservationSnapshot snapshot) {
            Integer eventCount = snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_EVENT_COUNT);
            Integer assistantCount = snapshot
                    .getInteger(DefaultObservationSnapshotAggregator.FACT_ASSISTANT_BEHAVIOUR_COUNT);
            this.lastSnapshotEventCount = eventCount == null ? 0 : eventCount;
            this.lastAssistantBehaviourCount = assistantCount == null ? 0 : assistantCount;
            this.lastEventType = snapshot.getString(DefaultObservationSnapshotAggregator.FACT_LAST_EVENT_TYPE);
        }
    }
}
