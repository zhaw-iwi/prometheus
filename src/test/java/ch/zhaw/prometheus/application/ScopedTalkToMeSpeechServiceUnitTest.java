package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.zhaw.prometheus.controllers.views.AgentInfoView;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import ch.zhaw.prometheus.spi.SpeechSynthesisGateway;

@ExtendWith(MockitoExtension.class)
class ScopedTalkToMeSpeechServiceUnitTest {
    @Mock
    private ScopedDemoService demoService;

    @Mock
    private SpeechSynthesisGateway speechGateway;

    @Test
    void rejectsNonTalkToMeAgentBeforeAcknowledgementOrSynthesis() {
        UUID agentId = UUID.randomUUID();
        AgentInfoView otherAgent = new AgentInfoView(agentId, "Valerian", "Conversational agent", true,
                AgentInteractionProfile.of(List.of(), List.of("speech"), List.of("cockpit.valerian")));
        when(this.demoService.getAgentInfo("abc12", agentId)).thenReturn(Optional.of(otherAgent));
        ScopedTalkToMeSpeechService service = new ScopedTalkToMeSpeechService(this.demoService, this.speechGateway);

        Optional<?> result = service.synthesize("abc12", agentId, new EventRequest(),
                new TalkToMeSpeechSettings(null, null));

        assertTrue(result.isEmpty());
        verify(this.demoService, never()).acknowledge(any(), any(), any(), any());
        verifyNoInteractions(this.speechGateway);
    }
}
