package ch.zhaw.prometheus.agentdefs.core;

import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.rps.*;

/** One persisted round owns selection, speech preparation, capture and its immutable result. */
final class CoreRpsRound {
    static final String KEY = "core.rps.round", REPLY = "core.rps.reply", COUNT = "core.rps.completed",
            COUNTDOWN = "core.rps.liveCountdown", PLAYED = "sys.core.rps.countdown_played", ENDED = "sys.core.rps.session_ended";
    private CoreRpsRound() {}
    static JsonObject round(Storage storage) { return storage.containsKey(KEY) ? storage.get(KEY).getAsJsonObject().deepCopy() : new JsonObject(); }
    static String text(JsonObject data, String key) { return data.has(key) ? data.get(key).getAsString() : ""; }
    static String phase(Storage storage) { return text(round(storage), "phase"); }
    static boolean live(JsonObject round) { return round.has("live") && round.get("live").getAsBoolean(); }
    static void start(Storage storage, ExternalSpeech owner, Instant now) {
        int completed = storage.containsKey(COUNT) ? storage.get(COUNT).getAsInt() : 0;
        var sign = new DeterministicRpsSignSelector().selectForNextRound(completed);
        var round = new JsonObject(); round.addProperty("id", UUID.randomUUID().toString());
        round.addProperty("phase", "WAITING"); round.addProperty("number", completed + 1);
        round.addProperty("agentSign", sign.canonical()); round.addProperty("live", owner != null);
        if (owner != null) { round.addProperty("session", owner.sessionId().toString()); round.addProperty("epoch", owner.epoch().toString()); }
        round.addProperty("started", now.toString());
        if (owner != null && storage.containsKey(COUNTDOWN) && storage.get(COUNTDOWN).getAsBoolean()) {
            round.addProperty("phase", "INTRO"); round.addProperty("deadline", now.plusSeconds(30).toString());
        } else round.addProperty("opened", now.toString());
        storage.put(KEY, round); storage.put(RpsStorageKeys.CURRENT_AGENT_SIGN, new JsonPrimitive(sign.canonical()));
        storage.put(RpsStorageKeys.CURRENT_ROUND_NUMBER, new JsonPrimitive(completed + 1));
        remove(storage, RpsStorageKeys.LAST_ROUND); remove(storage, REPLY);
    }
    static boolean owned(JsonObject round, ExternalSpeech owner) {
        return !round.has("session") || owner != null && owner.sessionId().toString().equals(text(round, "session"))
                && owner.epoch().toString().equals(text(round, "epoch"));
    }
    static boolean expired(Storage storage, Instant now) {
        var r = round(storage); return Set.of("INTRO", "SPOKEN").contains(text(r, "phase"))
                && !now.isBefore(Instant.parse(text(r, "deadline")));
    }
    static boolean captured(Storage storage, Event speech, ExternalSpeech owner, Instant started, Instant now, List<LiveSpeechPart> parts) {
        var r = round(storage); var p = speech.speechProvenance();
        if (!"INTRO".equals(text(r, "phase")) || !owned(r, owner) || expired(storage, now) || owner == null || p == null
                || p.origin() != SpeechProvenance.Origin.NATIVE || !p.complete() || p.segmentId() == null
                || !owner.sessionId().equals(p.sessionId()) || !owner.epoch().equals(p.epoch())) return false;
        Instant boundary = Instant.parse(text(r, "started"));
        String words = BehaviourPlan.fromJson(speech.getPayload()).getSpeech();
        if (!parts.isEmpty()) words = parts.stream().filter(part -> part.receivedAt() != null && !part.receivedAt().isBefore(boundary))
                .map(LiveSpeechPart::text).collect(java.util.stream.Collectors.joining());
        else if (started == null || started.isBefore(boundary)) return false;
        if (words == null || words.matches(".*[\"“”„«»].*")) return false;
        words = words.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{S}]+", " ").replaceAll("\\s+", " ").trim();
        if (!words.matches(".*\\b(one|1)\\s+(two|2)(\\s+and)?\\s+(three|3)\\b.*")) return false;
        r.addProperty("segment", p.segmentId()); r.addProperty("phase", "SPOKEN"); storage.put(KEY, r); return true;
    }
    static LivePlaybackCue cue(Storage storage, Instant now) {
        var r = round(storage);
        if (!Set.of("INTRO", "SPOKEN").contains(text(r, "phase")) || expired(storage, now)) return null;
        return new LivePlaybackCue(UUID.fromString(text(r, "id")), r.has("segment") ? text(r, "segment") : null,
                Instant.parse(text(r, "deadline")));
    }
    static boolean canOpen(Storage storage, String cueId, ExternalSpeech owner, Instant now) {
        var cue = cue(storage, now); return cue != null && cue.segmentId() != null
                && cue.id().toString().equals(cueId) && owned(round(storage), owner);
    }
    static void open(Storage storage, Instant now) {
        var r = round(storage); r.addProperty("phase", "WAITING"); r.addProperty("opened", now.toString()); r.remove("segment"); storage.put(KEY, r);
    }
    static String rejection(Storage storage, EventHistory history, ExternalSpeech owner, Instant now) {
        var r = round(storage);
        if (!"WAITING".equals(text(r, "phase"))) return "rps_not_ready";
        if (!owned(r, owner)) return "rps_wrong_capture";
        if (history.isEmpty() || !Event.TYPE_HAND_SIGN.equals(history.toList().getLast().getType())) return "rps_invalid_sign";
        try {
            var data = JsonParser.parseString(history.toList().getLast().getPayload()).getAsJsonObject();
            if (!data.has("captureId") || !data.get("captureId").isJsonPrimitive()
                    || !data.get("captureId").getAsJsonPrimitive().isString() || !text(r, "id").equals(text(data, "captureId"))) return "rps_wrong_capture";
            Instant at = ObservationEvidence.observed(data), opened = Instant.parse(text(r, "opened"));
            if (!ObservationEvidence.fresh(at, now, 15, 2) || at.isBefore(opened)) return "rps_stale_sign";
            var events = history.toList();
            for (int i = events.size() - 2; i >= 0; i--) {
                var event = events.get(i); if (!Event.TYPE_HAND_SIGN.equals(event.getType())) continue;
                try {
                    var prior = JsonParser.parseString(event.getPayload()).getAsJsonObject();
                    var time = ObservationEvidence.observed(prior);
                    if (text(r, "id").equals(text(prior, "captureId")) && ObservationEvidence.fresh(time, now, 15, 2)
                            && time.isAfter(at)) return "rps_out_of_order";
                } catch (RuntimeException invalid) { /* A malformed older event is not evidence. */ }
            }
            if (!data.has("sign") || !data.get("sign").isJsonPrimitive() || !data.get("sign").getAsJsonPrimitive().isString()
                    || !Set.of("rock", "paper", "scissor").contains(text(data, "sign"))) return "rps_invalid_sign";
            var confidence = data.get("confidence");
            if (confidence == null || !confidence.isJsonPrimitive() || !confidence.getAsJsonPrimitive().isNumber()
                    || !Double.isFinite(confidence.getAsDouble()) || confidence.getAsDouble() < .7 || confidence.getAsDouble() > 1) return "rps_low_confidence";
            if ("valerian.hand.camera".equals(text(data, "source"))) {
                var frames = data.get("stabilityFrames");
                if (frames == null || !frames.isJsonPrimitive() || !frames.getAsJsonPrimitive().isNumber()
                        || frames.getAsDouble() < 3 || frames.getAsDouble() != frames.getAsInt()) return "rps_unstable_sign";
            }
            return null;
        } catch (RuntimeException invalid) { return "rps_invalid_sign"; }
    }
    static void result(Storage storage, EventHistory events, PolicyRuntime runtime, Instant now) {
        if (rejection(storage, events, runtime.externalSpeech(), now) != null) return;
        new RpsEvaluateRoundAction(storage).execute(events, runtime);
        var r = round(storage); r.addProperty("phase", "RESULT"); r.addProperty("live", runtime.externalSpeech() != null);
        r.addProperty("resultAt", now.toString()); storage.put(KEY, r);
        storage.put(COUNT, r.get("number"));
        var history = storage.get(RpsStorageKeys.ROUNDS).getAsJsonArray().deepCopy();
        while (history.size() > 16) history.remove(0);
        storage.put(RpsStorageKeys.ROUNDS, history);
    }
    static void close(Storage storage, String phase, ExternalSpeech owner, Instant now) {
        var r = round(storage); if (!r.has("id")) r.addProperty("id", UUID.randomUUID().toString());
        r.addProperty("phase", phase); r.addProperty("live", owner != null || live(r)); r.addProperty("resultAt", now.toString());
        r.remove("segment"); r.remove("opened"); storage.put(KEY, r); remove(storage, REPLY);
    }
    static JsonObject display(Storage storage) {
        var r = round(storage); String phase = text(r, "phase"); var display = new JsonObject();
        display.addProperty("mode", "game_status"); display.addProperty("title", "Rock, Scissor, Paper");
        display.addProperty("phase", Set.of("INTRO", "SPOKEN").contains(phase) ? "spoken_countdown" : phase.toLowerCase(Locale.ROOT));
        display.addProperty("awaitingUserSign", "WAITING".equals(phase));
        if (r.has("id")) display.addProperty("captureId", text(r, "id"));
        if (r.has("agentSign")) display.addProperty("agentSign", text(r, "agentSign"));
        if (r.has("number")) display.add("round", r.get("number"));
        return display;
    }
    static void reset(Storage storage) {
        for (String key : List.of(KEY, REPLY, COUNT, RpsStorageKeys.CURRENT_AGENT_SIGN, RpsStorageKeys.CURRENT_ROUND_NUMBER,
                RpsStorageKeys.LAST_ROUND, RpsStorageKeys.ROUNDS)) remove(storage, key);
    }
    static void remove(Storage storage, String key) { if (storage.containsKey(key)) storage.remove(key); }
}
