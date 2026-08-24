package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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

import ch.zhaw.prometheus.application.ScopedTalkToMeSpeechService;
import ch.zhaw.prometheus.application.SpeechSynthesisSettings;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.spi.SpeechAudio;

@WebMvcTest(controllers = TalkToMeSpeechController.class)
class TalkToMeSpeechControllerWebMvcTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScopedTalkToMeSpeechService speechService;

    @Test
    void mapsDedicatedRequestAndReturnsUncachedAudio() throws Exception {
        UUID agentId = UUID.randomUUID();
        when(this.speechService.synthesize(eq("abc12"), eq(agentId), any(EventRequest.class),
                any(SpeechSynthesisSettings.class)))
                .thenReturn(Optional.of(new SpeechAudio(new byte[] { 1, 2, 3 }, "audio/mpeg")));

        var result = this.mockMvc.perform(post("/demo/talktome/agents/" + agentId + "/speech")
                .header(ScopedDemoController.ACCESS_CODE_HEADER, "abc12")
                .contentType(MediaType.APPLICATION_JSON)
                .queryParam("voice", "Marin")
                .queryParam("speed", "1.25")
                .content("""
                        {"type":"obs.user_utterance","actor":"user","kind":"observation","payload":"Exact text"}
                        """))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        this.mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(content().bytes(new byte[] { 1, 2, 3 }));

        ArgumentCaptor<SpeechSynthesisSettings> settings = ArgumentCaptor.forClass(SpeechSynthesisSettings.class);
        verify(this.speechService).synthesize(eq("abc12"), eq(agentId), any(EventRequest.class), settings.capture());
        assertEquals("marin", settings.getValue().getVoice());
        assertEquals(1.25, settings.getValue().getSpeed(), 0.0001);
    }

    @Test
    void rejectsUnsupportedSpeechSettingsBeforeSynthesis() throws Exception {
        UUID agentId = UUID.randomUUID();

        this.mockMvc.perform(post("/demo/talktome/agents/" + agentId + "/speech")
                .header(ScopedDemoController.ACCESS_CODE_HEADER, "abc12")
                .contentType(MediaType.APPLICATION_JSON)
                .queryParam("voice", "not-a-voice")
                .content("""
                        {"type":"obs.user_utterance","actor":"user","kind":"observation","payload":"Text"}
                        """))
                .andExpect(status().isBadRequest());

        verify(this.speechService, never()).synthesize(any(), any(), any(), any());
    }

    @Test
    void leavesTheSharedScopedAgentSpeechPathUnmapped() throws Exception {
        this.mockMvc.perform(post("/demo/agents/" + UUID.randomUUID() + "/speech")
                .header(ScopedDemoController.ACCESS_CODE_HEADER, "abc12")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"obs.user_utterance","actor":"user","kind":"observation","payload":"Text"}
                        """))
                .andExpect(status().isNotFound());

        verify(this.speechService, never()).synthesize(any(), any(), any(), any());
    }
}
