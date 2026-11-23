package ch.zhaw.prometheus.model.hand;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Active hand data is bounded separately from the framework's retained event journal. */
public final class HandStateBudget {
    public static final int MAX_BYTES = 65536;
    private static final Set<String> CURRENT = Set.of("coordination.draft", "coordination.agreement", "task.spec", "command.lastResult");
    private HandStateBudget() {}
    public static int bytes(Map<String, JsonElement> storage) {
        var hand = new JsonObject();
        storage.forEach((key, value) -> {
            if (key.startsWith("hand.") || CURRENT.contains(key)) hand.add(key, value);
        });
        return hand.toString().getBytes(StandardCharsets.UTF_8).length;
    }
    public static void require(Map<String, JsonElement> storage) {
        if (bytes(storage) > MAX_BYTES)
            throw new IllegalArgumentException("Active hand state exceeds 64 KiB; simplify the scene or agreement");
    }
}
