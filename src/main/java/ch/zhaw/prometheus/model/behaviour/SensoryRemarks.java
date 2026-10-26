package ch.zhaw.prometheus.model.behaviour;

import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.LiveActivityContext;

/** Admission for brief sensor demonstrations, separate from fresh evidence and task cues. */
public final class SensoryRemarks {
    public static final String ENABLED = "sensing.remarks.enabled", LAST = "sensing.remarks.last",
            PENDING = "sensing.remarks.pending", CORRECTION = "sensing.remarks.correction";
    private SensoryRemarks() {}
    public record Change(String type, JsonObject facts, Instant observed, String identity) {}

    public static boolean enabled(Storage storage) { return storage.containsKey(ENABLED) && storage.get(ENABLED).getAsBoolean(); }
    public static void cancel(Storage storage) { if (storage.containsKey(PENDING)) storage.remove(PENDING); }
    public static void reset(Storage storage) {
        for (String key : List.of(LAST, PENDING, CORRECTION)) if (storage.containsKey(key)) storage.remove(key);
    }

    /** The crossing is admitted once. Suppressed changes never become a delayed queue. */
    public static Optional<Change> change(EventHistory history, Storage storage, Instant now, long stableMs, long quietMs, long cooldownMs) {
        var events = history.toList(); if (events.isEmpty()) return Optional.empty();
        Event latest = events.getLast(); JsonObject facts = facts(latest, now);
        if (facts == null) return Optional.empty();
        Instant at = ObservationEvidence.observed(latest), since = at, prior = null;
        JsonObject previous = null;
        // Source order, not HTTP receipt order. Limit only the search, never extend freshness.
        var readings = new ArrayList<Event>();
        for (int i = Math.max(0, events.size() - 120); i < events.size() - 1; i++) {
            var e = events.get(i);
            if (!latest.getType().equals(e.getType())) continue;
            Instant source = ObservationEvidence.observed(e);
            if (source == null || source.isAfter(now.plusSeconds(2))) continue;
            if (!source.isBefore(at)) return Optional.empty();
            readings.add(e);
        }
        readings.sort(Comparator.comparing(ObservationEvidence::observed));
        for (Event e : readings) {
            Instant source = ObservationEvidence.observed(e);
            JsonObject value = facts(e, now);
            if (value == null) { previous = null; prior = source; continue; }
            if (!value.equals(previous) || prior == null || source.isAfter(prior.plusSeconds(15))) since = source;
            previous = value; prior = source;
        }
        if (!facts.equals(previous) || prior == null || at.isAfter(prior.plusSeconds(15))) since = at;
        if (at.isBefore(since.plusMillis(stableMs))) return Optional.empty();
        if (facts.equals(previous) && prior != null && !prior.isBefore(since.plusMillis(stableMs))) return Optional.empty();
        if (Event.TYPE_SOCIAL_GROUPING.equals(latest.getType()) && facts.get("humanCount").getAsInt() == 0
                && readings.stream().noneMatch(e -> { var f = facts(e, now); return f != null && f.get("humanCount").getAsInt() > 0; })) return Optional.empty();
        if (storage.containsKey(CORRECTION) && latest.getType().equals(Event.TYPE_FACE_EMOTION)) return Optional.empty();
        Instant last = time(storage, LAST);
        if (last != null && now.isBefore(last.plusMillis(cooldownMs))) return Optional.empty();
        for (int i = events.size() - 2; i >= Math.max(0, events.size() - 40); i--) {
            Event e = events.get(i);
            if (!Event.TYPE_USER_UTTERANCE.equals(e.getType()) && !Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(e.getType())) continue;
            if (e.getCreatedDate() != null && now.isBefore(e.getCreatedDate().plusMillis(quietMs))) return Optional.empty();
            break;
        }
        return Optional.of(new Change(latest.getType(), facts, at, latest.getId() == null ? at.toString() : latest.getId().toString()));
    }

