package ch.zhaw.prometheus.model.snapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
    public static final String FACT_FACE_EMOTION_TOTAL_COUNT = "face_emotion_total_count";
    public static final String FACT_FACE_EMOTION_CURRENT = "face_emotion_current";
    public static final String FACT_FACE_EMOTION_CURRENT_CONFIDENCE = "face_emotion_current_confidence";
    public static final String FACT_FACE_EMOTION_MAJORITY_LAST_WINDOW = "face_emotion_majority_last_window";
    public static final String FACT_FACE_EMOTION_NEGATIVE_STREAK = "face_emotion_negative_streak";
    public static final String FACT_FACE_EMOTION_VALENCE_TREND = "face_emotion_valence_trend";
    public static final String FACT_FACE_EMOTION_VALENCE_VOLATILITY = "face_emotion_valence_volatility";
    public static final String FACT_FACE_EMOTION_EVENTS_SINCE_CHANGE = "face_emotion_events_since_change";

    public static final DefaultObservationSnapshotAggregator INSTANCE = new DefaultObservationSnapshotAggregator();

    private static final int FACE_WINDOW_SIZE = 8;
    private static final double FACE_CONFIDENCE_THRESHOLD = 0.60d;
    private static final double TREND_EPSILON = 0.10d;
    private static final Set<String> NEGATIVE_EMOTIONS = Set.of("sad", "angry", "fearful", "disgusted");

    private final List<FactExtractor> extractors;

    public DefaultObservationSnapshotAggregator() {
        this(List.of(
                FactExtractors.count(FACT_USER_UTTERANCE_COUNT, EventSelector.type(Event.TYPE_USER_UTTERANCE)),
                FactExtractors.count(FACT_ASSISTANT_BEHAVIOUR_COUNT,
                        EventSelector.type(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN)),
                FactExtractors.lastContent(FACT_LAST_USER_UTTERANCE, EventSelector.type(Event.TYPE_USER_UTTERANCE)),
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
        this.addTemporalFaceEmotionFacts(source, facts);
        return new ObservationSnapshot(source.size(), facts);
    }

    private void addTemporalFaceEmotionFacts(List<Event> source, List<Fact> facts) {
        List<FaceEmotionSample> samples = source.stream()
                .filter(event -> event != null && Event.TYPE_FACE_EMOTION.equals(event.getType()))
                .map(this::toFaceEmotionSample)
                .filter(sample -> sample != null)
                .toList();

        facts.add(Fact.of(FACT_FACE_EMOTION_TOTAL_COUNT, samples.size(), 1.0d, List.of()));
        if (samples.isEmpty()) {
            return;
        }

        List<FaceEmotionSample> highConfidence = samples.stream()
                .filter(sample -> sample.confidence >= FACE_CONFIDENCE_THRESHOLD)
                .toList();
        List<FaceEmotionSample> basis = highConfidence.isEmpty() ? samples : highConfidence;
        List<FaceEmotionSample> window = takeLastWindow(basis, FACE_WINDOW_SIZE);
        FaceEmotionSample current = window.get(window.size() - 1);

        facts.add(Fact.of(FACT_FACE_EMOTION_CURRENT, current.emotion, 1.0d, List.of()));
        facts.add(Fact.of(FACT_FACE_EMOTION_CURRENT_CONFIDENCE, current.confidence, 1.0d, List.of()));
        facts.add(Fact.of(FACT_FACE_EMOTION_MAJORITY_LAST_WINDOW, majorityEmotion(window), 1.0d, List.of()));
        facts.add(Fact.of(FACT_FACE_EMOTION_NEGATIVE_STREAK, negativeStreak(window), 1.0d, List.of()));
        facts.add(Fact.of(FACT_FACE_EMOTION_VALENCE_TREND, valenceTrend(window), 1.0d, List.of()));
        facts.add(Fact.of(FACT_FACE_EMOTION_VALENCE_VOLATILITY, valenceVolatility(window), 1.0d, List.of()));
        facts.add(Fact.of(FACT_FACE_EMOTION_EVENTS_SINCE_CHANGE, eventsSinceEmotionChange(window), 1.0d, List.of()));
    }

    private FaceEmotionSample toFaceEmotionSample(Event event) {
        if (event.getPayload() == null || event.getPayload().isBlank()) {
            return null;
        }
        try {
            JsonObject payload = JsonParser.parseString(event.getPayload()).getAsJsonObject();
            if (!payload.has("emotion") || payload.get("emotion").isJsonNull()) {
                return null;
            }
            String emotion = payload.get("emotion").getAsString();
            double confidence = payload.has("confidence") && !payload.get("confidence").isJsonNull()
                    ? payload.get("confidence").getAsDouble()
                    : 0.0d;
            double valence = payload.has("valence") && !payload.get("valence").isJsonNull()
                    ? payload.get("valence").getAsDouble()
                    : 0.0d;
            return new FaceEmotionSample(emotion == null ? "" : emotion.trim().toLowerCase(), confidence, valence);
        } catch (Exception exception) {
            return null;
        }
    }

    private static List<FaceEmotionSample> takeLastWindow(List<FaceEmotionSample> samples, int size) {
        if (samples.size() <= size) {
            return samples;
        }
        return samples.subList(samples.size() - size, samples.size());
    }

    private static String majorityEmotion(List<FaceEmotionSample> samples) {
        Map<String, Long> counts = new HashMap<>();
        for (FaceEmotionSample sample : samples) {
            counts.merge(sample.emotion, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .max(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static int negativeStreak(List<FaceEmotionSample> samples) {
        int streak = 0;
        for (int i = samples.size() - 1; i >= 0; i--) {
            if (!NEGATIVE_EMOTIONS.contains(samples.get(i).emotion)) {
                break;
            }
            streak++;
        }
        return streak;
    }

    private static String valenceTrend(List<FaceEmotionSample> samples) {
        if (samples.size() < 2) {
            return "stable";
        }
        int mid = samples.size() / 2;
        double early = averageValence(samples.subList(0, mid));
        double late = averageValence(samples.subList(mid, samples.size()));
        double delta = late - early;
        if (delta > TREND_EPSILON) {
            return "improving";
        }
        if (delta < -TREND_EPSILON) {
            return "worsening";
        }
        return "stable";
    }

    private static double valenceVolatility(List<FaceEmotionSample> samples) {
        if (samples.size() < 2) {
            return 0.0d;
        }
        double totalAbsDelta = 0.0d;
        for (int i = 1; i < samples.size(); i++) {
            totalAbsDelta += Math.abs(samples.get(i).valence - samples.get(i - 1).valence);
        }
        return totalAbsDelta / (samples.size() - 1);
    }

    private static int eventsSinceEmotionChange(List<FaceEmotionSample> samples) {
        if (samples.isEmpty()) {
            return 0;
        }
        String current = samples.get(samples.size() - 1).emotion;
        int count = 0;
        for (int i = samples.size() - 1; i >= 0; i--) {
            if (!current.equals(samples.get(i).emotion)) {
                break;
            }
            count++;
        }
        return count;
    }

    private static double averageValence(List<FaceEmotionSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return 0.0d;
        }
        double sum = 0.0d;
        for (FaceEmotionSample sample : samples) {
            sum += sample.valence;
        }
        return sum / samples.size();
    }

    private record FaceEmotionSample(String emotion, double confidence, double valence) {
    }
}
