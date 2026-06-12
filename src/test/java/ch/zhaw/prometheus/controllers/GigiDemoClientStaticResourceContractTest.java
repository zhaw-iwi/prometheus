package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(index.contains("data-testid=\"agent-drawer-tab\""));
        assertTrue(index.contains("data-testid=\"diagnostics-drawer-tab\""));
        assertTrue(index.contains("data-testid=\"agent-interaction-profile\""));
        assertTrue(index.contains("data-testid=\"agent-profile-observations\""));
        assertTrue(index.contains("data-testid=\"agent-profile-behaviours\""));
        assertTrue(index.contains("data-testid=\"agent-profile-tags\""));
        assertTrue(index.contains("Interaction Profile"));
        assertTrue(index.contains("Agent &amp; Diagnostics"));
        assertTrue(index.contains("Connect"));
        assertTrue(index.contains("data-testid=\"agent-connection-state\""));
        assertTrue(index.contains("Start Agent"));
        assertTrue(index.contains("data-testid=\"text-input\""));
        assertTrue(index.contains("data-testid=\"send-text\""));
        assertTrue(index.contains("data-testid=\"message-list\""));
        assertTrue(index.contains("data-testid=\"text-interaction-tab\""));
        assertTrue(index.contains("data-testid=\"speech-interaction-tab\""));
    }

    @Test
    void gigiDemoClientExposesRealtimeSpeechControls() throws IOException {
        String index = Files.readString(INDEX);
        String script = Files.readString(SCRIPT);

        assertTrue(index.contains("id=\"speech_interaction_panel\""));
        assertTrue(index.contains("data-testid=\"toggle-realtime\""));
        assertTrue(index.contains("data-testid=\"voice-select\""));
        assertTrue(index.contains("data-testid=\"turn-detection-select\""));
        assertTrue(index.contains("data-testid=\"push-to-talk\""));
        assertTrue(index.contains("data-testid=\"generate-side-behaviour\""));
        assertTrue(index.contains("value=\"cedar\""));
        assertTrue(index.contains("value=\"marin\""));

        assertTrue(script.contains("/prompt?profile=realtime_speech"));
        assertTrue(script.contains("/realtime/session"));
        assertTrue(script.contains("sessionInfo.realtimeCallsUrl"));
        assertTrue(script.contains("output_modalities"));
        assertTrue(script.contains("turn_detection"));
        assertTrue(script.contains("input_audio_buffer.commit"));
        assertTrue(script.contains("profile: \"backend_complement\""));
        assertTrue(script.contains("outputProfile"));
        assertTrue(script.contains("ackResponseSpeech"));
        assertTrue(script.contains("speakStoredAssistantResponse"));
    }

    @Test
    void gigiDemoClientExposesDiagnosticsDrawerControlsAndSnapshots() throws IOException {
        String index = Files.readString(INDEX);
        String script = Files.readString(SCRIPT);

        assertTrue(index.contains("data-testid=\"clear-activity-log\""));
        assertTrue(index.contains("data-testid=\"activity-log-wrap\""));
        assertTrue(index.contains("data-testid=\"activity-log-timestamps\""));
        assertTrue(index.contains("Toggle line break"));
        assertTrue(index.contains("Show timestamps"));
        assertTrue(index.contains("data-testid=\"diagnostics-current-state\""));
        assertTrue(index.contains("data-testid=\"diagnostics-state-list\""));
        assertTrue(index.contains("data-testid=\"storage-list\""));
        assertTrue(index.contains("Current State"));
        assertTrue(index.contains("States"));
        assertTrue(index.contains("Storage"));

        assertTrue(script.contains("activityEntries: []"));
        assertTrue(script.contains("activityWrap: true"));
        assertTrue(script.contains("activityShowTimestamps: true"));
        assertTrue(script.contains("ACTIVITY_LOG_LIMIT"));
        assertTrue(script.contains("function clearActivityLog()"));
        assertTrue(script.contains("function renderActivityLog()"));
        assertTrue(script.contains("log.classList.toggle(\"is-wrapped\", state.activityWrap);"));
        assertTrue(script.contains("state.activityShowTimestamps ?"));
        assertTrue(script.contains("\"clear_activity_log\""));
        assertTrue(script.contains("\"activity_log_wrap\""));
        assertTrue(script.contains("\"activity_log_timestamps\""));

        assertTrue(script.contains("async function loadAgentState()"));
        assertTrue(script.contains("fetch(`/${state.agentId}/state`)"));
        assertTrue(script.contains("fetch(`/${state.agentId}/states`)"));
        assertTrue(script.contains("function applyMonitorSnapshot(data)"));
        assertTrue(script.contains("applyStateSnapshot(data);"));
        assertTrue(script.contains("function updateCurrentState"));
        assertTrue(script.contains("function renderStateList"));
        assertTrue(script.contains("badge.textContent = \"current\""));

        assertTrue(script.contains("function renderStorageList()"));
        assertTrue(script.contains("function formatStorageValue"));
        assertTrue(script.contains("function copyToClipboard"));
        assertTrue(script.contains("copyToClipboard(formatStorageValue(entry && entry.value));"));
        assertTrue(script.contains("collapse.setAttribute(\"data-bs-parent\", \"#storage_list\");"));
        assertTrue(script.contains("setStorageEntries(data.storage);"));

        assertFalse(index.contains("data-testid=\"storage-view\""));
        assertFalse(script.contains("\"storage_view\""));
    }

    @Test
    void gigiDemoClientRequiresExplicitAgentSelectionBeforeStreaming() throws IOException {
        String script = Files.readString(SCRIPT);

        assertTrue(script.contains("state.selectedAgentId = getAgentIdFromLocation();"));
        assertTrue(script.contains("selectedAgentId: null"));
        assertTrue(script.contains("selectAgent(event.target.value"));
        assertTrue(script.contains("async function selectAgent"));
        assertTrue(script.contains("async function disconnectAgent"));
        assertTrue(script.contains("updateConnectionButton"));
        assertTrue(script.contains("Disconnect"));
        assertTrue(script.contains("const infoLoaded = await loadAgentInfo();"));
        assertTrue(script.contains("if (!infoLoaded)"));
        assertTrue(script.contains("renderAgentInteractionProfile(data.interactionProfile);"));
        assertTrue(script.contains("renderAgentInteractionProfile(null);"));
        assertTrue(script.contains("function renderAgentInteractionProfile"));
        assertTrue(script.contains("function renderProfileTokenList"));
        assertTrue(script.contains("profile.supportedObservations"));
        assertTrue(script.contains("profile.supportedBehaviourModalities"));
        assertTrue(script.contains("profile.profileTags"));
        assertTrue(script.contains("token.textContent = value;"));
        assertTrue(script.contains("disconnectAgent({ preserveInput: true"));
        assertTrue(script.contains("connectMonitorStream();"));
        assertTrue(script.indexOf("const infoLoaded = await loadAgentInfo();") < script.indexOf("connectMonitorStream();"));
        assertTrue(script.contains("\"open_diagnostics\""));
        assertTrue(script.contains("\"agent_drawer_tab\""));
        assertTrue(script.contains("\"diagnostics_drawer_tab\""));
        assertTrue(script.contains("el.dataset.bsToggle === \"collapse\""));

        assertFalse(script.contains("localStorage.getItem(\"gigiDemoAgentId\")"));
        assertFalse(script.contains("localStorage.setItem(\"gigiDemoAgentId\""));
        assertFalse(script.contains("const firstGigi"));
        assertFalse(script.contains("connectToAgent(id);"));
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
        assertTrue(index.contains("Emit camera observations"));
        assertTrue(index.contains("Manual Emotion"));
        assertTrue(index.contains("Manual Social"));
        assertTrue(index.contains("Manual Hand Sign"));
        assertTrue(index.contains("Signals Sensed"));
        assertTrue(index.contains("data-testid=\"manual-emotion-happy\""));
        assertTrue(index.contains("data-testid=\"hand-sign-value\""));
        assertTrue(index.contains("data-testid=\"no-visual-sensing-state\""));

        assertTrue(script.contains("faceapi.nets.tinyFaceDetector"));
        assertTrue(script.contains("cocoSsd.load"));
        assertTrue(script.contains("GestureRecognizer.createFromOptions"));
        assertTrue(script.contains("Closed_Fist: \"rock\""));
        assertTrue(script.contains("Open_Palm: \"paper\""));
        assertTrue(script.contains("Victory: \"scissor\""));

        assertFalse(index.contains("data-testid=\"hand-auto-send\""));
        assertFalse(index.contains("Auto-send hand sign"));
        assertFalse(script.contains("hand_auto_send"));
    }

    @Test
    void gigiDemoClientSupportsIndependentRuntimeSensingModes() throws IOException {
        String script = Files.readString(SCRIPT);

        assertTrue(script.contains("handleSensorModeChange"));
        assertTrue(script.contains("resetDisabledSensorState()"));
        assertTrue(script.contains("isSensorModeEnabled(\"social\") && camera.socialDetectorReady"));
        assertTrue(script.contains("isSensorModeEnabled(\"emotion\") && camera.faceModelsReady"));
        assertTrue(script.contains("isSensorModeEnabled(\"hand\") && camera.handDetectorReady"));
        assertTrue(script.contains("lastEmotionEmitAt"));
        assertTrue(script.contains("lastSocialEmitAt"));
        assertTrue(script.contains("passesSensorEmitInterval(\"emotion\")"));
        assertTrue(script.contains("passesSensorEmitInterval(\"social\")"));
        assertTrue(script.contains("markSensorEmitted(\"hand\")"));
        assertTrue(script.contains("!candidate || !document.getElementById(\"sensor_emit_enabled\").checked"));
        assertTrue(script.contains("setCameraStatus(state.cameraRunning ? \"Camera Live\" : \"Camera Idle\""));

        assertFalse(script.contains("lastEmitAt: 0"));
        assertFalse(script.contains("passesEmitInterval()"));
    }

    @Test
    void gigiDemoClientEmitsExistingPrometheusObservationContracts() throws IOException {
        String script = Files.readString(SCRIPT);

        assertTrue(script.contains("type: \"obs.user_utterance\""));
        assertTrue(script.contains("type: \"obs.emotion.face\""));
        assertTrue(script.contains("type: \"obs.human.presence\""));
        assertTrue(script.contains("type: \"obs.social.grouping\""));
        assertTrue(script.contains("type: \"obs.hand.sign\""));
        assertTrue(script.contains("\"visual.facial\""));
        assertTrue(script.contains("\"visual.facial.manual\""));
        assertTrue(script.contains("submitEmotionSample"));
        assertTrue(script.contains("manualEmotionExpressions"));
        assertTrue(script.contains("source: \"rps.web.camera\""));
        assertTrue(script.contains("detectionMode: \"client_camera\""));
        assertTrue(script.contains("source: \"rps.web\""));
        assertTrue(script.contains("detectionMode: \"manual\""));
    }

    @Test
    void gigiDemoClientRendersBehaviourModalitiesAndManualEventShortcuts() throws IOException {
        String index = Files.readString(INDEX);
        String script = Files.readString(SCRIPT);

        assertTrue(index.contains("Signals Sensed"));
        assertTrue(index.contains("Manual Emotion"));
        assertTrue(index.contains("Manual Hand Sign"));
        assertTrue(index.contains("Manual Social"));
        assertTrue(index.contains("Conversation Shortcuts"));
        assertTrue(index.contains("data-testid=\"speech-preview\""));
        assertTrue(index.contains("data-testid=\"gesture-value\""));
        assertTrue(index.contains("data-testid=\"face-value\""));
        assertTrue(index.contains("data-testid=\"gaze-value\""));
        assertTrue(index.contains("data-testid=\"motion-value\""));
        assertTrue(index.contains("data-testid=\"display-value\""));
        assertTrue(index.contains("data-testid=\"latest-behaviour-event\""));
        assertTrue(index.contains("class=\"metric-row-list\""));
        assertTrue(index.contains("data-testid=\"manual-sign-rock\""));
        assertTrue(index.contains("data-testid=\"social-sample-crowd\""));
        assertFalse(index.contains("<span><i class=\"bi bi-lightning-charge me-2\"></i>Scenario</span>"));
        assertFalse(index.contains("Latest Event"));
        assertFalse(index.contains("Camera Sign"));
        assertFalse(index.contains("class=\"metric-grid\""));
        assertFalse(index.contains("behaviour-strip"));

        assertTrue(script.contains("new EventSource(behaviourStreamUrl())"));
        assertTrue(script.contains("monitor/stream"));
        assertTrue(script.contains("plan.nonVerbal"));
        assertTrue(script.contains("plan.motion"));
        assertTrue(script.contains("plan.display"));
        assertTrue(script.contains("motion.handSign"));
        assertTrue(script.contains("renderAgentSign(sign)"));
        assertTrue(script.contains("renderUserSign(sign)"));
        assertTrue(script.contains("latest_behaviour_event"));
        assertFalse(script.contains("latest_event"));
        assertFalse(script.contains("camera_sign_value"));
    }

    @Test
    void gigiDemoClientHydratesTextTranscriptFromEventHistory() throws IOException {
        String script = Files.readString(SCRIPT);

        assertTrue(script.contains("await loadEventHistory();"));
        assertTrue(script.contains("handleBehaviourEnvelope(event, { fromHistory: true });"));
        assertTrue(script.contains("event.type === \"obs.user_utterance\""));
        assertTrue(script.contains("renderHistoricalUserUtterance(event);"));
        assertTrue(script.contains("function renderHistoricalUserUtterance"));
        assertTrue(script.contains("const text = eventPayloadText(event && event.payload);"));
        assertTrue(script.contains("appendMessage(\"user\", text);"));
        assertTrue(script.contains("options.renderTranscript !== false"));
        assertTrue(script.contains("appendMessage(\"assistant\", plan.speech.trim());"));
        assertTrue(script.contains("!options.fromHistory && recentBehaviourPayloadSeen(event.payload)"));
    }

    @Test
    void gigiDemoClientSuppressesImmediateBehaviourResponseAndStreamDuplicates() throws IOException {
        String script = Files.readString(SCRIPT);

        assertTrue(script.contains("recentBehaviourPayloads: new Map()"));
        assertTrue(script.contains("BEHAVIOUR_DUPLICATE_WINDOW_MS"));
        assertTrue(script.contains("function behaviourEventKey"));
        assertTrue(script.contains("function recentBehaviourPayloadSeen"));
        assertTrue(script.contains("function rememberRecentBehaviourPayload"));
        assertTrue(script.contains("function pruneRecentBehaviourPayloads"));
        assertTrue(script.contains("function resetBehaviourDeduplication"));
        assertTrue(script.contains("resetBehaviourDeduplication();"));
        assertTrue(script.contains("state.recentBehaviourPayloads.set(payload, Date.now());"));
        assertTrue(script.contains("state.recentBehaviourPayloads.delete(payload);"));
        assertTrue(script.contains("state.seenBehaviourKeys.has(key)"));
        assertTrue(script.contains("state.seenBehaviourKeys.add(key)"));
    }

    @Test
    void gigiDemoClientConsumesAgentInteractionProfileForUiVisibility() throws IOException {
        String index = Files.readString(INDEX);
        String script = Files.readString(SCRIPT);

        assertTrue(index.contains("data-profile-observations=\"obs.emotion.face\""));
        assertTrue(index.contains("data-profile-observations=\"obs.human.presence obs.social.grouping\""));
        assertTrue(index.contains("data-profile-observations=\"obs.hand.sign\""));
        assertTrue(index.contains("data-profile-behaviours=\"speech\""));
        assertTrue(index.contains("data-profile-behaviours=\"nonVerbal.gesture\""));
        assertTrue(index.contains("data-profile-behaviours=\"nonVerbal.facialExpression\""));
        assertTrue(index.contains("data-profile-behaviours=\"nonVerbal.gaze\""));
        assertTrue(index.contains("data-profile-behaviours=\"motion.handSign display\""));
        assertTrue(index.contains("data-profile-behaviours=\"display\""));

        assertTrue(script.contains("applyInteractionProfile(data.interactionProfile);"));
        assertTrue(script.contains("function applyInteractionProfile(profile)"));
        assertTrue(script.contains("supportedObservations"));
        assertTrue(script.contains("supportedBehaviourModalities"));
        assertTrue(script.contains("fallbackAll: supportedObservations.length === 0"));
        assertTrue(script.contains("setProfileElementVisible(element, visible);"));
        assertTrue(script.contains("element.hidden = !visible;"));
        assertTrue(script.contains("element.classList.toggle(\"d-none\", !visible);"));
        assertTrue(script.contains("updateVisualSensingEmptyState(capabilities);"));
        assertTrue(script.contains("document.getElementById(\"sensing_accordion\")"));
        assertTrue(script.contains("document.getElementById(\"no_visual_sensing_message\")"));
        assertTrue(script.contains("function profileElementVisible"));
        assertTrue(script.contains("function setProfileElementVisible"));
        assertTrue(script.contains("function updateVisualSensingEmptyState"));
        assertTrue(script.contains("function profileTokenMatches"));
        assertTrue(script.contains("function resetUnsupportedSensorModes"));
        assertTrue(script.contains("PROFILE_SENSOR_OBSERVATIONS"));

        assertFalse(script.contains("applyInteractionProfile(isGigiAgent"));
        assertFalse(script.contains("profileElementVisible(isGigiAgent"));
    }
}
