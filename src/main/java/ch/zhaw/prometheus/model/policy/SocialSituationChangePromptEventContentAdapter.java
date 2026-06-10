package ch.zhaw.prometheus.model.policy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.zhaw.prometheus.model.event.Event;

public class SocialSituationChangePromptEventContentAdapter implements PromptEventContentAdapter {
    @Override
    public boolean supports(Event event) {
        return event != null && Event.TYPE_SOCIAL_SITUATION_CHANGE.equals(event.getType());
    }

    @Override
    public String toPromptContent(Event event) {
        if (event == null || event.getPayload() == null || event.getPayload().isBlank()) {
            return "Social situation changed.";
        }
        try {
            JsonObject payload = JsonParser.parseString(event.getPayload()).getAsJsonObject();
            String changeType = string(payload, "changeType", "unknown");
            String previous = nullableNumber(payload, "previousHumanCount");
            String current = nullableNumber(payload, "currentHumanCount");
            String largest = nullableNumber(payload, "currentLargestGroupSize");
            String confidence = nullableNumber(payload, "confidence");
            String reason = string(payload, "reason", "");
            StringBuilder result = new StringBuilder("Social situation change: ")
                    .append(changeType)
                    .append(" (humans ")
                    .append(previous)
                    .append(" -> ")
                    .append(current)
                    .append(", largest group ")
                    .append(largest)
                    .append(", confidence ")
                    .append(confidence)
                    .append(")");
            if (!reason.isBlank()) {
                result.append(". Reason: ").append(reason);
            }
            return result.toString();
        } catch (RuntimeException exception) {
            return "Social situation changed.";
        }
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
            return "null";
        }
        try {
            return payload.get(key).getAsString();
        } catch (RuntimeException exception) {
            return "unknown";
        }
    }
}
