package ch.zhaw.prometheus.agentdefs.usecases.gala;

import java.time.Instant;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.event.ObservationEvidence;

/** RPS acceptance is separate from browser emission; no frame-by-frame state writes. */
final class GalaDinnerHandSigns {
    static final double MIN_CONFIDENCE = 0.7;
    private GalaDinnerHandSigns() {}

    static String rejection(JsonObject data, Instant started, Instant now, Instant newer) {
        Instant at = ObservationEvidence.observed(data);
        if (!ObservationEvidence.fresh(at, now, 15, 2) || at.isBefore(started)) return "rps_stale_sign";
        if (newer != null && at.isBefore(newer)) return "rps_out_of_order";
        try {
            var sign = data.get("sign");
            if (sign == null || !sign.isJsonPrimitive() || !sign.getAsJsonPrimitive().isString()
                    || !java.util.Set.of("rock", "paper", "scissor").contains(sign.getAsString())) return "rps_invalid_sign";
            var confidence = data.get("confidence");
            if (confidence == null || !confidence.isJsonPrimitive() || !confidence.getAsJsonPrimitive().isNumber()) return "rps_low_confidence";
            double value = confidence.getAsDouble();
            if (!Double.isFinite(value) || value < MIN_CONFIDENCE || value > 1) return "rps_low_confidence";
            if (data.has("source") && "valerian.hand.camera".equals(data.get("source").getAsString())) {
                var frames = data.get("stabilityFrames");
                if (frames == null || !frames.isJsonPrimitive() || !frames.getAsJsonPrimitive().isNumber()
                        || frames.getAsDouble() < 3 || frames.getAsDouble() != frames.getAsInt()) return "rps_unstable_sign";
            }
            return null;
        } catch (RuntimeException invalid) { return "rps_invalid_sign"; }
    }

    static Instant newestPrior(EventHistory history, Instant started, Instant now) {
        Instant newest = started;
        var events = history.toList();
        for (int i = 0; i < events.size() - 1; i++) {
            var event = events.get(i);
            if (!Event.TYPE_HAND_SIGN.equals(event.getType())) continue;
            try {
                Instant at = ObservationEvidence.observed(JsonParser.parseString(event.getPayload()).getAsJsonObject());
                if (ObservationEvidence.fresh(at, now, 15, 2) && at.isAfter(newest)) newest = at;
            } catch (RuntimeException invalid) { /* No timestamp evidence. */ }
        }
        return newest;
    }
}
