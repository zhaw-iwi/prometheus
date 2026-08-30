package ch.zhaw.prometheus.model.rps;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Provider-free English RPS behaviour owned by the registered declarative components. */
public final class CoreRpsBehaviour {
    public static final String REVEAL_SPEECH = "Rock, scissor, paper";
    public static final String TITLE = "Rock, Scissor, Paper";

    private CoreRpsBehaviour() {
    }

    public static Plan reveal(RpsSign sign, int round) {
        if (sign == null || round < 1) {
            throw new IllegalArgumentException("RPS reveal requires a sign and positive round");
        }
        Map<String, Object> nonVerbal = map(
                "gesture", "ACKNOWLEDGE",
                "facialExpression", map("type", "playfulCurious", "intensity", 0.55),
                "gaze", map("direction", "toward_user", "focus", "person"),
                "motion", map("stillness", 0.58, "energy", 0.48));
        Map<String, Object> motion = map(
                "effector", "right_hand",
                "armPose", "present_forward",
                "handSign", sign.canonical(),
                "timing", map("synchronizeWithSpeech", REVEAL_SPEECH, "revealAt", "phrase_end"),
                "confidence", 1.0);
        Map<String, Object> display = map(
                "mode", "game_status", "title", TITLE, "agentSign", sign.canonical(), "round", round);
        return new Plan(REVEAL_SPEECH, nonVerbal, motion, display);
    }

    public static Plan result(RpsSign agentSign, RpsSign userSign, String winner, int round) {
        if (agentSign == null || userSign == null || winner == null || round < 1) {
            throw new IllegalArgumentException("RPS result requires signs, winner, and positive round");
        }
        String speech = switch (winner) {
            case "agent" -> "I win: " + englishReason(agentSign, userSign)
                    + ". My lab coat remains undefeated for twelve seconds. Again?";
            case "user" -> "You win: " + englishReason(userSign, agentSign)
                    + ". My digital agent dignity is lightly dented. Again?";
            case "draw" -> "A draw: we both showed " + label(agentSign)
                    + ". Very synchronized, suspiciously professional. Again?";
            default -> throw new IllegalArgumentException("unsupported RPS winner: " + winner);
        };
        Map<String, Object> nonVerbal = map(
                "gesture", "ACKNOWLEDGE",
                "facialExpression", map(
                        "type", "user".equals(winner) ? "playfulCurious" : "gentleSmile",
                        "intensity", "draw".equals(winner) ? 0.45 : 0.62),
                "gaze", map("direction", "toward_user", "focus", "person"),
                "motion", map(
                        "stillness", "draw".equals(winner) ? 0.72 : 0.62,
                        "energy", "draw".equals(winner) ? 0.32 : 0.52));
        String reason = switch (winner) {
            case "agent" -> englishReason(agentSign, userSign);
            case "user" -> englishReason(userSign, agentSign);
            case "draw" -> label(agentSign) + " against " + label(userSign);
            default -> throw new IllegalArgumentException("unsupported RPS winner: " + winner);
        };
        Map<String, Object> display = map(
                "mode", "game_status", "title", TITLE, "round", round,
                "agentSign", agentSign.canonical(), "userSign", userSign.canonical(),
                "winner", winner, "reason", reason);
        return new Plan(speech, nonVerbal, null, display);
    }

    private static String englishReason(RpsSign winningSign, RpsSign losingSign) {
        return label(winningSign) + " beats " + label(losingSign);
    }

    private static String label(RpsSign sign) {
        return switch (sign) {
            case ROCK -> "rock";
            case SCISSOR -> "scissor";
            case PAPER -> "paper";
        };
    }

    private static Map<String, Object> map(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("map entries must contain key/value pairs");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            values.put((String) entries[index], entries[index + 1]);
        }
        return Collections.unmodifiableMap(values);
    }

    public record Plan(String speech, Map<String, Object> nonVerbal, Map<String, Object> motion,
            Map<String, Object> display) {
    }
}
