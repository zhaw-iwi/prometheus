package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.controllers.views.RecordedSpeechTurnView;
import ch.zhaw.prometheus.controllers.views.ResponseView;
import ch.zhaw.prometheus.controllers.views.SpeechAudioView;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.spi.GeneratedSpeechAudio;
import ch.zhaw.prometheus.spi.OpenAIAudioClient;

class RecordedSpeechTurnServiceUnitTest {

    @Test
    void processTranscribesAcknowledgesWithRealtimeProfileAndSynthesizesBackendSpeech() {
        UUID agentId = UUID.randomUUID();
        AgentApplicationService agentService = mock(AgentApplicationService.class);
        ScopedDemoService scopedDemoService = mock(ScopedDemoService.class);
        OpenAIAudioClient audioClient = mock(OpenAIAudioClient.class);
        MockMultipartFile audio = new MockMultipartFile("audio", "turn.webm", "audio/webm",
                "audio".getBytes(StandardCharsets.UTF_8));
        Event responseEvent = new Event(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                Event.KIND_RESPONSE, "{\"speech\":\"Ist es etwas, das man anfassen kann?\"}");

        when(agentService.getAgentLanguageCode(agentId)).thenReturn(Optional.of("de"));
        when(audioClient.transcribe(any(), eq("turn.webm"), eq("audio/webm"), eq("de"))).thenReturn("Bereit.");
        when(agentService.acknowledge(eq(agentId), any(EventRequest.class), eq(OutputProfile.REALTIME_SPEECH)))
                .thenReturn(Optional.of(new ResponseView(responseEvent, true)));
        when(audioClient.createSpeech("Ist es etwas, das man anfassen kann?", "marin"))
                .thenReturn(new GeneratedSpeechAudio(new byte[] { 1, 2, 3 }, "audio/mpeg"));

        RecordedSpeechTurnView view = new RecordedSpeechTurnService(agentService, scopedDemoService, audioClient)
                .process(agentId, audio, "marin", true)
                .orElseThrow();

        assertEquals("Bereit.", view.getTranscript());
        assertEquals(responseEvent, view.getResponse().getResponseEvent());
        assertEquals("audio/mpeg", view.getAudioContentType());
        assertEquals("AQID", view.getAudioBase64());
        ArgumentCaptor<EventRequest> event = ArgumentCaptor.forClass(EventRequest.class);
        verify(agentService).acknowledge(eq(agentId), event.capture(), eq(OutputProfile.REALTIME_SPEECH));
        assertEquals(Event.TYPE_USER_UTTERANCE, event.getValue().getType());
        assertEquals(Event.ACTOR_USER, event.getValue().getActor());
        assertEquals(Event.KIND_OBSERVATION, event.getValue().getKind());
        assertEquals("Bereit.", event.getValue().getPayload());
        verify(agentService).generate(agentId, java.util.List.of("speech"), OutputProfile.BACKEND_COMPLEMENT);
    }

    @Test
    void processReturnsEmptyForUnknownAgentAfterTranscription() {
        UUID agentId = UUID.randomUUID();
        AgentApplicationService agentService = mock(AgentApplicationService.class);
        ScopedDemoService scopedDemoService = mock(ScopedDemoService.class);
        OpenAIAudioClient audioClient = mock(OpenAIAudioClient.class);
        MockMultipartFile audio = new MockMultipartFile("audio", "turn.webm", "audio/webm",
                "audio".getBytes(StandardCharsets.UTF_8));

        when(agentService.getAgentLanguageCode(agentId)).thenReturn(Optional.empty());
        when(audioClient.transcribe(any(), eq("turn.webm"), eq("audio/webm"), eq(null))).thenReturn("Hallo.");
        when(agentService.acknowledge(eq(agentId), any(EventRequest.class), eq(OutputProfile.REALTIME_SPEECH)))
                .thenReturn(Optional.empty());

        Optional<RecordedSpeechTurnView> view = new RecordedSpeechTurnService(agentService, scopedDemoService,
                audioClient).process(agentId, audio, null, false);

        assertTrue(view.isEmpty());
    }

