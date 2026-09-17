package ch.zhaw.prometheus.spi;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.google.gson.JsonObject;
import ch.zhaw.prometheus.logging.LatencyTrace;
import ch.zhaw.prometheus.model.policy.PromptMessage;

/** Immutable prompt snapshot; provider choices belong to configuration, not persisted policies. */
public record InferenceRequest(InferencePurpose purpose, List<PromptMessage> messages, Output output,
        JsonObject schema, String requestId, String traceId) {
    public enum Output { TEXT, BOOLEAN, JSON, JSON_OBJECT }

    public InferenceRequest {
        Objects.requireNonNull(purpose);
        Objects.requireNonNull(output);
        if (schema != null && output != Output.JSON_OBJECT) {
            throw new IllegalArgumentException("A structured schema requires JSON_OBJECT output");
        }
        messages = List.copyOf(messages);
        schema = schema == null ? null : schema.deepCopy();
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(traceId);
    }

    public InferenceRequest(InferencePurpose purpose, List<PromptMessage> messages, Output output) {
        this(purpose, messages, output, null, UUID.randomUUID().toString(), LatencyTrace.currentId());
    }

    public static InferenceRequest structured(InferencePurpose purpose, List<PromptMessage> messages, JsonObject schema) {
        return new InferenceRequest(purpose, messages, Output.JSON_OBJECT, schema,
                UUID.randomUUID().toString(), LatencyTrace.currentId());
    }

    @Override public JsonObject schema() { return schema == null ? null : schema.deepCopy(); }
    @Override public String toString() { return "InferenceRequest[" + requestId + ", " + purpose + "]"; }
}
