package ch.zhaw.prometheus.model.policy;

import java.util.ArrayList;
import java.util.List;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.snapshot.DefaultObservationSnapshotAggregator;
import ch.zhaw.prometheus.model.snapshot.ObservationSnapshot;

public final class PromptMessageAssembler {
    private PromptMessageAssembler() {
    }

    public static List<PromptMessage> compose(EventHistory eventHistory, String systemPrepend) {
        List<PromptMessage> messages = new ArrayList<>();
        requireSystem(systemPrepend);
        messages.add(PromptMessage.system(systemPrepend));
        if (eventHistory == null) {
            return messages;
        }
        for (Event event : eventHistory.toList()) {
            messages.add(toPromptMessage(event));
        }
        String nonverbalSummary = buildNonverbalSummary(eventHistory);
        if (nonverbalSummary != null && !nonverbalSummary.isBlank()) {
            messages.add(PromptMessage.system(nonverbalSummary));
        }
        return messages;
    }

    public static List<PromptMessage> compose(EventHistory eventHistory, String systemPrepend, String systemAppend) {
        List<PromptMessage> messages = compose(eventHistory, systemPrepend);
        if (systemAppend != null) {
            messages.add(PromptMessage.system(systemAppend));
        }
        return messages;
    }

    public static List<PromptMessage> composeCondensed(EventHistory eventHistory, String systemPrepend) {
        requireSystem(systemPrepend);
        if (eventHistory == null || eventHistory.isEmpty()) {
            throw new RuntimeException("cannot compose condensed prompt from empty events");
        }
        List<PromptMessage> messages = new ArrayList<>();
        messages.add(PromptMessage.system(systemPrepend));
        messages.add(PromptMessage.system("<eventhistory>" + eventHistory.toString() + "</eventhistory>"));
        return messages;
    }

    public static List<PromptMessage> composeCondensed(EventHistory eventHistory, String systemPrepend,
            String systemAppend) {
        if (systemAppend == null) {
            throw new NullPointerException("systemAppend cannot be null.");
        }
        List<PromptMessage> messages = composeCondensed(eventHistory, systemPrepend);
        messages.add(PromptMessage.system(systemAppend));
        return messages;
    }

    public static PromptMessage toPromptMessage(Event event) {
        return PromptMessage.of(mapRole(event), EventPromptSerializer.toPromptContent(event));
    }

    static String mapRole(Event event) {
        if (event == null) {
            return "user";
        }
        if (Event.TYPE_SYSTEM_PROMPT.equals(event.getType())
                || Event.KIND_SYSTEM.equals(event.getKind())
                || Event.ACTOR_SYSTEM.equals(event.getActor())) {
            return "system";
        }
        if (Event.ACTOR_ASSISTANT.equals(event.getActor()) || Event.KIND_RESPONSE.equals(event.getKind())) {
            return "assistant";
        }
        if (Event.ACTOR_USER.equals(event.getActor()) || Event.KIND_OBSERVATION.equals(event.getKind())) {
            return "user";
        }
        return "user";
    }

    private static void requireSystem(String systemPrepend) {
        if (systemPrepend == null) {
            throw new NullPointerException("systemPrepend (Decision prompt) cannot be null.");
        }
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
