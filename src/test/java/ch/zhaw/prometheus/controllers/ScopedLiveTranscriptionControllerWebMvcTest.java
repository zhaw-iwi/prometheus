package ch.zhaw.prometheus.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ch.zhaw.prometheus.application.DemoAccessDeniedException;
import ch.zhaw.prometheus.application.ScopedLiveTranscriptionService;
import ch.zhaw.prometheus.controllers.views.LiveTranscriptionCapabilitiesView;
import ch.zhaw.prometheus.controllers.views.LiveTranscriptionEffectiveSettingsView;
import ch.zhaw.prometheus.controllers.views.LiveTranscriptionSessionView;
import ch.zhaw.prometheus.spi.LiveTranscriptionProviderException;

@WebMvcTest(controllers = ScopedLiveTranscriptionController.class)
class ScopedLiveTranscriptionControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScopedLiveTranscriptionService transcriptionService;

    @Test
    void capabilitiesUseScopedAgentAndReturnTypedDescriptor() throws Exception {
        UUID agentId = UUID.randomUUID();
        LiveTranscriptionCapabilitiesView descriptor = new LiveTranscriptionCapabilitiesView(
                1, "transcription", "gpt-live-transcribe",
                new LiveTranscriptionCapabilitiesView.Capabilities(false, true),
                List.of(new LiveTranscriptionCapabilitiesView.Setting(
                        "noiseReduction", "select", "far_field", List.of("near_field", "far_field", "off"),
                        null, null, null, null, null, null, null, "live-input-boundary", null, false)));
        when(this.transcriptionService.capabilities("ABCDE", agentId)).thenReturn(Optional.of(descriptor));

        this.mockMvc.perform(get("/demo/agents/" + agentId + "/transcription/capabilities")
                .header(ScopedDemoController.ACCESS_CODE_HEADER, "ABCDE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.sessionType").value("transcription"))
                .andExpect(jsonPath("$.model").value("gpt-live-transcribe"))
                .andExpect(jsonPath("$.capabilities.assistantOutput").value(false))
                .andExpect(jsonPath("$.settings[0].defaultValue").value("far_field"));
    }

    @Test
    void sessionMapsTypedSettingsAndQueryAccessCode() throws Exception {
        UUID agentId = UUID.randomUUID();
        LiveTranscriptionSessionView view = new LiveTranscriptionSessionView(
                "ek_test", "transcription", "gpt-live-transcribe", 1,
                "https://example.test/v1/realtime/calls",
                new LiveTranscriptionEffectiveSettingsView(
                        new LiveTranscriptionEffectiveSettingsView.TurnDetection("local_vad", 1.5),
                        "far_field", true, 1, List.of("ar", "de"), "medium"));
        when(this.transcriptionService.createSession(eq("QUERY"), eq(agentId), any()))
                .thenReturn(Optional.of(view));

        this.mockMvc.perform(post("/demo/agents/" + agentId + "/transcription/session")
                .header(ScopedDemoController.ACCESS_CODE_HEADER, "HEADER")
                .queryParam("accessCode", "QUERY")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "turnDetection":{"type":"local_vad","silenceDurationSeconds":1.5},
                          "noiseReduction":"far_field",
                          "transcriptionPrompt":"PROMETHEUS meeting",
                          "transcriptionKeywords":["Valerian"],
                          "languages":["ar","de"],
                          "transcriptionDelay":"medium"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").value("ek_test"))
                .andExpect(jsonPath("$.settingsSchemaVersion").value(1))
                .andExpect(jsonPath("$.effectiveSettings.transcriptionPromptConfigured").value(true))
                .andExpect(jsonPath("$.effectiveSettings.transcriptionKeywordCount").value(1))
                .andExpect(jsonPath("$.effectiveSettings.languages[0]").value("ar"))
                .andExpect(jsonPath("$.effectiveSettings.languages[1]").value("de"));
    }

    @Test
    void missingBodyUnknownFieldsAndUnknownEnumAreRejectedBeforeIssuance() throws Exception {
        UUID agentId = UUID.randomUUID();
        this.mockMvc.perform(post("/demo/agents/" + agentId + "/transcription/session")
                .header(ScopedDemoController.ACCESS_CODE_HEADER, "ABCDE"))
                .andExpect(status().isBadRequest());
        this.mockMvc.perform(post("/demo/agents/" + agentId + "/transcription/session")
                .header(ScopedDemoController.ACCESS_CODE_HEADER, "ABCDE")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"model\":\"caller-selected\"}"))
                .andExpect(status().isBadRequest());
        this.mockMvc.perform(post("/demo/agents/" + agentId + "/transcription/session")
                .header(ScopedDemoController.ACCESS_CODE_HEADER, "ABCDE")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"noiseReduction\":\"unknown\"}"))
                .andExpect(status().isBadRequest());

        verify(this.transcriptionService, never()).createSession(any(), any(), any());
    }

    @Test
    void invisibleAgentUnauthorizedCodeAndProviderFailureHaveStableStatuses() throws Exception {
        UUID invisible = UUID.randomUUID();
        UUID unauthorized = UUID.randomUUID();
        UUID providerFailure = UUID.randomUUID();
        when(this.transcriptionService.createSession(eq("ABCDE"), eq(invisible), any()))
                .thenReturn(Optional.empty());
        when(this.transcriptionService.createSession(eq("ABCDE"), eq(unauthorized), any()))
                .thenThrow(new DemoAccessDeniedException());
        when(this.transcriptionService.createSession(eq("ABCDE"), eq(providerFailure), any()))
                .thenThrow(new LiveTranscriptionProviderException("provider-private-sentinel"));

        this.mockMvc.perform(validRequest(invisible)).andExpect(status().isNotFound());
        this.mockMvc.perform(validRequest(unauthorized)).andExpect(status().isUnauthorized());
        this.mockMvc.perform(validRequest(providerFailure)).andExpect(status().isBadGateway());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validRequest(
            UUID agentId) {
        return post("/demo/agents/" + agentId + "/transcription/session")
                .header(ScopedDemoController.ACCESS_CODE_HEADER, "ABCDE")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
    }
}
