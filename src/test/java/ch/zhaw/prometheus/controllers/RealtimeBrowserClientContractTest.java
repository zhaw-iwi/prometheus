package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class RealtimeBrowserClientContractTest {

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
    void valerianSpeechClientUsesPrometheusBoundRealtimeCallEndpoint() throws IOException {
        String script = Files.readString(VALERIAN_SCRIPT);

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

    @Test
    void valerianSpeechClientDoesNotOwnPromptOrResponseCreation() throws IOException {
        String script = Files.readString(VALERIAN_SCRIPT);

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
        assertDoesNotContain(script, "type: \"output_audio_buffer.clear\"");
        assertDoesNotContain(script, "output_modalities: [\"audio\"]");
        assertDoesNotContain(script, "sessionPayload.turn_detection");
        assertDoesNotContain(script, "sessionPayload.voice");
        assertDoesNotContain(script, "sessionPayload.temperature");
        assertDoesNotContain(script, "interrupt_response: false");
        assertDoesNotMatch(script, "(?m)^\\s*modalities\\s*:");
    }

    @Test
    void valerianSpeechClientDoesNotAcknowledgeRealtimeTurnsDirectly() throws IOException {
        String script = Files.readString(VALERIAN_SCRIPT);

        assertDoesNotContain(script, "ackResponseSpeech");
        assertDoesNotContain(script, "appendAssistantTranscript");
        assertDoesNotContain(script, "handleRealtimeUserTranscript");
        assertDoesNotContain(script, "handleUserTranscript");
        assertDoesNotContain(script, "profile=backend_complement");
        assertDoesNotContain(script, "speakStoredAssistantResponse");
    }

    @Test
    void valerianSpeechClientExposesDeterministicLiveTranscriptionModes() throws IOException {
        String index = Files.readString(VALERIAN_INDEX);
        String script = Files.readString(VALERIAN_SCRIPT);
        String client = Files.readString(TRANSCRIPTION_CLIENT);

        assertContains(index, "Continuous");
        assertContains(index, "Live Transcription Settings");
        assertContains(index, "/transcription/browser-global.js");
        assertContains(index, "value=\"server_vad\"");
        assertContains(index, "value=\"semantic_vad\"");
        assertDoesNotContain(index, "<option value=\"none\"");
        assertContains(index, "transcription_push_to_talk");

        assertContains(script, "REALTIME_MODE_CONTINUOUS");
        assertContains(script, "LiveTranscriptionClient");
        assertContains(script, "transcriptionSettingsPanel.apiValues()");
        assertContains(script, "setRealtimeControlsLocked");
        assertContains(client, "turnDetectionMode: this.settings.turnDetection.type");
        assertDoesNotContain(script, "REALTIME_MODE_PUSH_TO_TALK");
        assertDoesNotContain(script, "MediaRecorder");
        assertDoesNotContain(script, "new FormData()");
        assertDoesNotContain(script, "form.append(\"audio\"");
        assertDoesNotContain(script, "/speech-turn?");
        assertDoesNotContain(script, "/speech/latest?");
        assertDoesNotContain(script, "playRecordedSpeechAudio");
        assertDoesNotContain(script, "playLatestAssistantSpeechForPushToTalk");
        assertDoesNotContain(script, "startPushToTalk");
        assertDoesNotContain(script, "stopPushToTalk");
    }

    @Test
    void valerianSpeechClientDoesNotDropQueuedTranscriptCandidatesOnInputBufferClear() throws IOException {
        String script = Files.readString(VALERIAN_SCRIPT);

        assertDoesNotMatch(script,
                "input_audio_buffer\\.cleared[\\s\\S]{0,160}clearQueued\\w*TranscriptCandidates\\(");
    }

    @Test
    void valerianSpeechClientExposesCurrentGaVoiceOptions() throws IOException {
        String index = Files.readString(VALERIAN_INDEX);

        assertContains(index, "value=\"cedar\"");
        assertContains(index, "value=\"marin\"");
    }

    @Test
    void multilateralListenerUsesSharedScopedLiveTranscriptionClient() throws IOException {
        String script = Files.readString(MULTILATERAL_LISTEN_SCRIPT);
        String client = Files.readString(TRANSCRIPTION_CLIENT);
        String events = Files.readString(TRANSCRIPTION_EVENTS);

        assertContains(script, "../../transcription/client.js");
        assertContains(script, "new LiveTranscriptionClient");
        assertContains(script, "TranscriptionSettingsPanel");
        assertContains(script, "isLikelyAsrHallucination");
        assertContains(script, "amara org community");
        assertContains(client, "/transcription/capabilities");
        assertContains(client, "/transcription/session");
        assertContains(client, "gpt-live-transcribe");
        assertContains(events, "unexpected_assistant_event");

        assertDoesNotContain(script, "/realtime/transcription/session");
        assertDoesNotContain(script, "new RTCPeerConnection");
        assertDoesNotContain(script, "type: \"response.create\"");
        assertDoesNotContain(script, "type: \"realtime\"");
        assertDoesNotContain(script, "output_modalities");
        assertDoesNotContain(script, "create_response");
        assertDoesNotContain(script, "response.cancel");
        assertDoesNotContain(script, "output_audio_buffer.clear");
        assertDoesNotContain(script, "interrupt_response");
    }

    @Test
    void finalizedTranscriptsUseOneSharedScopedFullPlanIngress() throws IOException {
        String valerian = Files.readString(VALERIAN_SCRIPT);
        String multilateral = Files.readString(MULTILATERAL_LISTEN_SCRIPT);
        String ingress = Files.readString(TRANSCRIPTION_INGRESS);

        assertContains(valerian, "new api.ScopedTranscriptIngress");
        assertContains(valerian, "handleAcceptedLiveTranscript");
        assertContains(multilateral, "../../transcription/ingress.js");
        assertContains(multilateral, "new ScopedTranscriptIngress");
        assertContains(ingress, "/acknowledge?profile=${FULL_PLAN}");
        assertContains(ingress, "type: \"obs.user_utterance\"");
        assertContains(ingress, "X-Prometheus-Access-Code");
        assertContains(ingress, "outputProfile: FULL_PLAN");
        assertDoesNotContain(ingress, "realtime_speech");
        assertDoesNotContain(ingress, "backend_complement");
        assertDoesNotContain(multilateral, "`/${session.agentId}/acknowledge`");
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
        assertContains(script, "realtime.transcriptIngress?.setAccepting(inputEnabled)");
        assertContains(script, "realtime.transcriptionClient.setInputEnabled(inputEnabled)");
        assertContains(script, "applySelectedSpeechOutputDevice()");
        assertContains(playback, "item.delivery !== LIVE_DELIVERY");
        assertContains(playback, "this.completed = new Set()");
        assertContains(playback, "this.failed = new Set()");
        assertContains(playback, "this.skipped = new Set()");
        assertContains(playback, "prometheus.valerian.output-lease.v1.");
        assertContains(playback, "this.current.controller.abort(reason)");
        assertDoesNotContain(script, "addEventListener(\"behaviour\",");
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
