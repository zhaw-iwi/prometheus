package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ch.zhaw.prometheus.application.AgentApplicationService;
import ch.zhaw.prometheus.application.RealtimeCallOrchestrationService;
import ch.zhaw.prometheus.application.RealtimeCallSettings;
import ch.zhaw.prometheus.spi.RealtimeCallInfo;
import ch.zhaw.prometheus.spi.RealtimeSessionClient;
import ch.zhaw.prometheus.spi.RealtimeSessionInfo;

@WebMvcTest(controllers = RealtimeController.class)
class RealtimeControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RealtimeSessionClient realtimeSessionClient;

    @MockitoBean
    private RealtimeCallOrchestrationService realtimeCallService;

    @MockitoBean
    private AgentApplicationService agentService;

    @Test
    void createsAgentBoundRealtimeCallView() throws Exception {
        UUID agentId = UUID.randomUUID();
        when(this.realtimeCallService.createCall(eq(agentId), eq("offer-sdp"), any(RealtimeCallSettings.class)))
                .thenReturn(Optional.of(new RealtimeCallInfo("answer-sdp", "gpt-realtime-2", "rtc_123",
                        "wss://example.test/v1/realtime?call_id=rtc_123")));

        this.mockMvc.perform(post("/" + agentId + "/realtime/call")
                .contentType(MediaType.valueOf("application/sdp"))
                .queryParam("voice", "marin")
                .queryParam("turnDetection", "server_vad")
                .content("offer-sdp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sdp").value("answer-sdp"))
                .andExpect(jsonPath("$.model").value("gpt-realtime-2"))
                .andExpect(jsonPath("$.callId").value("rtc_123"));
    }

    @Test
    void mapsRealtimeTuningQueryOptionsToCallSettings() throws Exception {
        UUID agentId = UUID.randomUUID();
        when(this.realtimeCallService.createCall(eq(agentId), eq("offer-sdp"), any(RealtimeCallSettings.class)))
                .thenReturn(Optional.of(new RealtimeCallInfo("answer-sdp", "gpt-realtime-2", "rtc_456",
                        "wss://example.test/v1/realtime?call_id=rtc_456")));

        this.mockMvc.perform(post("/" + agentId + "/realtime/call")
                .contentType(MediaType.valueOf("application/sdp"))
                .queryParam("voice", "Cedar")
                .queryParam("turnDetection", "server_vad")
                .queryParam("generateComplement", "false")
                .queryParam("vadThreshold", "0.7")
                .queryParam("vadPrefixPaddingMs", "140")
                .queryParam("vadSilenceDurationMs", "900")
                .queryParam("vadInterruptResponse", "true")
                .queryParam("inputNoiseReduction", "near_field")
                .queryParam("outputSpeed", "1.15")
                .queryParam("reasoningEffort", "high")
                .queryParam("maxOutputTokens", "768")
                .queryParam("includeInputTranscriptionLogprobs", "true")
                .content("offer-sdp"))
                .andExpect(status().isOk());

        ArgumentCaptor<RealtimeCallSettings> settings = ArgumentCaptor.forClass(RealtimeCallSettings.class);
        verify(this.realtimeCallService).createCall(eq(agentId), eq("offer-sdp"), settings.capture());
        assertEquals("cedar", settings.getValue().getVoice());
        assertEquals("server_vad", settings.getValue().getTurnDetection());
        assertFalse(settings.getValue().isGenerateComplement());
        assertEquals(0.7, settings.getValue().getVadThreshold(), 0.0001);
        assertEquals(140, settings.getValue().getVadPrefixPaddingMs());
        assertEquals(900, settings.getValue().getVadSilenceDurationMs());
        assertTrue(settings.getValue().isVadInterruptResponse());
        assertEquals("near_field", settings.getValue().getInputNoiseReduction());
        assertEquals(1.15, settings.getValue().getOutputSpeed(), 0.0001);
        assertEquals("high", settings.getValue().getReasoningEffort());
        assertEquals(768, settings.getValue().getMaxOutputTokens());
        assertTrue(settings.getValue().isIncludeInputTranscriptionLogprobs());
    }

    @Test
    void rejectsRealtimeCallWhenVadCreateResponseIsEnabled() throws Exception {
        UUID agentId = UUID.randomUUID();

        this.mockMvc.perform(post("/" + agentId + "/realtime/call")
                .contentType(MediaType.valueOf("application/sdp"))
                .queryParam("vadCreateResponse", "true")
                .content("offer-sdp"))
                .andExpect(status().isBadRequest());

        verify(this.realtimeCallService, never()).createCall(any(), any(), any());
    }

    @Test
    void rejectsAgentBoundRealtimeCallWithoutSdp() throws Exception {
        this.mockMvc.perform(post("/" + UUID.randomUUID() + "/realtime/call")
                .contentType(MediaType.TEXT_PLAIN)
                .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void closesRealtimeCallSideband() throws Exception {
        this.mockMvc.perform(delete("/realtime/calls/rtc_123"))
                .andExpect(status().isNoContent());
    }

    @Test
    void createsTranscriptionSessionView() throws Exception {
        when(this.realtimeSessionClient.createTranscriptionSession(null)).thenReturn(new RealtimeSessionInfo(
                "ek_transcription", "gpt-realtime-whisper", "https://example.test/v1/realtime/calls"));

        this.mockMvc.perform(post("/realtime/transcription/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").value("ek_transcription"))
                .andExpect(jsonPath("$.model").value("gpt-realtime-whisper"))
                .andExpect(jsonPath("$.realtimeCallsUrl").value("https://example.test/v1/realtime/calls"));
    }

    @Test
    void createsAgentLanguageAwareTranscriptionSessionView() throws Exception {
        UUID agentId = UUID.randomUUID();
        when(this.agentService.getAgentLanguageCode(agentId)).thenReturn(Optional.of("de"));
        when(this.realtimeSessionClient.createTranscriptionSession("de")).thenReturn(new RealtimeSessionInfo(
                "ek_transcription", "gpt-realtime-whisper", "https://example.test/v1/realtime/calls"));

        this.mockMvc.perform(post("/realtime/transcription/session")
                .queryParam("agentId", agentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").value("ek_transcription"))
                .andExpect(jsonPath("$.model").value("gpt-realtime-whisper"))
                .andExpect(jsonPath("$.realtimeCallsUrl").value("https://example.test/v1/realtime/calls"));
    }
}
