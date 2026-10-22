package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;
import java.time.Instant;
import java.util.Set;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;

/** Bounded ambient admission; raw refreshes and uncertain interpretations never queue speech. */
final class GalaDinnerSensing {
    static final String PREFIX = "gala.sensing.", GROUP_BASE = PREFIX + "group_base", GROUP_CANDIDATE = PREFIX + "group_candidate",
            GROUP_SINCE = PREFIX + "group_since", FACE_BASE = PREFIX + "face_base", FACE_CANDIDATE = PREFIX + "face_candidate",
            FACE_SINCE = PREFIX + "face_since", AMBIENT_AT = PREFIX + "ambient_at", AMBIENT_SOURCE = PREFIX + "ambient_source", CORRECTED = PREFIX + "corrected";
    private GalaDinnerSensing() {}
    static void begin(Storage storage, Instant now) {
        clear(storage); put(storage, GROUP_BASE, text(storage, COUNT, "0")); put(storage, AMBIENT_AT, now.toString());
    }
    static void clear(Storage storage) {
        for (String key : storage.toMap().keySet()) if (key.startsWith(PREFIX)) remove(storage, key);
    }
    static void activityEnded(Storage storage) {
        for (String key : Set.of(GROUP_CANDIDATE, GROUP_SINCE, FACE_CANDIDATE, FACE_SINCE, FACE_BASE)) remove(storage, key);
        put(storage, GROUP_BASE, text(storage, COUNT, "0"));
    }
    static void cancelAmbient(Storage storage) {
        if (!"true".equals(text(storage, CONTINUATION_AMBIENT, ""))) return;
        for (String key : Set.of(CONTINUATION_ID, CONTINUATION_FACTS, CONTINUATION_EXPIRES, CONTINUATION_OCCASION, CONTINUATION_AMBIENT)) remove(storage, key);
    }
    static void corrected(Storage storage) { put(storage, CORRECTED, "true"); cancelAmbient(storage); }
    private static void cancelSource(Storage storage, String source) {
        if (source.equals(text(storage, AMBIENT_SOURCE, ""))) cancelAmbient(storage);
    }

    static void presence(Storage storage, EventHistory events, PolicyRuntime runtime, Instant now) {
        try {
            var data = JsonParser.parseString(events.toList().getLast().getPayload()).getAsJsonObject();
            Instant observed = ObservationEvidence.observed(data);
            if (!ObservationEvidence.fresh(observed, now, 15, 2) || !observed.isAfter(time(storage, SEEN))) return;
            var value = data.get("humanCount");
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return;
            int count = value.getAsBigDecimal().intValueExact(); if (count < 0 || count > 1000) return;
            int before = Integer.parseInt(text(storage, COUNT, "0"));
            boolean gap = observed.isAfter(time(storage, SEEN).plusSeconds(15));
            put(storage, SEEN, observed.toString()); put(storage, COUNT, Integer.toString(count));
            if (count == 0) {
                if (before > 0 && runtime.externalSpeech() != null && !storage.containsKey(ABSENT_SINCE))
                    put(storage, ABSENT_SINCE, now.toString());
                cancelAmbient(storage);
                // Browser owns the confirmed departure deadline and closes Live. A second
                // server timer could erase a resumed encounter behind a delayed positive ACK.
                // No missed cue or unstable expression is queued for a returning visitor.
                activityEnded(storage);
                reply(storage, null);
                if (runtime.externalSpeech() == null) endEncounter(storage, events.toList().size());
                return;
            }
            boolean resumed = storage.containsKey(ABSENT_SINCE);
            remove(storage, ABSENT_SINCE);
            if (runtime.externalSpeech() == null) {
                reception(storage); remove(storage, QUIET); remove(storage, LAST_INPUT); return;
            }
            String current = Integer.toString(count);
            if (resumed) { put(storage, GROUP_BASE, current); remove(storage, GROUP_CANDIDATE); return; }
            if (gap) { put(storage, GROUP_BASE, current); remove(storage, GROUP_CANDIDATE); }
            if (!current.equals(text(storage, GROUP_CANDIDATE, ""))) cancelSource(storage, "group");
            if (!storage.containsKey(GROUP_BASE)) put(storage, GROUP_BASE, current);
            if (!stable(storage, GROUP_CANDIDATE, GROUP_SINCE, current, observed)) return;
            String previous = text(storage, GROUP_BASE, current); put(storage, GROUP_BASE, current);
            if (previous.equals(current) || "0".equals(previous) || !eligible(storage, runtime, now)) return;
            occasion(storage, "group", "Visible group count changed from " + previous + " to " + current
                    + ". Make one brief, warm comment about the changed group. No new introduction, identity claim or farewell to an empty room.", observed, now);
        } catch (RuntimeException invalid) { /* Missing/invalid sensing is not departure. */ }
    }

