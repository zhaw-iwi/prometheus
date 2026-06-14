package ch.zhaw.prometheus.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ch.zhaw.prometheus.application.AgentApplicationService;
import ch.zhaw.prometheus.application.RecordedSpeechTurnService;
import ch.zhaw.prometheus.application.RealtimeCallOrchestrationService;
import ch.zhaw.prometheus.application.RealtimeCallSettings;
import ch.zhaw.prometheus.controllers.views.RecordedSpeechTurnView;
import ch.zhaw.prometheus.controllers.views.ResponseView;
import ch.zhaw.prometheus.controllers.views.SpeechAudioView;
import ch.zhaw.prometheus.model.event.Event;
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

    @MockitoBean
    private RecordedSpeechTurnService recordedSpeechTurnService;

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
    void acceptsRecordedSpeechTurnUpload() throws Exception {
        UUID agentId = UUID.randomUUID();
        Event responseEvent = new Event(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                Event.KIND_RESPONSE, "{\"speech\":\"Hallo.\"}");
        when(this.recordedSpeechTurnService.process(org.mockito.ArgumentMatchers.eq(agentId),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("marin"),
                org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(Optional.of(new RecordedSpeechTurnView("Bereit.",
                        new ResponseView(responseEvent, true), "audio/mpeg", "AQID")));
        MockMultipartFile audio = new MockMultipartFile("audio", "turn.webm", "audio/webm", new byte[] { 1, 2, 3 });

        this.mockMvc.perform(multipart("/" + agentId + "/speech-turn")
                .file(audio)
                .queryParam("voice", "marin")
                .queryParam("generateComplement", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcript").value("Bereit."))
                .andExpect(jsonPath("$.response.active").value(true))
                .andExpect(jsonPath("$.response.responseEvent.payload").value("{\"speech\":\"Hallo.\"}"))
                .andExpect(jsonPath("$.audioContentType").value("audio/mpeg"))
                .andExpect(jsonPath("$.audioBase64").value("AQID"));
    }

    @Test
    void rendersLatestAssistantSpeechAudio() throws Exception {
        UUID agentId = UUID.randomUUID();
        when(this.recordedSpeechTurnService.latestAssistantSpeech(org.mockito.ArgumentMatchers.eq(agentId),
                org.mockito.ArgumentMatchers.eq("cedar")))
                .thenReturn(Optional.of(new SpeechAudioView("Hallo.", "audio/mpeg", "BAUG")));

        this.mockMvc.perform(post("/" + agentId + "/speech/latest")
                .queryParam("voice", "cedar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speech").value("Hallo."))
                .andExpect(jsonPath("$.audioContentType").value("audio/mpeg"))
                .andExpect(jsonPath("$.audioBase64").value("BAUG"));
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
