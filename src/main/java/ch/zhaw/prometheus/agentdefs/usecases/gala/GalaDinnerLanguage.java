package ch.zhaw.prometheus.agentdefs.usecases.gala;

import ch.zhaw.prometheus.model.Storage;

/** Definition-owned language default; independent of transient game/encounter state. */
public final class GalaDinnerLanguage {
    private static final String DEFAULT = GalaDinnerMemory.PREFIX + "default_language";
    private GalaDinnerLanguage() {}

    public static void configure(Storage storage, String code) {
        if ("en".equals(code)) GalaDinnerMemory.put(storage, DEFAULT, "en");
    }

    static boolean english(Storage storage) { return "en".equals(GalaDinnerMemory.text(storage, DEFAULT, "de")); }
    static String rpsTitle(Storage storage) { return english(storage) ? "Rock, Paper, Scissors" : "Schere, Stein, Papier"; }
    static String sign(Storage storage, String sign) {
        return english(storage) ? switch (sign) {
            case "rock" -> "rock";
            case "paper" -> "paper";
            default -> "scissors";
        } : switch (sign) {
            case "rock" -> "Stein";
            case "paper" -> "Papier";
            default -> "Schere";
        };
    }
}
