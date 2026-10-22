package ch.zhaw.prometheus.agentdefs.usecases.gala;

import java.util.Locale;
import java.util.regex.Pattern;

/** Narrow, deterministic handling of non-speech and unspecified game requests. */
final class GalaDinnerInput {
    private static final Pattern SOUND = Pattern.compile(
            "\\[(?:laugh(?:ter|ing)?|chuckl(?:e|ing)|cough(?:ing)?|sigh(?:ing)?|breath(?:ing)?"
            + "|noise|background noise|inaudible|unintelligible|lachen|lacht|husten|seufzen)\\]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PLAY = Pattern.compile(
            "(?:(?:okay|ok|ja|also|bitte|die) )*(?:"
            + "(?:können|koennen|wollen|sollen) wir (?:bitte )?(?:(?:etwas|was|ein(?: kleines)? spiel) )?spielen"
            + "|(?:ich möchte|ich moechte|ich will|wir möchten|wir moechten|lass uns) (?:bitte )?(?:(?:etwas|was|ein(?: kleines)? spiel) )?spielen"
            + "|was (?:können|koennen) wir spielen"
            + "|(?:etwas |was |ein spiel )?spielen(?: wir)?"
            + "|(?:can|could|shall) we (?:please )?play(?: something| a(?: little)? game)?"
            + "|let s play(?: something| a game)?"
            + ")(?: bitte)?");

    private GalaDinnerInput() {}

    static String speech(String transcript) {
        String speech = SOUND.matcher(transcript).replaceAll(" ").replaceAll("\\s+", " ").trim();
        String normalized = normalize(speech);
        return normalized.isEmpty() || normalized.matches("(?:(?:ha|he|hi) *){2,}") ? "" : speech;
    }

    static boolean unspecifiedGame(String speech) { return PLAY.matcher(normalize(speech)).matches(); }

    static boolean contextualFollowUp(String speech) {
        return java.util.Set.of("und jetzt", "und nun", "and now", "what about now").contains(normalize(speech));
    }

    static boolean acknowledgement(String speech) {
        // Whole utterances only: an acknowledgement prefix cannot hide a subsequent request.
        return java.util.Set.of("ja gerne", "ja gern", "gerne", "gern", "ok", "okay", "alles klar",
                "verstanden", "danke", "vielen dank", "gut", "yes please", "sure", "all right",
                "got it", "thanks", "thank you").contains(normalize(speech));
    }

    static boolean nonMutatingAcknowledgement(ch.zhaw.prometheus.model.Storage storage, String speech, java.time.Instant now) {
        if (storage.containsKey(GalaDinnerMemory.EXIT_REQUEST)) return false;
        if (!acknowledgement(speech)) return false;
        String mode = GalaDinnerMemory.mode(storage);
        if (java.util.Set.of("GUESS", "RPS").contains(mode)) return true;
        // An affirmative acceptance can still select the next game/task. Only neutral
        // receipt/thanks forms qualify after a completed round in reception.
        if (!"RECEPTION".equals(mode) || !java.util.Set.of("ok", "okay", "alles klar", "verstanden",
                "danke", "vielen dank", "thanks", "thank you", "got it").contains(normalize(speech))) return false;
        return !GalaDinnerMemory.recentOutcome(storage, now).isBlank()
                || now.isBefore(GalaDinnerMemory.time(storage, GalaDinnerMemory.CONTINUATION_EXPIRES))
                && GalaDinnerMemory.text(storage, GalaDinnerMemory.CONTINUATION_FACTS, "").startsWith("RPS round completed.");
    }

    static boolean orderedCueSequence(String speech) {
        String value = normalize(speech);
        String hand = "(?:stein|schere|papier|rock|scissors?|paper)";
        String face = "(?:fröhlich|glücklich|traurig|lächel\\p{L}*|happy|sad|smil\\p{L}*)";
        String order = ".*\\b(?:zuerst|erst|first)\\b.*\\b";
        String then = "\\b.*\\b(?:dann|danach|then)\\b.*\\b";
        return value.matches(order + hand + then + face + "\\b.*")
                || value.matches(order + face + then + hand + "\\b.*");
    }