    static void face(Storage storage, Event event, PolicyRuntime runtime, Instant now) {
        if (runtime.externalSpeech() == null || !runtime.taskContinuation().getAsBoolean() || !present(storage, now)) return;
        try {
            var data = JsonParser.parseString(event.getPayload()).getAsJsonObject();
            Instant observed = ObservationEvidence.observed(data);
            if (!ObservationEvidence.fresh(observed, now, 15, 2) || !observed.isAfter(time(storage, FACE_AT))) return;
            if (observed.isAfter(time(storage, FACE_AT).plusSeconds(15))) {
                remove(storage, FACE_BASE); remove(storage, FACE_CANDIDATE);
            }
            put(storage, FACE_AT, observed.toString());
            if (!ObservationEvidence.usableFace(data, .75)) {
                for (String key : Set.of(FACE, FACE_BASE, FACE_CANDIDATE, FACE_SINCE)) remove(storage, key);
                cancelSource(storage, "face"); return;
            }
            String emotion = data.get("emotion").getAsString();
            if (!Set.of("happy", "neutral", "sad", "angry", "fearful", "disgusted", "surprised").contains(emotion)) return;
            if (!emotion.equals(text(storage, FACE_CANDIDATE, ""))) cancelSource(storage, "face");
            if (!emotion.equals(text(storage, FACE, "")) && !now.isBefore(time(storage, FACE_REACTED).plusSeconds(5))
                    && !"true".equals(text(storage, QUIET, ""))) {
                put(storage, FACE, emotion); put(storage, FACE_REACTED, now.toString());
                String expression = switch (emotion) { case "happy", "surprised" -> "gentleSmile"; case "neutral" -> "attentive"; default -> "thoughtful"; };
                reply(storage, GalaDinnerAction.plan(null, "NONE", expression,
                        Integer.parseInt(text(storage, COUNT, "0")) > 1 ? "toward_group" : "toward_user"));
            }
            if (!stable(storage, FACE_CANDIDATE, FACE_SINCE, emotion, observed)) return;
            String previous = text(storage, FACE_BASE, emotion); put(storage, FACE_BASE, emotion);
            if (previous.equals(emotion) || "true".equals(text(storage, CORRECTED, "")) || !eligible(storage, runtime, now)) return;
            occasion(storage, "face", "A fresh, stable facial-expression estimate changed from " + previous + " to " + emotion
                    + ". One gentle, tentative comment is permitted. This is an uncertain camera estimate, not proof of feelings or whose face it is. "
                    + "No joke about negative expressions and no personal diagnosis.", observed, now);
        } catch (RuntimeException invalid) { /* Unknown evidence authorizes no comment. */ }
    }
    private static boolean stable(Storage storage, String key, String since, String value, Instant observed) {
        if (!value.equals(text(storage, key, "")) || observed.isAfter(time(storage, since).plusSeconds(15))) {
            put(storage, key, value); put(storage, since, observed.toString()); return false;
        }
        return !observed.isBefore(time(storage, since).plusSeconds(2));
    }

    /** Reception-only observations reuse durable history for deduplication, including
     * samples received while games/conversation suppressed comments. No replay queue. */
    static void ambient(Storage storage, EventHistory history, PolicyRuntime runtime, Instant now) {
        var events = history.toList();
        if (events.isEmpty()) return;
        Event event = events.getLast(); String type = event.getType();
        boolean weather = Event.TYPE_WEATHER_CURRENT.equals(type);
        if (!weather && !Event.TYPE_HAND_SIGN.equals(type)) return;
        Instant observed = ObservationEvidence.observed(event);
        if (!ObservationEvidence.fresh(observed, now, ObservationEvidence.ttl(type), 0)) return;
        JsonObject previous = null; Instant newest = Instant.MIN;
        int from = Math.max(0, Math.min(events.size(), Integer.parseInt(text(storage, SESSION, "0"))));
        for (int i = from; i < events.size() - 1; i++) {
            Event prior = events.get(i);
            if (!type.equals(prior.getType())) continue;
            Instant at = ObservationEvidence.observed(prior);
            if (at == null || at.isAfter(now)) continue;
            if (!observed.isAfter(at)) return; // Duplicate or delayed source, regardless of receipt time.
            JsonObject facts = ambientFacts(prior, weather, at);
            if (facts != null && at.isAfter(newest)) { previous = facts; newest = at; }
        }
        JsonObject facts = ambientFacts(event, weather, now);
        if (facts == null) { cancelSource(storage, type); return; }
        if (previous != null && !meaningfulChange(previous, facts, weather)) return;
        cancelSource(storage, type); // Superseded facts cannot survive a pending provider ACK.
        if (!eligible(storage, runtime, now)) return;
        if (!weather && JsonParser.parseString(event.getPayload()).getAsJsonObject().has("captureId")) return;
        String guidance = weather
                ? "New supplied current weather (data): " + facts + ". One brief comment is permitted. Use the stated location; "
                    + "do not assume the venue, claim to see outdoors or announce a lookup."
                : "Fresh supplied hand-shape observation (data): " + facts + ". One brief tentative acknowledgment is permitted. "
                    + "Do not infer intent, identity or social meaning; no game invitation/start/result or matching robot gesture.";
        occasion(storage, type, guidance, observed, now, ObservationEvidence.ttl(type));
    }

