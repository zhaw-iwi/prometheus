package ch.zhaw.prometheus.application;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.controllers.views.PolicyResponseView;
import ch.zhaw.prometheus.controllers.views.ResponseView;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.spi.OpenAIProperties;
import jakarta.annotation.PreDestroy;

@Service
public class RealtimeSidebandService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RealtimeSidebandService.class);
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final long TRANSCRIPT_BATCH_DELAY_MS = 900;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ScheduledExecutorService transcriptExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "prometheus-realtime-transcript-ingress");
        thread.setDaemon(true);
        return thread;
    });
    private final OpenAIProperties properties;
    private final AgentApplicationService agentService;
    private final ConcurrentMap<String, SidebandSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> callIdsByAgent = new ConcurrentHashMap<>();

    public RealtimeSidebandService(OpenAIProperties properties, AgentApplicationService agentService) {
        this.properties = properties;
        this.agentService = agentService;
    }

    public void attach(RealtimeSidebandSessionConfig config) {
        if (config == null || config.getCallId() == null || config.getCallId().isBlank()
                || config.getSidebandUrl() == null || config.getSidebandUrl().isBlank()
                || config.getAgentId() == null) {
            throw new IllegalArgumentException("sideband session config is incomplete");
        }
        String previousCallId = this.callIdsByAgent.put(config.getAgentId(), config.getCallId());
        if (previousCallId != null && !previousCallId.equals(config.getCallId())) {
            close(previousCallId);
        }
        SidebandSession session = new SidebandSession(config);
        this.sessions.put(config.getCallId(), session);
        this.httpClient.newWebSocketBuilder()
                .header(this.properties.headerKeyNameForAPIKey(), this.properties.getKey())
                .buildAsync(URI.create(config.getSidebandUrl()), session)
                .exceptionally(failure -> {
                    this.sessions.remove(config.getCallId());
                    this.callIdsByAgent.remove(config.getAgentId(), config.getCallId());
                    LOGGER.warn("Realtime sideband connection failed; callId={} agentId={}",
                            config.getCallId(), config.getAgentId(), failure);
                    return null;
                });
    }

    public void close(String callId) {
        if (callId == null || callId.isBlank()) {
            return;
        }
        SidebandSession session = this.sessions.remove(callId);
        if (session != null) {
            this.callIdsByAgent.remove(session.agentId(), callId);
            session.close();
        }
    }

    @EventListener
    public void speakPublishedAssistantBehaviour(AssistantBehaviourPublishedEvent event) {
        if (event == null || event.agentId() == null || event.event() == null) {
            return;
        }
        String callId = this.callIdsByAgent.get(event.agentId());
        if (!isPresent(callId)) {
            return;
        }
        SidebandSession session = this.sessions.get(callId);
        if (session != null) {
            session.speakPublishedBehaviour(event.event());
        }
    }

    @PreDestroy
    public void closeAll() {
        for (String callId : List.copyOf(this.sessions.keySet())) {
            close(callId);
        }
        this.transcriptExecutor.shutdownNow();
    }

    private final class SidebandSession implements WebSocket.Listener {
        private final RealtimeSidebandSessionConfig config;
        private final StringBuilder messageBuffer = new StringBuilder();
        private final Set<String> pendingInputItemIds = new LinkedHashSet<>();
        private final Set<String> processedInputItemIds = new HashSet<>();
        private final Set<String> spokenBehaviourEventKeys = ConcurrentHashMap.newKeySet();
        private final List<TranscriptCandidate> pendingTranscriptCandidates = new ArrayList<>();
        private volatile WebSocket webSocket;
        private volatile boolean closed;
        private String pendingExactSpeech;
        private ScheduledFuture<?> pendingTranscriptFlush;

        private SidebandSession(RealtimeSidebandSessionConfig config) {
            this.config = config;
        }

        private UUID agentId() {
            return this.config.getAgentId();
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            this.webSocket = webSocket;
            if (this.closed) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "PROMETHEUS realtime call closed");
                return;
            }
            webSocket.request(1);
            if (isPresent(this.config.getInitialExactSpeech())) {
                updateSessionThenRespond(this.config.getInitialInstructions(), this.config.getInitialExactSpeech());
            } else {
                sendSessionUpdate(this.config.getInitialInstructions());
            }
        }

        private void speakPublishedBehaviour(Event event) {
            if (this.closed || event == null || this.webSocket == null) {
                return;
            }
            String speech = speechFromEvent(event);
            if (!isPresent(speech)) {
                return;
            }
            if (!markBehaviourEventSpoken(event)) {
                return;
            }
            PolicyResponseView prompt = agentService.prompt(this.config.getAgentId(), OutputProfile.REALTIME_SPEECH)
                    .orElse(null);
            updateSessionThenRespond(RealtimePromptInstructions.systemInstructions(prompt), speech);
        }

        private boolean markBehaviourEventSpoken(Event event) {
            return this.spokenBehaviourEventKeys.add(behaviourEventKey(event));
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            this.messageBuffer.append(data);
            if (last) {
                String raw = this.messageBuffer.toString();
                this.messageBuffer.setLength(0);
                handleRawEvent(raw);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            sessions.remove(this.config.getCallId(), this);
            callIdsByAgent.remove(this.config.getAgentId(), this.config.getCallId());
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            sessions.remove(this.config.getCallId(), this);
            callIdsByAgent.remove(this.config.getAgentId(), this.config.getCallId());
            LOGGER.warn("Realtime sideband failed; callId={} agentId={}",
                    this.config.getCallId(), this.config.getAgentId(), error);
        }

        private void close() {
            this.closed = true;
            discardQueuedTranscriptState();
            WebSocket socket = this.webSocket;
            if (socket != null) {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "PROMETHEUS realtime call closed");
            }
        }

        private void handleRawEvent(String raw) {
            JsonObject event;
            try {
                event = JsonParser.parseString(raw).getAsJsonObject();
            } catch (RuntimeException failure) {
                LOGGER.debug("Ignoring non-json realtime sideband event; callId={}", this.config.getCallId());
                return;
            }
            String type = string(event, "type");
            if ("input_audio_buffer.committed".equals(type)) {
                handleInputAudioCommitted(string(event, "item_id"));
                return;
            }
            if ("input_audio_buffer.cleared".equals(type)) {
                handleInputAudioCleared();
                return;
            }
            if ("conversation.item.input_audio_transcription.completed".equals(type)) {
                queueUserTranscript(string(event, "transcript"), string(event, "item_id"), string(event, "event_id"));
                return;
            }
            if ("session.updated".equals(type)) {
                flushPendingResponse();
            }
        }

        private synchronized void handleInputAudioCommitted(String itemId) {
            if (!isPresent(itemId) || this.processedInputItemIds.contains(itemId)) {
                return;
            }
            this.pendingInputItemIds.add(itemId);
        }

        private synchronized void handleInputAudioCleared() {
            this.pendingInputItemIds.clear();
        }

        private synchronized void discardQueuedTranscriptState() {
            this.pendingInputItemIds.clear();
            this.pendingTranscriptCandidates.clear();
            ScheduledFuture<?> flush = this.pendingTranscriptFlush;
            if (flush != null) {
                flush.cancel(false);
                this.pendingTranscriptFlush = null;
            }
        }

        private void queueUserTranscript(String transcript, String itemId, String eventId) {
            if (!isPresent(transcript)) {
                markTranscriptItemsProcessed(List.of(new TranscriptCandidate(itemId, eventId, transcript)));
                return;
            }
            synchronized (this) {
                this.pendingTranscriptCandidates.add(new TranscriptCandidate(itemId, eventId, transcript.trim()));
                if (this.pendingTranscriptFlush == null || this.pendingTranscriptFlush.isDone()) {
                    this.pendingTranscriptFlush = transcriptExecutor.schedule(this::flushQueuedTranscripts,
                            TRANSCRIPT_BATCH_DELAY_MS, TimeUnit.MILLISECONDS);
                }
            }
        }

        private void flushQueuedTranscripts() {
            List<TranscriptCandidate> candidates;
            synchronized (this) {
                candidates = List.copyOf(this.pendingTranscriptCandidates);
                this.pendingTranscriptCandidates.clear();
                this.pendingTranscriptFlush = null;
            }
            if (this.closed || candidates.isEmpty()) {
                return;
            }
            TranscriptCandidate selected = selectTranscriptCandidate(candidates);
            markTranscriptItemsProcessed(candidates);
            if (selected == null) {
                LOGGER.debug("Realtime sideband ignored transcript batch; callId={} agentId={} candidates={}",
                        this.config.getCallId(), this.config.getAgentId(), candidates.size());
                return;
            }
            processUserTranscript(selected.transcript(), selected.itemId());
        }

        private TranscriptCandidate selectTranscriptCandidate(List<TranscriptCandidate> candidates) {
            TranscriptCandidate selected = null;
            synchronized (this) {
                for (TranscriptCandidate candidate : candidates) {
                    if (!isPresent(candidate.transcript()) || transcriptItemAlreadyProcessed(candidate)
                            || !transcriptItemMatchesPendingCommit(candidate)
                            || isLikelyAsrHallucination(candidate.transcript())) {
                        continue;
                    }
                    selected = candidate;
                }
            }
            return selected;
        }

        private boolean transcriptItemAlreadyProcessed(TranscriptCandidate candidate) {
            return isPresent(candidate.itemId()) && this.processedInputItemIds.contains(candidate.itemId());
        }

        private boolean transcriptItemMatchesPendingCommit(TranscriptCandidate candidate) {
            if (!isPresent(candidate.itemId()) || this.pendingInputItemIds.isEmpty()) {
                return true;
            }
            return this.pendingInputItemIds.contains(candidate.itemId());
        }

        private synchronized void markTranscriptItemsProcessed(List<TranscriptCandidate> candidates) {
            for (TranscriptCandidate candidate : candidates) {
                if (!isPresent(candidate.itemId())) {
                    continue;
                }
                this.processedInputItemIds.add(candidate.itemId());
                this.pendingInputItemIds.remove(candidate.itemId());
            }
        }

        private void processUserTranscript(String transcript, String itemId) {
            if (!isPresent(transcript)) {
                return;
            }
            LOGGER.debug("Realtime sideband accepting user transcript; callId={} agentId={} itemId={}",
                    this.config.getCallId(), this.config.getAgentId(), itemId);
            Optional<ResponseView> acknowledged = agentService.acknowledge(this.config.getAgentId(),
                    new EventRequest(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, Event.KIND_OBSERVATION,
                            transcript.trim()),
                    OutputProfile.REALTIME_SPEECH);
            if (acknowledged.isEmpty()) {
                LOGGER.warn("Realtime sideband transcript for unknown agent; callId={} agentId={}",
                        this.config.getCallId(), this.config.getAgentId());
                return;
            }
            ResponseView ack = acknowledged.get();
            boolean speechPublished = isPresent(speechFromEvent(ack.getResponseEvent()));
            if (!speechPublished) {
                speechPublished = generateRealtimeSpeech();
            }
            if (speechPublished && this.config.getSettings().isGenerateComplement()) {
                agentService.generate(this.config.getAgentId(), List.of("speech"), OutputProfile.BACKEND_COMPLEMENT);
            }
            if (!speechPublished) {
                PolicyResponseView prompt = agentService.prompt(this.config.getAgentId(), OutputProfile.REALTIME_SPEECH)
                        .orElse(null);
                sendSessionUpdate(RealtimePromptInstructions.systemInstructions(prompt));
            }
        }

        private boolean generateRealtimeSpeech() {
            int historySizeBefore = agentService.getAgentEventHistory(this.config.getAgentId())
                    .map(List::size)
                    .orElse(0);
            BehaviourGenerationOutcome outcome = agentService.generate(this.config.getAgentId(), null,
                    OutputProfile.REALTIME_SPEECH);
            if (outcome != BehaviourGenerationOutcome.GENERATED) {
                return false;
            }
            return agentService.getAgentEventHistory(this.config.getAgentId())
                    .map(history -> isPresent(latestAssistantSpeechAfter(history, historySizeBefore)))
                    .orElse(false);
        }

        private void sendSessionUpdate(String instructions) {
            RealtimeCallSettings settings = this.config.getSettings() == null
                    ? new RealtimeCallSettings(null, null, true)
                    : this.config.getSettings();
            JsonObject session = new JsonObject();
            session.addProperty("type", "realtime");
            if (isPresent(instructions)) {
                session.addProperty("instructions", instructions.trim());
            }
            session.add("output_modalities", GSON.toJsonTree(new String[] { "audio" }));
            JsonObject audio = new JsonObject();
            JsonObject input = new JsonObject();
            String turnDetection = settings.getTurnDetection();
            JsonObject vad = new JsonObject();
            vad.addProperty("type", turnDetection);
            if ("server_vad".equals(turnDetection)) {
                addOptionalNumber(vad, "threshold", settings.getVadThreshold());
                addOptionalNumber(vad, "prefix_padding_ms", settings.getVadPrefixPaddingMs());
                addOptionalNumber(vad, "silence_duration_ms", settings.getVadSilenceDurationMs());
            } else if ("semantic_vad".equals(turnDetection)) {
                addOptionalProperty(vad, "eagerness", settings.getVadEagerness());
            }
            vad.addProperty("create_response", false);
            vad.addProperty("interrupt_response", settings.isVadInterruptResponse());
            input.add("turn_detection", vad);
            addInputNoiseReduction(input, settings);
            audio.add("input", input);
            if (isPresent(settings.getVoice()) || settings.getOutputSpeed() != null) {
                JsonObject output = new JsonObject();
                addOptionalProperty(output, "voice", settings.getVoice());
                addOptionalNumber(output, "speed", settings.getOutputSpeed());
                audio.add("output", output);
            }
            session.add("audio", audio);
            addSessionTuning(session, settings);
            JsonObject event = new JsonObject();
            event.addProperty("type", "session.update");
            event.add("session", session);
            send(event);
        }

        private void updateSessionThenRespond(String instructions, String exactSpeech) {
            this.pendingExactSpeech = exactSpeech;
            sendSessionUpdate(instructions);
        }

        private void flushPendingResponse() {
            String exactSpeech = this.pendingExactSpeech;
            this.pendingExactSpeech = null;
            if (isPresent(exactSpeech)) {
                sendExactSpeech(exactSpeech);
            }
        }

        private void sendExactSpeech(String speech) {
            sendResponseCreate("Say exactly the following text and nothing else. Do not add, remove, paraphrase, "
                    + "or explain.\n" + speech.trim());
        }

        private void sendResponseCreate(String instructions) {
            JsonObject response = new JsonObject();
            response.addProperty("conversation", "none");
            response.add("input", GSON.toJsonTree(new Object[0]));
            response.add("output_modalities", GSON.toJsonTree(new String[] { "audio" }));
            if (isPresent(instructions)) {
                response.addProperty("instructions", instructions.trim());
            }
            JsonObject event = new JsonObject();
            event.addProperty("type", "response.create");
            event.add("response", response);
            send(event);
        }

        private void send(JsonObject event) {
            if (this.closed) {
                return;
            }
            WebSocket socket = this.webSocket;
            if (socket == null) {
                return;
            }
            socket.sendText(GSON.toJson(event), true);
        }
    }

    private record TranscriptCandidate(String itemId, String eventId, String transcript) {
    }

    private static String speechFromEvent(Event event) {
        if (event == null || !Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType())) {
            return null;
        }
        BehaviourPlan plan = BehaviourPlan.fromJson(event.getPayload());
        return plan == null ? null : plan.getSpeech();
    }

    private static String latestAssistantSpeechAfter(List<Event> history, int firstIndex) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        int start = Math.max(0, firstIndex);
        for (int i = history.size() - 1; i >= start; i--) {
            String speech = speechFromEvent(history.get(i));
            if (isPresent(speech)) {
                return speech;
            }
        }
        return null;
    }

    private static String behaviourEventKey(Event event) {
        if (event == null) {
            return "null";
        }
        if (event.getId() != null) {
            return "id:" + event.getId();
        }
        return "object:" + System.identityHashCode(event);
    }

    private static String string(JsonObject object, String member) {
        if (object == null || member == null || !object.has(member) || object.get(member).isJsonNull()) {
            return "";
        }
        return object.get(member).getAsString();
    }

    private static void addOptionalProperty(JsonObject object, String name, String value) {
        if (isPresent(value)) {
            object.addProperty(name, value.trim());
        }
    }

    private static void addOptionalNumber(JsonObject object, String name, Number value) {
        if (value != null) {
            object.addProperty(name, value);
        }
    }

    private static void addInputNoiseReduction(JsonObject audioInput, RealtimeCallSettings settings) {
        if (settings == null || !isPresent(settings.getInputNoiseReduction())) {
            return;
        }
        String noiseReduction = settings.getInputNoiseReduction().trim();
        if ("off".equals(noiseReduction)) {
            audioInput.add("noise_reduction", JsonNull.INSTANCE);
            return;
        }
        JsonObject inputNoiseReduction = new JsonObject();
        inputNoiseReduction.addProperty("type", noiseReduction);
        audioInput.add("noise_reduction", inputNoiseReduction);
    }

    private static void addSessionTuning(JsonObject session, RealtimeCallSettings settings) {
        if (settings == null) {
            return;
        }
        if (isPresent(settings.getReasoningEffort())) {
            JsonObject reasoning = new JsonObject();
            reasoning.addProperty("effort", settings.getReasoningEffort().trim());
            session.add("reasoning", reasoning);
        }
        addOptionalNumber(session, "max_output_tokens", settings.getMaxOutputTokens());
        if (settings.isIncludeInputTranscriptionLogprobs()) {
            session.add("include", GSON.toJsonTree(new String[] { "item.input_audio_transcription.logprobs" }));
        }
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static boolean isLikelyAsrHallucination(String transcript) {
        String normalized = normalizeTranscriptForGate(transcript);
        return "untertitel der amara org community".equals(normalized)
                || "subtitles by the amara org community".equals(normalized)
                || "captions by the amara org community".equals(normalized);
    }

    private static String normalizeTranscriptForGate(String transcript) {
        if (transcript == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(transcript, Normalizer.Form.NFKD);
        return decomposed
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
