package ch.zhaw.prometheus.model.behaviour;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;

/** Bounded history of published requests, not evidence of physical execution. Opt-in only. */
public final class DecorativeGestures {
    public static final String ENABLED = "behaviour.gestureCadence", HISTORY = "behaviour.recentGestures",
            REQUIRED = "behaviour.requiredGesture";
    private static final Set<String> LABELS = Set.of("OPEN_QUESTION", "EXPLAIN", "UNCERTAIN", "ACKNOWLEDGE", "POLITE");
    private DecorativeGestures() {}

    public static boolean enabled(Storage storage) {
        return storage != null && storage.containsKey(ENABLED) && storage.get(ENABLED).isJsonPrimitive()
                && storage.get(ENABLED).getAsJsonPrimitive().isBoolean() && storage.get(ENABLED).getAsBoolean();
    }

    public static String guidance(Storage storage, Instant now) {
        return "\nRecent published decorative gesture requests (data, not physical execution): " + recent(storage, now)
                + ". Choose an occasional gesture for a clear conversational point; NONE is appropriate otherwise. "
                + "Allow 12 seconds between decorative gestures and 60 seconds before repeating a label. "
                + "Never rotate labels merely for variety. Explicitly requested gestures and agreed task actions are exempt.";
    }

    public static BehaviourPlan publish(Storage storage, BehaviourPlan plan, boolean required, Instant now) {
        if (plan == null || plan.getNonVerbal() == null || !plan.getNonVerbal().isJsonObject()) return plan;
        var gesture = plan.getNonVerbal().getAsJsonObject().get("gesture");
        if (gesture == null || !gesture.isJsonPrimitive() || !gesture.getAsJsonPrimitive().isString()) return plan;
        String label = gesture.getAsString();
        // Hand signs, task motion and all other channels retain their existing contract.
        if (!LABELS.contains(label)) return plan;
        var history = recent(storage, now);
        for (var value : history) {
            var item = value.getAsJsonObject();
            Instant at = Instant.parse(item.get("at").getAsString());
            if (!required && (now.isBefore(at.plusSeconds(12)) || label.equals(item.get("label").getAsString()))) {
                var copy = BehaviourPlan.fromJson(plan.toJson());
                copy.getNonVerbal().getAsJsonObject().addProperty("gesture", "NONE");
                return copy;
            }
        }
        while (history.size() >= 5) history.remove(0);
        var item = new JsonObject(); item.addProperty("label", label); item.addProperty("at", now.toString());
        history.add(item); storage.put(HISTORY, history);
        return plan;
    }

    public static boolean requested(EventHistory events) {
        if (events == null || events.isEmpty()) return false;
        var latest = events.toList().getLast();
        return Event.TYPE_USER_UTTERANCE.equals(latest.getType()) && requested(latest.getPayload());
    }

    /** Exempts a separately validated gesture; never authorizes a new action. */
    public static boolean requested(String utterance) {
        if (utterance == null) return false;
        String text = utterance.strip().toLowerCase(Locale.ROOT);
        return text.matches("(?s).*(geste|gesture|nod|wave|bow).*")
                && (text.matches("(?s)^(bitte |please )?(zeig\\w*|mach\\w*|show|perform|do|nod|wave|bow)\\b.*")
                    || text.matches("(?s)^(kannst du|könntest du|würdest du|can you|could you|would you|i want you to)\\b.*(zeig|mach|show|perform|do |nod|wave|bow).*") )
                && !text.matches("(?s).*(keine|nicht|don't|do not|never|wenn|if |beispiel|example).*");
    }

    public static void clear(Storage storage) {
        for (String key : Set.of(HISTORY, REQUIRED)) if (storage.containsKey(key)) storage.remove(key);
    }

    private static JsonArray recent(Storage storage, Instant now) {
        var result = new JsonArray();
        if (storage == null || !storage.containsKey(HISTORY) || !storage.get(HISTORY).isJsonArray()) return result;
        for (var value : storage.get(HISTORY).getAsJsonArray()) {
            try {
                var item = value.getAsJsonObject();
                if (!LABELS.contains(item.get("label").getAsString())) continue;
                Instant at = Instant.parse(item.get("at").getAsString());
                if (!at.isAfter(now) && now.isBefore(at.plusSeconds(60))) result.add(item.deepCopy());
            } catch (RuntimeException invalid) { /* Optional corrupt history cannot prevent valid output. */ }
        }
        while (result.size() > 5) result.remove(0);
        return result;
    }
}
