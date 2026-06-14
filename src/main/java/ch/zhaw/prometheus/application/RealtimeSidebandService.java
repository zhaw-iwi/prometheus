package ch.zhaw.prometheus.application;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
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
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient = HttpClient.newHttpClient();
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

    @PreDestroy
    public void closeAll() {
        for (String callId : List.copyOf(this.sessions.keySet())) {
            close(callId);
        }
    }

    private final class SidebandSession implements WebSocket.Listener {
        private final RealtimeSidebandSessionConfig config;
        private final StringBuilder messageBuffer = new StringBuilder();
        private final StringBuilder assistantTranscript = new StringBuilder();
        private volatile WebSocket webSocket;
        private volatile boolean closed;
        private boolean assistantAudioSeen;
        private boolean skipNextAssistantPersistence;
        private String pendingResponseInstruction;
        private String pendingExactSpeech;

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
                sendExactSpeech(this.config.getInitialExactSpeech());
            } else {
                sendResponseCreate(this.config.getInitialResponseInstruction());
            }
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
            if ("conversation.item.input_audio_transcription.completed".equals(type)) {
                handleUserTranscript(string(event, "transcript"));
                return;
            }
            if ("response.created".equals(type)) {
                this.assistantAudioSeen = false;
                this.assistantTranscript.setLength(0);
                return;
            }
            if ("session.updated".equals(type)) {
                flushPendingResponse();
                return;
            }
            if ("response.output_audio_transcript.delta".equals(type)) {
                this.assistantAudioSeen = true;
                this.assistantTranscript.append(string(event, "delta"));
                return;
            }
            if ("response.output_text.delta".equals(type) && !this.assistantAudioSeen) {
                this.assistantTranscript.append(string(event, "delta"));
                return;
            }
            if ("response.output_audio_transcript.done".equals(type)) {
                this.assistantAudioSeen = false;
                completeAssistantTranscript(firstPresent(string(event, "transcript"),
                        this.assistantTranscript.toString()));
                return;
            }
            if ("response.output_text.done".equals(type) && !this.assistantAudioSeen) {
                completeAssistantTranscript(firstPresent(string(event, "text"),
                        this.assistantTranscript.toString()));
            }
        }

        private void handleUserTranscript(String transcript) {
            if (!isPresent(transcript)) {
                return;
            }
            Optional<ResponseView> acknowledged = agentService.acknowledge(this.config.getAgentId(),
                    new EventRequest(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, Event.KIND_OBSERVATION,
                            transcript.trim()),
                    OutputProfile.BACKEND_COMPLEMENT);
            if (acknowledged.isEmpty()) {
                LOGGER.warn("Realtime sideband transcript for unknown agent; callId={} agentId={}",
                        this.config.getCallId(), this.config.getAgentId());
                return;
            }
            ResponseView ack = acknowledged.get();
            String ackSpeech = speechFromEvent(ack.getResponseEvent());
            if (!isPresent(ackSpeech) && ack.isActive() && this.config.getSettings().isGenerateComplement()) {
                agentService.generate(this.config.getAgentId(), List.of("speech"), OutputProfile.BACKEND_COMPLEMENT);
            }
            PolicyResponseView prompt = agentService.prompt(this.config.getAgentId(), OutputProfile.REALTIME_SPEECH)
                    .orElse(null);
            if (isPresent(ackSpeech)) {
                updateSessionThenRespond(RealtimePromptInstructions.systemInstructions(prompt), null, ackSpeech);
            } else {
                updateSessionThenRespond(RealtimePromptInstructions.systemInstructions(prompt),
                        RealtimePromptInstructions.responseInstruction(prompt), null);
            }
        }

        private void completeAssistantTranscript(String transcript) {
            this.assistantTranscript.setLength(0);
            if (!isPresent(transcript)) {
                return;
            }
            if (this.skipNextAssistantPersistence) {
                this.skipNextAssistantPersistence = false;
                return;
            }
            agentService.recordRealtimeAssistantSpeech(this.config.getAgentId(), transcript.trim());
        }

        private void sendSessionUpdate(String instructions) {
            JsonObject session = new JsonObject();
            session.addProperty("type", "realtime");
            if (isPresent(instructions)) {
                session.addProperty("instructions", instructions.trim());
            }
            session.add("output_modalities", GSON.toJsonTree(new String[] { "audio" }));
            JsonObject audio = new JsonObject();
            JsonObject input = new JsonObject();
            String turnDetection = this.config.getSettings().getTurnDetection();
            if ("none".equals(turnDetection)) {
                input.add("turn_detection", null);
            } else {
                JsonObject vad = new JsonObject();
                vad.addProperty("type", turnDetection);
                vad.addProperty("create_response", false);
                vad.addProperty("interrupt_response", false);
                input.add("turn_detection", vad);
            }
            audio.add("input", input);
            if (isPresent(this.config.getSettings().getVoice())) {
                JsonObject output = new JsonObject();
                output.addProperty("voice", this.config.getSettings().getVoice());
                audio.add("output", output);
            }
            session.add("audio", audio);
            JsonObject event = new JsonObject();
            event.addProperty("type", "session.update");
            event.add("session", session);
            send(event);
        }

        private void updateSessionThenRespond(String instructions, String responseInstruction, String exactSpeech) {
            this.pendingResponseInstruction = responseInstruction;
            this.pendingExactSpeech = exactSpeech;
            sendSessionUpdate(instructions);
        }

        private void flushPendingResponse() {
            String exactSpeech = this.pendingExactSpeech;
            String responseInstruction = this.pendingResponseInstruction;
            this.pendingExactSpeech = null;
            this.pendingResponseInstruction = null;
            if (isPresent(exactSpeech)) {
                sendExactSpeech(exactSpeech);
            } else if (isPresent(responseInstruction)) {
                sendResponseCreate(responseInstruction);
            }
        }

        private void sendExactSpeech(String speech) {
            this.skipNextAssistantPersistence = true;
            sendResponseCreate("Say exactly the following text and nothing else. Do not add, remove, paraphrase, "
                    + "or explain.\n" + speech.trim());
        }

        private void sendResponseCreate(String instructions) {
            JsonObject response = new JsonObject();
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

    private static String speechFromEvent(Event event) {
        if (event == null || !Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType())) {
            return null;
        }
        BehaviourPlan plan = BehaviourPlan.fromJson(event.getPayload());
        return plan == null ? null : plan.getSpeech();
    }

    private static String string(JsonObject object, String member) {
        if (object == null || member == null || !object.has(member) || object.get(member).isJsonNull()) {
            return "";
        }
        return object.get(member).getAsString();
    }

    private static String firstPresent(String first, String second) {
        return isPresent(first) ? first : second;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
