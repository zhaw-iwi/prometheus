package ch.zhaw.prometheus.agentdefs.core;

import com.google.gson.JsonObject;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.rps.RpsMatchScore;
import ch.zhaw.prometheus.model.rps.RpsStorageKeys;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

@Entity
public class CoreRpsMatchRoundResultPolicy extends Policy {
    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Storage storage;

    protected CoreRpsMatchRoundResultPolicy() {
    }

    public CoreRpsMatchRoundResultPolicy(Storage storage) {
        this.storage = storage;
    }

    @Override
    public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        BehaviourPlan base = new CoreRpsResultPolicy(this.storage)
                .onStart(state, events, assembler, languageModelGateway);
        JsonObject round = lastRound();
        RpsMatchScore score = RpsMatchScore.from(this.storage);
        int targetWins = targetWins();
        String winner = round.get("winner").getAsString();
        String reason = base.getDisplay().getAsJsonObject().get("reason").getAsString();
        String speech = englishSpeech(winner, reason, score, targetWins);

        JsonObject display = base.getDisplay().getAsJsonObject().deepCopy();
        display.addProperty("agentScore", score.agentWins());
        display.addProperty("userScore", score.userWins());
        display.addProperty("targetWins", targetWins);
        return new BehaviourPlan(speech, base.getNonVerbal(), base.getMotion(), display);
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
        return "Deterministic English Core rock-scissor-paper match round-result policy.";
    }

    private static String englishSpeech(String winner, String reason, RpsMatchScore score, int targetWins) {
        String roundResult = switch (winner) {
            case "agent" -> "I win this round: " + reason + ".";
            case "user" -> "You win this round: " + reason + ".";
            case "draw" -> "This round is a draw.";
            default -> throw new IllegalStateException("unsupported RPS winner: " + winner);
        };
        return roundResult + " The score is you " + score.userWins() + ", me " + score.agentWins()
                + "; first to " + targetWins + " wins. Say ready for the next round.";
    }

    private JsonObject lastRound() {
        if (this.storage == null || !this.storage.containsKey(RpsStorageKeys.LAST_ROUND)
                || !this.storage.get(RpsStorageKeys.LAST_ROUND).isJsonObject()) {
            throw new IllegalStateException("RPS round result is not available");
        }
        return this.storage.get(RpsStorageKeys.LAST_ROUND).getAsJsonObject();
    }

    private int targetWins() {
        if (this.storage == null || !this.storage.containsKey(RpsStorageKeys.TARGET_WINS)) {
            throw new IllegalStateException("RPS target wins is not available");
        }
        return this.storage.get(RpsStorageKeys.TARGET_WINS).getAsInt();
    }
}
