package fixtures.behaviour;

import java.time.Instant;
import com.google.gson.*;
import ch.zhaw.prometheus.model.event.Event;

public final class NamedHandSignFixture {
    private NamedHandSignFixture() {}
    public static JsonObject observation(String id, String sign, String side, Instant time) {
        return JsonParser.parseString("""
                {"schemaVersion":1,"observationId":"%s","source":"test.camera","ts":"%s",
                 "frame":{"id":"camera","revision":1,"space":"camera_image","mirrored":false},
                 "hands":[{"id":"hand-0","handedness":"%s","sign":"%s","confidence":0.9}]}
                """.formatted(id, time, side, sign)).getAsJsonObject();
    }
    public static Event event(String id, String sign, String side, Instant time) {
        return Event.observation(Event.TYPE_HAND_SIGNS, "camera", observation(id, sign, side, time).toString());
    }
    public static JsonObject agreement() {
        try (var input = NamedHandSignFixture.class.getResourceAsStream("/hand-interaction/named-sign-agreement.json")) {
            return JsonParser.parseReader(new java.io.InputStreamReader(java.util.Objects.requireNonNull(input),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
}
