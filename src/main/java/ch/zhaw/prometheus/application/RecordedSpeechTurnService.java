package ch.zhaw.prometheus.application;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.controllers.views.RecordedSpeechTurnView;
import ch.zhaw.prometheus.controllers.views.ResponseView;
import ch.zhaw.prometheus.controllers.views.SpeechAudioView;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.spi.GeneratedSpeechAudio;
import ch.zhaw.prometheus.spi.OpenAIAudioClient;

@Service
public class RecordedSpeechTurnService {
    private final AgentApplicationService agentService;
    private final ScopedDemoService scopedDemoService;
    private final OpenAIAudioClient audioClient;

    public RecordedSpeechTurnService(AgentApplicationService agentService, ScopedDemoService scopedDemoService,
            OpenAIAudioClient audioClient) {
        this.agentService = agentService;
        this.scopedDemoService = scopedDemoService;
        this.audioClient = audioClient;
    }

    public Optional<RecordedSpeechTurnView> process(UUID agentId, MultipartFile audio, String voice,
            boolean generateComplement) {
        validateAudio(audio);
        String languageCode = this.agentService.getAgentLanguageCode(agentId).orElse(null);
        String transcript = transcribe(audio, languageCode);
        ResponseView response = acknowledgeAndGenerate(agentId, transcript, generateComplement);
        if (response == null) {
            return Optional.empty();
        }
        return Optional.of(toView(transcript, response, voice));
    }

    public Optional<RecordedSpeechTurnView> processScoped(String accessCode, UUID agentId, MultipartFile audio,
            String voice, boolean generateComplement) {
        validateAudio(audio);
        String languageCode = this.scopedDemoService.getAgentLanguageCode(accessCode, agentId).orElse(null);
        String transcript = transcribe(audio, languageCode);
        ResponseView response = acknowledgeScopedAndGenerate(accessCode, agentId, transcript, generateComplement);
        if (response == null) {
            return Optional.empty();
        }
        return Optional.of(toView(transcript, response, voice));
    }

    public Optional<SpeechAudioView> latestAssistantSpeech(UUID agentId, String voice) {
        return this.agentService.getAgentCurrentStateEventHistory(agentId)
                .flatMap(SpeechTurnSelector::latestAssistantSpeechIfLatestUtterance)
                .map(speech -> toSpeechAudioView(speech, voice));
    }

    public Optional<SpeechAudioView> latestScopedAssistantSpeech(String accessCode, UUID agentId, String voice) {
        return this.scopedDemoService.getAgentCurrentStateEventHistory(accessCode, agentId)
                .flatMap(SpeechTurnSelector::latestAssistantSpeechIfLatestUtterance)
                .map(speech -> toSpeechAudioView(speech, voice));
    }

    private ResponseView acknowledgeAndGenerate(UUID agentId, String transcript, boolean generateComplement) {
        Optional<ResponseView> acknowledged = this.agentService.acknowledge(agentId, userUtterance(transcript),
                OutputProfile.REALTIME_SPEECH);
        if (acknowledged.isEmpty()) {
            return null;
        }
        ResponseView response = responseWithGeneratedSpeechIfNeeded(agentId, acknowledged.get());
        String speech = speechFromEvent(response.getResponseEvent());
        if (isPresent(speech) && generateComplement) {
            this.agentService.generate(agentId, List.of("speech"), OutputProfile.BACKEND_COMPLEMENT);
        }
        return response;
    }

    private ResponseView acknowledgeScopedAndGenerate(String accessCode, UUID agentId, String transcript,
            boolean generateComplement) {
        Optional<ResponseView> acknowledged = this.scopedDemoService.acknowledge(accessCode, agentId,
                userUtterance(transcript), OutputProfile.REALTIME_SPEECH);
        if (acknowledged.isEmpty()) {
            return null;
        }
        ResponseView response = scopedResponseWithGeneratedSpeechIfNeeded(accessCode, agentId, acknowledged.get());
        String speech = speechFromEvent(response.getResponseEvent());
        if (isPresent(speech) && generateComplement) {
            this.scopedDemoService.generate(accessCode, agentId, List.of("speech"), OutputProfile.BACKEND_COMPLEMENT);
        }
        return response;
    }

