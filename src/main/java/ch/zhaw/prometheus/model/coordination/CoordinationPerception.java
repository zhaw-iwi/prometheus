package ch.zhaw.prometheus.model.coordination;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import com.google.gson.JsonParser;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.task.TaskMemory;

/** Narrow factual questions need neither a task operation nor a generated sensor estimate. */
public final class CoordinationPerception {
    private CoordinationPerception() {}
    public static String answer(EventHistory history, String text, Instant now) {
        String value = text.toLowerCase(Locale.ROOT).strip();
        boolean question = value.matches("^(what|which|do you|can you see|was|welch\\p{L}*|siehst du|erkennst du).*?")
                || value.matches("(?:hand sign|facial expression) do you see(?: now)?[?]?");
        if (!question) return null;
        String type = value.matches(".*\\b(handzeichen|hand sign|sign|zeichen)\\b.*") ? Event.TYPE_HAND_SIGN
                : value.matches(".*\\b(gesichtsausdruck|facial expression|face expression)\\b.*") ? Event.TYPE_FACE_EMOTION : null;
        if (type == null) return null;
        if (value.matches(".*\\b(should|could|would|use|kannst|sollen|nutzen|verwenden)\\b.*")) return null;
        if (value.matches(".*\\b(triggers?|rules?|agreement|agreed|proposal|task|auslöst|auslösen|regel|vereinbarung|aufgabe)\\b.*")) return null;
        // A proposal/control request is not turned into a perception question by mentioning a sensor.
        if (value.matches(".*\\b(start|stop|pause|resume|restart|when|whenever|wenn|sobald|erzähle|tell|propose)\\b.*")) return null;
        Event latest = null;
        for (var event : history.toList()) if (type.equals(event.getType())) latest = event;
        String label = type.equals(Event.TYPE_HAND_SIGN) ? "hand sign" : "facial expression";
        if (latest == null) return "No " + label + " observation has been supplied. Check that the detector and observation delivery are enabled.";
        if (!TaskMemory.fresh(latest, now)) return "The supplied " + label + " observation is expired or has an invalid source time. I cannot describe it as current. Supply a fresh observation.";
        try {
            var data = JsonParser.parseString(latest.getPayload()).getAsJsonObject();
            if (!data.has("confidence") || !data.get("confidence").isJsonPrimitive()
                    || !data.get("confidence").getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
            double confidence = data.get("confidence").getAsDouble();
            if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) throw new IllegalArgumentException();
            if (type.equals(Event.TYPE_HAND_SIGN)) {
                String sign = data.get("sign").getAsString();
                if (!Set.of("rock", "scissor", "paper").contains(sign)) throw new IllegalArgumentException();
                return "When you asked, a fresh supplied hand observation reported " + (sign.equals("scissor") ? "scissors" : sign)
                        + " (recognition confidence " + Math.round(confidence * 100) + "%). This is a supplied estimate, not direct access to camera images.";
            }
            if (!ObservationEvidence.usableFace(data, .5)) return "The supplied facial estimate is too uncertain or has no reliably detected face. I cannot establish your expression or feelings from it.";
            String emotion = data.get("emotion").getAsString();
            if (!Set.of("neutral", "happy", "sad", "angry", "fearful", "disgusted", "surprised").contains(emotion)) throw new IllegalArgumentException();
            return "When you asked, a fresh supplied facial estimate reported " + emotion + " (recognition confidence "
                    + Math.round(confidence * 100) + "%). It does not establish your feelings or intentions.";
        } catch (RuntimeException invalid) { return "The supplied " + label + " observation is invalid or incomplete. I cannot give a reliable estimate; supply a fresh valid observation."; }
    }
}
