package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.SpeechProvenance;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.model.policy.LivePlaybackCue;

/** Select -> captured countdown -> client-observed playback -> reveal/capture. */
final class GalaDinnerRps {
    static final String PHASE = PREFIX + "rps_phase", INTRO = PREFIX + "rps_intro",
            INTRO_AT = PREFIX + "rps_intro_at", DEADLINE = PREFIX + "rps_intro_deadline",
            SESSION_ID = PREFIX + "rps_session", EPOCH = PREFIX + "rps_epoch", SEGMENT = PREFIX + "rps_countdown_segment",
            RECEIPTS = PREFIX + "rps_countdown_receipts", SEGMENTS = PREFIX + "rps_countdown_segments",
            LEAD_IN = PREFIX + "rps_ready_lead_in", ACK_INPUT = PREFIX + "rps_acknowledged_input";
    private GalaDinnerRps() {}

    static String countdownInstructions(boolean leadIn) {
        return leadIn
                ? "Use the current conversation language. German: Say \"Mach dich bereit.\" once as a short lead-in, pause briefly, then say Schere, Stein, Papier. "
                    + "English: say \"Get ready.\" once, pause briefly, then say Rock, Paper, Scissors (no Shoot). "
                    + "Use three steady, unhurried beats, roughly one second per word, with audible pauses. "
                    + "Continue automatically; a brief Ja/Yes/Okay is an acknowledgement, not an interruption or a new start. "
                    + "Do not answer it or restart counting. Stop/wait requests still interrupt. "
                    + "After Papier or Scissors, stay silent. Never add Ich schaue auf Ihre Hand, Okay ich schaue, I'm looking at your hand, "
                    + "a hand invitation or other post-countdown commentary."
                : "Say exactly: Schere, Stein, Papier. Only this countdown, including on replay; then listen. "
                    + "No promise, consent question, hand command or old result.";
    }

