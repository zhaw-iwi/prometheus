package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.controllers.views.ResponseView;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.spi.OpenAIProperties;

class RealtimeSidebandServiceContractTest {
    private static final Path SIDEBAND_SERVICE = Path.of(
            "src/main/java/ch/zhaw/prometheus/application/RealtimeSidebandService.java");

    @Test
    void userSpeechAcknowledgementUsesRealtimeSpeechProfile() throws IOException {
        String source = Files.readString(SIDEBAND_SERVICE);
        int callStart = source.indexOf("Optional<ResponseView> acknowledged = agentService.acknowledgeAndGenerate");
        assertTrue(callStart >= 0);
        int profileIndex = source.indexOf("OutputProfile.REALTIME_SPEECH", callStart);
        assertTrue(profileIndex > callStart);
        int callEnd = source.indexOf(");", profileIndex);
        assertTrue(callEnd > profileIndex);
        String acknowledgeCall = source.substring(callStart, callEnd);

        assertTrue(acknowledgeCall.contains("OutputProfile.REALTIME_SPEECH"));
        assertFalse(acknowledgeCall.contains("OutputProfile.BACKEND_COMPLEMENT"));
    }

    @Test
    void sidebandDoesNotCreateFreeFormAssistantResponses() throws IOException {
        String source = Files.readString(SIDEBAND_SERVICE);

        assertFalse(source.contains("pendingResponseInstruction"));
        assertFalse(source.contains("RealtimePromptInstructions.responseInstruction"));
        assertFalse(source.contains("recordRealtimeAssistantSpeech"));
    }

    @Test
    void sidebandCreatesOutOfBandExactSpeechResponsesWithEmptyContext() throws Exception {
        UUID agentId = UUID.randomUUID();
        RealtimeSidebandService service = new RealtimeSidebandService(new OpenAIProperties(),
                mock(AgentApplicationService.class));
        Object session = newSidebandSession(service, agentId);
        WebSocket socket = mock(WebSocket.class);
        try {
            Field webSocketField = session.getClass().getDeclaredField("webSocket");
            webSocketField.setAccessible(true);
            webSocketField.set(session, socket);

            Method sendExactSpeech = session.getClass().getDeclaredMethod("sendExactSpeech", String.class);
            sendExactSpeech.setAccessible(true);
            sendExactSpeech.invoke(session, "Ist es etwas, das man anfassen kann?");

            ArgumentCaptor<CharSequence> payload = ArgumentCaptor.forClass(CharSequence.class);
            verify(socket).sendText(payload.capture(), eq(true));
            JsonObject event = JsonParser.parseString(payload.getValue().toString()).getAsJsonObject();
            JsonObject response = event.getAsJsonObject("response");

            assertEquals("response.create", event.get("type").getAsString());
            assertEquals("none", response.get("conversation").getAsString());
            assertEquals(0, response.getAsJsonArray("input").size());
            assertEquals("audio", response.getAsJsonArray("output_modalities").get(0).getAsString());
            assertTrue(response.get("instructions").getAsString()
                    .contains("Ist es etwas, das man anfassen kann?"));
        } finally {
            service.closeAll();
        }
    }

    @Test
    void sidebandSessionUpdatePreservesRealtimeTuningOptions() throws Exception {
        UUID agentId = UUID.randomUUID();
        RealtimeCallSettings settings = new RealtimeCallSettings("Cedar", "server_vad", false,
                "0.6", "120", "800", null, null, "true", "near_field", "1.1", "medium", "256", "true");
        RealtimeSidebandService service = new RealtimeSidebandService(new OpenAIProperties(),
                mock(AgentApplicationService.class));
        Object session = newSidebandSession(service, agentId, settings);
        WebSocket socket = mock(WebSocket.class);
        try {
            setWebSocket(session, socket);

            Method sendSessionUpdate = session.getClass().getDeclaredMethod("sendSessionUpdate", String.class);
            sendSessionUpdate.setAccessible(true);
            sendSessionUpdate.invoke(session, "PROMETHEUS instructions");

            ArgumentCaptor<CharSequence> payload = ArgumentCaptor.forClass(CharSequence.class);
            verify(socket).sendText(payload.capture(), eq(true));
            JsonObject event = JsonParser.parseString(payload.getValue().toString()).getAsJsonObject();
            JsonObject updatedSession = event.getAsJsonObject("session");
            JsonObject audio = updatedSession.getAsJsonObject("audio");
            JsonObject audioInput = audio.getAsJsonObject("input");
            JsonObject turnDetection = audioInput.getAsJsonObject("turn_detection");

            assertEquals("session.update", event.get("type").getAsString());
            assertEquals("realtime", updatedSession.get("type").getAsString());
            assertEquals("PROMETHEUS instructions", updatedSession.get("instructions").getAsString());
            assertEquals("audio", updatedSession.getAsJsonArray("output_modalities").get(0).getAsString());
            assertEquals("server_vad", turnDetection.get("type").getAsString());
            assertEquals(0.6, turnDetection.get("threshold").getAsDouble(), 0.0001);
            assertEquals(120, turnDetection.get("prefix_padding_ms").getAsInt());
            assertEquals(800, turnDetection.get("silence_duration_ms").getAsInt());
            assertFalse(turnDetection.get("create_response").getAsBoolean());
            assertTrue(turnDetection.get("interrupt_response").getAsBoolean());
            assertEquals("near_field", audioInput.getAsJsonObject("noise_reduction").get("type").getAsString());
            assertEquals("cedar", audio.getAsJsonObject("output").get("voice").getAsString());
            assertEquals(1.1, audio.getAsJsonObject("output").get("speed").getAsDouble(), 0.0001);
            assertEquals("medium", updatedSession.getAsJsonObject("reasoning").get("effort").getAsString());
            assertEquals(256, updatedSession.get("max_output_tokens").getAsInt());
            assertEquals("item.input_audio_transcription.logprobs",
                    updatedSession.getAsJsonArray("include").get(0).getAsString());
        } finally {
            service.closeAll();
        }
    }

