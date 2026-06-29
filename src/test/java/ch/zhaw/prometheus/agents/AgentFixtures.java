package ch.zhaw.prometheus.agents;

import ch.zhaw.prometheus.model.Agent;

public final class AgentFixtures {
    private AgentFixtures() {
    }

    public static Agent fourStatesCircular() {
        return new ch.zhaw.prometheus.agentdefs.basic.FourStatesCircular().createAgent();
    }

    public static Agent fourStatesLinear() {
        return new ch.zhaw.prometheus.agentdefs.basic.FourStatesLinear().createAgent();
    }

    public static Agent singleStateGuessingGame() {
        return new ch.zhaw.prometheus.agentdefs.basic.SingleStateGuessingGame().createAgent();
    }

    public static Agent singleStateCoCreation() {
        return new ch.zhaw.prometheus.agentdefs.basic.SingleStateCoCreation().createAgent();
    }

    public static Agent singleStateMicroCoaching() {
        return new ch.zhaw.prometheus.agentdefs.basic.SingleStateMicroCoaching().createAgent();
    }

    public static Agent gigiTdsrGuessingGameWithGestures() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.de.GuessingGameWithGestures().createAgent();
    }

    public static Agent gigiTdsrSocialContextSensitivity() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.de.SocialContextSensitivity().createAgent();
    }

    public static Agent gigiTdsrRockScissorPaper() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.de.RockScissorPaper().createAgent();
    }

    public static Agent gigiTdsrTourConversation() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.de.TourConversation().createAgent();
    }

    public static Agent gigiTdsrTourConversationSocialContextSensitivity() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.de.TourConversationSocialContextSensitivity().createAgent();
    }
}