    static String exitConfirmation(String speech) {
        String value = normalize(speech);
        if (java.util.Set.of("ja", "ja bitte", "ja gerne", "ja gern", "yes", "yes please", "sure",
                "beenden", "spiel beenden", "end the game").contains(value)) return "EXIT_GAME";
        if (java.util.Set.of("nein", "nein danke", "no", "no thanks", "weiterspielen", "weiter spielen",
                "lass uns weiterspielen", "keep playing", "continue playing").contains(value)) return "KEEP_GAME";
        return null; // Okay, quoted answers and mixed requests need contextual classification.
    }

    static boolean explicitReferenceQuestion(String speech) {
        // Whole, unambiguous institution/programme questions only. "Does the person work at ZHAW?"
        // and pronoun-based guesses deliberately remain with ordinary game interpretation.
        String value = normalize(speech);
        return value.matches("(?:was (?:macht|ist|bedeutet) (?:das |die |der )?|what is (?:the )?)"
                + "(?:iwi|sml|sira(?: lab)?|institut für wirtschaftsinformatik|school of management and law|home of innovation)")
                || value.matches("wann (?:beginnt|startet) (?:das |der )?(?:essen|dinner|gala dinner|hauptgang|programm)(?: heute(?: abend)?)?");
    }

    static boolean giveUpWithoutReveal(String speech) {
        if (speech.matches(".*[\"“”„«»].*")) return false;
        String value = normalize(speech);
        return value.matches("(?:ich gebe auf|ich geb auf) (?:aber |bitte )?(?:verrate?|nenne?|sage?) (?:mir )?(?:bitte )?"
                + "(?:den namen|die person) (?:bitte )?nicht")
                || value.matches("i give up (?:but )?(?:do not|don t|never) (?:reveal|tell me) (?:the name|the person)");
    }

    static boolean revealRequest(String speech) {
        // A model-selected operation is not enough to reveal on quotes, negation or hypotheticals.
        String value = speech.toLowerCase(Locale.ROOT).trim();
        if (value.matches(".*[\"“”„«»].*")) return false;
        value = value.replaceFirst("^(?:ok(?:ay)?|alles klar|gut)[,;:]\\s*", "");
        if (value.matches("(?:ich gebe auf|ich geb auf|i give up)[, ]+(?:wenn|falls|if)\\b.*")) return false;
        if (value.matches("(?:ich gebe auf|ich geb auf|i give up)(?:[,.!?].*)?"))
            return !value.matches(".*\\b(?:nicht|niemals|never|not)\\b.*(?:nam|person|nenn|verrat|sag|reveal|tell).*" )
                    && !value.matches(".*(?:nenn|verrat|sag|reveal|tell).*\\b(?:nicht|niemals|never|not)\\b.*")
                    && !value.matches(".*(?:doch nicht|lieber nicht|ohne.*(?:nam|person|nenn|verrat)|don't reveal|do not reveal).*" );
        if (value.matches(".*\\b(nicht|kein|keine|keinen|niemals|not|never)\\b.*")) return false;
        if (value.matches(".*\\b(wenn|falls|if)\\b.*")) return false;
        return value.matches("(?:bitte )?(?:verrat|verrate|sag|sage|nenn|nenne)(?: mir| uns)?(?: bitte)?[, ]+"
                        + "(?:die person|den namen|wer (?:es|das) (?:ist|war)|an wen du denkst).*")
                || value.matches("(?:please )?(?:tell me|reveal) (?:the person|the name|who it (?:is|was))[.!?]*");
    }

    private static String normalize(String speech) {
        return speech.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{S}]+", " ")
                .replaceAll("\\s+", " ").trim();
    }
}
