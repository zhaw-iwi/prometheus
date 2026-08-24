package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.spi.SpeechAudio;
import ch.zhaw.prometheus.spi.SpeechSynthesisException;
import ch.zhaw.prometheus.spi.SpeechSynthesisGateway;

@ExtendWith(MockitoExtension.class)
class ScopedBehaviourSpeechServiceUnitTest {
    private static final UUID AGENT_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final SpeechSynthesisSettings SETTINGS = new SpeechSynthesisSettings("cedar", "1.2");

    @Mock
    private ScopedDemoService demoService;

    @Mock
    private SpeechSynthesisGateway speechGateway;

    private ScopedBehaviourSpeechService service;

    @BeforeEach
    void setUp() {
        this.service = new ScopedBehaviourSpeechService(this.demoService, this.speechGateway);
    }

    @Test
    void resolvesCanonicalPersistedSpeechWithoutRewritingIt() {
        String speech = "  Exact persisted speech.  ";
        Event event = event(EVENT_ID, Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN,
                "{\"speech\":\"  Exact persisted speech.  \",\"motion\":{\"handSign\":\"rock\"}}");
        SpeechAudio audio = new SpeechAudio(new byte[] { 1 }, "audio/mpeg");
        when(this.demoService.getAgentEventHistory("abc12", AGENT_ID)).thenReturn(Optional.of(List.of(event)));
        when(this.speechGateway.synthesize(speech, "cedar", 1.2)).thenReturn(audio);

        assertEquals(audio, this.service.synthesize("abc12", AGENT_ID, EVENT_ID, SETTINGS).orElseThrow());
        verify(this.speechGateway).synthesize(speech, "cedar", 1.2);
    }

    @Test
    void rejectsForeignAgentAndUnknownOrForeignEventBeforeProviderCall() {
        when(this.demoService.getAgentEventHistory("abc12", AGENT_ID)).thenReturn(Optional.empty());
        assertTrue(this.service.synthesize("abc12", AGENT_ID, EVENT_ID, SETTINGS).isEmpty());

        Event otherEvent = event(UUID.randomUUID(), Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN,
                "{\"speech\":\"Other\"}");
        when(this.demoService.getAgentEventHistory("abc12", AGENT_ID))
                .thenReturn(Optional.of(List.of(otherEvent)));
        assertTrue(this.service.synthesize("abc12", AGENT_ID, EVENT_ID, SETTINGS).isEmpty());
        verifyNoInteractions(this.speechGateway);
    }

    @Test
    void rejectsNonBehaviourMalformedAndSpeechlessEvents() {
        for (Event invalid : List.of(
                event(EVENT_ID, Event.TYPE_USER_UTTERANCE, "hello"),
                event(EVENT_ID, Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, "not-json"),
                event(EVENT_ID, Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, "{}"),
                event(EVENT_ID, Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, "{\"speech\":\"  \"}"))) {
            when(this.demoService.getAgentEventHistory("abc12", AGENT_ID))
                    .thenReturn(Optional.of(List.of(invalid)));
            assertThrows(BehaviourSpeechUnavailableException.class,
                    () -> this.service.synthesize("abc12", AGENT_ID, EVENT_ID, SETTINGS));
        }
        verify(this.speechGateway, never()).synthesize(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void propagatesProviderFailureForHttpTranslation() {
        Event event = event(EVENT_ID, Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, "{\"speech\":\"Canonical\"}");
        when(this.demoService.getAgentEventHistory("abc12", AGENT_ID)).thenReturn(Optional.of(List.of(event)));
        when(this.speechGateway.synthesize("Canonical", "cedar", 1.2))
                .thenThrow(new SpeechSynthesisException("provider failed"));

        assertThrows(SpeechSynthesisException.class,
                () -> this.service.synthesize("abc12", AGENT_ID, EVENT_ID, SETTINGS));
    }

    private static Event event(UUID id, String type, String payload) {
        Event event = org.mockito.Mockito.mock(Event.class);
        when(event.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(event.getType()).thenReturn(type);
        org.mockito.Mockito.lenient().when(event.getPayload()).thenReturn(payload);
        return event;
    }
}
