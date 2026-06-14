package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.controllers.views.ResponseView;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.spi.OpenAIProperties;

class RealtimeSidebandServiceContractTest {
    private static final Path SIDEBAND_SERVICE = Path.of(
            "src/main/java/ch/zhaw/prometheus/application/RealtimeSidebandService.java");

    @Test
    void userSpeechAcknowledgementUsesRealtimeSpeechProfile() throws IOException {
        String source = Files.readString(SIDEBAND_SERVICE);
        int callStart = source.indexOf("Optional<ResponseView> acknowledged = agentService.acknowledge");
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
    void sidebandBatchesTranscriptCompletionsAndIgnoresKnownAsrHallucination() throws Exception {
        UUID agentId = UUID.randomUUID();
        AgentApplicationService agentService = mock(AgentApplicationService.class);
        when(agentService.acknowledge(eq(agentId), any(EventRequest.class), eq(OutputProfile.REALTIME_SPEECH)))
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
                    .acknowledge(eq(agentId), event.capture(), eq(OutputProfile.REALTIME_SPEECH));
            assertEquals("Bereit?", event.getValue().getPayload());
        } finally {
            service.closeAll();
        }
    }

    @Test
    void sidebandProcessesDuplicateTranscriptItemOnlyOnce() throws Exception {
        UUID agentId = UUID.randomUUID();
        AgentApplicationService agentService = mock(AgentApplicationService.class);
        when(agentService.acknowledge(eq(agentId), any(EventRequest.class), eq(OutputProfile.REALTIME_SPEECH)))
                .thenReturn(Optional.<ResponseView>empty());
        RealtimeSidebandService service = new RealtimeSidebandService(new OpenAIProperties(), agentService);
        Object session = newSidebandSession(service, agentId);
        try {
            emit(session, committed("item_yes"));
            emit(session, completed("item_yes", "event_yes_1", "Nein."));
            emit(session, completed("item_yes", "event_yes_2", "Nein."));

            ArgumentCaptor<EventRequest> event = ArgumentCaptor.forClass(EventRequest.class);
            verify(agentService, timeout(2500).times(1))
                    .acknowledge(eq(agentId), event.capture(), eq(OutputProfile.REALTIME_SPEECH));
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
                new RealtimeCallSettings("marin", "none", false));
        return constructor.newInstance(service, config);
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
}
