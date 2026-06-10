package ch.zhaw.prometheus.model.social;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;

public class SocialSituationChangeDetector {
    public static final String SOURCE = "prometheus.social_situation_change";
    public static final String CHANGE_ARRIVAL = "arrival";
    public static final String CHANGE_DEPARTURE = "departure";
    public static final String CHANGE_CROWD_DETECTED = "crowd_detected";
    public static final String CHANGE_NOW_ALONE = "now_alone";
    public static final String CHANGE_SINGLE_PERSON_NEARBY = "single_person_nearby";
    public static final String CHANGE_GROUP_SIZE_CHANGED = "group_size_changed";

    private final int crowdThreshold;

    public SocialSituationChangeDetector(int crowdThreshold) {
        if (crowdThreshold < 2) {
            throw new IllegalArgumentException("crowdThreshold must be at least 2");
        }
        this.crowdThreshold = crowdThreshold;
    }

    public static SocialSituationChangeDetector defaultThresholds() {
        return new SocialSituationChangeDetector(3);
    }

    public Optional<Event> detect(EventHistory history) {
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        List<Event> events = history.toList();
        IndexedSocialGrouping current = latestGrouping(events);
        if (current == null) {
            return Optional.empty();
        }
        if (hasComputedChangeAfter(events, current.index())) {
            return Optional.empty();
        }

        IndexedSocialGrouping previous = previousGrouping(events, current.index());
        SocialChange change = determineChange(previous == null ? null : previous.sample(), current.sample());
        if (change == null) {
            return Optional.empty();
        }
        double confidence = latestPresenceConfidence(events, current.index()).orElse(1.0d);
        return Optional.of(Event.observation(
                Event.TYPE_SOCIAL_SITUATION_CHANGE,
                Event.ACTOR_SYSTEM,
                payload(change, previous == null ? null : previous.sample(), current.sample(), confidence)));
    }

    private SocialChange determineChange(SocialGrouping previous, SocialGrouping current) {
        if (current == null) {
            return null;
        }
        if (previous == null) {
            if (current.humanCount() == 0) {
                return new SocialChange(CHANGE_NOW_ALONE, "initial observation contains no humans");
            }
            if (current.humanCount() >= this.crowdThreshold) {
                return new SocialChange(CHANGE_CROWD_DETECTED,
                        "initial observation meets crowd threshold " + this.crowdThreshold);
            }
            if (current.humanCount() == 1) {
                return new SocialChange(CHANGE_SINGLE_PERSON_NEARBY,
                        "initial observation contains one nearby human");
            }
            return null;
        }

        if (current.humanCount() >= this.crowdThreshold && previous.humanCount() < this.crowdThreshold) {
            return new SocialChange(CHANGE_CROWD_DETECTED,
                    "human count reached crowd threshold " + this.crowdThreshold);
        }
        if (previous.humanCount() == 0 && current.humanCount() > 0) {
            return new SocialChange(CHANGE_ARRIVAL,
                    "human count increased from 0 to " + current.humanCount());
        }
        if (previous.humanCount() > 0 && current.humanCount() == 0) {
            return new SocialChange(CHANGE_DEPARTURE,
                    "human count decreased from " + previous.humanCount() + " to 0");
        }
        if (current.humanCount() == 1 && previous.humanCount() != 1) {
            return new SocialChange(CHANGE_SINGLE_PERSON_NEARBY,
                    "human count changed from " + previous.humanCount() + " to 1");
        }
        if (previous.groupCount() != current.groupCount()
                || previous.singletonCount() != current.singletonCount()
                || previous.largestGroupSize() != current.largestGroupSize()) {
            return new SocialChange(CHANGE_GROUP_SIZE_CHANGED,
                    "social grouping changed while human count stayed near current level");
        }
        return null;
    }

    private static IndexedSocialGrouping latestGrouping(List<Event> events) {
        for (int i = events.size() - 1; i >= 0; i--) {
            Event event = events.get(i);
            if (event == null || !Event.TYPE_SOCIAL_GROUPING.equals(event.getType())) {
                continue;
            }
            SocialGrouping sample = toGrouping(event);
            if (sample == null) {
                return null;
            }
            return new IndexedSocialGrouping(i, sample);
        }
        return null;
    }

