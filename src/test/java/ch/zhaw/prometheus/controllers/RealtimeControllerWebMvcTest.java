package ch.zhaw.prometheus.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ch.zhaw.prometheus.spi.RealtimeSessionClient;
import ch.zhaw.prometheus.spi.RealtimeSessionInfo;

@WebMvcTest(controllers = RealtimeController.class)
class RealtimeControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RealtimeSessionClient realtimeSessionClient;

    @Test
    void createsSpeechSessionView() throws Exception {
        when(this.realtimeSessionClient.createSession()).thenReturn(
                new RealtimeSessionInfo("ek_speech", "gpt-realtime", "https://example.test/v1/realtime/calls"));

        this.mockMvc.perform(post("/realtime/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").value("ek_speech"))
                .andExpect(jsonPath("$.model").value("gpt-realtime"))
                .andExpect(jsonPath("$.realtimeCallsUrl").value("https://example.test/v1/realtime/calls"));
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
