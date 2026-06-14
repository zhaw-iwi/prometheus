package ch.zhaw.prometheus.application;

import java.util.UUID;

public class RealtimeSidebandSessionConfig {
    private final String callId;
    private final String sidebandUrl;
    private final UUID agentId;
    private final String initialInstructions;
    private final String initialExactSpeech;
    private final RealtimeCallSettings settings;

    public RealtimeSidebandSessionConfig(String callId, String sidebandUrl, UUID agentId, String initialInstructions,
            String initialExactSpeech, RealtimeCallSettings settings) {
        this.callId = callId;
        this.sidebandUrl = sidebandUrl;
        this.agentId = agentId;
        this.initialInstructions = initialInstructions;
        this.initialExactSpeech = initialExactSpeech;
        this.settings = settings;
    }

    public String getCallId() {
        return this.callId;
    }

    public String getSidebandUrl() {
        return this.sidebandUrl;
    }

    public UUID getAgentId() {
        return this.agentId;
    }

    public String getInitialInstructions() {
        return this.initialInstructions;
    }

    public String getInitialExactSpeech() {
        return this.initialExactSpeech;
    }

    public RealtimeCallSettings getSettings() {
        return this.settings;
    }
}