    static BehaviourPlan introduce(Storage storage, ExternalSpeech owner, Instant now) {
        return introduce(storage, owner, now, false);
    }
    static BehaviourPlan introduce(Storage storage, ExternalSpeech owner, Instant now, boolean leadIn) {
        put(storage, PHASE, "INTRO"); put(storage, INTRO, UUID.randomUUID().toString());
        put(storage, INTRO_AT, now.toString()); put(storage, DEADLINE, now.plusSeconds(30).toString());
        put(storage, SESSION_ID, owner.sessionId().toString()); put(storage, EPOCH, owner.epoch().toString());
        remove(storage, SEGMENT); remove(storage, RECEIPTS); remove(storage, SEGMENTS);
        remove(storage, ACK_INPUT);
        remove(storage, GalaDinnerCountdown.PENDING); remove(storage, CAPTURE_ID);
        boolean preparation = GalaDinnerRpsPreparation.enabled(storage);
        continueRequest(storage, preparation
                ? "RPS round confirmed: " + text(storage, GalaDinnerRounds.ID, "")
                    + ". Capture CLOSED; no result exists for this round. " + countdownInstructions(leadIn)
                : "RPS round confirmed. Your sign is selected but NOT executed. "
                + "Ask the visitor to prepare/show one hand, then count aloud ONE, TWO, THREE in their language and stop speaking. "
                + "No extra confirmation question, no naming your sign, no result. The cockpit waits for this countdown's playback before motion/capture.");
        if (preparation) put(storage, CONTINUATION_ID, text(storage, INTRO, ""));
        var response = GalaDinnerAction.plan(null, "NONE", "attentive", "toward_user");
        var display = new JsonObject(); display.addProperty("mode", "game_countdown");
        display.addProperty("title", GalaDinnerLanguage.rpsTitle(storage)); display.addProperty("phase", "spoken_countdown");
        display.addProperty("awaitingUserSign", false);
        if (preparation) display.addProperty("roundId", text(storage, GalaDinnerRounds.ID, ""));
        else display.addProperty("agentSign", text(storage, SIGN, ""));
        response.setDisplay(display);
        return response;
    }
    static boolean captured(Storage storage, Event speech, ExternalSpeech owner, Instant started, Instant now) {
        return captured(storage, speech, owner, started, now, List.of());
    }
    static boolean captured(Storage storage, Event speech, ExternalSpeech owner, Instant started, Instant now,
            List<ch.zhaw.prometheus.model.policy.LiveSpeechPart> parts) {
        if (!"INTRO".equals(text(storage, PHASE, "")) || !owned(storage, owner) || expired(storage, now)
                || storage.containsKey(EXIT_REQUEST) || started == null) return false;
        var provenance = speech.speechProvenance();
        if (provenance == null || provenance.origin() != SpeechProvenance.Origin.NATIVE
                || !owner.sessionId().equals(provenance.sessionId()) || !owner.epoch().equals(provenance.epoch())) return false;
        if (!provenance.complete()) { remove(storage, GalaDinnerCountdown.PENDING); return false; }
        String text = BehaviourPlan.fromJson(speech.getPayload()).getSpeech();
        if (text == null || text.matches(".*[\"“”„«»].*")) { remove(storage, GalaDinnerCountdown.PENDING); return false; }
        Instant boundary = time(storage, INTRO_AT).isAfter(time(storage, LAST_INPUT))
                ? time(storage, INTRO_AT) : time(storage, LAST_INPUT);
        var freshParts = parts.stream().filter(part -> part.receivedAt() != null && !part.receivedAt().isBefore(boundary)).toList();
        if (!parts.isEmpty()) text = join(freshParts);
        else if (started.isBefore(boundary)) return false;
        // Live may add a sentence after counting. The completed segment and client
        // settling still gate motion; the countdown need not be its final words.
        boolean preparation = GalaDinnerRpsPreparation.enabled(storage);
        List<String> segmentIds = List.of(provenance.segmentId());
        if (!countdown(text, preparation)) {
            if (!preparation) return false;
            var combined = GalaDinnerCountdown.append(storage, provenance.segmentId(), freshParts, boundary);
            if (combined.isEmpty()) return false;
            freshParts = combined.stream().map(GalaDinnerCountdown.Part::speech).toList();
            segmentIds = combined.stream().map(GalaDinnerCountdown.Part::segmentId).distinct().toList();
        }
        remove(storage, GalaDinnerCountdown.PENDING);
        var selected = countdownReceipts(freshParts, preparation);
        if (segmentIds.size() > 1 && selected.isEmpty()) return false;
        remove(storage, RECEIPTS);
        if (!selected.isEmpty()) {
            var ids = new JsonArray(); selected.forEach(ids::add); storage.put(RECEIPTS, ids);
        }
        remove(storage, SEGMENTS);
        if (segmentIds.size() > 1) {
            var ids = new JsonArray(); segmentIds.forEach(ids::add); storage.put(SEGMENTS, ids);
        }
        put(storage, SEGMENT, provenance.segmentId()); put(storage, PHASE, "SPOKEN");
        return true;
    }
    private static String join(List<ch.zhaw.prometheus.model.policy.LiveSpeechPart> parts) {
        return parts.stream().map(ch.zhaw.prometheus.model.policy.LiveSpeechPart::text)
                .collect(java.util.stream.Collectors.joining());
    }
    static boolean countdown(String text, boolean preparation) {
        String words = text.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{S}]+", " ").replaceAll("\\s+", " ").trim();
        // Live can append a looking-at-the-hand status despite the silence rule.
        // Accept only these harmless observed tails; questions/commands still reject.
        return preparation ? words.matches("(?:.*\\s)?(?:schere stein papier"
                    + "(?:\\s+(?:(?:okay|ok)\\s+)?ich schaue(?:\\s+auf (?:ihre|deine|eure) hand)?)?|rock paper scissors)")
                : words.matches(".*\\b(?:eins|one|1)\\s+(?:zwei|two|2)(?:\\s+(?:und|and))?\\s+(?:drei|three|3)\\b.*");
    }
    private static List<String> countdownReceipts(List<ch.zhaw.prometheus.model.policy.LiveSpeechPart> parts, boolean preparation) {
        // Find the smallest contiguous fragment span that still contains a complete countdown.
        // Fragment boundaries can split words; keep the existing language/freshness rules.
        int from = 0, to = parts.size();
        while (from + 1 < to && countdown(join(parts.subList(from + 1, to)), preparation)) from++;
        while (from + 1 < to && countdown(join(parts.subList(from, to - 1)), preparation)) to--;
        var ids = parts.subList(from, to).stream().map(ch.zhaw.prometheus.model.policy.LiveSpeechPart::receiptId).toList();
        // Older recording callers have no receipt identity and retain whole-segment evidence.
        if (ids.size() > LivePlaybackCue.MAX_RECEIPT_IDS || ids.stream().anyMatch(java.util.Objects::isNull)
                || new java.util.HashSet<>(ids).size() != ids.size()) return List.of();
        return ids;
    }
    static LivePlaybackCue cue(Storage storage, Instant now) {
        if (!"RPS".equals(mode(storage)) || !List.of("INTRO", "SPOKEN").contains(text(storage, PHASE, ""))
                || storage.containsKey(EXIT_REQUEST) || !present(storage, now)) return null;
        return new LivePlaybackCue(UUID.fromString(text(storage, INTRO, "")),
                storage.containsKey(SEGMENT) ? text(storage, SEGMENT, "") : null, time(storage, DEADLINE),
                storage.containsKey(RECEIPTS) ? storage.get(RECEIPTS).getAsJsonArray().asList().stream()
                        .map(com.google.gson.JsonElement::getAsString).toList() : List.of(), false,
                storage.containsKey(SEGMENTS) ? storage.get(SEGMENTS).getAsJsonArray().asList().stream()
                        .map(com.google.gson.JsonElement::getAsString).toList()
                        : storage.containsKey(SEGMENT) ? List.of(text(storage, SEGMENT, "")) : List.of());
    }
    static BehaviourPlan reveal(Storage storage, ExternalSpeech owner, String intro, Instant now) {
        var cue = cue(storage, now);
        if (cue == null || cue.expired(now) || cue.segmentId() == null || !cue.id().toString().equals(intro) || !owned(storage, owner)) return null;
        put(storage, PHASE, "WAITING"); remove(storage, SEGMENT); remove(storage, RECEIPTS); remove(storage, SEGMENTS);
        remove(storage, ACK_INPUT);
        GalaDinnerAction.openCapture(storage, now);
        // Same speaking occasion already counted. No second invitation or repeated countdown.
        for (String key : List.of(CONTINUATION_ID, CONTINUATION_FACTS, CONTINUATION_EXPIRES, CONTINUATION_OCCASION)) remove(storage, key);
        var response = GalaDinnerAction.plan(null, "NONE", "attentive", "toward_user");
        response.setDisplay(GalaDinnerAction.captureDisplay(storage));
        var motion = new JsonObject(); motion.addProperty("handSign", text(storage, SIGN, "")); response.setMotion(motion);
        ch.zhaw.prometheus.logging.ActivityTrace.cue("rps_waiting_for_sign", null, Event.TYPE_HAND_SIGN);
        return response;
    }
    static boolean expired(Storage storage, Instant now) {
        return "RPS".equals(mode(storage)) && List.of("INTRO", "SPOKEN").contains(text(storage, PHASE, ""))
                && !now.isBefore(time(storage, DEADLINE));
    }
    static boolean owned(Storage storage, ExternalSpeech owner) {
        return owner != null && owner.sessionId().toString().equals(text(storage, SESSION_ID, ""))
                && owner.epoch().toString().equals(text(storage, EPOCH, ""));
    }
    static boolean acknowledging(Storage storage, ExternalSpeech owner, Instant now) {
        return "RPS".equals(mode(storage)) && List.of("INTRO", "SPOKEN").contains(text(storage, PHASE, ""))
                && !storage.containsKey(EXIT_REQUEST) && owned(storage, owner) && !expired(storage, now) && present(storage, now);
    }
    static void acknowledgeInput(Storage storage, Event input) {
        if (input.sourcePosition() == null) return;
        var evidence = new JsonObject();
        evidence.addProperty("round", text(storage, INTRO, ""));
        evidence.addProperty("position", input.sourcePosition());
        storage.put(ACK_INPUT, evidence);
    }
    static boolean acknowledgedInput(Storage storage, Event input, ExternalSpeech owner, Instant now) {
        if (!acknowledging(storage, owner, now) || !storage.containsKey(ACK_INPUT) || input.sourcePosition() == null
                || !Event.TYPE_USER_UTTERANCE.equals(input.getType()) || !Event.ACTOR_USER.equals(input.getActor())) return false;
        var evidence = storage.get(ACK_INPUT).getAsJsonObject();
        return text(storage, INTRO, "").equals(evidence.get("round").getAsString())
                && input.sourcePosition() == evidence.get("position").getAsLong();
    }
    static void clear(Storage storage) {
        for (String key : List.of(PHASE, INTRO, INTRO_AT, DEADLINE, SESSION_ID, EPOCH, SEGMENT, RECEIPTS, SEGMENTS,
                GalaDinnerCountdown.PENDING, LEAD_IN, ACK_INPUT)) remove(storage, key);
    }
}
