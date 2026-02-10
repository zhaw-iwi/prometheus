package ch.zhaw.prometheus.model.policy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.zhaw.prometheus.model.event.Event;

public class BehaviourPlanPromptEventContentAdapter implements PromptEventContentAdapter {
    @Override
    public boolean supports(Event event) {
        return event != null && Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType());
    }

    @Override
    public String toPromptContent(Event event) {
        String speech = extractJsonString(event.getPayload(), "speech");
        if (speech != null && !speech.isBlank()) {
            return speech;
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

