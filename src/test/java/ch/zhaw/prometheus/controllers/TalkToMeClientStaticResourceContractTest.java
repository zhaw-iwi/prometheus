package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TalkToMeClientStaticResourceContractTest {
    private static final Path INDEX = Path.of("src/main/resources/public/talktome/index.html");
    private static final Path SCRIPT = Path.of("src/main/resources/public/talktome/script.js");
    private static final Path STYLE = Path.of("src/main/resources/public/talktome/talktome.css");

    @Test
    void publicClientExposesReducedValerianStyleLifecycleAndSpeechControls() throws IOException {
        String index = Files.readString(INDEX);
        String style = Files.readString(STYLE);

        assertContains(index, "data-testid=\"access-screen\"");
        assertContains(index, "data-testid=\"create-agent\"");
        assertContains(index, "data-testid=\"connect-agent\"");
        assertContains(index, "data-testid=\"disconnect-agent\"");
        assertContains(index, "data-testid=\"delete-agent\"");
        assertContains(index, "data-testid=\"speech-text\"");
        assertContains(index, "data-testid=\"voice-select\"");
        assertContains(index, "data-testid=\"speed-select\"");
        assertContains(index, "data-testid=\"speaker-select\"");
        assertContains(index, "data-testid=\"refresh-speakers\"");
        assertContains(index, "data-testid=\"assistant-audio\"");
        assertContains(index, "value=\"marin\"");
        assertContains(index, "value=\"cedar\"");
        assertContains(style, "@import url(\"/style.css\")");
        assertContains(style, ".talk-grid");
        assertContains(style, "[data-theme=\"dark\"]");
    }

    @Test
    void publicClientUsesScopedPrometheusAgentAndRealtimeContracts() throws IOException {
        String script = Files.readString(SCRIPT);

        assertContains(script, "core.talk_to_me");
        assertContains(script, "utility.talk_to_me");
        assertContains(script, "fetchJson(\"/demo/session\"");
        assertContains(script, "scopedFetchJson(\"/demo/agents\"");
        assertContains(script, "agentDefinitionKey: TALK_TO_ME_AGENT_KEY");
        assertContains(script, "/acknowledge?profile=realtime_speech");
        assertContains(script, "/realtime/call?");
        assertContains(script, "generateComplement: \"false\"");
        assertContains(script, "X-Prometheus-Access-Code");
        assertContains(script, "addTransceiver(\"audio\", { direction: \"recvonly\" })");
        assertContains(script, "response.output_audio_transcript.delta");
        assertContains(script, "response.cancel");
        assertContains(script, "output_audio_buffer.clear");
        assertContains(script, "MAX_TEXT_CODE_POINTS = 2000");

        assertFalse(script.contains("getUserMedia("));
        assertFalse(script.contains("type: \"response.create\""));
        assertFalse(script.contains("type: \"session.update\""));
        assertFalse(script.contains("clientSecret"));
        assertFalse(script.contains("Authorization: `Bearer"));
    }

    @Test
    void publicClientRefreshesAndRoutesBrowserSpeakerWithoutMicrophonePermission() throws IOException {
        String script = Files.readString(SCRIPT);

        assertContains(script, "navigator.mediaDevices.enumerateDevices()");
        assertContains(script, "device.kind === \"audiooutput\"");
        assertContains(script, "audio.setSinkId(deviceId)");
        assertContains(script, "Speaker selection is not supported by this browser");
        assertFalse(script.contains("device.kind === \"audioinput\""));
    }

    private static void assertContains(String text, String expected) {
        assertTrue(text.contains(expected), "Expected client resource to contain: " + expected);
    }
}
