package ch.zhaw.prometheus.model.policy;

import java.util.Locale;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.zhaw.prometheus.model.event.Event;

public class FaceEmotionPromptEventContentAdapter implements PromptEventContentAdapter {
    @Override
    public boolean supports(Event event) {
        return event != null && Event.TYPE_FACE_EMOTION.equals(event.getType());
    }

    @Override
    public String toPromptContent(Event event) {
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