    private ResponseView responseWithGeneratedSpeechIfNeeded(UUID agentId, ResponseView response) {
        if (isPresent(speechFromEvent(response.getResponseEvent())) || !response.isActive()) {
            return response;
        }
        int historySizeBefore = this.agentService.getAgentEventHistory(agentId).map(List::size).orElse(0);
        BehaviourGenerationOutcome outcome = this.agentService.generate(agentId, null, OutputProfile.REALTIME_SPEECH);
        if (outcome != BehaviourGenerationOutcome.GENERATED) {
            return response;
        }
        Event generated = this.agentService.getAgentEventHistory(agentId)
                .map(history -> latestAssistantBehaviourAfter(history, historySizeBefore))
                .orElse(null);
        return generated == null ? response : new ResponseView(generated, response.isActive());
    }

    private ResponseView scopedResponseWithGeneratedSpeechIfNeeded(String accessCode, UUID agentId,
            ResponseView response) {
        if (isPresent(speechFromEvent(response.getResponseEvent())) || !response.isActive()) {
            return response;
        }
        int historySizeBefore = this.scopedDemoService.getAgentEventHistory(accessCode, agentId)
                .map(List::size)
                .orElse(0);
        BehaviourGenerationOutcome outcome = this.scopedDemoService.generate(accessCode, agentId, null,
                OutputProfile.REALTIME_SPEECH);
        if (outcome != BehaviourGenerationOutcome.GENERATED) {
            return response;
        }
        Event generated = this.scopedDemoService.getAgentEventHistory(accessCode, agentId)
                .map(history -> latestAssistantBehaviourAfter(history, historySizeBefore))
                .orElse(null);
        return generated == null ? response : new ResponseView(generated, response.isActive());
    }

    private RecordedSpeechTurnView toView(String transcript, ResponseView response, String voice) {
        String speech = speechFromEvent(response.getResponseEvent());
        if (!isPresent(speech)) {
            return new RecordedSpeechTurnView(transcript, response, null, null);
        }
        GeneratedSpeechAudio audio = this.audioClient.createSpeech(speech, voice);
        return new RecordedSpeechTurnView(
                transcript,
                response,
                audio.getContentType(),
                Base64.getEncoder().encodeToString(audio.getBytes()));
    }

    private SpeechAudioView toSpeechAudioView(String speech, String voice) {
        GeneratedSpeechAudio audio = this.audioClient.createSpeech(speech, voice);
        return new SpeechAudioView(speech, audio.getContentType(), Base64.getEncoder().encodeToString(
                audio.getBytes()));
    }

    private String transcribe(MultipartFile audio, String languageCode) {
        try {
            String transcript = this.audioClient.transcribe(audio.getBytes(), audio.getOriginalFilename(),
                    audio.getContentType(), languageCode);
            if (!isPresent(transcript)) {
                throw new IllegalArgumentException("audio transcription returned no transcript");
            }
            return transcript.trim();
        } catch (IOException failure) {
            throw new IllegalArgumentException("unable to read uploaded audio", failure);
        }
    }

    private static void validateAudio(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new IllegalArgumentException("audio file must not be empty");
        }
    }

    private static EventRequest userUtterance(String transcript) {
        return new EventRequest(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, Event.KIND_OBSERVATION,
                transcript.trim());
    }

    private static Event latestAssistantBehaviourAfter(List<Event> history, int firstIndex) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        int start = Math.max(0, firstIndex);
        for (int i = history.size() - 1; i >= start; i--) {
            Event event = history.get(i);
            if (isPresent(speechFromEvent(event))) {
                return event;
            }
        }
        return null;
    }

    private static String speechFromEvent(Event event) {
        if (event == null || !Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType())) {
            return null;
        }
        BehaviourPlan plan = BehaviourPlan.fromJson(event.getPayload());
        return plan == null ? null : plan.getSpeech();
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
