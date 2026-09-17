package ch.zhaw.prometheus.spi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ch.zhaw.prometheus.model.policy.PromptMessage;

/** Only composes isolated boolean tasks; it has no reference to states, actions or persistence. */
public final class GuardRequests {
    private GuardRequests() {}

    public static InferenceRequest combine(Map<String, InferenceRequest> checks) {
        JsonObject tasks = new JsonObject();
        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();
        for (var entry : checks.entrySet()) {
            JsonArray messages = new JsonArray();
            for (PromptMessage prompt : entry.getValue().messages()) {
                JsonObject message = new JsonObject();
                message.addProperty("role", prompt.getRole()); message.addProperty("content", prompt.getContent());
                messages.add(message);
            }
            tasks.add(entry.getKey(), messages);
            JsonObject type = new JsonObject(); type.addProperty("type", "boolean");
            properties.add(entry.getKey(), type); required.add(entry.getKey());
        }
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object"); schema.add("properties", properties);
        schema.add("required", required); schema.addProperty("additionalProperties", false);
        return InferenceRequest.structured(InferencePurpose.DECISION, List.of(
                PromptMessage.system("Evaluate every independent boolean task in the JSON map. Each task has its own instructions and selected history. "
                        + "Use only that task's history, never another task's context. Return exactly the given IDs mapped to JSON booleans. "
                        + "Do not select a transition, execute actions, or generate speech. Java retains control of priority."),
                PromptMessage.user(tasks.toString())), schema);
    }

    public static Map<String, Boolean> validate(String raw, java.util.Set<String> expected) {
        var parsed = InferenceResult.json(raw);
        if (!parsed.isJsonObject() || !parsed.getAsJsonObject().keySet().equals(expected)) throw invalid();
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String id : expected) {
            var value = parsed.getAsJsonObject().get(id);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw invalid();
            result.put(id, value.getAsBoolean());
        }
        return Map.copyOf(result);
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("Guard batch must contain exactly the requested boolean results");
    }
}
