package ch.zhaw.prometheus.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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

    @Test
    void createsAgentBoundRealtimeCallView() throws Exception {
        UUID agentId = UUID.randomUUID();
        when(this.realtimeCallService.createCall(org.mockito.ArgumentMatchers.eq(agentId),
                org.mockito.ArgumentMatchers.eq("offer-sdp"),
                org.mockito.ArgumentMatchers.any(RealtimeCallSettings.class)))
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
        when(this.realtimeSessionClient.createTranscriptionSession()).thenReturn(new RealtimeSessionInfo(
                "ek_transcription", "gpt-realtime-whisper", "https://example.test/v1/realtime/calls"));

        this.mockMvc.perform(post("/realtime/transcription/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").value("ek_transcription"))
                .andExpect(jsonPath("$.model").value("gpt-realtime-whisper"))
                .andExpect(jsonPath("$.realtimeCallsUrl").value("https://example.test/v1/realtime/calls"));
    }
}
