package ch.zhaw.prometheus.spi;

import java.io.StringReader;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

/** Reject invalid structured output instead of interpreting it as false or persisting corrupt data. */
public final class InferenceResult {
    private InferenceResult() {}

    public static boolean bool(String raw) {
        if (raw != null && "true".equals(raw.trim())) return true;
        if (raw != null && "false".equals(raw.trim())) return false;
        throw new IllegalStateException("Inference did not return a boolean");
    }

    public static JsonElement json(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalStateException("Inference returned empty JSON");
        try {
            JsonReader reader = new JsonReader(new StringReader(raw));
            reader.setStrictness(Strictness.STRICT);
            JsonElement result = new Gson().fromJson(reader, JsonElement.class);
            if (result == null || result.isJsonNull() || reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalStateException("Inference returned null or trailing JSON");
            }
            return result;
        } catch (Exception invalid) {
            // Parser exceptions can contain fragments of private provider output.
            throw new IllegalStateException("Inference did not return valid non-null JSON");
        }
    }

    public static String validate(String raw, InferenceRequest.Output output) {
        if (raw == null || raw.isBlank()) throw new IllegalStateException("Inference returned empty content");
        switch (output) {
            case BOOLEAN -> bool(raw);
            case JSON -> json(raw);
            case JSON_OBJECT -> {
                if (!json(raw).isJsonObject()) throw new IllegalStateException("Inference did not return a JSON object");
            }
            case TEXT -> { }
        }
        return raw;
    }
}
