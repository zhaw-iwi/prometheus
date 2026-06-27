package ch.zhaw.prometheus.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import ch.zhaw.prometheus.controllers.views.PolicyResponseView;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.spi.RealtimeCallConfig;
import ch.zhaw.prometheus.spi.RealtimeCallInfo;
import ch.zhaw.prometheus.spi.RealtimeSessionClient;

@Service
public class RealtimeCallOrchestrationService {
    private final AgentApplicationService agentService;
    private final ScopedDemoService scopedDemoService;
    private final RealtimeSessionClient realtimeSessionClient;
    private final RealtimeSidebandService sidebandService;

    public RealtimeCallOrchestrationService(AgentApplicationService agentService, ScopedDemoService scopedDemoService,
            RealtimeSessionClient realtimeSessionClient, RealtimeSidebandService sidebandService) {
        this.agentService = agentService;
        this.scopedDemoService = scopedDemoService;
        this.realtimeSessionClient = realtimeSessionClient;
        this.sidebandService = sidebandService;
    }

    public Optional<RealtimeCallInfo> createCall(UUID agentId, String offerSdp, RealtimeCallSettings settings) {
        Optional<PolicyResponseView> prompt = this.agentService.prompt(agentId, OutputProfile.REALTIME_SPEECH);
        if (prompt.isEmpty()) {
            return Optional.empty();
        }
        List<Event> history = this.agentService.getAgentCurrentStateEventHistory(agentId).orElse(List.of());
        String languageCode = this.agentService.getAgentLanguageCode(agentId).orElse(null);
        return Optional.of(this.createCall(agentId, offerSdp, settings, prompt.get(), history, languageCode));
    }

    public Optional<RealtimeCallInfo> createScopedCall(String accessCode, UUID agentId, String offerSdp,
            RealtimeCallSettings settings) {
        Optional<PolicyResponseView> prompt = this.scopedDemoService.prompt(accessCode, agentId,
                OutputProfile.REALTIME_SPEECH);
        if (prompt.isEmpty()) {
            return Optional.empty();
        }
        List<Event> history = this.scopedDemoService.getAgentCurrentStateEventHistory(accessCode, agentId)
                .orElse(List.of());
        String languageCode = this.scopedDemoService.getAgentLanguageCode(accessCode, agentId).orElse(null);
        return Optional.of(this.createCall(agentId, offerSdp, settings, prompt.get(), history, languageCode));
    }

    public void closeCall(String callId) {
        this.sidebandService.close(callId);
    }

    private RealtimeCallInfo createCall(UUID agentId, String offerSdp, RealtimeCallSettings settings,
            PolicyResponseView prompt, List<Event> history, String languageCode) {
        RealtimeCallSettings resolvedSettings = settings == null
                ? new RealtimeCallSettings(null, null, true)
                : settings;
        String instructions = RealtimePromptInstructions.systemInstructions(prompt);
        RealtimeCallInfo call = this.realtimeSessionClient.createCall(offerSdp,
                new RealtimeCallConfig(instructions, resolvedSettings.getVoice(),
                        resolvedSettings.getTurnDetection(), languageCode,
                        resolvedSettings.getVadThreshold(), resolvedSettings.getVadPrefixPaddingMs(),
                        resolvedSettings.getVadSilenceDurationMs(), resolvedSettings.getVadEagerness(),
                        resolvedSettings.isVadInterruptResponse(), resolvedSettings.getInputNoiseReduction(),
                        resolvedSettings.getOutputSpeed(), resolvedSettings.getReasoningEffort(),
                        resolvedSettings.getMaxOutputTokens(),
                        resolvedSettings.isIncludeInputTranscriptionLogprobs()));
        this.sidebandService.attach(new RealtimeSidebandSessionConfig(
                call.getCallId(),
                call.getSidebandUrl(),
                agentId,
                instructions,
                SpeechTurnSelector.latestAssistantSpeechIfLatestUtterance(history).orElse(null),
                resolvedSettings));
        return call;
    }
}
