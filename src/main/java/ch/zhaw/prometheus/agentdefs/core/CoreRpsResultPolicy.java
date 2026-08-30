package ch.zhaw.prometheus.agentdefs.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.rps.CoreRpsBehaviour;
import ch.zhaw.prometheus.model.rps.RpsSign;
import ch.zhaw.prometheus.model.rps.RpsStorageKeys;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

/** Temporary legacy policy adapter over the reusable trusted RPS behaviour. */
@Entity
public class CoreRpsResultPolicy extends Policy {
    private static final Gson GSON = new Gson();

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
        JsonObject round = lastRound();
        CoreRpsBehaviour.Plan plan = CoreRpsBehaviour.result(
                RpsSign.parse(round.get("agentSign").getAsString()),
                RpsSign.parse(round.get("userSign").getAsString()),
                round.get("winner").getAsString(), round.get("round").getAsInt());
        return new BehaviourPlan(plan.speech(), GSON.toJsonTree(plan.nonVerbal()), null,
                GSON.toJsonTree(plan.display()));
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

    private JsonObject lastRound() {
        if (this.storage == null || !this.storage.containsKey(RpsStorageKeys.LAST_ROUND)) {
            throw new IllegalStateException("RPS round result is not available");
        }
        JsonElement value = this.storage.get(RpsStorageKeys.LAST_ROUND);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalStateException("RPS round result is malformed");
        }
        return value.getAsJsonObject();
    }
}
