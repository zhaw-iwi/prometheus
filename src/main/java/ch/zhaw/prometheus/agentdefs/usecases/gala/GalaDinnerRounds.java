package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import com.google.gson.JsonObject;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.policy.LiveSpeech;

/** Clone-only round state. Conversation readiness, round identity and capture identity are independent. */
public final class GalaDinnerRounds {
    static final String ENABLED = "gala.lifecycle_enabled", ID = "gala.round_id", GAME = "gala.round_game",
            PHASE = "gala.round_phase", CUE = "gala.countdown_id", CUE_AT = "gala.countdown_at",
            COMPLETED = "gala.completed_round", INVITATION = "gala.replay_invitation", USER_END = "gala.user_end_ms";
    public static final String SPEECH_CUE = "sys.gala.speech_cue";
    private GalaDinnerRounds() {}
    public static void enable(Storage storage) { put(storage, ENABLED, "true"); }
    static boolean enabled(Storage storage) { return "true".equals(text(storage, ENABLED, "")); }
    static int next(Storage storage, String game) {
        String key = "gala." + game.toLowerCase(Locale.ROOT) + "_round";
        int index = Integer.parseInt(text(storage, key, "0"));
        put(storage, key, Integer.toString(index + 1));
        return index;
    }
    static void begin(Storage storage, String game) {
        if (!enabled(storage)) return;
        remove(storage, INVITATION);
        put(storage, ID, UUID.randomUUID().toString());
        put(storage, GAME, game);
        put(storage, PHASE, "PLAYING");
    }
    static void introduction(Storage storage, Instant now) {
        put(storage, MODE, "RPS_INTRO");
        put(storage, PHASE, "INTRODUCTION");
        put(storage, CUE, UUID.randomUUID().toString());
        put(storage, CUE_AT, now.toString());
        remove(storage, CAPTURE_ID);
        continueRequest(storage, "RPS round confirmed, introduction only; hand capture CLOSED. Invite the visitor to hold one sign after your countdown. "
                + "Count aloud once in German: eins, zwei, drei. Then stop speaking and wait for backend capture confirmation. "
                + "Do not name your sign, invent a result or promise exact synchronisation. No robot hand command has been issued.");
    }
    static JsonObject introductionDisplay(Storage storage) {
        var display = new JsonObject();
        display.addProperty("mode", "game_countdown");
        display.addProperty("phase", "introduction");
        display.addProperty("awaitingUserSign", false);
        display.addProperty("roundId", text(storage, ID, ""));
        var cue = new JsonObject();
        cue.addProperty("id", text(storage, CUE, ""));
        cue.addProperty("roundId", text(storage, ID, ""));
        cue.addProperty("timeoutMs", 30000);
        cue.addProperty("minimumStartMs", Long.parseLong(text(storage, USER_END, "0")));
        display.add("speechCue", cue);
        return display;
    }
    static void completed(Storage storage, String game, String facts, Instant now) {
        if (!enabled(storage)) return;
        var result = new JsonObject();
        result.addProperty("id", text(storage, ID, ""));
        result.addProperty("game", game);
        result.addProperty("facts", facts);
        result.addProperty("at", now.toString());
        storage.put(COMPLETED, result);
        remove(storage, INVITATION);
    }
    static void clearActive(Storage storage) {
        for (String key : List.of(ID, GAME, PHASE, CUE, CUE_AT)) remove(storage, key);
    }
    static void endEncounter(Storage storage) { remove(storage, COMPLETED); remove(storage, INVITATION); }
    static void newConversation(Storage storage) {
        if (!enabled(storage)) return;
        clearActive(storage); endEncounter(storage);
        remove(storage, "gala.rps_round"); remove(storage, "gala.guess_round");
        remove(storage, USER_END);
    }
    private static JsonObject completed(Storage storage, Instant now) {
        if (!storage.containsKey(COMPLETED)) return null;
        var result = storage.get(COMPLETED).getAsJsonObject();
        return now.isBefore(Instant.parse(result.get("at").getAsString()).plusSeconds(300)) ? result : null;
    }
    private static JsonObject invitation(Storage storage, Instant now) {
        if (!storage.containsKey(INVITATION)) return null;
        var invitation = storage.get(INVITATION).getAsJsonObject();
        var result = completed(storage, now);
        return result != null && result.get("id").equals(invitation.get("roundId"))
                && now.isBefore(Instant.parse(invitation.get("expires").getAsString())) ? invitation : null;
    }
    static void assistant(Storage storage, LiveSpeech speech, Instant now) {
        if (!enabled(storage) || !"RECEPTION".equals(mode(storage)) || !speech.complete()
                || speech.startMs() == null || speech.endMs() == null || speech.endMs() < speech.startMs()) return;
        var result = completed(storage, now);
        if (result == null || speech.receivedAt().toEpochMilli() < Instant.parse(result.get("at").getAsString()).toEpochMilli()) return;
        String words = normalize(speech.text());
        // Narrow invitations only. Statements, negation and quoted examples never authorize a replay.
        if (speech.text().matches(".*[\"“”„«»].*") || words.matches(".*\\b(?:nicht|kein|keine|not|never)\\b.*")
                || !words.matches(".*(?:noch (?:eine runde|einmal)|nochmal|play again|another round).*")) return;
        if (!speech.text().contains("?") && !words.matches(".*(?:möchtest|willst|wollen wir|sollen wir|would you like|do you want).*")) return;
        String game = result.get("game").getAsString();
        if ("RPS".equals(game) && words.contains("personenraten")
                || "GUESS".equals(game) && words.matches(".*(?:schere|stein|papier|rock|scissors|paper).*")) return;
        var value = new JsonObject();
        value.addProperty("id", speech.id().toString());
        value.addProperty("roundId", result.get("id").getAsString());
        value.addProperty("game", game);
        value.addProperty("endMs", speech.endMs());
        value.addProperty("receivedAt", speech.receivedAt().toString());
        value.addProperty("expires", speech.receivedAt().plusSeconds(20).toString());
        storage.put(INVITATION, value);
    }
    static LiveSpeech.Admission admission(Storage storage, LiveSpeech speech, Instant now) {
        if (!enabled(storage) || !"RECEPTION".equals(mode(storage))) return LiveSpeech.Admission.DEFAULT;
        var result = completed(storage, now);
        if (result == null) return LiveSpeech.Admission.DEFAULT;
        if (repeat(speech.text())) return LiveSpeech.Admission.ADMIT;
        if (!decision(speech.text())) return LiveSpeech.Admission.DEFAULT;
        var invitation = invitation(storage, now);
        if (invitation != null) {
            return speech.startMs() != null && speech.startMs() >= invitation.get("endMs").getAsLong()
                    && !speech.receivedAt().isBefore(Instant.parse(invitation.get("receivedAt").getAsString()))
                    ? LiveSpeech.Admission.ADMIT : LiveSpeech.Admission.REJECT;
        }
        Instant ended = Instant.parse(result.get("at").getAsString());
        return !speech.receivedAt().isBefore(ended) && speech.receivedAt().isBefore(ended.plusSeconds(8))
                && now.isBefore(speech.receivedAt().plusSeconds(5)) ? LiveSpeech.Admission.WAIT : LiveSpeech.Admission.REJECT;
    }
    /** Null leaves ordinary classification intact; WAIT consumes a decision without starting anything. */
    static String request(Storage storage, String speech, Instant now) {
        if (!enabled(storage) || !"RECEPTION".equals(mode(storage))) return null;
        var result = completed(storage, now);
        if (result == null) return null;
        if (repeat(speech)) {
            remove(storage, INVITATION); return result.get("game").getAsString();
        }
        if (decision(speech)) {
            var invitation = invitation(storage, now);
            remove(storage, INVITATION);
            return invitation != null && affirmative(speech) ? invitation.get("game").getAsString() : "WAIT";
        }
        remove(storage, INVITATION);
        return null;
    }
    private static boolean repeat(String speech) { return Set.of("noch einmal", "noch einmal bitte", "nochmal", "nochmal bitte", "noch eine runde", "again", "play again", "one more round").contains(normalize(speech)); }
    private static boolean decision(String speech) { return affirmative(speech) || Set.of("nein", "nein danke", "no", "no thanks").contains(normalize(speech)); }
    private static boolean affirmative(String speech) { return Set.of("ja", "ja bitte", "ja gerne", "ja gern", "gerne", "gern", "yes", "yes please", "sure").contains(normalize(speech)); }
    private static String normalize(String text) { return text.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{S}]+", " ").replaceAll("\\s+", " ").trim(); }
    static String facts(Storage storage, Instant now) {
        if (!enabled(storage)) return "";
        String facts = "\nRound " + text(storage, ID, "none") + "; phase " + text(storage, PHASE, "none") + ". ";
        if ("RPS_INTRO".equals(mode(storage))) facts += "Capture CLOSED; await the current countdown playback cue. Never invite a new round or claim a result. ";
        if ("RECEPTION".equals(mode(storage))) {
            var result = completed(storage, now);
            if (result != null) facts += "Completed " + result + ". Announce the result once, then listen. Do not routinely ask for another round. "
                    + "Replay requires backend confirmation; a bare yes needs an identifiable fresh replay invitation. ";
        }
        return facts;
    }
}
