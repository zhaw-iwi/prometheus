package ch.zhaw.prometheus.agents;

import ch.zhaw.prometheus.agents.gigitdsr.GuessingGameWithGestures;
import ch.zhaw.prometheus.agents.gigitdsr.RockScissorPaper;
import ch.zhaw.prometheus.agents.gigitdsr.SocialContextSensitivity;
import ch.zhaw.prometheus.model.Agent;

public final class AgentFixtures {
    private AgentFixtures() {
    }

    public static Agent fourStatesCircular() {
        return FourStatesCircular.createAgentDefinition();
    }

    public static Agent fourStatesLinear() {
        return FourStatesLinear.createAgentDefinition();
    }

    public static Agent singleStateGuessingGame() {
        return SingleStateGuessingGame.createAgentDefinition();
    }

    public static Agent singleStateCoCreation() {
        return SingleStateCoCreation.createAgentDefinition();
    }

    public static Agent singleStateMicroCoaching() {
        return SingleStateMicroCoaching.createAgentDefinition();
    }

    public static Agent gigiTdsrGuessingGameWithGestures() {
        return GuessingGameWithGestures.createAgentDefinition();
    }

    public static Agent gigiTdsrSocialContextSensitivity() {
        return SocialContextSensitivity.createAgentDefinition();
    }

    public static Agent gigiTdsrRockScissorPaper() {
        return RockScissorPaper.createAgentDefinition();
    }
}
