package ch.zhaw.prometheus.model.snapshot;

import java.util.ArrayList;
import java.util.List;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.event.EventSelector;

public class DefaultObservationSnapshotAggregator implements SnapshotAggregator {
    public static final String FACT_EVENT_COUNT = "event_count";
    public static final String FACT_LAST_EVENT_TYPE = "last_event_type";
    public static final String FACT_LAST_EVENT_ACTOR = "last_event_actor";
    public static final String FACT_LAST_USER_UTTERANCE = "last_user_utterance";
    public static final String FACT_USER_UTTERANCE_COUNT = "user_utterance_count";
    public static final String FACT_ASSISTANT_BEHAVIOUR_COUNT = "assistant_behaviour_count";
    public static final String FACT_FACE_EMOTION_OBSERVATION_COUNT = "face_emotion_observation_count";
    public static final String FACT_LAST_FACE_EMOTION = "last_face_emotion";
    public static final String FACT_LAST_FACE_EMOTION_CONFIDENCE = "last_face_emotion_confidence";

    public static final DefaultObservationSnapshotAggregator INSTANCE = new DefaultObservationSnapshotAggregator();

    private final List<FactExtractor> extractors;

    public DefaultObservationSnapshotAggregator() {
        this(List.of(
                FactExtractors.count(FACT_USER_UTTERANCE_COUNT, EventSelector.type(Event.TYPE_USER_UTTERANCE)),
                FactExtractors.count(FACT_ASSISTANT_BEHAVIOUR_COUNT,
                        EventSelector.type(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN)),
                FactExtractors.count(FACT_FACE_EMOTION_OBSERVATION_COUNT, EventSelector.type(Event.TYPE_FACE_EMOTION)),
                FactExtractors.lastContent(FACT_LAST_USER_UTTERANCE, EventSelector.type(Event.TYPE_USER_UTTERANCE)),
                FactExtractors.lastJsonString(FACT_LAST_FACE_EMOTION, EventSelector.type(Event.TYPE_FACE_EMOTION),
                        "emotion"),
                FactExtractors.lastJsonDouble(FACT_LAST_FACE_EMOTION_CONFIDENCE,
                        EventSelector.type(Event.TYPE_FACE_EMOTION), "confidence"),
                FactExtractors.last(FACT_LAST_EVENT_TYPE, EventSelector.any(), Event::getType),
                FactExtractors.last(FACT_LAST_EVENT_ACTOR, EventSelector.any(), Event::getActor)));
    }

    public DefaultObservationSnapshotAggregator(List<FactExtractor> extractors) {
        this.extractors = extractors == null ? List.of() : List.copyOf(extractors);
    }

    @Override
    public ObservationSnapshot aggregate(EventHistory events) {
        if (events == null) {
            return ObservationSnapshot.empty();
        }
        List<Event> source = events.toList();
        List<Fact> facts = new ArrayList<>();
        facts.add(Fact.of(FACT_EVENT_COUNT, source.size(), 1.0d, List.of()));
        for (FactExtractor extractor : this.extractors) {
            if (extractor == null) {
                continue;
            }
            extractor.extract(events).ifPresent(facts::add);
        }
        return new ObservationSnapshot(source.size(), facts);
    }
}
