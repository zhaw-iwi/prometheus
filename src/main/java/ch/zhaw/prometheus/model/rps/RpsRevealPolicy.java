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
public class RpsRevealPolicy extends Policy {
    private static final String SPEECH = "Schere, Stein, Papier";

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Storage storage;

    protected RpsRevealPolicy() {
    }

    public RpsRevealPolicy(Storage storage) {
        this.storage = storage;
    }

    @Override
    public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        RpsSign sign = RpsStorageSupport.currentAgentSign(this.storage);
        int round = RpsStorageSupport.currentRoundNumber(this.storage);
        return new BehaviourPlan(SPEECH, null, motion(sign), display(sign, round));
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
                Deterministic Schere-Stein-Papier reveal policy.
                Emits speech "Schere, Stein, Papier" and a top-level motion.handSign payload.
                """.trim();
    }

    private static JsonObject motion(RpsSign sign) {
        JsonObject timing = new JsonObject();
        timing.addProperty("synchronizeWithSpeech", SPEECH);
        timing.addProperty("revealAt", "phrase_end");

        JsonObject motion = new JsonObject();
        motion.addProperty("effector", "right_hand");
        motion.addProperty("armPose", "present_forward");
        motion.addProperty("handSign", sign.canonical());
        motion.add("timing", timing);
        motion.addProperty("confidence", 1.0);
        return motion;
    }

    private static JsonObject display(RpsSign sign, int round) {
        JsonObject display = new JsonObject();
        display.addProperty("mode", "game_status");
        display.addProperty("title", "Schere, Stein, Papier");
        display.addProperty("agentSign", sign.canonical());
        display.addProperty("round", round);
        return display;
    }
}

