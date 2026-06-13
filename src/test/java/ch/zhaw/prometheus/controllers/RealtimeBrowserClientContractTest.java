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
    void realtimeClientsUseGaWebRtcCallsEndpoint() throws IOException {
        for (Path scriptPath : allRealtimeScripts()) {
            String script = Files.readString(scriptPath);

            assertContains(script, "sessionInfo.realtimeCallsUrl");
            assertDoesNotContain(script, "sessionInfo.realtimeUrl");
            assertDoesNotContain(script, "?model=");
            assertDoesNotContain(script, "/v1/realtime/sessions");
            assertDoesNotContain(script, "realtimeSessionUrl");
        }
    }

    @Test
    void speechRealtimeClientsSendGaSessionUpdatePayload() throws IOException {
        for (Path scriptPath : List.of(REALTIME_SCRIPT, VALERIAN_SCRIPT)) {
            String script = Files.readString(scriptPath);

            assertContains(script, "type: \"session.update\"");
            assertContains(script, "type: \"realtime\"");
            assertContains(script, "output_modalities: [\"audio\"]");
            assertContains(script, "audio.input = {");
            assertContains(script, "turn_detection: {");
            assertContains(script, "interrupt_response: false");
            assertContains(script, "sessionPayload.audio = audio;");

            assertDoesNotContain(script, "sessionPayload.turn_detection");
            assertDoesNotContain(script, "sessionPayload.voice");
            assertDoesNotContain(script, "sessionPayload.temperature");
            assertDoesNotMatch(script, "(?m)^\\s*modalities\\s*:");
        }
    }

    @Test
    void speechRealtimeClientsSendGaResponseCreatePayloadAndEvents() throws IOException {
        for (Path scriptPath : List.of(REALTIME_SCRIPT, VALERIAN_SCRIPT)) {
            String script = Files.readString(scriptPath);

            assertContains(script, "type: \"response.create\"");
            assertContains(script, "output_modalities: [\"audio\"]");
            assertContains(script, "response.output_audio_transcript.delta");
            assertContains(script, "response.output_audio_transcript.done");

            assertDoesNotContain(script, "response.audio_transcript.delta");
            assertDoesNotContain(script, "response.audio_transcript.done");
        }
    }

    @Test
    void speechRealtimeClientsPreferAcknowledgeResponseEventOverDuplicateGeneration() throws IOException {
        for (Path scriptPath : List.of(REALTIME_SCRIPT, VALERIAN_SCRIPT)) {
            String script = Files.readString(scriptPath);

            assertContains(script, "ackResponseSpeech");
            assertContains(script, "speakStoredAssistantResponse(ackResponseSpeech)");
            assertContains(script, "!ackResponseSpeech");
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
        assertContains(script, "input_audio_buffer.commit");
        assertContains(script, "gpt-realtime-whisper");
        assertContains(script, "data.delta || data.transcript");

        assertDoesNotContain(script, "type: \"session.update\"");
        assertDoesNotContain(script, "type: \"realtime\"");
        assertDoesNotContain(script, "output_modalities");
        assertDoesNotContain(script, "create_response");
        assertDoesNotContain(script, "interrupt_response");
    }

    private static List<Path> allRealtimeScripts() {
        return List.of(REALTIME_SCRIPT, VALERIAN_SCRIPT, MULTILATERAL_LISTEN_SCRIPT);
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