    public static JsonObject facts(Event event, Instant now) {
        try {
            if (!ObservationEvidence.fresh(ObservationEvidence.observed(event), now, 15, 2)) return null;
            JsonObject data = JsonParser.parseString(event.getPayload()).getAsJsonObject(), result = new JsonObject();
            if (Event.TYPE_FACE_EMOTION.equals(event.getType())) {
                if (data.has("facePresent") && data.get("facePresent").isJsonPrimitive()
                        && data.get("facePresent").getAsJsonPrimitive().isBoolean() && !data.get("facePresent").getAsBoolean()) {
                    result.addProperty("facePresent", false); return result;
                }
                if (!ObservationEvidence.usableFace(data, .65)) return null;
                String emotion = data.get("emotion").getAsString();
                if (!Set.of("happy", "neutral", "sad", "angry", "fearful", "disgusted", "surprised").contains(emotion)) return null;
                result.addProperty("expression", emotion);
            } else if (Event.TYPE_SOCIAL_GROUPING.equals(event.getType()) || Event.TYPE_SOCIAL_CONTEXT.equals(event.getType())) {
                var count = data.get("humanCount");
                if (count == null || !count.isJsonPrimitive() || !count.getAsJsonPrimitive().isNumber()) return null;
                int n = count.getAsBigDecimal().intValueExact(); if (n < 0 || n > 1000) return null;
                result.addProperty("humanCount", n);
                if (Event.TYPE_SOCIAL_CONTEXT.equals(event.getType())) {
                    for (String field : List.of("groupCount", "largestGroupSize")) if (data.has(field)) {
                        int value = data.get(field).getAsBigDecimal().intValueExact();
                        if (value >= 0 && value <= 1000) result.addProperty(field, value);
                    }
                    // Aggregate stable labels; tracker IDs and tiny confidence fluctuations are not social changes.
                    var signals = new TreeMap<String, Integer>();
                    if (data.has("people") && data.get("people").isJsonArray()) for (var person : data.getAsJsonArray("people")) {
                        var p = person.getAsJsonObject();
                        for (String channel : List.of("movement", "attention")) if (p.has(channel)) {
                            var signal = p.getAsJsonObject(channel); double confidence = signal.get("confidence").getAsDouble();
                            String state = signal.get("state").getAsString();
                            if (confidence >= .65 && confidence <= 1 && Set.of("stationary", "moving", "approaching", "receding", "attending", "not_attending").contains(state))
                                signals.merge(channel + ":" + state, 1, Integer::sum);
                        }
                    }
                    if (!signals.isEmpty()) result.add("signals", new Gson().toJsonTree(signals));
                }
            } else return null;
            return result;
        } catch (RuntimeException invalid) { return null; }
    }

    public static void supersede(Storage storage, Event event, Instant now) {
        if (!storage.containsKey(PENDING)) return;
        var pending = storage.get(PENDING).getAsJsonObject();
        if (!event.getType().equals(pending.get("type").getAsString())) return;
        var observed = ObservationEvidence.observed(event);
        if (observed != null && observed.isBefore(Instant.parse(pending.get("observed").getAsString()))) return;
        if (!Objects.equals(facts(event, now), pending.get("data"))) cancel(storage);
    }

    public static void attempted(Storage storage, Instant now) { storage.put(LAST, new JsonPrimitive(now.toString())); }
    public static String guidance(Change change) {
        return "Fresh sensor change (uncertain observation, not intent or private feelings): " + change.facts()
                + ". You may make one brief respectful comment. No repeated introduction, task change, personal diagnosis or demand for attention. "
                + "User corrections take precedence. Stay silent if conversation has moved on.";
    }
    public static void occasion(Storage storage, Change change, Instant now) {
        var pending = new JsonObject(); pending.addProperty("id", UUID.randomUUID().toString());
        pending.addProperty("sourceId", change.identity());
        pending.addProperty("type", change.type()); pending.add("data", change.facts().deepCopy());
        pending.addProperty("observed", change.observed().toString()); pending.addProperty("facts", guidance(change));
        pending.addProperty("expires", (now.plusSeconds(6).isBefore(change.observed().plusSeconds(15))
                ? now.plusSeconds(6) : change.observed().plusSeconds(15)).toString()); storage.put(PENDING, pending);
    }
    public static LiveActivityContext context(Storage storage, Instant now) {
        if (!storage.containsKey(PENDING)) return null;
        var p = storage.get(PENDING).getAsJsonObject(); Instant expiry = Instant.parse(p.get("expires").getAsString());
        if (!now.isBefore(expiry)) return null;
        String facts = p.get("facts").getAsString();
        return new LiveActivityContext(facts, new LiveActivityContext.Continuation(UUID.fromString(p.get("id").getAsString()),
                LiveActivityContext.Occasion.PROACTIVE, facts, expiry, true), expiry);
    }
    public static void userTurn(Storage storage, EventHistory history) {
        if (history == null || history.isEmpty()) return;
        var latest = history.toList().getLast(); if (!Event.TYPE_USER_UTTERANCE.equals(latest.getType())) return;
        cancel(storage);
        String text = latest.getPayload().toLowerCase(Locale.ROOT);
        if (text.matches("(?s).*(stop|don't|do not).*(comment|interpret|read).*(face|expression|emotion).*")
                || text.matches("(?s).*(i'm not|i am not).*(sad|angry|afraid|happy|upset|nervous).*"))
            storage.put(CORRECTION, new JsonPrimitive(true));
        if (text.matches("(?s).*(resume|continue|start).*(face|facial|expression).*") && storage.containsKey(CORRECTION)) storage.remove(CORRECTION);
    }
    private static Instant time(Storage storage, String key) {
        try { return storage.containsKey(key) ? Instant.parse(storage.get(key).getAsString()) : null; }
        catch (RuntimeException invalid) { return null; }
    }
}
