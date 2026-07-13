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
    void publicClientExposesDirectSpeechSynthesisControls() throws IOException {
        String index = Files.readString(INDEX);
        String style = Files.readString(STYLE);

        assertContains(index, "data-testid=\"access-screen\"");
        assertContains(index, "data-testid=\"create-agent\"");
        assertContains(index, "data-testid=\"delete-agent\"");
        assertContains(index, "data-testid=\"create-delete-row\"");
        assertContains(index, "data-testid=\"speech-settings\"");
        assertContains(index, "data-testid=\"speech-settings-guidance\"");
        assertContains(index, "data-testid=\"speech-renderer-status\"");
        assertContains(index, "data-testid=\"speech-text\"");
        assertContains(index, "data-testid=\"load-default-text\"");
        assertContains(index, "data-testid=\"clear-speech-text\"");
        assertContains(index, "data-testid=\"voice-select\"");
        assertContains(index, "data-testid=\"speed-select\"");
        assertContains(index, "data-testid=\"speaker-select\"");
        assertContains(index, "data-testid=\"assistant-audio\"");
        assertContains(index, "value=\"alloy\" selected");
        assertContains(index, "value=\"marin\"");
        assertContains(index, "value=\"cedar\"");
        assertContains(index, "value=\"fable\"");
        assertContains(index, "value=\"nova\"");
        assertContains(index, "value=\"onyx\"");
        assertContains(style, "@import url(\"/style.css\")");
        assertContains(style, ".talk-grid");
        assertContains(style, ".lifecycle-row .btn");
        assertContains(style, ".speech-settings-grid");
        assertContains(style, ".speech-settings-guidance");
        assertContains(style, ".speech-text-heading");
        assertContains(style, "[data-theme=\"dark\"]");

        assertFalse(index.contains("data-testid=\"connect-agent\""));
        assertFalse(index.contains("data-testid=\"disconnect-agent\""));
        assertFalse(index.contains("Realtime"));
    }

    @Test
    void publicClientUsesScopedSpeechAudioContractAndMediaCompletion() throws IOException {
        String script = Files.readString(SCRIPT);

        assertContains(script, "core.talk_to_me");
        assertContains(script, "utility.talk_to_me");
        assertContains(script, "fetchJson(\"/demo/session\"");
        assertContains(script, "scopedFetchJson(\"/demo/agents\"");
        assertContains(script, "agentDefinitionKey: TALK_TO_ME_AGENT_KEY");
        assertContains(script, "/demo/talktome/agents/");
        assertContains(script, "/speech?");
        assertContains(script, "X-Prometheus-Access-Code");
        assertContains(script, "await response.blob()");
        assertContains(script, "URL.createObjectURL(audioBlob)");
        assertContains(script, "audio.addEventListener(\"ended\", handleAudioEnded)");
        assertContains(script, "setStatus(\"speech_status\", \"Speech completed.\"");
        assertContains(script, "new AbortController()");
        assertContains(script, "renderSpeechText(text)");
        assertContains(script, "\"Speech text\\n\"");
        assertContains(script, "selectStoredValue(\"voice_select\", VOICE_STORAGE_KEY, \"alloy\")");
        assertContains(script, "DEFAULT_SPEECH_TEXT");
        assertContains(script, "setSpeechText(DEFAULT_SPEECH_TEXT);");
        assertContains(script, "getElementById(\"load_default_text\").addEventListener");
        assertContains(script, "getElementById(\"clear_speech_text\").addEventListener");
        assertContains(script, "getElementById(\"speech_text\").disabled = !selected || active");
        assertContains(script, "Voice and output speed apply to the next request.");
        assertContains(script, "MAX_TEXT_CODE_POINTS = 2000");

        assertFalse(script.contains("RTCPeerConnection"));
        assertFalse(script.contains("/realtime/call"));
        assertFalse(script.contains("scopedFetch(\"/demo/agents/"));
        assertFalse(script.contains("/acknowledge?profile=realtime_speech"));
        assertFalse(script.contains("response.output_audio_transcript"));
        assertFalse(script.contains("getUserMedia("));
        assertFalse(script.contains("clientSecret"));
    }

    @Test
    void publicClientRefreshesAndRoutesBrowserSpeakerWithoutMicrophonePermission() throws IOException {
        String script = Files.readString(SCRIPT);

        assertContains(script, "navigator.mediaDevices.enumerateDevices()");
        assertContains(script, "device.kind === \"audiooutput\"");
        assertContains(script, "audio.setSinkId(deviceId)");
        assertContains(script, "Speaker selection is not supported by this browser");
        assertFalse(script.contains("getElementById(\"speaker_select\").disabled = connected"));
        assertFalse(script.contains("device.kind === \"audioinput\""));
    }

    private static void assertContains(String text, String expected) {
        assertTrue(text.contains(expected), "Expected client resource to contain: " + expected);
    }
}
