package ch.zhaw.prometheus.model.policy;

import java.util.List;

import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.snapshot.DefaultObservationSnapshotAggregator;
import ch.zhaw.prometheus.model.snapshot.ObservationSnapshot;

public class NonverbalSummaryPromptContextAugmenter implements PromptContextAugmenter {
    @Override
    public List<PromptMessage> augment(EventHistory eventHistory) {
        if (eventHistory == null) {
            return List.of();
        }
        String summary = buildNonverbalSummary(eventHistory);
        if (summary == null || summary.isBlank()) {
            return List.of();
        }
        return List.of(PromptMessage.system(summary));
    }

    private static String buildNonverbalSummary(EventHistory eventHistory) {
        ObservationSnapshot snapshot = DefaultObservationSnapshotAggregator.INSTANCE.aggregate(eventHistory);
        Integer total = snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_FACE_EMOTION_TOTAL_COUNT);
        if (total == null || total <= 0) {
            return null;
        }
        String current = snapshot.getString(DefaultObservationSnapshotAggregator.FACT_FACE_EMOTION_CURRENT);
        Double confidence = snapshot
                .getDouble(DefaultObservationSnapshotAggregator.FACT_FACE_EMOTION_CURRENT_CONFIDENCE);
        String majority = snapshot.getString(DefaultObservationSnapshotAggregator.FACT_FACE_EMOTION_MAJORITY_LAST_WINDOW);
        String trend = snapshot.getString(DefaultObservationSnapshotAggregator.FACT_FACE_EMOTION_VALENCE_TREND);
        Integer streak = snapshot.getInteger(DefaultObservationSnapshotAggregator.FACT_FACE_EMOTION_NEGATIVE_STREAK);
        Integer sinceChange = snapshot
                .getInteger(DefaultObservationSnapshotAggregator.FACT_FACE_EMOTION_EVENTS_SINCE_CHANGE);

        StringBuilder summary = new StringBuilder();
        summary.append("Nonverbal summary (temporal aggregation): ");
        summary.append("current=").append(current == null ? "unknown" : current);
        if (confidence != null) {
            summary.append(" (confidence ").append(String.format(java.util.Locale.ROOT, "%.2f", confidence)).append(")");
        }
        summary.append(", majority_recent=").append(majority == null ? "unknown" : majority);
        summary.append(", trend=").append(trend == null ? "stable" : trend);
        summary.append(", negative_streak=").append(streak == null ? 0 : streak);
        summary.append(", events_since_change=").append(sinceChange == null ? 0 : sinceChange);
        summary.append(".");
        summary.append(" Use as contextual cue only; prioritize explicit verbal content on conflict.");
        return summary.toString();
    }
}

