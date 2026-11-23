package ch.zhaw.prometheus.model.task;

import java.util.Set;
import com.google.gson.*;
import ch.zhaw.prometheus.model.hand.HandSignObservation;

/** One named sign on one identifiable side, or either single hand. */
public record HandSignCondition(String sign, String handedness) {
    public static HandSignCondition parse(JsonElement raw) {
        var object = raw.getAsJsonObject();
        TaskSpec.exact(object, Set.of("sign", "handedness"));
        String sign = TaskSpec.string(object, "sign", 16), side = TaskSpec.string(object, "handedness", 5);
        if (!HandSignObservation.SIGNS.contains(sign) || !Set.of("any", "left", "right").contains(side)) throw new IllegalArgumentException();
        return new HandSignCondition(sign, side);
    }
    public boolean matches(HandSignObservation observation, double threshold) {
        var candidates = observation.hands().stream().filter(h -> handedness.equals("any") || handedness.equals(h.handedness())).toList();
        // Two reported left/right hands cannot establish which requested hand was meant.
        if (!handedness.equals("any") && candidates.size() != 1) return false;
        return candidates.stream().anyMatch(h -> sign.equals(h.sign()) && h.confidence() != null && h.confidence() >= threshold);
    }
    public String description() { return sign + " on " + (handedness.equals("any") ? "either single hand" : "the " + handedness + " hand"); }
    public static String instructions() {
        return """

                Named-hand extension for obs.hand.signs: fist, peace, open_palm (halt), thumbs_up,
                thumbs_down, index_up (attention). Each rule has eventType, handSign, action, effect,
                minConfidence, samples, cooldownSeconds. handSign has exactly sign and handedness (any/left/right).
                Preparation checks have exactly eventType, handSign, minConfidence. No scalar field/operator/value.
                Default minConfidence 0, samples 1, cooldownSeconds 3; a requested side is the human's handedness.
                Keep old obs.hand.sign rock/scissor/paper tasks unchanged. A sign is neither consent nor intent.
                Distinguish input support from output: new named-sign output is not yet supported.
                OK ring, custom poses, counting and pointing are not available yet; explain that limit.
                """;
    }
}