    @Test
    void transcriptBatchDelayDefaultsLowAndCanBeConfigured() throws Exception {
        AgentApplicationService agentService = mock(AgentApplicationService.class);
        RealtimeSidebandService defaultService = new RealtimeSidebandService(new OpenAIProperties(), agentService);
        try {
            Field delay = RealtimeSidebandService.class.getDeclaredField("transcriptBatchDelayMs");
            delay.setAccessible(true);
            assertEquals(RealtimeSidebandService.DEFAULT_TRANSCRIPT_BATCH_DELAY_MS, delay.getLong(defaultService));

            OpenAIProperties properties = new OpenAIProperties();
            properties.setRealtimeTranscriptBatchDelayMs(125L);
            RealtimeSidebandService configuredService = new RealtimeSidebandService(properties, agentService);
            try {
                assertEquals(125L, delay.getLong(configuredService));
            } finally {
                configuredService.closeAll();
            }
        } finally {
            defaultService.closeAll();
        }
    }

    @Test
    void activeSidebandSpeaksPublishedAssistantBehaviourSpeech() throws Exception {
        UUID agentId = UUID.randomUUID();
        AgentApplicationService agentService = mock(AgentApplicationService.class);
        RealtimeSidebandService service = new RealtimeSidebandService(new OpenAIProperties(), agentService);
        Object session = newSidebandSession(service, agentId);
        WebSocket socket = mock(WebSocket.class);
        try {
            setWebSocket(session, socket);
            registerSession(service, "call_test", agentId, session);

            Event event = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                    "{\"speech\":\"Du gewinnst diese Runde.\"}");
            service.speakPublishedAssistantBehaviour(new AssistantBehaviourPublishedEvent(agentId, event));
            emit(session, "{\"type\":\"session.updated\"}");

            ArgumentCaptor<CharSequence> payload = ArgumentCaptor.forClass(CharSequence.class);
            verify(socket, times(2)).sendText(payload.capture(), eq(true));
            JsonObject sessionUpdate = JsonParser.parseString(payload.getAllValues().get(0).toString())
                    .getAsJsonObject();
            JsonObject responseCreate = JsonParser.parseString(payload.getAllValues().get(1).toString())
                    .getAsJsonObject();

            assertEquals("session.update", sessionUpdate.get("type").getAsString());
            assertEquals("response.create", responseCreate.get("type").getAsString());
            assertTrue(responseCreate.getAsJsonObject("response").get("instructions").getAsString()
                    .contains("Du gewinnst diese Runde."));
        } finally {
            service.closeAll();
        }
    }

    @Test
    void activeSidebandIgnoresPublishedAssistantBehaviourWithoutSpeech() throws Exception {
        UUID agentId = UUID.randomUUID();
        RealtimeSidebandService service = new RealtimeSidebandService(new OpenAIProperties(),
                mock(AgentApplicationService.class));
        Object session = newSidebandSession(service, agentId);
        WebSocket socket = mock(WebSocket.class);
        try {
            setWebSocket(session, socket);
            registerSession(service, "call_test", agentId, session);

            Event event = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                    "{\"nonVerbal\":{\"gesture\":\"OPEN_QUESTION\"}}");
            service.speakPublishedAssistantBehaviour(new AssistantBehaviourPublishedEvent(agentId, event));

            verifyNoInteractions(socket);
        } finally {
            service.closeAll();
        }
    }

    @Test
    void activeSidebandSpeaksPublishedAssistantBehaviourOnlyOnce() throws Exception {
        UUID agentId = UUID.randomUUID();
        RealtimeSidebandService service = new RealtimeSidebandService(new OpenAIProperties(),
                mock(AgentApplicationService.class));
        Object session = newSidebandSession(service, agentId);
        WebSocket socket = mock(WebSocket.class);
        try {
            setWebSocket(session, socket);
            registerSession(service, "call_test", agentId, session);

            Event event = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                    "{\"speech\":\"Noch einmal?\"}");
            service.speakPublishedAssistantBehaviour(new AssistantBehaviourPublishedEvent(agentId, event));
            emit(session, "{\"type\":\"session.updated\"}");
            clearInvocations(socket);

            service.speakPublishedAssistantBehaviour(new AssistantBehaviourPublishedEvent(agentId, event));
            emit(session, "{\"type\":\"session.updated\"}");

            verifyNoInteractions(socket);
        } finally {
            service.closeAll();
        }
    }

    @Test
    void sidebandBatchesTranscriptCompletionsAndIgnoresKnownAsrHallucination() throws Exception {
        UUID agentId = UUID.randomUUID();
        AgentApplicationService agentService = mock(AgentApplicationService.class);
        when(agentService.acknowledgeAndGenerate(eq(agentId), any(EventRequest.class),
                eq(OutputProfile.REALTIME_SPEECH)))
                .thenReturn(Optional.<ResponseView>empty());
        RealtimeSidebandService service = new RealtimeSidebandService(new OpenAIProperties(), agentService);
        Object session = newSidebandSession(service, agentId);
        try {
            emit(session, committed("item_noise"));
            emit(session, completed("item_noise", "event_noise", "Untertitel der Amara.org-Community"));
            emit(session, committed("item_real"));
            emit(session, completed("item_real", "event_real", "Bereit?"));

            ArgumentCaptor<EventRequest> event = ArgumentCaptor.forClass(EventRequest.class);
            verify(agentService, timeout(2500).times(1))
                    .acknowledgeAndGenerate(eq(agentId), event.capture(), eq(OutputProfile.REALTIME_SPEECH));
            assertEquals("Bereit?", event.getValue().getPayload());
        } finally {
            service.closeAll();
        }
    }

    @Test
    void sidebandUsesCombinedBackendTurnForRealtimeSpeech() throws Exception {
        UUID agentId = UUID.randomUUID();
        Event speech = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                "{\"speech\":\"Die Runde ist beendet. Starte bitte eine neue Sitzung, wenn du weiterspielen moechtest.\"}");
        AgentApplicationService agentService = mock(AgentApplicationService.class);
        when(agentService.acknowledgeAndGenerate(eq(agentId), any(EventRequest.class),
                eq(OutputProfile.REALTIME_SPEECH)))
                .thenReturn(Optional.of(new ResponseView(speech, false)));
        RealtimeSidebandService service = new RealtimeSidebandService(new OpenAIProperties(), agentService);
        Object session = newSidebandSession(service, agentId);
        try {
            emit(session, committed("item_final"));
            emit(session, completed("item_final", "event_final", "Koennen wir nochmals spielen?"));

            verify(agentService, timeout(2500))
                    .acknowledgeAndGenerate(eq(agentId), any(EventRequest.class), eq(OutputProfile.REALTIME_SPEECH));
            verify(agentService, times(0)).generate(eq(agentId), isNull(), eq(OutputProfile.REALTIME_SPEECH));
        } finally {
            service.closeAll();
        }
    }

    @Test
    void sidebandProcessesDuplicateTranscriptItemOnlyOnce() throws Exception {
        UUID agentId = UUID.randomUUID();
        AgentApplicationService agentService = mock(AgentApplicationService.class);
        when(agentService.acknowledgeAndGenerate(eq(agentId), any(EventRequest.class),
                eq(OutputProfile.REALTIME_SPEECH)))
                .thenReturn(Optional.<ResponseView>empty());
        RealtimeSidebandService service = new RealtimeSidebandService(new OpenAIProperties(), agentService);
        Object session = newSidebandSession(service, agentId);
        try {
            emit(session, committed("item_yes"));
            emit(session, completed("item_yes", "event_yes_1", "Nein."));
            emit(session, completed("item_yes", "event_yes_2", "Nein."));

            ArgumentCaptor<EventRequest> event = ArgumentCaptor.forClass(EventRequest.class);
            verify(agentService, timeout(2500).times(1))
                    .acknowledgeAndGenerate(eq(agentId), event.capture(), eq(OutputProfile.REALTIME_SPEECH));
            assertEquals("Nein.", event.getValue().getPayload());
        } finally {
            service.closeAll();
        }
    }

    @Test
    void sidebandDoesNotDropCompletedTranscriptWhenInputBufferIsClearedBeforeBatchFlush() throws Exception {
        UUID agentId = UUID.randomUUID();
        AgentApplicationService agentService = mock(AgentApplicationService.class);
        when(agentService.acknowledgeAndGenerate(eq(agentId), any(EventRequest.class),
                eq(OutputProfile.REALTIME_SPEECH)))
                .thenReturn(Optional.<ResponseView>empty());
        RealtimeSidebandService service = new RealtimeSidebandService(new OpenAIProperties(), agentService);
        Object session = newSidebandSession(service, agentId);
        try {
            emit(session, committed("item_yes"));
            emit(session, completed("item_yes", "event_yes", "Nein."));
            emit(session, cleared());

            ArgumentCaptor<EventRequest> event = ArgumentCaptor.forClass(EventRequest.class);
            verify(agentService, timeout(2500).times(1))
                    .acknowledgeAndGenerate(eq(agentId), event.capture(), eq(OutputProfile.REALTIME_SPEECH));
            assertEquals("Nein.", event.getValue().getPayload());
        } finally {
            service.closeAll();
        }
    }

    @Test
    void sidebandDropsPureKnownAsrHallucinationBatch() throws Exception {
        UUID agentId = UUID.randomUUID();
        AgentApplicationService agentService = mock(AgentApplicationService.class);
        RealtimeSidebandService service = new RealtimeSidebandService(new OpenAIProperties(), agentService);
        Object session = newSidebandSession(service, agentId);
        try {
            emit(session, committed("item_noise"));
            emit(session, completed("item_noise", "event_noise", "Untertitel der Amara.org-Community"));
            Thread.sleep(1200);

            verifyNoInteractions(agentService);
        } finally {
            service.closeAll();
        }
    }

    private static Object newSidebandSession(RealtimeSidebandService service, UUID agentId) throws Exception {
        return newSidebandSession(service, agentId, new RealtimeCallSettings("marin", "server_vad", false));
    }

    private static Object newSidebandSession(RealtimeSidebandService service, UUID agentId,
            RealtimeCallSettings settings) throws Exception {
        Class<?> sidebandSession = null;
        for (Class<?> candidate : RealtimeSidebandService.class.getDeclaredClasses()) {
            if ("SidebandSession".equals(candidate.getSimpleName())) {
                sidebandSession = candidate;
                break;
            }
        }
        assertTrue(sidebandSession != null);
        Constructor<?> constructor = sidebandSession.getDeclaredConstructor(RealtimeSidebandService.class,
                RealtimeSidebandSessionConfig.class);
        constructor.setAccessible(true);
        RealtimeSidebandSessionConfig config = new RealtimeSidebandSessionConfig(
                "call_test", "wss://example.test/realtime", agentId, "instructions", null,
                settings);
        return constructor.newInstance(service, config);
    }

    private static void setWebSocket(Object session, WebSocket socket) throws Exception {
        Field webSocketField = session.getClass().getDeclaredField("webSocket");
        webSocketField.setAccessible(true);
        webSocketField.set(session, socket);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void registerSession(RealtimeSidebandService service, String callId, UUID agentId, Object session)
            throws Exception {
        Field sessions = RealtimeSidebandService.class.getDeclaredField("sessions");
        sessions.setAccessible(true);
        ((Map) sessions.get(service)).put(callId, session);

        Field callIdsByAgent = RealtimeSidebandService.class.getDeclaredField("callIdsByAgent");
        callIdsByAgent.setAccessible(true);
        ((Map<UUID, String>) callIdsByAgent.get(service)).put(agentId, callId);
    }

    private static void emit(Object session, String eventJson) throws Exception {
        Method handleRawEvent = session.getClass().getDeclaredMethod("handleRawEvent", String.class);
        handleRawEvent.setAccessible(true);
        handleRawEvent.invoke(session, eventJson);
    }

    private static String committed(String itemId) {
        return "{\"type\":\"input_audio_buffer.committed\",\"item_id\":\"" + itemId + "\"}";
    }

    private static String completed(String itemId, String eventId, String transcript) {
        return "{\"type\":\"conversation.item.input_audio_transcription.completed\",\"item_id\":\"" + itemId
                + "\",\"event_id\":\"" + eventId + "\",\"transcript\":\"" + transcript + "\"}";
    }

    private static String cleared() {
        return "{\"type\":\"input_audio_buffer.cleared\"}";
    }
}
