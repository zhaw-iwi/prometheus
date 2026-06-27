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

import ch.zhaw.prometheus.application.RealtimeCallOrchestrationService;
import ch.zhaw.prometheus.application.RealtimeCallSettings;
import ch.zhaw.prometheus.application.ScopedDemoService;
import ch.zhaw.prometheus.spi.RealtimeCallInfo;

@WebMvcTest(controllers = ScopedDemoController.class)
class ScopedDemoControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScopedDemoService demoService;

    @MockitoBean
    private RealtimeCallOrchestrationService realtimeCallService;

    @Test
    void mapsScopedRealtimeTuningQueryOptionsToCallSettings() throws Exception {
        UUID agentId = UUID.randomUUID();
        when(this.realtimeCallService.createScopedCall(eq("abc12"), eq(agentId), eq("offer-sdp"),
                any(RealtimeCallSettings.class)))
                .thenReturn(Optional.of(new RealtimeCallInfo("answer-sdp", "gpt-realtime-2", "rtc_scoped",
                        "wss://example.test/v1/realtime?call_id=rtc_scoped")));

        this.mockMvc.perform(post("/demo/agents/" + agentId + "/realtime/call")
                .header(ScopedDemoController.ACCESS_CODE_HEADER, "abc12")
                .contentType(MediaType.valueOf("application/sdp"))
                .queryParam("voice", "Marin")
                .queryParam("turnDetection", "semantic_vad")
                .queryParam("generateComplement", "false")
                .queryParam("vadEagerness", "low")
                .queryParam("vadInterruptResponse", "true")
                .queryParam("inputNoiseReduction", "off")
                .queryParam("outputSpeed", "0.85")
                .queryParam("reasoningEffort", "medium")
                .queryParam("maxOutputTokens", "512")
                .queryParam("includeInputTranscriptionLogprobs", "true")
                .content("offer-sdp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sdp").value("answer-sdp"))
                .andExpect(jsonPath("$.model").value("gpt-realtime-2"))
                .andExpect(jsonPath("$.callId").value("rtc_scoped"));

        ArgumentCaptor<RealtimeCallSettings> settings = ArgumentCaptor.forClass(RealtimeCallSettings.class);
        verify(this.realtimeCallService).createScopedCall(eq("abc12"), eq(agentId), eq("offer-sdp"),
                settings.capture());
        assertEquals("marin", settings.getValue().getVoice());
        assertEquals("semantic_vad", settings.getValue().getTurnDetection());
        assertFalse(settings.getValue().isGenerateComplement());
        assertEquals("low", settings.getValue().getVadEagerness());
        assertTrue(settings.getValue().isVadInterruptResponse());
        assertEquals("off", settings.getValue().getInputNoiseReduction());
        assertEquals(0.85, settings.getValue().getOutputSpeed(), 0.0001);
        assertEquals("medium", settings.getValue().getReasoningEffort());
        assertEquals(512, settings.getValue().getMaxOutputTokens());
        assertTrue(settings.getValue().isIncludeInputTranscriptionLogprobs());
    }

    @Test
    void rejectsScopedRealtimeCallWhenVadCreateResponseIsEnabled() throws Exception {
        UUID agentId = UUID.randomUUID();

        this.mockMvc.perform(post("/demo/agents/" + agentId + "/realtime/call")
                .header(ScopedDemoController.ACCESS_CODE_HEADER, "abc12")
                .contentType(MediaType.valueOf("application/sdp"))
                .queryParam("vadCreateResponse", "true")
                .content("offer-sdp"))
                .andExpect(status().isBadRequest());

        verify(this.realtimeCallService, never()).createScopedCall(any(), any(), any(), any());
    }
}
