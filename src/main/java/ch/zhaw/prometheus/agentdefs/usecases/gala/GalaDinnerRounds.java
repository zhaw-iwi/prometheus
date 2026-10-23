package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.event.SpeechProvenance;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;

/** One current round and one bounded replay referent; neither is a spoken response. */
final class GalaDinnerRounds {
    static final String ID = PREFIX + "round_id", COMPLETED_GAME = PREFIX + "completed_game",
            COMPLETED_AT = PREFIX + "completed_at", COMPLETED_ID = PREFIX + "completed_id",
            COMPLETED_FROM = PREFIX + "completed_from";
    static final String RPS_COUNT = PREFIX + "rps_rounds", GUESS_COUNT = PREFIX + "guess_rounds";
    static final String FINISH_GUIDANCE = " Round ended; now at reception. State the result once. "
            + "Do not offer replay or another game. End there, or ask what the visitor wants to do next. "
            + "A replay needs a new backend-confirmed start.";
    private GalaDinnerRounds() {}

    static int start(Storage storage, String game) {
        String counter = "RPS".equals(game) ? RPS_COUNT : GUESS_COUNT;
        int index = Integer.parseInt(text(storage, counter, "0"));
        reception(storage);
        put(storage, counter, Integer.toString(index + 1));
        put(storage, ID, UUID.randomUUID().toString());
        return index;
    }
    static void complete(Storage storage, String game, String outcome, Instant now, int historySize) {
        String id = text(storage, ID, UUID.randomUUID().toString());
        reception(storage);
        put(storage, COMPLETED_GAME, game); put(storage, COMPLETED_ID, id);
        put(storage, COMPLETED_AT, now.toString());
        put(storage, COMPLETED_FROM, Integer.toString(historySize));
        put(storage, OUTCOME_ID, id); put(storage, OUTCOME, outcome);
        put(storage, OUTCOME_EXPIRES, now.plusSeconds(30).toString());
    }
    static void clear(Storage storage) {
        remove(storage, ID); forgetReplay(storage);
    }
    static void forgetReplay(Storage storage) {
        for (String key : List.of(COMPLETED_GAME, COMPLETED_ID, COMPLETED_AT, COMPLETED_FROM)) remove(storage, key);
    }
    static String recentGame(Storage storage, Instant now) {
        return now.isBefore(time(storage, COMPLETED_AT).plusSeconds(60))
                ? text(storage, COMPLETED_GAME, "") : "";
    }
    static boolean again(String input) {
        return Set.of("nochmal", "noch einmal", "noch eine runde", "nochmals", "again", "play again",
                "nochmal bitte", "noch einmal bitte", "one more round", "again please").contains(normalize(input));
    }
    static boolean affirmative(String input) {
        return Set.of("ja", "ja bitte", "ja gerne", "ja gern", "gerne", "yes", "yes please", "sure").contains(normalize(input));
    }
    static String replay(Storage storage, EventHistory history, ExternalSpeech owner, String input, Instant now) {
        String game = recentGame(storage, now);
        if (!"RECEPTION".equals(mode(storage)) || game.isEmpty()) return null;
        if (again(input)) return "START_" + game;
        if (!affirmative(input) || owner == null) return null;
        var events = history.toList();
        for (int i = events.size() - 1; i >= Integer.parseInt(text(storage, COMPLETED_FROM, "0")); i--) {
            Event event = events.get(i);
            // The action sees the current user event; ingress has not appended it yet.
            if (i == events.size() - 1 && Event.TYPE_USER_UTTERANCE.equals(event.getType())
                    && input.equals(event.getPayload())) continue;
            if (Event.TYPE_USER_UTTERANCE.equals(event.getType())) return null;
            if (!Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType())) continue;
            String speech = BehaviourPlan.fromJson(event.getPayload()).getSpeech();
            if (speech == null || speech.isBlank()) continue;
            var provenance = event.speechProvenance();
            if (provenance == null || provenance.origin() != SpeechProvenance.Origin.NATIVE || !provenance.complete()
                    || !owner.sessionId().equals(provenance.sessionId()) || !owner.epoch().equals(provenance.epoch())) return null;
            String words = normalize(speech);
            if (speech.matches(".*[\"“”„«»].*") || words.matches(".*\\b(oder|or|nicht|kein|keine|wenn|falls|if|not|never)\\b.*")) return null;
            boolean question = speech.contains("?") && words.matches(".*\\b(möchtest|willst|wollen|sollen|lust|would|want|shall)\\b.*")
                    && words.matches(".*\\b(nochmal|nochmals|noch einmal|noch eine runde|weiterspielen|again|another round)\\b.*");
            boolean otherGame = "RPS".equals(game) ? words.contains("raten") || words.contains("guess")
                    : words.contains("schere") || words.contains("paper");
            return question && !otherGame ? "START_" + game : null;
        }
        return null;
    }
    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{S}]+", " ").replaceAll("\\s+", " ").trim();
    }
}
