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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import ch.zhaw.prometheus.application.BehaviourSpeechUnavailableException;
import ch.zhaw.prometheus.application.DemoAccessDeniedException;
import ch.zhaw.prometheus.application.ScopedBehaviourSpeechService;
import ch.zhaw.prometheus.application.SpeechSynthesisSettings;
import ch.zhaw.prometheus.spi.SpeechAudio;
import ch.zhaw.prometheus.spi.SpeechSynthesisException;

@WebMvcTest(controllers = BehaviourSpeechController.class)
class BehaviourSpeechControllerWebMvcTest {
    private static final UUID AGENT_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScopedBehaviourSpeechService speechService;

    @Test
    void streamsUncachedCanonicalEventAudioWithSharedSettings() throws Exception {
        when(this.speechService.synthesize(eq("abc12"), eq(AGENT_ID), eq(EVENT_ID), any()))
                .thenReturn(Optional.of(new SpeechAudio(new byte[] { 1, 2, 3 }, "audio/mpeg")));

        MvcResult result = this.mockMvc.perform(post(path())
                .header(ScopedDemoController.ACCESS_CODE_HEADER, "abc12")
                .queryParam("voice", "Cedar")
                .queryParam("speed", "1.25")
                .contentType("application/json")
                .content("{\"text\":\"browser-authored text must be ignored\"}"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        this.mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().longValue("Content-Length", 3L))
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(content().bytes(new byte[] { 1, 2, 3 }));

        ArgumentCaptor<SpeechSynthesisSettings> settings = ArgumentCaptor.forClass(SpeechSynthesisSettings.class);
        verify(this.speechService).synthesize(eq("abc12"), eq(AGENT_ID), eq(EVENT_ID), settings.capture());
        assertEquals("cedar", settings.getValue().getVoice());
        assertEquals(1.25, settings.getValue().getSpeed(), 0.0001);
    }

    @Test
    void returnsNotFoundForForeignAgentOrUnknownEvent() throws Exception {
        when(this.speechService.synthesize(eq("abc12"), eq(AGENT_ID), eq(EVENT_ID), any()))
                .thenReturn(Optional.empty());

        this.mockMvc.perform(post(path()).header(ScopedDemoController.ACCESS_CODE_HEADER, "abc12"))
                .andExpect(status().isNotFound());
    }

    @Test
    void mapsInvalidAccessCodeAndUnavailableCanonicalSpeech() throws Exception {
        when(this.speechService.synthesize(eq("bad"), eq(AGENT_ID), eq(EVENT_ID), any()))
                .thenThrow(new DemoAccessDeniedException());
        this.mockMvc.perform(post(path()).header(ScopedDemoController.ACCESS_CODE_HEADER, "bad"))
                .andExpect(status().isUnauthorized());

        when(this.speechService.synthesize(eq("abc12"), eq(AGENT_ID), eq(EVENT_ID), any()))
                .thenThrow(new BehaviourSpeechUnavailableException("non-behaviour or no speech"));
        this.mockMvc.perform(post(path()).header(ScopedDemoController.ACCESS_CODE_HEADER, "abc12"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidVoiceOrSpeedBeforeServiceCall() throws Exception {
        this.mockMvc.perform(post(path())
                .header(ScopedDemoController.ACCESS_CODE_HEADER, "abc12")
                .queryParam("voice", "not-a-voice"))
                .andExpect(status().isBadRequest());
        this.mockMvc.perform(post(path())
                .header(ScopedDemoController.ACCESS_CODE_HEADER, "abc12")
                .queryParam("speed", "9"))
                .andExpect(status().isBadRequest());

        verify(this.speechService, never()).synthesize(any(), any(), any(), any());
    }

    @Test
    void translatesProviderFailureWithoutLeakingItsBody() throws Exception {
        when(this.speechService.synthesize(eq("abc12"), eq(AGENT_ID), eq(EVENT_ID), any()))
                .thenThrow(new SpeechSynthesisException("provider body must remain private"));

        this.mockMvc.perform(post(path()).header(ScopedDemoController.ACCESS_CODE_HEADER, "abc12"))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(""));
    }

    private static String path() {
        return "/demo/agents/" + AGENT_ID + "/behaviours/" + EVENT_ID + "/speech";
    }
}
