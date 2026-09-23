package ch.zhaw.prometheus.agentdefs.core;

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
public class CoreRpsRevealPolicy extends Policy {
    private static final String SPEECH = "Rock, scissor, paper—show!";

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
        // Validate that the choice was committed before inviting the user to play,
        // but keep that choice out of every emitted channel until their sign arrives.
        currentAgentSign(this.storage);
        int round = currentRoundNumber(this.storage);
        return new BehaviourPlan(SPEECH, nonVerbal(), null, display(round));
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
                Deterministic English Core rock-scissor-paper countdown policy.
                The agent sign is already committed in storage, but this policy emits only
                the countdown and capture state so the user cannot see the sign early.
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
        nonVerbal.addProperty("gesture", "NONE");
        nonVerbal.add("facialExpression", face);
        nonVerbal.add("gaze", gaze);
        nonVerbal.add("motion", expressiveMotion);
        return nonVerbal;
    }

    private static JsonObject display(int round) {
        JsonObject display = new JsonObject();
        display.addProperty("mode", "game_countdown");
        display.addProperty("title", "Rock, Scissor, Paper");
        display.addProperty("round", round);
        display.addProperty("phase", "capture_user_sign");
        display.addProperty("awaitingUserSign", true);
        return display;
    }
}
