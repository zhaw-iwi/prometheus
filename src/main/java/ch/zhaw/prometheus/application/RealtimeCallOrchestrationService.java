package ch.zhaw.prometheus.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import ch.zhaw.prometheus.controllers.views.PolicyResponseView;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
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
        List<Event> history = this.agentService.getAgentEventHistory(agentId).orElse(List.of());
        return Optional.of(this.createCall(agentId, offerSdp, settings, prompt.get(), history));
    }

    public Optional<RealtimeCallInfo> createScopedCall(String accessCode, UUID agentId, String offerSdp,
            RealtimeCallSettings settings) {
        Optional<PolicyResponseView> prompt = this.scopedDemoService.prompt(accessCode, agentId,
                OutputProfile.REALTIME_SPEECH);
        if (prompt.isEmpty()) {
            return Optional.empty();
        }
        List<Event> history = this.scopedDemoService.getAgentEventHistory(accessCode, agentId).orElse(List.of());
        return Optional.of(this.createCall(agentId, offerSdp, settings, prompt.get(), history));
    }

    public void closeCall(String callId) {
        this.sidebandService.close(callId);
    }

    private RealtimeCallInfo createCall(UUID agentId, String offerSdp, RealtimeCallSettings settings,
            PolicyResponseView prompt, List<Event> history) {
        RealtimeCallSettings resolvedSettings = settings == null
                ? new RealtimeCallSettings(null, null, true)
                : settings;
        String instructions = RealtimePromptInstructions.systemInstructions(prompt);
        RealtimeCallInfo call = this.realtimeSessionClient.createCall(offerSdp,
                new RealtimeCallConfig(instructions, resolvedSettings.getVoice(),
                        resolvedSettings.getTurnDetection()));
        this.sidebandService.attach(new RealtimeSidebandSessionConfig(
                call.getCallId(),
                call.getSidebandUrl(),
                agentId,
                instructions,
                RealtimePromptInstructions.responseInstruction(prompt),
                latestAssistantSpeech(history),
                resolvedSettings));
        return call;
    }

    private static String latestAssistantSpeech(List<Event> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        Event last = history.get(history.size() - 1);
        if (last == null || !Event.ACTOR_ASSISTANT.equals(last.getActor())
                || !Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(last.getType())) {
            return null;
        }
        BehaviourPlan plan = BehaviourPlan.fromJson(last.getPayload());
        return plan == null ? null : plan.getSpeech();
    }
}
