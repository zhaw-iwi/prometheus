package ch.zhaw.prometheus.agentdefs.tdsr.lab;

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
public class LabRpsRevealPolicy extends Policy {
    private static final String SPEECH = "Rock, scissor, paper";

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Storage storage;

    protected LabRpsRevealPolicy() {
    }

    public LabRpsRevealPolicy(Storage storage) {
        this.storage = storage;
    }

    @Override
    public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        RpsSign sign = currentAgentSign(this.storage);
        int round = currentRoundNumber(this.storage);
        return new BehaviourPlan(SPEECH, nonVerbal(), motion(sign), display(sign, round));
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
                Deterministic English SIRA Lab rock-scissor-paper reveal policy.
                Emits speech "Rock, scissor, paper", visible nonverbal state, display state,
                and a top-level motion.handSign payload.
                """.trim();
    }

    private static RpsSign currentAgentSign(Storage storage) {
        if (storage == null || !storage.containsKey(RpsStorageKeys.CURRENT_AGENT_SIGN)) {
            throw new IllegalStateException("RPS agent sign has not been selected");
        }
        return RpsSign.parse(storage.get(RpsStorageKeys.CURRENT_AGENT_SIGN).getAsString());
    }

    private static int currentRoundNumber(Storage storage) {
        if (storage == null || !storage.containsKey(RpsStorageKeys.CURRENT_ROUND_NUMBER)) {
            return 1;
        }
        return storage.get(RpsStorageKeys.CURRENT_ROUND_NUMBER).getAsInt();
    }

    private static JsonObject nonVerbal() {
        JsonObject face = new JsonObject();
        face.addProperty("type", "playfulCurious");
        face.addProperty("intensity", 0.55);

        JsonObject gaze = new JsonObject();
        gaze.addProperty("direction", "toward_user");
        gaze.addProperty("focus", "person");

        JsonObject expressiveMotion = new JsonObject();
        expressiveMotion.addProperty("stillness", 0.58);
        expressiveMotion.addProperty("energy", 0.48);

        JsonObject nonVerbal = new JsonObject();
        nonVerbal.addProperty("gesture", "ACKNOWLEDGE");
        nonVerbal.add("facialExpression", face);
        nonVerbal.add("gaze", gaze);
        nonVerbal.add("motion", expressiveMotion);
        return nonVerbal;
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
        display.addProperty("title", "Rock, Scissor, Paper");
        display.addProperty("agentSign", sign.canonical());
        display.addProperty("round", round);
        return display;
    }
}
