package ch.zhaw.prometheus.model.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.zhaw.prometheus.model.event.Event;

public class SocialContextPromptEventContentAdapter implements PromptEventContentAdapter {
    @Override
    public boolean supports(Event event) {
        return event != null && Event.TYPE_SOCIAL_CONTEXT.equals(event.getType());
    }

    @Override
    public String toPromptContent(Event event) {
        if (event == null || event.getPayload() == null || event.getPayload().isBlank()) {
            return "Social context observed.";
        }
        try {
            JsonObject payload = JsonParser.parseString(event.getPayload()).getAsJsonObject();
            String humanCount = nullableNumber(payload, "humanCount");
            String groupCount = nullableNumber(payload, "groupCount");
            String singletonCount = nullableNumber(payload, "singletonCount");
            String largestGroupSize = nullableNumber(payload, "largestGroupSize");
            List<String> groupDescriptions = groups(payload);
            List<String> peopleDescriptions = people(payload);

            StringBuilder result = new StringBuilder("Social context: ")
                    .append(humanCount)
                    .append(" people visible; ")
                    .append(groupCount)
                    .append(" ")
                    .append("1".equals(groupCount) ? "group" : "groups")
                    .append(", largest ")
                    .append(largestGroupSize)
                    .append(", singletons ")
                    .append(singletonCount);
            if (!groupDescriptions.isEmpty()) {
                result.append(". Groups: ").append(String.join("; ", groupDescriptions));
            }
            if (!peopleDescriptions.isEmpty()) {
                result.append(". People: ").append(String.join("; ", peopleDescriptions));
            }
            return result.toString();
        } catch (RuntimeException exception) {
            return "Social context observed.";
        }
    }

    private static List<String> groups(JsonObject payload) {
        JsonArray groups = array(payload, "groups");
        if (groups == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement element : groups) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject group = element.getAsJsonObject();
            String size = nullableNumber(group, "size");
            List<String> memberIds = strings(array(group, "memberIds"));
            String members = memberIds.isEmpty() ? "members unknown" : "members " + String.join(", ", memberIds);
            values.add("size " + size + " (" + members + ")");
        }
        return values;
    }

    private static List<String> people(JsonObject payload) {
        JsonArray people = array(payload, "people");
        if (people == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement element : people) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject person = element.getAsJsonObject();
            String id = string(person, "id", "?");
            String detection = decimal(person, "detectionConfidence");
            JsonObject movement = object(person, "movement");
            JsonObject attention = object(person, "attention");
            String movementState = string(movement, "state", "unknown");
            String movementConfidence = decimal(movement, "confidence");
            String attentionState = string(attention, "state", "unknown").replace('_', ' ');
            String attentionConfidence = decimal(attention, "confidence");
            List<String> cues = attentionCues(attention);
            StringBuilder value = new StringBuilder("person ")
                    .append(id)
                    .append(" detection ")
                    .append(detection)
                    .append(", movement ")
                    .append(movementState)
                    .append(" ")
                    .append(movementConfidence)
                    .append(", attention ")
                    .append(attentionState)
                    .append(" ")
                    .append(attentionConfidence);
            if (!cues.isEmpty()) {
                value.append(" (").append(String.join(", ", cues)).append(")");
            }
            values.add(value.toString());
        }
        return values;
    }

    private static List<String> attentionCues(JsonObject attention) {
        if (attention == null) {
            return List.of();
        }
        List<String> cues = new ArrayList<>();
        if (bool(attention, "personVisible")) {
            cues.add("person visible");
        }
        if (bool(attention, "faceVisible")) {
            cues.add("face likely");
        }
        if (bool(attention, "frontalCentered")) {
            cues.add("centered/frontal");
        } else if (bool(attention, "centered")) {
            cues.add("centered");
        } else if (bool(attention, "nearFrontal")) {
            cues.add("near frontal");
        }
        return cues;
    }

    private static JsonArray array(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || !object.get(key).isJsonArray()) {
            return null;
        }
        return object.getAsJsonArray(key);
    }

    private static JsonObject object(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || !object.get(key).isJsonObject()) {
            return null;
        }
        return object.getAsJsonObject(key);
    }

    private static List<String> strings(JsonArray array) {
        if (array == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            if (element == null || element.isJsonNull()) {
                continue;
            }
            try {
                values.add(element.getAsString());
            } catch (RuntimeException exception) {
                // Ignore malformed member values inside an otherwise useful social context event.
            }
        }
        return values;
    }

    private static String string(JsonObject payload, String key, String fallback) {
        if (payload == null || key == null || !payload.has(key) || payload.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return payload.get(key).getAsString();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static String nullableNumber(JsonObject payload, String key) {
        if (payload == null || key == null || !payload.has(key) || payload.get(key).isJsonNull()) {
            return "unknown";
        }
        try {
            return payload.get(key).getAsString();
        } catch (RuntimeException exception) {
            return "unknown";
        }
    }

    private static String decimal(JsonObject payload, String key) {
        if (payload == null || key == null || !payload.has(key) || payload.get(key).isJsonNull()) {
            return "unknown";
        }
        try {
            return String.format(Locale.ROOT, "%.2f", payload.get(key).getAsDouble());
        } catch (RuntimeException exception) {
            return "unknown";
        }
    }

    private static boolean bool(JsonObject payload, String key) {
        if (payload == null || key == null || !payload.has(key) || payload.get(key).isJsonNull()) {
            return false;
        }
        try {
            return payload.get(key).getAsBoolean();
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