    private static IndexedSocialGrouping previousGrouping(List<Event> events, int beforeIndex) {
        for (int i = beforeIndex - 1; i >= 0; i--) {
            Event event = events.get(i);
            if (event == null || !Event.TYPE_SOCIAL_GROUPING.equals(event.getType())) {
                continue;
            }
            SocialGrouping sample = toGrouping(event);
            if (sample == null) {
                continue;
            }
            return new IndexedSocialGrouping(i, sample);
        }
        return null;
    }

    private static boolean hasComputedChangeAfter(List<Event> events, int groupingIndex) {
        for (int i = groupingIndex + 1; i < events.size(); i++) {
            Event event = events.get(i);
            if (event != null && Event.TYPE_SOCIAL_SITUATION_CHANGE.equals(event.getType())) {
                return true;
            }
        }
        return false;
    }

    private static Optional<Double> latestPresenceConfidence(List<Event> events, int beforeOrAtIndex) {
        for (int i = beforeOrAtIndex; i >= 0; i--) {
            Event event = events.get(i);
            if (event == null || !Event.TYPE_HUMAN_PRESENCE.equals(event.getType())) {
                continue;
            }
            try {
                JsonObject payload = JsonParser.parseString(event.getPayload()).getAsJsonObject();
                if (!payload.has("avgDetectionConfidence") || payload.get("avgDetectionConfidence").isJsonNull()) {
                    return Optional.empty();
                }
                double confidence = payload.get("avgDetectionConfidence").getAsDouble();
                if (Double.isNaN(confidence) || Double.isInfinite(confidence)) {
                    return Optional.empty();
                }
                return Optional.of(Math.max(0.0d, Math.min(1.0d, confidence)));
            } catch (RuntimeException exception) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static SocialGrouping toGrouping(Event event) {
        if (event.getPayload() == null || event.getPayload().isBlank()) {
            return null;
        }
        try {
            JsonObject payload = JsonParser.parseString(event.getPayload()).getAsJsonObject();
            Integer humanCount = readRequiredInt(payload, "humanCount");
            Integer groupCount = readRequiredInt(payload, "groupCount");
            Integer singletonCount = readRequiredInt(payload, "singletonCount");
            Integer largestGroupSize = readRequiredInt(payload, "largestGroupSize");
            if (humanCount == null || groupCount == null || singletonCount == null || largestGroupSize == null) {
                return null;
            }
            if (humanCount < 0 || groupCount < 0 || singletonCount < 0 || largestGroupSize < 0) {
                return null;
            }
            if (largestGroupSize > humanCount) {
                return null;
            }
            return new SocialGrouping(humanCount, groupCount, singletonCount, largestGroupSize);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Integer readRequiredInt(JsonObject payload, String key) {
        if (payload == null || key == null || !payload.has(key) || payload.get(key).isJsonNull()) {
            return null;
        }
        try {
            return payload.get(key).getAsInt();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String payload(SocialChange change, SocialGrouping previous, SocialGrouping current,
            double confidence) {
        JsonObject payload = new JsonObject();
        payload.addProperty("source", SOURCE);
        payload.addProperty("changeType", change.changeType());
        addNullableInt(payload, "previousHumanCount", previous == null ? null : previous.humanCount());
        payload.addProperty("currentHumanCount", current.humanCount());
        addNullableInt(payload, "previousLargestGroupSize", previous == null ? null : previous.largestGroupSize());
        payload.addProperty("currentLargestGroupSize", current.largestGroupSize());
        addNullableInt(payload, "previousGroupCount", previous == null ? null : previous.groupCount());
        payload.addProperty("currentGroupCount", current.groupCount());
        payload.addProperty("confidence", confidence);
        payload.addProperty("reason", change.reason());
        JsonArray sourceTypes = new JsonArray();
        sourceTypes.add(Event.TYPE_SOCIAL_GROUPING);
        sourceTypes.add(Event.TYPE_HUMAN_PRESENCE);
        payload.add("sourceEventTypes", sourceTypes);
        payload.addProperty("ts", Instant.now().toString());
        return payload.toString();
    }

    private static void addNullableInt(JsonObject payload, String key, Integer value) {
        if (value == null) {
            payload.add(key, JsonNull.INSTANCE);
            return;
        }
        payload.addProperty(key, value);
    }

    private record IndexedSocialGrouping(int index, SocialGrouping sample) {
    }

    private record SocialGrouping(int humanCount, int groupCount, int singletonCount, int largestGroupSize) {
    }

    private record SocialChange(String changeType, String reason) {
    }
}
