package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ch.zhaw.prometheus.controllers.views.PolicyResponseView;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.model.policy.PolicyResult;
import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.spi.RealtimeCallConfig;
import ch.zhaw.prometheus.spi.RealtimeCallInfo;
import ch.zhaw.prometheus.spi.RealtimeSessionClient;

class RealtimeCallOrchestrationServiceUnitTest {

    @Test
    void createCallInstallsPrometheusInstructionsAndAttachesSideband() {
        UUID agentId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        AgentApplicationService agentService = mock(AgentApplicationService.class);
        ScopedDemoService scopedDemoService = mock(ScopedDemoService.class);
        RealtimeSessionClient realtimeClient = mock(RealtimeSessionClient.class);
        RealtimeSidebandService sidebandService = mock(RealtimeSidebandService.class);
        RealtimeCallInfo call = new RealtimeCallInfo("answer-sdp", "gpt-realtime-2", "rtc_123",
                "wss://api.openai.com/v1/realtime?call_id=rtc_123");
        PolicyResponseView prompt = prompt(
                PromptMessage.system("PROMETHEUS system prompt."),
                PromptMessage.user("Latest user input."));
        Event previousAssistant = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                BehaviourPlan.speechOnly("Previous assistant speech.").toJson());

        when(agentService.prompt(agentId, OutputProfile.REALTIME_SPEECH)).thenReturn(Optional.of(prompt));
        when(agentService.getAgentEventHistory(agentId)).thenReturn(Optional.of(List.of(previousAssistant)));
        when(agentService.getAgentLanguageCode(agentId)).thenReturn(Optional.of("de"));
        when(realtimeClient.createCall(eq("offer-sdp"), any()))
                .thenReturn(call);

        RealtimeCallOrchestrationService service = new RealtimeCallOrchestrationService(agentService,
                scopedDemoService, realtimeClient, sidebandService);

        Optional<RealtimeCallInfo> created = service.createCall(agentId, "offer-sdp",
                new RealtimeCallSettings("Marin", "server_vad", false));

        assertTrue(created.isPresent());
        assertSame(call, created.get());
        ArgumentCaptor<RealtimeCallConfig> callConfig = ArgumentCaptor.forClass(RealtimeCallConfig.class);
        verify(realtimeClient).createCall(eq("offer-sdp"), callConfig.capture());
        assertEquals("system: PROMETHEUS system prompt.\nuser: Latest user input.",
                callConfig.getValue().getInstructions());
        assertEquals("marin", callConfig.getValue().getVoice());
        assertEquals("server_vad", callConfig.getValue().getTurnDetection());
        assertEquals("de", callConfig.getValue().getLanguageCode());

        ArgumentCaptor<RealtimeSidebandSessionConfig> sidebandConfig = ArgumentCaptor
                .forClass(RealtimeSidebandSessionConfig.class);
        verify(sidebandService).attach(sidebandConfig.capture());
        assertEquals("rtc_123", sidebandConfig.getValue().getCallId());
        assertEquals("wss://api.openai.com/v1/realtime?call_id=rtc_123",
                sidebandConfig.getValue().getSidebandUrl());
        assertEquals(agentId, sidebandConfig.getValue().getAgentId());
        assertEquals("Previous assistant speech.", sidebandConfig.getValue().getInitialExactSpeech());
        assertFalse(sidebandConfig.getValue().getSettings().isGenerateComplement());
    }

    @Test
    void createCallReturnsEmptyWhenAgentPromptIsMissing() {
        UUID agentId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        AgentApplicationService agentService = mock(AgentApplicationService.class);
        RealtimeSessionClient realtimeClient = mock(RealtimeSessionClient.class);
        RealtimeSidebandService sidebandService = mock(RealtimeSidebandService.class);
        RealtimeCallOrchestrationService service = new RealtimeCallOrchestrationService(agentService,
                mock(ScopedDemoService.class), realtimeClient, sidebandService);

        when(agentService.prompt(agentId, OutputProfile.REALTIME_SPEECH)).thenReturn(Optional.empty());

        Optional<RealtimeCallInfo> created = service.createCall(agentId, "offer-sdp",
                new RealtimeCallSettings(null, null, true));

        assertTrue(created.isEmpty());
        verify(realtimeClient, never()).createCall(any(), any());
        verify(sidebandService, never()).attach(any());
    }

    private static PolicyResponseView prompt(PromptMessage... messages) {
        State state = new State("conversation", new PromptPolicy("Respond naturally.", null,
                PromptPolicy.DEFAULT_SUMMARISE_PROMPT), List.of(), false, false);
        return new PolicyResponseView(new PolicyResult(state, List.of(messages)), true);
    }
}
