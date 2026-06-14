package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class RealtimeBrowserClientContractTest {

    private static final Path REALTIME_SCRIPT = Path.of("src/main/resources/public/realtime/script.js");
    private static final Path REALTIME_INDEX = Path.of("src/main/resources/public/realtime/index.html");
    private static final Path VALERIAN_SCRIPT = Path.of("src/main/resources/public/valerian/script.js");
    private static final Path VALERIAN_INDEX = Path.of("src/main/resources/public/valerian/index.html");
    private static final Path MULTILATERAL_LISTEN_SCRIPT = Path.of(
            "src/main/resources/public/multilateral/listen/script.js");

    @Test
    void speechRealtimeClientsUsePrometheusBoundRealtimeCallEndpoint() throws IOException {
        for (Path scriptPath : List.of(REALTIME_SCRIPT, VALERIAN_SCRIPT)) {
            String script = Files.readString(scriptPath);

            assertContains(script, "/realtime/call?");
            assertContains(script, "Content-Type\": \"application/sdp\"");
            assertContains(script, "call.callId");
            assertContains(script, "call.sdp");
            assertContains(script, "/realtime/calls/");

            assertDoesNotContain(script, "/realtime/session");
            assertDoesNotContain(script, "clientSecret");
            assertDoesNotContain(script, "sessionInfo.realtimeCallsUrl");
            assertDoesNotContain(script, "Authorization: `Bearer");
            assertDoesNotContain(script, "sessionInfo.realtimeUrl");
            assertDoesNotContain(script, "?model=");
            assertDoesNotContain(script, "/v1/realtime/sessions");
            assertDoesNotContain(script, "realtimeSessionUrl");
        }
    }

    @Test
    void speechRealtimeClientsDoNotOwnPromptOrResponseCreation() throws IOException {
        for (Path scriptPath : List.of(REALTIME_SCRIPT, VALERIAN_SCRIPT)) {
            String script = Files.readString(scriptPath);

            assertContains(script, "response.output_audio_transcript.delta");
            assertContains(script, "response.output_audio_transcript.done");
            assertContains(script, "input_audio_buffer.committed");
            assertContains(script, "TRANSCRIPT_BATCH_DELAY_MS");
            assertContains(script, "isLikelyAsrHallucination");
            assertContains(script, "amara org community");
            assertContains(script, "processedInputItemIds");

            assertDoesNotContain(script, "type: \"session.update\"");
            assertDoesNotContain(script, "type: \"response.create\"");
            assertDoesNotContain(script, "type: \"input_audio_buffer.commit\"");
            assertDoesNotContain(script, "type: \"input_audio_buffer.clear\"");
            assertDoesNotContain(script, "type: \"response.cancel\"");
            assertDoesNotContain(script, "type: \"output_audio_buffer.clear\"");
            assertDoesNotContain(script, "output_modalities: [\"audio\"]");
            assertDoesNotContain(script, "sessionPayload.turn_detection");
            assertDoesNotContain(script, "sessionPayload.voice");
            assertDoesNotContain(script, "sessionPayload.temperature");
            assertDoesNotContain(script, "interrupt_response: false");
            assertDoesNotMatch(script, "(?m)^\\s*modalities\\s*:");
        }
    }

    @Test
    void speechRealtimeClientsDoNotAcknowledgeRealtimeTurnsDirectly() throws IOException {
        for (Path scriptPath : List.of(REALTIME_SCRIPT, VALERIAN_SCRIPT)) {
            String script = Files.readString(scriptPath);

            assertDoesNotContain(script, "ackResponseSpeech");
            assertDoesNotContain(script, "appendAssistantTranscript");
            assertDoesNotContain(script, "handleRealtimeUserTranscript");
            assertDoesNotContain(script, "handleUserTranscript");
            assertDoesNotContain(script, "profile=backend_complement");
            assertDoesNotContain(script, "speakStoredAssistantResponse");
        }
    }

    @Test
    void speechRealtimeClientsExposeSeparateContinuousAndPushToTalkModes() throws IOException {
        for (Path indexPath : List.of(REALTIME_INDEX, VALERIAN_INDEX)) {
            String index = Files.readString(indexPath);

            assertContains(index, "Push to Talk");
            assertContains(index, "Continuous");
            assertContains(index, "value=\"server_vad\"");
            assertContains(index, "value=\"semantic_vad\"");
            assertDoesNotContain(index, "<option value=\"none\"");
        }
        for (Path scriptPath : List.of(REALTIME_SCRIPT, VALERIAN_SCRIPT)) {
            String script = Files.readString(scriptPath);

            assertContains(script, "REALTIME_MODE_CONTINUOUS");
            assertContains(script, "REALTIME_MODE_PUSH_TO_TALK");
            assertContains(script, "activeTurnDetection");
            assertContains(script, "setRealtimeControlsLocked");
        }
    }

    @Test
    void speechRealtimeClientsUseRecordedTurnsForPushToTalk() throws IOException {
        for (Path scriptPath : List.of(REALTIME_SCRIPT, VALERIAN_SCRIPT)) {
            String script = Files.readString(scriptPath);
            int start = script.indexOf("function startPushToTalk");
            int startRecorded = script.indexOf("startRecordedTurn();", start);
            int stop = script.indexOf("function stopPushToTalk");
            int stopRecorded = script.indexOf("stopRecordedTurn();", stop);

            assertTrue(startRecorded > start, "Expected push-to-talk press to start a local recording in "
                    + scriptPath);
            assertTrue(stopRecorded > stop, "Expected push-to-talk release to stop a local recording in "
                    + scriptPath);
            assertContains(script, "window.MediaRecorder");
            assertContains(script, "new MediaRecorder");
            assertContains(script, "new FormData()");
            assertContains(script, "form.append(\"audio\"");
            assertContains(script, "/speech-turn?");
            assertContains(script, "/speech/latest?");
            assertContains(script, "playRecordedSpeechAudio");
            assertContains(script, "playLatestAssistantSpeechForPushToTalk");
            assertDoesNotContain(script, "MANUAL_TURN_COMMIT_DELAY_MS");
            assertDoesNotContain(script, "commitManualTurn");
            assertDoesNotContain(script, "scheduleManualTurnCommit");
            assertDoesNotContain(script, "prepareManualTurn");
            assertDoesNotContain(script, "type: \"input_audio_buffer.commit\"");
            assertDoesNotContain(script, "type: \"input_audio_buffer.clear\"");
        }
    }

    @Test
    void speechRealtimeClientsDoNotDropQueuedTranscriptCandidatesOnInputBufferClear() throws IOException {
        for (Path scriptPath : List.of(REALTIME_SCRIPT, VALERIAN_SCRIPT)) {
            String script = Files.readString(scriptPath);

            assertDoesNotMatch(script,
                    "input_audio_buffer\\.cleared[\\s\\S]{0,160}clearQueued\\w*TranscriptCandidates\\(");
        }
    }

    @Test
    void speechRealtimeClientsExposeCurrentGaVoiceOptions() throws IOException {
        for (Path indexPath : List.of(REALTIME_INDEX, VALERIAN_INDEX)) {
            String index = Files.readString(indexPath);

            assertContains(index, "value=\"cedar\"");
            assertContains(index, "value=\"marin\"");
        }
    }

    @Test
    void multilateralListenerUsesGaTranscriptionSession() throws IOException {
        String script = Files.readString(MULTILATERAL_LISTEN_SCRIPT);

        assertContains(script, "/realtime/transcription/session");
        assertContains(script, "params.set(\"agentId\", session.agentId)");
        assertContains(script, "input_audio_buffer.commit");
        assertContains(script, "gpt-realtime-whisper");
        assertContains(script, "data.delta || data.transcript");
        assertContains(script, "isLikelyAsrHallucination");
        assertContains(script, "amara org community");

        assertDoesNotContain(script, "type: \"session.update\"");
        assertDoesNotContain(script, "type: \"response.create\"");
        assertDoesNotContain(script, "type: \"realtime\"");
        assertDoesNotContain(script, "output_modalities");
        assertDoesNotContain(script, "create_response");
        assertDoesNotContain(script, "response.cancel");
        assertDoesNotContain(script, "output_audio_buffer.clear");
        assertDoesNotContain(script, "interrupt_response");
    }

    private static void assertContains(String text, String expected) {
        assertTrue(text.contains(expected), "Expected realtime client script to contain: " + expected);
    }

    private static void assertDoesNotContain(String text, String unexpected) {
        assertFalse(text.contains(unexpected), "Realtime client script must not contain: " + unexpected);
    }

    private static void assertDoesNotMatch(String text, String regex) {
        assertFalse(Pattern.compile(regex).matcher(text).find(), "Realtime client script must not match: " + regex);
    }
}
