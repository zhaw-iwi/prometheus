package ch.zhaw.prometheus.spi;

import java.util.Locale;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.zhaw.prometheus.model.event.Event;

final class EventPromptSerializer {
    private EventPromptSerializer() {
    }

    static String toPromptContent(Event event) {
        if (event == null) {
            return "";
        }
        if (Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType())) {
            String speech = extractJsonString(event.getPayload(), "speech");
            if (speech != null && !speech.isBlank()) {
                return speech;
            }
        }
        if (Event.TYPE_FACE_EMOTION.equals(event.getType())) {
            String emotion = extractJsonString(event.getPayload(), "emotion");
            if (emotion == null || emotion.isBlank()) {
                return "User facial emotion observed.";
            }
            Double confidence = extractJsonDouble(event.getPayload(), "confidence");
            if (confidence == null) {
                return "User facial emotion: " + emotion;
            }
            return "User facial emotion: " + emotion + " (confidence "
                    + String.format(Locale.ROOT, "%.2f", confidence) + ")";
        }
        return event.getPayload() == null ? "" : event.getPayload();
    }

    private static String extractJsonString(String payload, String field) {
        JsonObject object = parseObject(payload);
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
            return null;
        }
        try {
            return object.get(field).getAsString();
        } catch (Exception exception) {
            return null;
        }
    }

    private static Double extractJsonDouble(String payload, String field) {
        JsonObject object = parseObject(payload);
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
            return null;
        }
        try {
            return object.get(field).getAsDouble();
        } catch (Exception exception) {
            return null;
        }
    }

    private static JsonObject parseObject(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return JsonParser.parseString(payload).getAsJsonObject();
        } catch (Exception exception) {
            return null;
        }
    }
}
