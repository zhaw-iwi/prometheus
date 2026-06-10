package ch.zhaw.prometheus.model.rps;

import com.google.gson.JsonObject;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

@Entity
public class RpsResultPolicy extends Policy {
    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Storage storage;

    protected RpsResultPolicy() {
    }

    public RpsResultPolicy(Storage storage) {
        this.storage = storage;
    }

    @Override
    public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        JsonObject round = RpsStorageSupport.lastRound(this.storage);
        String speech = speech(round);
        return new BehaviourPlan(speech, null, null, display(round));
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
        return "Deterministic Schere-Stein-Papier result policy.";
    }

    private static String speech(JsonObject round) {
        String winner = round.get("winner").getAsString();
        String reason = round.get("reason").getAsString();
        return switch (winner) {
            case "agent" -> "Ich gewinne diese Runde: " + reason + ". Moechtest du noch einmal spielen?";
            case "user" -> "Du gewinnst diese Runde: " + reason + ". Moechtest du noch einmal spielen?";
            case "draw" -> "Unentschieden: Wir hatten beide "
                    + RpsSign.parse(round.get("agentSign").getAsString()).germanLabel()
                    + ". Moechtest du noch einmal spielen?";
            default -> throw new IllegalStateException("unsupported RPS winner: " + winner);
        };
    }

    private static JsonObject display(JsonObject round) {
        JsonObject display = new JsonObject();
        display.addProperty("mode", "game_status");
        display.addProperty("title", "Schere, Stein, Papier");
        display.addProperty("round", round.get("round").getAsInt());
        display.addProperty("agentSign", round.get("agentSign").getAsString());
        display.addProperty("userSign", round.get("userSign").getAsString());
        display.addProperty("winner", round.get("winner").getAsString());
        display.addProperty("reason", round.get("reason").getAsString());
        return display;
    }
}
