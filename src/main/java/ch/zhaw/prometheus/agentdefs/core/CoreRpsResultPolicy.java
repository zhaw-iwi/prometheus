package ch.zhaw.prometheus.agentdefs.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.rps.RpsSign;
import ch.zhaw.prometheus.model.rps.RpsStorageKeys;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

@Entity
public class CoreRpsResultPolicy extends Policy {
    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Storage storage;

    protected CoreRpsResultPolicy() {
    }

    public CoreRpsResultPolicy(Storage storage) {
        this.storage = storage;
    }

    @Override
    public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        JsonObject round = lastRound(this.storage);
        String winner = round.get("winner").getAsString();
        String speech = speech(round, winner);
        return new BehaviourPlan(speech, nonVerbal(winner), null, display(round));
    }

    @Override
    public BehaviourPlan onRespond(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        return this.onStart(state, events, assembler, languageModelGateway);
    }

    @Override
    public String summarise(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        return null;
    }

    @Override
    public String describe() {
        return "Deterministic English Core rock-scissor-paper result policy.";
    }

    private static JsonObject lastRound(Storage storage) {
        if (storage == null || !storage.containsKey(RpsStorageKeys.LAST_ROUND)) {
            throw new IllegalStateException("RPS round result is not available");
        }
        JsonElement value = storage.get(RpsStorageKeys.LAST_ROUND);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalStateException("RPS round result is malformed");
        }
        return value.getAsJsonObject();
    }

    private static String speech(JsonObject round, String winner) {
        RpsSign agentSign = RpsSign.parse(round.get("agentSign").getAsString());
        RpsSign userSign = RpsSign.parse(round.get("userSign").getAsString());
        return switch (winner) {
            case "agent" -> "I win: " + reason(agentSign, userSign)
                    + ". My lab coat remains undefeated for twelve seconds. Again?";
            case "user" -> "You win: " + reason(userSign, agentSign)
                    + ". My digital agent dignity is lightly dented. Again?";
            case "draw" -> "A draw: we both showed " + label(agentSign)
                    + ". Very synchronized, suspiciously professional. Again?";
            default -> throw new IllegalStateException("unsupported RPS winner: " + winner);
        };
    }

    private static JsonObject nonVerbal(String winner) {
        JsonObject face = new JsonObject();
        face.addProperty("type", "user".equals(winner) ? "playfulCurious" : "gentleSmile");
        face.addProperty("intensity", "draw".equals(winner) ? 0.45 : 0.62);

        JsonObject gaze = new JsonObject();
        gaze.addProperty("direction", "toward_user");
        gaze.addProperty("focus", "person");

        JsonObject expressiveMotion = new JsonObject();
        expressiveMotion.addProperty("stillness", "draw".equals(winner) ? 0.72 : 0.62);
        expressiveMotion.addProperty("energy", "draw".equals(winner) ? 0.32 : 0.52);

        JsonObject nonVerbal = new JsonObject();
        nonVerbal.addProperty("gesture", "ACKNOWLEDGE");
        nonVerbal.add("facialExpression", face);
        nonVerbal.add("gaze", gaze);
        nonVerbal.add("motion", expressiveMotion);
        return nonVerbal;
    }

    private static JsonObject display(JsonObject round) {
        JsonObject display = new JsonObject();
        display.addProperty("mode", "game_status");
        display.addProperty("title", "Rock, Scissor, Paper");
        display.addProperty("round", round.get("round").getAsInt());
        display.addProperty("agentSign", round.get("agentSign").getAsString());
        display.addProperty("userSign", round.get("userSign").getAsString());
        display.addProperty("winner", round.get("winner").getAsString());
        display.addProperty("reason", englishReason(round));
        return display;
    }

    private static String englishReason(JsonObject round) {
        String winner = round.get("winner").getAsString();
        RpsSign agentSign = RpsSign.parse(round.get("agentSign").getAsString());
        RpsSign userSign = RpsSign.parse(round.get("userSign").getAsString());
        return switch (winner) {
            case "agent" -> reason(agentSign, userSign);
            case "user" -> reason(userSign, agentSign);
            case "draw" -> label(agentSign) + " against " + label(userSign);
            default -> throw new IllegalStateException("unsupported RPS winner: " + winner);
        };
    }

    private static String reason(RpsSign winningSign, RpsSign losingSign) {
        return label(winningSign) + " beats " + label(losingSign);
    }

    private static String label(RpsSign sign) {
        return switch (sign) {
            case ROCK -> "rock";
            case SCISSOR -> "scissor";
            case PAPER -> "paper";
        };
    }
}