    @Test
    void processScopedUsesScopedLanguageAndAcknowledgementPath() {
        UUID agentId = UUID.randomUUID();
        AgentApplicationService agentService = mock(AgentApplicationService.class);
        ScopedDemoService scopedDemoService = mock(ScopedDemoService.class);
        OpenAIAudioClient audioClient = mock(OpenAIAudioClient.class);
        MockMultipartFile audio = new MockMultipartFile("audio", "turn.webm", "audio/webm",
                "audio".getBytes(StandardCharsets.UTF_8));
        Event responseEvent = new Event(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                Event.KIND_RESPONSE, "{\"speech\":\"Hallo.\"}");

        when(scopedDemoService.getAgentLanguageCode("A49a1", agentId)).thenReturn(Optional.of("de"));
        when(audioClient.transcribe(any(), eq("turn.webm"), eq("audio/webm"), eq("de"))).thenReturn("Ja.");
        when(scopedDemoService.acknowledge(eq("A49a1"), eq(agentId), any(EventRequest.class),
                eq(OutputProfile.REALTIME_SPEECH)))
                .thenReturn(Optional.of(new ResponseView(responseEvent, true)));
        when(audioClient.createSpeech("Hallo.", null))
                .thenReturn(new GeneratedSpeechAudio(new byte[] { 9 }, "audio/mpeg"));

        RecordedSpeechTurnView view = new RecordedSpeechTurnService(agentService, scopedDemoService, audioClient)
                .processScoped("A49a1", agentId, audio, null, false)
                .orElseThrow();

        assertEquals("Ja.", view.getTranscript());
        assertEquals("CQ==", view.getAudioBase64());
        verify(scopedDemoService).acknowledge(eq("A49a1"), eq(agentId), any(EventRequest.class),
                eq(OutputProfile.REALTIME_SPEECH));
    }

    @Test
    void latestAssistantSpeechSynthesizesLatestBackendAuthoredSpeech() {
        UUID agentId = UUID.randomUUID();
        AgentApplicationService agentService = mock(AgentApplicationService.class);
        ScopedDemoService scopedDemoService = mock(ScopedDemoService.class);
        OpenAIAudioClient audioClient = mock(OpenAIAudioClient.class);
        Event olderAssistant = new Event(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                Event.KIND_RESPONSE, "{\"speech\":\"Hallo.\"}");
        Event userEvent = new Event(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER,
                Event.KIND_OBSERVATION, "Bereit.");
        Event latestAssistant = new Event(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                Event.KIND_RESPONSE, "{\"speech\":\"Ist es etwas, das man anfassen kann?\"}");

        when(agentService.getAgentCurrentStateEventHistory(agentId)).thenReturn(Optional.of(List.of(
                olderAssistant, userEvent, latestAssistant)));
        when(audioClient.createSpeech("Ist es etwas, das man anfassen kann?", "cedar"))
                .thenReturn(new GeneratedSpeechAudio(new byte[] { 4, 5, 6 }, "audio/mpeg"));

        SpeechAudioView view = new RecordedSpeechTurnService(agentService, scopedDemoService, audioClient)
                .latestAssistantSpeech(agentId, "cedar")
                .orElseThrow();

        assertEquals("Ist es etwas, das man anfassen kann?", view.getSpeech());
        assertEquals("audio/mpeg", view.getAudioContentType());
        assertEquals("BAUG", view.getAudioBase64());
        verify(audioClient).createSpeech("Ist es etwas, das man anfassen kann?", "cedar");
    }

    @Test
    void latestAssistantSpeechReturnsEmptyWhenLatestUtteranceIsUser() {
        UUID agentId = UUID.randomUUID();
        AgentApplicationService agentService = mock(AgentApplicationService.class);
        ScopedDemoService scopedDemoService = mock(ScopedDemoService.class);
        OpenAIAudioClient audioClient = mock(OpenAIAudioClient.class);
        Event assistant = new Event(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                Event.KIND_RESPONSE, "{\"speech\":\"Hallo.\"}");
        Event complement = new Event(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                Event.KIND_RESPONSE, "{\"nonVerbal\":{\"gesture\":\"ACKNOWLEDGE\"}}");
        Event user = new Event(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER,
                Event.KIND_OBSERVATION, "Ja.");

        when(agentService.getAgentCurrentStateEventHistory(agentId)).thenReturn(Optional.of(List.of(
                assistant, user, complement)));

        Optional<SpeechAudioView> view = new RecordedSpeechTurnService(agentService, scopedDemoService, audioClient)
                .latestAssistantSpeech(agentId, "cedar");

        assertTrue(view.isEmpty());
    }
}
