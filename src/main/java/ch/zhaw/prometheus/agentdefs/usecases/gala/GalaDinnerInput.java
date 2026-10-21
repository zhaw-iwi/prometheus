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

    private static String normalize(String speech) {
        return speech.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{S}]+", " ")
                .replaceAll("\\s+", " ").trim();
    }
}
