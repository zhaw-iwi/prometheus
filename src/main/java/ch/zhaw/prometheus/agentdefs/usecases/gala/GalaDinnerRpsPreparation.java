package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;

import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ch.zhaw.prometheus.model.Storage;

/** Distinct accepted signs in the current preparation, persisted across observations. */
public final class GalaDinnerRpsPreparation {
    private static final String ENABLED = PREFIX + "rps_preparation_enabled";
    private static final List<String> SIGNS = List.of("rock", "paper", "scissor");
    private GalaDinnerRpsPreparation() {}

    public static void enable(Storage storage) { put(storage, ENABLED, "true"); }
    public static boolean enabled(Storage storage) { return "true".equals(text(storage, ENABLED, "")); }

    static void beginConversation(Storage storage) {
        if (!enabled(storage)) return;
        for (String key : List.of(RPS_PREPARED, RPS_RECOGNISED, GalaDinnerRounds.RPS_COUNT,
                GalaDinnerRounds.GUESS_COUNT)) remove(storage, key);
        GalaDinnerRounds.clear(storage);
        for (String key : List.of(OUTCOME, OUTCOME_ID, OUTCOME_EXPIRES)) remove(storage, key);
    }

    static List<String> recognised(Storage storage) {
        return storage.containsKey(RPS_RECOGNISED)
                ? storage.get(RPS_RECOGNISED).getAsJsonArray().asList().stream().map(value -> value.getAsString()).toList()
                : List.of();
    }

    static List<String> remaining(Storage storage) {
        var recognised = recognised(storage);
        return SIGNS.stream().filter(sign -> !recognised.contains(sign)).toList();
    }

    static boolean recognise(Storage storage, String sign) {
        var recognised = recognised(storage);
        if (recognised.contains(sign)) return false;
        var signs = array(recognised);
        signs.add(sign);
        storage.put(RPS_RECOGNISED, signs);
        return true;
    }

    static String facts(Storage storage) {
        return "RPS preparation: before playing, ask the visitor to show rock, paper and scissor individually to the camera, "
                + "in any order. This short tutorial checks whether all three hand signs are recognised before playing. "
                + "All three must be recognised before the game starts. Recognised: " + recognised(storage)
                + "; still needed: " + remaining(storage) + ". No round or robot sign chosen yet; no result or countdown.";
    }

    static String introduction(Storage storage) {
        return facts(storage) + " Introduce this as a short tutorial and explain its purpose with light robot self-irony. "
                + "German example (adapt to the requested language): "
                + "\"Erst ein kurzes Tutorial – auch Roboter brauchen ein Warm-up! "
                + "Zeig mir bitte nacheinander Stein, Schere und Papier, damit wir prüfen, ob ich alle drei Handzeichen erkenne.\"";
    }

    static void display(Storage storage, JsonObject display) {
        display.addProperty("mode", "game_setup");
        display.addProperty("phase", "recognise_all_signs");
        display.add("recognisedSigns", array(recognised(storage)));
        display.add("remainingSigns", array(remaining(storage)));
        display.addProperty("instruction", "Kurzes Tutorial: Wir prüfen, ob ich alle drei Handzeichen erkenne. "
                + "Zeige sie einzeln ruhig vor der Kamera. Noch nötig: "
                + String.join(", ", remaining(storage).stream().map(sign -> switch (sign) {
                    case "rock" -> "Stein";
                    case "paper" -> "Papier";
                    default -> "Schere";
                }).toList()) + ".");
    }

    private static JsonArray array(List<String> signs) {
        var array = new JsonArray();
        signs.forEach(array::add);
        return array;
    }
}
