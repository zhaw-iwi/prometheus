package ch.zhaw.prometheus.agentdefs.usecases.gala;

import java.time.Instant;
import java.util.Locale;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;

/** Encounter-local published requests, never claims of physical execution. */
final class GalaDinnerGestures {
    static final String HISTORY = "gala.gestures", REQUIRED = "gala.gesture_required";
    private static final java.util.Set<String> LABELS = java.util.Set.of("OPEN_QUESTION", "EXPLAIN", "UNCERTAIN", "ACKNOWLEDGE", "POLITE");
    private GalaDinnerGestures() {}

    static String guidance(Storage storage, Instant now) {
        return "\nRecent published gesture requests (data, not physical execution): " + recent(storage, now)
                + ". Occasionally support a clear conversational point with a fitting gesture; choose NONE for routine/listening moments. Decorative gestures wait 12 seconds; "
                + "the same label waits 60 seconds. Never rotate labels just for variety. "
                + "Requested gestures and agreed task actions take precedence over decorative cadence.";
    }

    static BehaviourPlan publish(Storage storage, BehaviourPlan plan, boolean required, Instant now) {
        if (plan == null || plan.getNonVerbal() == null || !plan.getNonVerbal().isJsonObject()) return plan;
        var nonverbal = plan.getNonVerbal().getAsJsonObject();
        if (!nonverbal.has("gesture") || !nonverbal.get("gesture").isJsonPrimitive()) return plan;
        String label = nonverbal.get("gesture").getAsString();
        if ("NONE".equals(label)) return plan;
        // These are game signs, not decorative movements; motion.handSign is untouched as well.
        if (java.util.Set.of("rock", "scissor", "paper").contains(label)) return plan;
        var history = recent(storage, now);
        boolean suppress = false;
        for (var entry : history) {
            var item = entry.getAsJsonObject();
            Instant at = Instant.parse(item.get("at").getAsString());
            if (now.isBefore(at.plusSeconds(12)) || label.equals(item.get("label").getAsString())
                    && now.isBefore(at.plusSeconds(60))) suppress = true;
        }
        if (!required && suppress) {
            var copy = BehaviourPlan.fromJson(plan.toJson());
            copy.getNonVerbal().getAsJsonObject().addProperty("gesture", "NONE");
            return copy;
        }
        while (history.size() >= 5) history.remove(0);
        var item = new JsonObject(); item.addProperty("label", label); item.addProperty("at", now.toString());
        history.add(item); storage.put(HISTORY, history);
        return plan;
    }

    static boolean requested(String utterance) {
        String text = utterance.toLowerCase(Locale.ROOT);
        // This only exempts a separately selected gesture from cadence; it cannot authorize motion.
        return text.matches("(?s).*(geste|gesture).*")
                && (text.matches("(?s)^(bitte |please )?(zeig\\w*|mach\\w*|show|perform|do)\\b.*")
                    || text.matches("(?s)^(kannst du|könntest du|würdest du|can you|could you|would you|i want you to)\\b.*(zeig|mach|show|perform|do ).*"))
                && !text.matches("(?s).*(keine|nicht|don't|do not|never|wenn|if |beispiel|example).*");
    }

    private static JsonArray recent(Storage storage, Instant now) {
        var result = new JsonArray();
        if (!storage.containsKey(HISTORY) || !storage.get(HISTORY).isJsonArray()) return result;
        for (var value : storage.get(HISTORY).getAsJsonArray()) {
            try {
                var item = value.getAsJsonObject();
                if (!LABELS.contains(item.get("label").getAsString())) continue;
                Instant at = Instant.parse(item.get("at").getAsString());
                if (!at.isAfter(now) && now.isBefore(at.plusSeconds(60))) result.add(item.deepCopy());
            } catch (RuntimeException invalid) { /* Corrupt optional history cannot block activity. */ }
        }
        while (result.size() > 5) result.remove(0);
        return result;
    }
}