    private static JsonObject ambientFacts(Event event, boolean weather, Instant now) {
        try {
            var data = JsonParser.parseString(event.getPayload()).getAsJsonObject();
            var facts = new JsonObject();
            if (!weather) {
                // Reuse shape/confidence and camera stability validation, without round control.
                if (GalaDinnerHandSigns.rejection(data, "", Instant.EPOCH, now, null) != null) return null;
                facts.addProperty("sign", data.get("sign").getAsString());
                return facts;
            }
            String location = data.has("location_label") ? data.get("location_label").getAsString().trim()
                    : data.get("location_name").getAsString().trim();
            if (location.isEmpty() || location.length() > 120 || location.chars().anyMatch(Character::isISOControl)) return null;
            String condition = data.get("condition").getAsString();
            if (!Set.of("clear", "cloudy", "fog", "rain", "snow", "storm").contains(condition)) return null;
            facts.addProperty("location", location); facts.addProperty("condition", condition);
            for (String key : java.util.List.of("wind", "intensity")) {
                var allowed = key.equals("wind") ? Set.of("calm", "windy") : Set.of("none", "light", "medium", "heavy");
                if (data.has(key) && allowed.contains(data.get(key).getAsString()))
                    facts.addProperty(key, data.get(key).getAsString());
            }
            if (data.has("temperature_c") && !data.get("temperature_c").isJsonNull()) {
                var value = data.get("temperature_c");
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return null;
                double temperature = value.getAsDouble();
                if (!Double.isFinite(temperature) || temperature < -100 || temperature > 70) return null;
                facts.addProperty("temperature_c", temperature);
            }
            return facts;
        } catch (RuntimeException invalid) { return null; }
    }

    private static boolean meaningfulChange(JsonObject previous, JsonObject current, boolean weather) {
        if (!weather) return !previous.equals(current);
        var before = previous.deepCopy(); var after = current.deepCopy();
        var firstTemperature = before.remove("temperature_c"); var nextTemperature = after.remove("temperature_c");
        return !before.equals(after) || (firstTemperature == null || nextTemperature == null
                ? !java.util.Objects.equals(firstTemperature, nextTemperature)
                : Math.abs(firstTemperature.getAsDouble() - nextTemperature.getAsDouble()) >= 2);
    }
    private static boolean eligible(Storage storage, PolicyRuntime runtime, Instant now) {
        return "RECEPTION".equals(mode(storage)) && runtime.externalSpeech() != null && runtime.taskContinuation().getAsBoolean()
                && present(storage, now) && !"true".equals(text(storage, QUIET, ""))
                && !now.isBefore(time(storage, LAST_INPUT).plusSeconds(8))
                && !now.isBefore(time(storage, AMBIENT_AT).plusSeconds(25))
                && !(storage.containsKey(CONTINUATION_ID) && now.isBefore(time(storage, CONTINUATION_EXPIRES)))
                && !(storage.containsKey(OUTCOME) && now.isBefore(time(storage, OUTCOME_EXPIRES)));
    }
    private static void occasion(Storage storage, String source, String facts, Instant observed, Instant now) {
        occasion(storage, source, facts, observed, now, 15);
    }
    private static void occasion(Storage storage, String source, String facts, Instant observed, Instant now, long ttl) {
        continueRequest(storage, facts); put(storage, CONTINUATION_OCCASION, "PROACTIVE"); put(storage, CONTINUATION_AMBIENT, "true");
        Instant expiry = observed.plusSeconds(ttl).isBefore(now.plusSeconds(8)) ? observed.plusSeconds(ttl) : now.plusSeconds(8);
        put(storage, CONTINUATION_EXPIRES, expiry.toString()); put(storage, AMBIENT_AT, now.toString()); put(storage, AMBIENT_SOURCE, source);
    }
}
