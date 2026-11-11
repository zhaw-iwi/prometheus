package ch.zhaw.prometheus.model.policy;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.hand.FingerState;

/** Render bounded numeric pose data and known state labels, never arbitrary client text. */
public class HandPosePromptEventContentAdapter implements PromptEventContentAdapter {
    @Override public boolean supports(Event event) {
        return event != null && Event.TYPE_HAND_POSE.equals(event.getType());
    }

    @Override public String toPromptContent(Event event) {
        try {
            var hands = JsonParser.parseString(event.getPayload()).getAsJsonObject().getAsJsonArray("hands");
            if (hands == null || hands.size() > 2) throw new IllegalArgumentException();
            if (hands.isEmpty()) return "Hand pose: no hands detected in the camera view.";
            var text = new StringBuilder("Estimated finger bend (0=straight, 1=fully bent; unknown=unavailable). "
                    + "Client states: below 0.4 straight, above 0.55 bent, 0.4..0.55 neutral. ");
            for (int i = 0; i < hands.size(); i++) {
                var hand = hands.get(i).getAsJsonObject();
                String side = hand.has("handedness") ? hand.get("handedness").getAsString() : "unknown";
                if (!Set.of("left", "right").contains(side)) side = "unknown";
                text.append("Hand ").append(i + 1).append(" (").append(side).append("): ");
                var fingers = hand.getAsJsonObject("fingers");
                for (String finger : List.of("thumb", "index", "middle", "ring", "little")) {
                    text.append(finger).append('=').append(value(fingers == null ? null : fingers.get(finger))).append(' ');
                }
                var states = hand.get("fingerStates");
                text.append("States: ");
                for (String finger : List.of("thumb", "index", "middle", "ring", "little")) {
                    var state = states != null && states.isJsonObject() ? FingerState.from(states.getAsJsonObject().get(finger)) : null;
                    text.append(finger).append('=').append(available(fingers == null ? null : fingers.get(finger))
                            && state != null ? state.label() : "unknown").append(' ');
                }
            }
            return text.append("Hidden fingers may be inferred; handedness is not identity. No named sign or intent is established.").toString();
        } catch (RuntimeException invalid) {
            return "Hand pose unknown: unavailable or invalid finger readings.";
        }
    }

    private static String value(JsonElement value) {
        return available(value) ? String.format(Locale.ROOT, "%.3f", value.getAsDouble()) : "unknown";
    }
    private static boolean available(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return false;
        double number = value.getAsDouble();
        return Double.isFinite(number) && number >= 0 && number <= 1;
    }
}
