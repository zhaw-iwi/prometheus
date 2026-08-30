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
import ch.zhaw.prometheus.controllers.views.ResponseView;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.spi.SpeechAudio;
import ch.zhaw.prometheus.spi.SpeechSynthesisGateway;

@ExtendWith(MockitoExtension.class)
class ScopedTalkToMeSpeechServiceUnitTest {
    @Mock
    private ScopedDemoService demoService;

    @Mock
    private SpeechSynthesisGateway speechGateway;

    @Test
    void acknowledgesTalkToMeInputAsAFullPlanBeforeSynthesizingItsSpeech() {
        UUID agentId = UUID.randomUUID();
        EventRequest request = new EventRequest();
        AgentInfoView talkToMe = new AgentInfoView(agentId, "Talk to Me", "Voice-only agent", true,
                AgentInteractionProfile.of(List.of("obs.user_utterance"), List.of("speech"),
                        List.of("utility.talk_to_me")));
        Event response = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                BehaviourPlan.speechOnly("Hello there").toJson());
        SpeechAudio audio = new SpeechAudio(new byte[] { 1, 2, 3 }, "audio/mpeg");
        when(this.demoService.getAgentInfo("abc12", agentId)).thenReturn(Optional.of(talkToMe));
        when(this.demoService.acknowledge("abc12", agentId, request, OutputProfile.FULL_PLAN))
                .thenReturn(Optional.of(new ResponseView(response, true)));
        when(this.speechGateway.synthesize("Hello there", "cedar", 1.1)).thenReturn(audio);
        ScopedTalkToMeSpeechService service = new ScopedTalkToMeSpeechService(this.demoService, this.speechGateway);

        Optional<SpeechAudio> result = service.synthesize("abc12", agentId, request,
                new SpeechSynthesisSettings("cedar", "1.1"));

        assertTrue(result.isPresent());
        verify(this.demoService).acknowledge("abc12", agentId, request, OutputProfile.FULL_PLAN);
        verify(this.speechGateway).synthesize("Hello there", "cedar", 1.1);
    }

    @Test
    void rejectsNonTalkToMeAgentBeforeAcknowledgementOrSynthesis() {
        UUID agentId = UUID.randomUUID();
        AgentInfoView otherAgent = new AgentInfoView(agentId, "Valerian", "Conversational agent", true,
                AgentInteractionProfile.of(List.of(), List.of("speech"), List.of("cockpit.valerian")));
        when(this.demoService.getAgentInfo("abc12", agentId)).thenReturn(Optional.of(otherAgent));
        ScopedTalkToMeSpeechService service = new ScopedTalkToMeSpeechService(this.demoService, this.speechGateway);

        Optional<?> result = service.synthesize("abc12", agentId, new EventRequest(),
                new SpeechSynthesisSettings(null, null));

        assertTrue(result.isEmpty());
        verify(this.demoService, never()).acknowledge(any(), any(), any(), any());
        verifyNoInteractions(this.speechGateway);
    }
}
