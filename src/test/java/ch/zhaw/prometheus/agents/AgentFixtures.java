package ch.zhaw.prometheus.agents;

import ch.zhaw.prometheus.model.Agent;

public final class AgentFixtures {
    private AgentFixtures() {
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
