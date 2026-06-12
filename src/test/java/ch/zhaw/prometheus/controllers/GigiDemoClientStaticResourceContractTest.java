package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class GigiDemoClientStaticResourceContractTest {
    private static final Path INDEX = Path.of("src/main/resources/public/gigi-demo/index.html");
    private static final Path SCRIPT = Path.of("src/main/resources/public/gigi-demo/script.js");

    @Test
    void gigiDemoClientExposesSinglePageAgentAndInteractionControls() throws IOException {
        String index = Files.readString(INDEX);

        assertTrue(index.contains("<script src=\"script.js\"></script>"));
        assertTrue(index.contains("data-testid=\"agent-select\""));
        assertTrue(index.contains("data-testid=\"agent-id-input\""));
        assertTrue(index.contains("data-testid=\"connect-agent\""));
        assertTrue(index.contains("data-testid=\"start-agent\""));
        assertTrue(index.contains("data-testid=\"reset-agent\""));
        assertTrue(index.contains("data-testid=\"text-input\""));
        assertTrue(index.contains("data-testid=\"send-text\""));
        assertTrue(index.contains("data-testid=\"message-list\""));
    }

    @Test
    void gigiDemoClientExposesRealtimeSpeechControls() throws IOException {
        String index = Files.readString(INDEX);
        String script = Files.readString(SCRIPT);

        assertTrue(index.contains("data-testid=\"toggle-realtime\""));
        assertTrue(index.contains("data-testid=\"voice-select\""));
        assertTrue(index.contains("data-testid=\"turn-detection-select\""));
        assertTrue(index.contains("data-testid=\"push-to-talk\""));
        assertTrue(index.contains("data-testid=\"generate-side-behaviour\""));

        assertTrue(script.contains("/prompt?profile=realtime_speech"));
        assertTrue(script.contains("/realtime/session"));
        assertTrue(script.contains("sessionInfo.realtimeCallsUrl"));
        assertTrue(script.contains("output_modalities"));
        assertTrue(script.contains("turn_detection"));
        assertTrue(script.contains("input_audio_buffer.commit"));
        assertTrue(script.contains("profile: \"backend_complement\""));
        assertTrue(script.contains("outputProfile"));
    }

    @Test
    void gigiDemoClientExposesUnifiedVisualSensingControls() throws IOException {
        String index = Files.readString(INDEX);
        String script = Files.readString(SCRIPT);

        assertTrue(index.contains("data-testid=\"camera-video\""));
        assertTrue(index.contains("data-testid=\"overlay-canvas\""));
        assertTrue(index.contains("data-testid=\"sensor-emotion-enabled\""));
        assertTrue(index.contains("data-testid=\"sensor-social-enabled\""));
        assertTrue(index.contains("data-testid=\"sensor-hand-enabled\""));
        assertTrue(index.contains("data-testid=\"face-confidence-threshold\""));
        assertTrue(index.contains("data-testid=\"group-distance-threshold\""));
        assertTrue(index.contains("data-testid=\"hand-confidence-threshold\""));
        assertTrue(index.contains("data-testid=\"hand-auto-send\""));

        assertTrue(script.contains("faceapi.nets.tinyFaceDetector"));
        assertTrue(script.contains("cocoSsd.load"));
        assertTrue(script.contains("GestureRecognizer.createFromOptions"));
        assertTrue(script.contains("Closed_Fist: \"rock\""));
        assertTrue(script.contains("Open_Palm: \"paper\""));
        assertTrue(script.contains("Victory: \"scissor\""));
    }

    @Test
    void gigiDemoClientEmitsExistingPrometheusObservationContracts() throws IOException {
        String script = Files.readString(SCRIPT);

        assertTrue(script.contains("type: \"obs.user_utterance\""));
        assertTrue(script.contains("type: \"obs.emotion.face\""));
        assertTrue(script.contains("type: \"obs.human.presence\""));
        assertTrue(script.contains("type: \"obs.social.grouping\""));
        assertTrue(script.contains("type: \"obs.hand.sign\""));
        assertTrue(script.contains("source: \"visual.facial\""));
        assertTrue(script.contains("source: \"rps.web.camera\""));
        assertTrue(script.contains("detectionMode: \"client_camera\""));
        assertTrue(script.contains("source: \"rps.web\""));
        assertTrue(script.contains("detectionMode: \"manual\""));
    }

    @Test
    void gigiDemoClientRendersBehaviourModalitiesAndScenarioCards() throws IOException {
        String index = Files.readString(INDEX);
        String script = Files.readString(SCRIPT);

        assertTrue(index.contains("data-testid=\"speech-preview\""));
        assertTrue(index.contains("data-testid=\"gesture-value\""));
        assertTrue(index.contains("data-testid=\"face-value\""));
        assertTrue(index.contains("data-testid=\"gaze-value\""));
        assertTrue(index.contains("data-testid=\"motion-value\""));
        assertTrue(index.contains("data-testid=\"display-value\""));
        assertTrue(index.contains("data-testid=\"manual-sign-rock\""));
        assertTrue(index.contains("data-testid=\"social-sample-crowd\""));

        assertTrue(script.contains("new EventSource(behaviourStreamUrl())"));
        assertTrue(script.contains("monitor/stream"));
        assertTrue(script.contains("plan.nonVerbal"));
        assertTrue(script.contains("plan.motion"));
        assertTrue(script.contains("plan.display"));
        assertTrue(script.contains("motion.handSign"));
        assertTrue(script.contains("renderAgentSign(sign)"));
        assertTrue(script.contains("renderUserSign(sign)"));
    }
}
