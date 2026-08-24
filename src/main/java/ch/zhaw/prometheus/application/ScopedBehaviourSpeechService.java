package ch.zhaw.prometheus.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.spi.SpeechAudio;
import ch.zhaw.prometheus.spi.SpeechSynthesisGateway;

@Service
public class ScopedBehaviourSpeechService {
    private final ScopedDemoService demoService;
    private final SpeechSynthesisGateway speechGateway;

    public ScopedBehaviourSpeechService(ScopedDemoService demoService, SpeechSynthesisGateway speechGateway) {
        this.demoService = demoService;
        this.speechGateway = speechGateway;
    }

    public Optional<SpeechAudio> synthesize(String accessCode, UUID agentId, UUID eventId,
            SpeechSynthesisSettings settings) {
        Optional<List<Event>> history = this.demoService.getAgentEventHistory(accessCode, agentId);
        if (history.isEmpty()) {
            return Optional.empty();
        }
        Optional<Event> event = history.get().stream()
                .filter(candidate -> eventId != null && eventId.equals(candidate.getId()))
                .findFirst();
        if (event.isEmpty()) {
            return Optional.empty();
        }
        String speech = canonicalSpeech(event.get());
        return Optional.of(this.speechGateway.synthesize(speech, settings.getVoice(), settings.getSpeed()));
    }

    static String canonicalSpeech(Event event) {
        if (!Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType())) {
            throw new BehaviourSpeechUnavailableException("event is not a behaviour plan");
        }
        BehaviourPlan plan;
        try {
            plan = BehaviourPlan.fromJson(event.getPayload());
        } catch (RuntimeException failure) {
            throw new BehaviourSpeechUnavailableException("behaviour plan is malformed");
        }
        String speech = plan == null ? null : plan.getSpeech();
        if (speech == null || speech.isBlank()) {
            throw new BehaviourSpeechUnavailableException("behaviour plan does not contain speech");
        }
        return speech;
    }
}
