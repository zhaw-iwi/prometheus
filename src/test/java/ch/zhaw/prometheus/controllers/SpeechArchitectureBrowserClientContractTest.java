package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SpeechArchitectureBrowserClientContractTest {

    private static final Path VALERIAN_SCRIPT = Path.of("src/main/resources/public/valerian/script.js");
    private static final Path VALERIAN_INDEX = Path.of("src/main/resources/public/valerian/index.html");
    private static final Path MULTILATERAL_LISTEN_SCRIPT = Path.of(
            "src/main/resources/public/multilateral/listen/script.js");
    private static final Path TRANSCRIPTION_CLIENT = Path.of(
            "src/main/resources/public/transcription/client.js");
    private static final Path TRANSCRIPTION_EVENTS = Path.of(
            "src/main/resources/public/transcription/events.js");
    private static final Path TRANSCRIPTION_INGRESS = Path.of(
            "src/main/resources/public/transcription/ingress.js");
    private static final Path SPEECH_PLAYBACK = Path.of(
            "src/main/resources/public/speech/playback.js");

    @Test
    void valerianUsesScopedLiveTranscriptionAsItsOnlySpeechInputTransport() throws IOException {
        String index = Files.readString(VALERIAN_INDEX);
        String script = Files.readString(VALERIAN_SCRIPT);
        String client = Files.readString(TRANSCRIPTION_CLIENT);

        assertContains(index, "data-testid=\"toggle-transcription\"");
        assertContains(index, "data-testid=\"live-transcription-settings\"");
        assertContains(index, "/transcription/browser-global.js");
        assertContains(index, "data-testid=\"transcription-push-to-talk\"");
        assertContains(script, "new api.LiveTranscriptionClient");
        assertContains(script, "new api.ScopedTranscriptIngress");
        assertContains(script, "transcriptionSettingsPanel.apiValues()");
        assertContains(script, "setTranscriptionControlsLocked");
        assertContains(client, "/transcription/capabilities");
        assertContains(client, "/transcription/session");
        assertContains(client, "turnDetectionMode: this.settings.turnDetection.type");
        assertContains(script, "isLikelyAsrHallucination");

        assertDoesNotContain(index, "toggle-realtime");
        assertDoesNotContain(index, "advanced-speech-settings");
        assertDoesNotContain(script, "/realtime/call");
        assertDoesNotContain(script, "Content-Type\": \"application/sdp\"");
        assertDoesNotContain(script, "new RTCPeerConnection");
        assertDoesNotContain(script, "response.create");
        assertDoesNotContain(script, "response.cancel");
        assertDoesNotContain(script, "output_audio_transcript");
        assertDoesNotContain(script, "audio.srcObject = event.streams[0]");
        assertDoesNotContain(script, "REALTIME_MODE_");
        assertDoesNotContain(script, "MediaRecorder");
        assertDoesNotContain(script, "/speech-turn");
        assertDoesNotContain(script, "/speech/latest");
    }

    @Test
    void valerianKeepsInputAndOutputConfigurationAtTheirOwningBoundaries() throws IOException {
        String index = Files.readString(VALERIAN_INDEX);
        String script = Files.readString(VALERIAN_SCRIPT);

        assertContains(index, "Live Transcription Settings");
        assertContains(index, "data-testid=\"speech-output-settings\"");
        assertContains(index, "data-testid=\"speech-voice\"");
        assertContains(index, "data-testid=\"speech-output-speed\"");
        assertContains(index, "data-testid=\"speech-output-device\"");
        assertContains(script, "function speechOutputSettingControls");
        assertContains(script, "function applySelectedSpeechOutputDevice");

        assertDoesNotContain(index, "speech-input-device");
        assertDoesNotContain(index, "speech-complement");
        assertDoesNotContain(index, "speech-barge-in-cancel");
        assertDoesNotContain(index, "speech-echo-guard");
        assertDoesNotContain(index, "speech-reasoning-effort");
        assertDoesNotContain(index, "speech-max-output-tokens");
        assertDoesNotContain(script, "speechSessionSettingControls");
        assertDoesNotContain(script, "getUserMedia({ audio: true })");
        assertDoesNotContain(script, "device.kind === \"audioinput\"");
    }

    @Test
    void multilateralListenerUsesSharedScopedLiveTranscriptionClient() throws IOException {
        String script = Files.readString(MULTILATERAL_LISTEN_SCRIPT);
        String client = Files.readString(TRANSCRIPTION_CLIENT);
        String events = Files.readString(TRANSCRIPTION_EVENTS);

        assertContains(script, "../../transcription/client.js");
        assertContains(script, "new LiveTranscriptionClient");
        assertContains(script, "TranscriptionSettingsPanel");
        assertContains(client, "/transcription/capabilities");
        assertContains(client, "/transcription/session");
        assertContains(client, "gpt-live-transcribe");
        assertContains(events, "unexpected_assistant_event");

        assertDoesNotContain(script, "/realtime/transcription/session");
        assertDoesNotContain(script, "new RTCPeerConnection");
        assertDoesNotContain(script, "response.create");
        assertDoesNotContain(script, "response.cancel");
        assertDoesNotContain(script, "output_modalities");
    }

    @Test
    void finalizedTranscriptsUseOneSharedScopedFullPlanIngress() throws IOException {
        String valerian = Files.readString(VALERIAN_SCRIPT);
        String multilateral = Files.readString(MULTILATERAL_LISTEN_SCRIPT);
        String ingress = Files.readString(TRANSCRIPTION_INGRESS);

        assertContains(valerian, "new api.ScopedTranscriptIngress");
        assertContains(multilateral, "new ScopedTranscriptIngress");
        assertContains(ingress, "/acknowledge?profile=${FULL_PLAN}");
        assertContains(ingress, "type: \"obs.user_utterance\"");
        assertContains(ingress, "X-Prometheus-Access-Code");
        assertContains(ingress, "outputProfile: FULL_PLAN");
        assertDoesNotContain(ingress, "realtime_speech");
        assertDoesNotContain(ingress, "backend_complement");
    }

    @Test
    void valerianSpeaksOnlyLivePersistedBehaviourThroughOrderedOutputOwnership() throws IOException {
        String index = Files.readString(VALERIAN_INDEX);
        String script = Files.readString(VALERIAN_SCRIPT);
        String playback = Files.readString(SPEECH_PLAYBACK);

        assertContains(index, "/speech/browser-global.js");
        assertContains(index, "data-testid=\"speech-playback-status\"");
        assertContains(index, "data-testid=\"stop-speech-playback\"");
        assertContains(script, "[\"behaviour-live\", \"behaviour-replay\"]");
        assertContains(script, "delivery: eventName === \"behaviour-live\" ? \"live\" : \"replay\"");
        assertContains(script, "/behaviours/${encodeURIComponent(item.eventId)}/speech");
        assertContains(script, "transcription.transcriptIngress?.setAccepting(inputEnabled)");
        assertContains(script, "transcription.transcriptionClient.setInputEnabled(inputEnabled)");
        assertContains(playback, "item.delivery !== LIVE_DELIVERY");
        assertContains(playback, "prometheus.valerian.output-lease.v1.");
        assertContains(playback, "this.current.controller.abort(reason)");
        assertDoesNotContain(script, "addEventListener(\"behaviour\",");
    }

    private static void assertContains(String text, String expected) {
        assertTrue(text.contains(expected), "Expected speech client source to contain: " + expected);
    }

    private static void assertDoesNotContain(String text, String unexpected) {
        assertFalse(text.contains(unexpected), "Speech client source must not contain: " + unexpected);
    }
}
