package ch.zhaw.prometheus.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import ch.zhaw.prometheus.agentdefs.core.TalkToMe;
import ch.zhaw.prometheus.controllers.views.AgentInfoView;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.controllers.views.ResponseView;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.spi.SpeechAudio;
import ch.zhaw.prometheus.spi.SpeechSynthesisGateway;

@Service
public class ScopedTalkToMeSpeechService {
    private final ScopedDemoService demoService;
    private final SpeechSynthesisGateway speechGateway;

    public ScopedTalkToMeSpeechService(ScopedDemoService demoService, SpeechSynthesisGateway speechGateway) {
        this.demoService = demoService;
        this.speechGateway = speechGateway;
    }

    public Optional<SpeechAudio> synthesize(String accessCode, UUID agentId, EventRequest request,
            SpeechSynthesisSettings settings) {
        Optional<AgentInfoView> agentInfo = this.demoService.getAgentInfo(accessCode, agentId);
        if (agentInfo.isEmpty() || !isTalkToMe(agentInfo.get())) {
            return Optional.empty();
        }

        Optional<ResponseView> acknowledged = this.demoService.acknowledge(accessCode, agentId, request,
                OutputProfile.REALTIME_SPEECH);
        if (acknowledged.isEmpty()) {
            return Optional.empty();
        }
        String speech = speechFromBehaviourEvent(acknowledged.get().getResponseEvent())
                .orElseThrow(TalkToMeSpeechUnavailableException::new);
        return Optional.of(this.speechGateway.synthesize(speech, settings.getVoice(), settings.getSpeed()));
    }

    private static boolean isTalkToMe(AgentInfoView agentInfo) {
        return agentInfo.getInteractionProfile().getProfileTags().contains(TalkToMe.PROFILE_TAG);
    }

    private static Optional<String> speechFromBehaviourEvent(Event event) {
        if (event == null || !Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType())) {
            return Optional.empty();
        }
        BehaviourPlan plan = BehaviourPlan.fromJson(event.getPayload());
        String speech = plan == null ? null : plan.getSpeech();
        return speech == null || speech.isBlank() ? Optional.empty() : Optional.of(speech);
    }
}
