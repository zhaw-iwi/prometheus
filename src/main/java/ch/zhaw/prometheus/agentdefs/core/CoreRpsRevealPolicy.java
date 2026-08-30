package ch.zhaw.prometheus.agentdefs.core;

import com.google.gson.Gson;

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
public class CoreRpsRevealPolicy extends Policy {
    private static final Gson GSON = new Gson();

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Storage storage;

    protected CoreRpsRevealPolicy() {
    }

    public CoreRpsRevealPolicy(Storage storage) {
        this.storage = storage;
    }

    @Override
    public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        CoreRpsBehaviour.Plan plan = CoreRpsBehaviour.reveal(currentAgentSign(), currentRoundNumber());
        return new BehaviourPlan(plan.speech(), GSON.toJsonTree(plan.nonVerbal()),
                GSON.toJsonTree(plan.motion()), GSON.toJsonTree(plan.display()));
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
        return """
                Deterministic English Core rock-scissor-paper reveal policy.
                Emits speech "Rock, scissor, paper", visible nonverbal state, display state,
                and a top-level motion.handSign payload.
                """.trim();
    }

    private RpsSign currentAgentSign() {
        if (this.storage == null || !this.storage.containsKey(RpsStorageKeys.CURRENT_AGENT_SIGN)) {
            throw new IllegalStateException("RPS agent sign has not been selected");
        }
        return RpsSign.parse(this.storage.get(RpsStorageKeys.CURRENT_AGENT_SIGN).getAsString());
    }

    private int currentRoundNumber() {
        if (this.storage == null || !this.storage.containsKey(RpsStorageKeys.CURRENT_ROUND_NUMBER)) {
            return 1;
        }
        return this.storage.get(RpsStorageKeys.CURRENT_ROUND_NUMBER).getAsInt();
    }
}
