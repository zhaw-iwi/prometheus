package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ValerianClientStaticResourceContractTest {
    private static final Path INDEX = Path.of("src/main/resources/public/valerian/index.html");
    private static final Path SCRIPT = Path.of("src/main/resources/public/valerian/script.js");

    @Test
    void valerianClientExposesSinglePageAgentAndInteractionControls() throws IOException {
        String index = Files.readString(INDEX);

        assertTrue(index.contains("<title>Prometheus Demo Cockpit</title>"));
        assertTrue(index.contains("Prometheus Demo Cockpit"));
        assertTrue(index.contains("PROMETHEUS demo console"));
        assertTrue(index.contains("<script src=\"script.js\"></script>"));
        assertTrue(index.contains("data-testid=\"access-screen\""));
        assertTrue(index.contains("data-testid=\"access-code-input\""));
        assertTrue(index.contains("data-testid=\"submit-access-code\""));
        assertTrue(index.contains("data-testid=\"cockpit-shell\""));
        assertTrue(index.contains("data-testid=\"active-access-code\""));
        assertTrue(index.contains("data-testid=\"access-theme-toggle\""));
        assertTrue(index.contains("data-testid=\"cockpit-theme-toggle\""));
        assertTrue(index.contains("data-theme-toggle"));
        assertTrue(index.contains("aria-label=\"Switch to dark mode\""));
        assertTrue(index.contains("bi bi-moon-stars"));
        assertTrue(index.contains("[data-theme=\"dark\"]"));
        assertTrue(index.contains("document.documentElement.dataset.bsTheme = theme;"));
        assertTrue(index.contains("data-testid=\"clear-access-code\""));
        assertTrue(index.contains("data-testid=\"agent-type-select\""));
        assertTrue(index.contains("data-testid=\"create-agent-instance\""));
        assertTrue(index.contains("data-testid=\"agent-select\""));
        assertTrue(index.contains("data-testid=\"agent-id-input\""));
        assertTrue(index.contains("data-testid=\"connect-agent\""));
        assertTrue(index.contains("data-testid=\"start-agent\""));
        assertTrue(index.contains("data-testid=\"reset-agent\""));
        assertTrue(index.contains("data-testid=\"delete-agent\""));
        assertTrue(index.contains("data-testid=\"agent-drawer-tab\""));
        assertTrue(index.contains("data-testid=\"diagnostics-drawer-tab\""));
        assertTrue(index.contains("data-testid=\"agent-interaction-profile\""));
        assertTrue(index.contains("data-testid=\"agent-profile-observations\""));
        assertTrue(index.contains("data-testid=\"agent-profile-behaviours\""));
        assertTrue(index.contains("data-testid=\"agent-profile-tags\""));
        assertTrue(index.contains("data-testid=\"agent-info-language\""));
        assertTrue(index.contains("Language"));
        assertTrue(index.contains("Interaction Profile"));
        assertTrue(index.contains("Agent &amp; Diagnostics"));
        assertTrue(index.indexOf("data-testid=\"agent-drawer-tab\"")
                < index.indexOf("data-testid=\"diagnostics-drawer-tab\""));
        assertTrue(index.indexOf("id=\"agent_drawer_panel\"")
                < index.indexOf("id=\"diagnostics_drawer_panel\""));
        assertTrue(index.contains("Available Agent Types"));
        assertTrue(index.contains("Connect"));
        assertTrue(index.contains("data-testid=\"agent-connection-state\""));
        assertTrue(index.contains("Start Agent"));
        assertTrue(index.contains("data-testid=\"text-input\""));
        assertTrue(index.contains("data-testid=\"send-text\""));
        assertTrue(index.contains("data-testid=\"message-list\""));
        assertTrue(index.contains("data-testid=\"text-interaction-tab\""));
        assertTrue(index.contains("data-testid=\"continuous-speech-tab\""));
        assertTrue(index.contains("bi-radar"));
        assertTrue(index.contains("bi-send-fill"));
        assertFalse(index.contains("data-testid=\"push-to-talk-tab\""));
        assertFalse(index.toLowerCase().contains("gigi"));
        assertFalse(index.toLowerCase().contains("tdsr"));
    }

    @Test
    void valerianClientSupportsMaximizableCockpitColumns() throws IOException {
        String index = Files.readString(INDEX);
        String script = Files.readString(SCRIPT);

        assertTrue(index.contains("data-column-panel=\"sensing\""));
        assertTrue(index.contains("data-column-panel=\"interaction\""));
        assertTrue(index.contains("data-column-panel=\"behaviour\""));
        assertTrue(index.contains("data-testid=\"maximize-sensing-column\""));
        assertTrue(index.contains("data-testid=\"maximize-interaction-column\""));
        assertTrue(index.contains("data-testid=\"maximize-behaviour-column\""));
        assertTrue(index.contains("data-column-maximize=\"sensing\""));
        assertTrue(index.contains("data-column-maximize=\"interaction\""));
        assertTrue(index.contains("data-column-maximize=\"behaviour\""));
        assertTrue(index.contains("data-testid=\"sensing-column-placeholder\""));
        assertTrue(index.contains("data-testid=\"interaction-column-placeholder\""));
        assertTrue(index.contains("data-testid=\"behaviour-column-placeholder\""));
        assertTrue(index.contains("data-testid=\"column-expansion-modal\""));
        assertTrue(index.contains("data-testid=\"column-expansion-title\""));
        assertTrue(index.contains("data-testid=\"column-expansion-body\""));
        assertTrue(index.contains("modal-fullscreen-lg-down"));
        assertTrue(index.contains("column-expansion-dialog"));
        assertTrue(index.contains("column-expansion-body [data-column-maximize]"));
        assertTrue(index.contains("bi bi-arrows-fullscreen"));

        assertTrue(script.contains("const columnExpansion = {"));
        assertTrue(script.contains("wireColumnExpansion();"));
        assertTrue(script.contains("function wireColumnExpansion()"));
        assertTrue(script.contains("window.bootstrap.Modal.getOrCreateInstance(modalElement);"));
        assertTrue(script.contains("document.querySelectorAll(\"[data-column-maximize]\")"));
        assertTrue(script.contains("openColumnExpansion(button.dataset.columnMaximize"));
        assertTrue(script.contains("modalElement.addEventListener(\"hidden.bs.modal\", restoreExpandedColumn);"));
        assertTrue(script.contains("modalElement.addEventListener(\"shown.bs.modal\", refreshExpandedColumnLayout);"));
        assertTrue(script.contains("function openColumnExpansion(columnKey, title)"));
        assertTrue(script.contains("const panel = document.querySelector(`[data-column-panel=\"${columnKey}\"]`);"));
        assertTrue(script.contains("originalParent: panel.parentNode"));
        assertTrue(script.contains("nextSibling: panel.nextSibling"));
        assertTrue(script.contains("modalBody.replaceChildren(panel);"));
        assertTrue(script.contains("function restoreExpandedColumn()"));
        assertTrue(script.contains("active.originalParent.insertBefore(active.panel, active.nextSibling);"));
        assertTrue(script.contains("active.originalParent.appendChild(active.panel);"));
        assertTrue(script.contains("function setColumnPlaceholderVisible(placeholder, visible)"));
        assertTrue(script.contains("placeholder.hidden = !visible;"));
        assertTrue(script.contains("placeholder.classList.toggle(\"d-none\", !visible);"));
        assertTrue(script.contains("function refreshExpandedColumnLayout()"));
        assertTrue(script.contains("clearOverlay();"));
        assertTrue(script.contains("el.hasAttribute(\"data-column-maximize\")"));
    }

    @Test
    void valerianClientUsesAccessCodeScopedDemoApi() throws IOException {
        String script = Files.readString(SCRIPT);

        assertTrue(script.contains("ACCESS_CODE_STORAGE_KEY = \"prometheus.valerian.accessCode\""));
        assertTrue(script.contains("ACCESS_CODE_HEADER = \"X-Prometheus-Access-Code\""));
        assertTrue(script.contains("THEME_STORAGE_KEY = \"prometheus.valerian.theme\""));
        assertTrue(script.contains("function applyStoredTheme()"));
        assertTrue(script.contains("function toggleTheme()"));
        assertTrue(script.contains("function setTheme(theme, options = {})"));
        assertTrue(script.contains("document.documentElement.dataset.bsTheme = nextTheme;"));
        assertTrue(script.contains("localStorage.setItem(THEME_STORAGE_KEY, nextTheme);"));
        assertTrue(script.contains("button.setAttribute(\"aria-pressed\", dark ? \"true\" : \"false\");"));
        assertTrue(script.contains("iconElement.className = `bi ${icon}`;"));
        assertTrue(script.contains("el.hasAttribute(\"data-theme-toggle\")"));
        assertTrue(script.contains("sessionStorage.getItem(ACCESS_CODE_STORAGE_KEY)"));
        assertTrue(script.contains("sessionStorage.setItem(ACCESS_CODE_STORAGE_KEY, state.accessCode)"));
        assertTrue(script.contains("fetch(\"/demo/session\""));
        assertTrue(script.contains("scopedFetch(\"/demo/agents\""));
        assertTrue(script.contains("JSON.stringify({ agentDefinitionKey })"));
        assertTrue(script.contains("scopedFetch(`/demo/agents/${encodeURIComponent(selectedAgentId)}`"));
        assertTrue(script.contains("headers.set(ACCESS_CODE_HEADER, state.accessCode);"));
        assertTrue(script.contains("demoAgentPath(\"/info\")"));
        assertTrue(script.contains("demoAgentPath(\"/eventhistory\")"));
        assertTrue(script.contains("demoAgentPath(\"/state\")"));
        assertTrue(script.contains("demoAgentPath(\"/states\")"));
        assertTrue(script.contains("demoAgentPath(\"/storage\")"));
        assertTrue(script.contains("demoAgentPath(\"/start\")"));
        assertTrue(script.contains("demoAgentPath(\"/reset\")"));
        assertTrue(script.contains("demoAgentPath(`/acknowledge${profile}`)"));
        assertTrue(script.contains("demoAgentPath(\"/behaviour/generate\")"));
        assertTrue(script.contains("demoAgentPath(\"/behaviour/stream\")"));
        assertTrue(script.contains("demoAgentPath(\"/monitor/stream\")"));
        assertTrue(script.contains("demoAgentPath(`/realtime/call?${params.toString()}`)"));
        assertTrue(script.contains("params.set(\"accessCode\", state.accessCode);"));
        assertTrue(script.contains("function isVisibleAgentId"));
        assertTrue(script.contains("function prometheusFacingText"));
        assertTrue(script.contains("function agentLanguageLabel"));
        assertTrue(script.contains("data.languageCode"));
        assertTrue(script.contains("agent_info_language"));
        assertTrue(script.contains("show.bs.offcanvas"));
        assertTrue(script.contains("showAgentDrawerTab"));
        assertTrue(script.contains("window.bootstrap.Tab.getOrCreateInstance(tab).show();"));

        assertFalse(script.contains("fetch(\"/agent\")"));
        assertFalse(script.contains("fetch(`/${state.agentId}/"));
        assertFalse(script.toLowerCase().contains("gigi"));
        assertFalse(script.toLowerCase().contains("tdsr"));
    }

    @Test
    void valerianClientExposesRealtimeSpeechControls() throws IOException {
        String index = Files.readString(INDEX);
        String script = Files.readString(SCRIPT);

        assertTrue(index.contains("data-testid=\"continuous-speech-tab\""));
        assertTrue(index.contains("id=\"continuous_speech_panel\""));
        assertTrue(index.contains("data-testid=\"toggle-realtime\""));
        assertTrue(index.contains("data-testid=\"advanced-speech-settings\""));
        assertTrue(index.contains("data-testid=\"speech-voice\""));
        assertTrue(index.contains("data-testid=\"speech-vad\""));
        assertTrue(index.contains("data-testid=\"speech-complement\""));
        assertTrue(index.contains("data-testid=\"speech-transcription-logprobs\""));
        assertTrue(index.contains("data-testid=\"speech-barge-in-cancel\""));
        assertTrue(index.contains("data-testid=\"speech-echo-guard\""));
        assertTrue(index.contains("data-testid=\"speech-vad-threshold\""));
        assertTrue(index.contains("data-testid=\"speech-vad-prefix\""));
        assertTrue(index.contains("data-testid=\"speech-vad-silence\""));
        assertTrue(index.contains("data-testid=\"speech-vad-eagerness\""));
        assertTrue(index.contains("data-testid=\"speech-vad-interrupt-response\""));
        assertTrue(index.contains("data-testid=\"speech-input-noise-reduction\""));
        assertTrue(index.contains("data-testid=\"speech-output-speed\""));
        assertTrue(index.contains("data-testid=\"speech-reasoning-effort\""));
        assertTrue(index.contains("data-testid=\"speech-max-output-tokens\""));
        assertTrue(index.contains("data-testid=\"speech-input-device\""));
        assertTrue(index.contains("data-testid=\"speech-output-device\""));
        assertTrue(index.contains("data-testid=\"refresh-audio-devices\""));
        assertTrue(index.contains("data-testid=\"speech-device-status\""));
        assertTrue(index.contains("data-testid=\"realtime-transport-status\""));
        assertTrue(index.contains("data-testid=\"realtime-transport-detail\""));
        assertTrue(index.contains("System / browser default"));
        assertTrue(index.contains("data-testid=\"assistant-audio\""));
        assertTrue(index.contains("data-testid=\"continuous-speech-sensing-panel\""));
        assertTrue(index.contains("data-testid=\"continuous-speech-sensing-value\""));
        assertTrue(index.contains("Speech Sensing"));
        assertTrue(index.contains("User Speech"));
        assertTrue(index.contains("value=\"cedar\""));
        assertTrue(index.contains("value=\"marin\""));
        assertTrue(index.contains("value=\"semantic_vad\""));
        assertTrue(index.contains("value=\"near_field\""));
        assertTrue(index.contains("value=\"far_field\""));
        assertTrue(index.contains("value=\"off\""));
        assertTrue(index.contains("Half-duplex fallback"));
        assertTrue(index.contains("Barge-in cancellation"));
        assertFalse(index.contains("data-testid=\"speech-vad-create-response\""));
        assertFalse(index.contains("speechVadCreateResponseSelect"));
        assertFalse(index.contains("<option value=\"none\""));
        assertTrue(index.indexOf("data-testid=\"advanced-speech-settings\"")
                < index.indexOf("id=\"continuous_speech_panel\""));
        assertTrue(index.indexOf("id=\"continuous_speech_panel\"")
                < index.indexOf("data-testid=\"continuous-speech-sensing-panel\""));
        assertTrue(index.indexOf("data-testid=\"assistant-audio\"")
                < index.indexOf("data-testid=\"continuous-speech-sensing-panel\""));
        assertTrue(index.indexOf("data-testid=\"continuous-speech-sensing-panel\"")
                < index.indexOf("<aside class=\"right-column cockpit-column\""));
        assertTrue(index.indexOf("data-testid=\"continuous-speech-sensing-panel\"")
                > index.indexOf("</aside>", index.indexOf("<aside class=\"left-column cockpit-column\"")));
        assertFalse(index.contains("Push to Talk"));
        assertFalse(index.contains("push_to_talk"));
        assertFalse(index.contains("push-to-talk"));

        assertTrue(script.contains("/realtime/call?"));
        assertTrue(script.contains("Content-Type\": \"application/sdp\""));
        assertTrue(script.contains("call.callId || call.id"));
        assertTrue(script.contains("call.sdp"));
        assertTrue(script.contains("/realtime/calls/"));
        assertTrue(script.contains("input_audio_buffer.committed"));
        assertTrue(script.contains("input_audio_buffer.speech_started"));
        assertTrue(script.contains("input_audio_buffer.speech_stopped"));
        assertTrue(script.contains("TRANSCRIPT_BATCH_DELAY_MS"));
        assertTrue(script.contains("isLikelyAsrHallucination"));
        assertTrue(script.contains("isProbableAssistantEcho"));
        assertTrue(script.contains("transcriptTokenSimilarity"));
        assertTrue(script.contains("Suppressed probable assistant echo transcript."));
        assertTrue(script.contains("amara org community"));
        assertTrue(script.contains("processedInputItemIds"));
        assertTrue(script.contains("SPEECH_INPUT_DEVICE_STORAGE_KEY"));
        assertTrue(script.contains("SPEECH_OUTPUT_DEVICE_STORAGE_KEY"));
        assertTrue(script.contains("SPEECH_VAD_THRESHOLD_STORAGE_KEY"));
        assertTrue(script.contains("SPEECH_BARGE_IN_CANCEL_STORAGE_KEY"));
        assertTrue(script.contains("SPEECH_ECHO_GUARD_STORAGE_KEY"));
        assertTrue(script.contains("speechSessionSettingControls"));
        assertTrue(script.contains("loadStoredSpeechSettings"));
        assertTrue(script.contains("saveSpeechSessionSettingSelection"));
        assertTrue(script.contains("function refreshAudioDevices"));
        assertTrue(script.contains("navigator.mediaDevices.enumerateDevices()"));
        assertTrue(script.contains("navigator.mediaDevices.getUserMedia({ audio: true })"));
        assertTrue(script.contains("device.kind === \"audioinput\""));
        assertTrue(script.contains("device.kind === \"audiooutput\""));
        assertTrue(script.contains("function speechInputConstraints"));
        assertTrue(script.contains("constraints.deviceId = { exact: deviceId };"));
        assertTrue(script.contains("channelCount: { ideal: 1 }"));
        assertTrue(script.contains("voiceIsolation: true"));
        assertTrue(script.contains("function logActiveSpeechInputSettings"));
        assertTrue(script.contains("audio: speechInputConstraints()"));
        assertTrue(script.contains("function applySelectedSpeechOutputDevice"));
        assertTrue(script.contains("audio.setSinkId(deviceId)"));
        assertTrue(script.contains("Speaker selection is not supported by this browser; using browser default output."));
        assertTrue(script.contains("Microphone saved. Restart speech to use the new input device."));
        assertTrue(script.contains("renderSpeechSensingTranscript(selected.transcript);"));
        assertTrue(script.contains("function renderSpeechSensingTranscript"));
        assertTrue(script.contains("setText(\"continuous_speech_sensing_value\""));
        assertTrue(script.contains("resetSpeechSensingPanel();"));
        assertTrue(script.contains("REALTIME_MODE_CONTINUOUS"));
        assertTrue(script.contains("audio.srcObject = event.streams[0];"));
        assertTrue(script.contains("applySelectedSpeechOutputDevice().finally"));
        assertTrue(script.contains("audio.play().catch"));
        assertTrue(script.contains("REALTIME_ICE_FAILURE_MESSAGE"));
        assertTrue(script.contains("Realtime WebRTC ICE failed. Stop and restart speech; check network/STUN/TURN if it repeats."));
        assertTrue(script.contains("function wireRealtimePeerDiagnostics"));
        assertTrue(script.contains("function registerAssistantAudioDiagnostics"));
        assertTrue(script.contains("function registerRemoteAudioTrackDiagnostics"));
        assertTrue(script.contains("function startRealtimeStatsDiagnostics"));
        assertTrue(script.contains("peerConnection.getStats()"));
        assertTrue(script.contains("Realtime audio stats warning:"));
        assertTrue(script.contains("iceconnectionstatechange"));
        assertTrue(script.contains("connectionstatechange"));
        assertTrue(script.contains("icegatheringstatechange"));
        assertTrue(script.contains("icecandidateerror"));
        assertTrue(script.contains("function handleRealtimeIceCandidateError"));
        assertTrue(script.contains("function setRealtimeTransportStatus"));
        assertTrue(script.contains("setRealtimeGlobalStatus(\"Realtime ICE Failed\", \"error\")"));
        assertTrue(script.contains("appendRealtimeCallParam(params, \"vadThreshold\""));
        assertTrue(script.contains("appendRealtimeCallParam(params, \"vadPrefixPaddingMs\""));
        assertTrue(script.contains("appendRealtimeCallParam(params, \"vadSilenceDurationMs\""));
        assertTrue(script.contains("appendRealtimeCallParam(params, \"vadEagerness\""));
        assertTrue(script.contains("appendRealtimeCallParam(params, \"vadInterruptResponse\""));
        assertTrue(script.contains("appendRealtimeCallParam(params, \"inputNoiseReduction\""));
        assertTrue(script.contains("appendRealtimeCallParam(params, \"outputSpeed\""));
        assertTrue(script.contains("appendRealtimeCallParam(params, \"reasoningEffort\""));
        assertTrue(script.contains("appendRealtimeCallParam(params, \"maxOutputTokens\""));
        assertTrue(script.contains("appendRealtimeCallParam(params, \"includeInputTranscriptionLogprobs\", true);"));
        assertTrue(script.contains("parseNumberRange(document.getElementById(\"speechVadThresholdInput\").value, 0, 1)"));
        assertTrue(script.contains("parseIntegerRange(document.getElementById(\"speechMaxOutputTokensInput\").value, 1, 4096)"));
        assertTrue(script.contains("sendRealtimeClientEvent({ type: \"response.cancel\" }"));
        assertTrue(script.contains("track.enabled = enabled;"));
        assertTrue(script.contains("speechSettings.echoGuardEnabled"));
        assertFalse(script.contains("speechVadCreateResponse"));
        assertFalse(script.contains("vadCreateResponse"));

        assertFalse(script.contains("/realtime/session"));
        assertFalse(script.contains("REALTIME_MODE_PUSH_TO_TALK"));
        assertFalse(script.contains("push_to_talk"));
        assertFalse(script.contains("push-to-talk"));
        assertFalse(script.contains("window.MediaRecorder"));
        assertFalse(script.contains("new MediaRecorder"));
        assertFalse(script.contains("new FormData()"));
        assertFalse(script.contains("form.append(\"audio\""));
        assertFalse(script.contains("speech-turn"));
        assertFalse(script.contains("speech/latest"));
        assertFalse(script.contains("playRecordedSpeechAudio"));
        assertFalse(script.contains("playLatestAssistantSpeechForPushToTalk"));
        assertFalse(script.contains("sessionInfo.realtimeCallsUrl"));
        assertFalse(script.contains("clientSecret"));
        assertFalse(script.contains("type: \"session.update\""));
        assertFalse(script.contains("type: \"response.create\""));
        assertFalse(script.contains("type: \"input_audio_buffer.commit\""));
        assertFalse(script.contains("type: \"input_audio_buffer.clear\""));
        assertFalse(script.contains("type: \"output_audio_buffer.clear\""));
        assertFalse(script.contains("ackResponseSpeech"));
        assertFalse(script.contains("speakStoredAssistantResponse"));
    }

    @Test
    void valerianClientExposesDiagnosticsDrawerControlsAndSnapshots() throws IOException {
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
        assertTrue(script.contains("scopedFetch(demoAgentPath(\"/state\"))"));
        assertTrue(script.contains("scopedFetch(demoAgentPath(\"/states\"))"));
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
    void valerianClientRequiresExplicitAgentSelectionBeforeStreaming() throws IOException {
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

        assertFalse(script.contains("localStorage.getItem(\"selectedAgent"));
        assertFalse(script.contains("localStorage.setItem(\"selectedAgent"));
        assertFalse(script.contains("localStorage.getItem(\"prometheus.valerian.selectedAgent"));
        assertFalse(script.contains("localStorage.setItem(\"prometheus.valerian.selectedAgent"));
        assertTrue(script.contains("sessionStorage.getItem(ACCESS_CODE_STORAGE_KEY)"));
        assertTrue(script.contains("sessionStorage.setItem(ACCESS_CODE_STORAGE_KEY, state.accessCode)"));
        assertFalse(script.contains("connectToAgent(id);"));
    }

    @Test
    void valerianClientExposesUnifiedVisualSensingControls() throws IOException {
        String index = Files.readString(INDEX);
        String script = Files.readString(SCRIPT);

        assertTrue(index.contains("data-testid=\"camera-video\""));
        assertTrue(index.contains("data-testid=\"overlay-canvas\""));
        assertTrue(index.contains("data-testid=\"camera-device-input-group\""));
        assertTrue(index.contains("class=\"input-group input-group-sm\""));
        assertTrue(index.contains("data-testid=\"camera-device\""));
        assertTrue(index.contains("data-testid=\"refresh-camera-devices\""));
        assertTrue(index.contains("data-testid=\"camera-device-status\""));
        assertTrue(index.contains("aria-label=\"Refresh camera sources\""));
        assertTrue(index.contains("bi bi-arrow-clockwise"));
        assertTrue(index.contains("data-testid=\"sensor-emotion-enabled\""));
        assertTrue(index.contains("data-testid=\"sensor-social-enabled\""));
        assertTrue(index.contains("data-testid=\"sensor-hand-enabled\""));
        assertTrue(index.contains("data-testid=\"face-confidence-threshold\""));
        assertTrue(index.contains("data-testid=\"group-distance-threshold\""));
        assertTrue(index.contains("data-testid=\"hand-confidence-threshold\""));
        assertTrue(index.contains("data-testid=\"weather-location-input\""));
        assertTrue(index.contains("data-testid=\"fetch-weather-current\""));
        assertTrue(index.contains("data-testid=\"send-weather-current\""));
        assertTrue(index.contains("data-testid=\"send-weather-forecast\""));
        assertTrue(index.contains("data-testid=\"weather-value\""));
        assertTrue(index.contains("Camera uses browser default."));
        assertTrue(index.contains("Emit camera observations"));
        assertTrue(index.contains("for=\"sensor_social_enabled\">Social context</label>"));
        assertFalse(index.contains("for=\"sensor_social_enabled\">Social grouping</label>"));
        assertTrue(index.contains("Manual Emotion"));
        assertTrue(index.contains("Manual Social Context"));
        assertTrue(index.contains("Manual Hand Sign"));
        assertTrue(index.contains("Weather"));
        assertTrue(index.contains("Signals Sensed"));
        assertTrue(index.contains("data-testid=\"manual-emotion-happy\""));
        assertTrue(index.contains("data-testid=\"emotion-report\""));
        assertTrue(index.contains("data-testid=\"emotion-affect-plane\""));
        assertTrue(index.contains("data-testid=\"emotion-affect-marker\""));
        assertTrue(index.contains("data-testid=\"emotion-valence-value\""));
        assertTrue(index.contains("data-testid=\"emotion-arousal-value\""));
        assertTrue(index.contains("data-testid=\"emotion-confidence-value\""));
        assertTrue(index.contains("data-testid=\"emotion-face-confidence-value\""));
        assertTrue(index.contains("data-testid=\"emotion-emit-status\""));
        assertTrue(index.contains("data-testid=\"emotion-expression-list\""));
        assertTrue(index.contains("data-testid=\"emotion-expression-happy-meter\""));
        assertTrue(index.contains("data-testid=\"emotion-expression-surprised-value\""));
        assertTrue(index.contains("data-testid=\"social-context-report\""));
        assertTrue(index.contains("data-testid=\"social-context-status\""));
        assertTrue(index.contains("data-testid=\"social-context-human-count\""));
        assertTrue(index.contains("data-testid=\"social-context-group-count\""));
        assertTrue(index.contains("data-testid=\"social-context-largest-group\""));
        assertTrue(index.contains("data-testid=\"social-context-singleton-count\""));
        assertTrue(index.contains("data-testid=\"social-group-list\""));
        assertTrue(index.contains("data-testid=\"social-person-list\""));
        assertTrue(index.indexOf("data-testid=\"emotion-value\"")
                < index.indexOf("data-testid=\"emotion-report\""));
        assertTrue(index.indexOf("data-testid=\"emotion-report\"")
                < index.indexOf("data-testid=\"human-count\""));
        assertTrue(index.indexOf("data-testid=\"group-count\"")
                < index.indexOf("data-testid=\"social-context-report\""));
        assertTrue(index.indexOf("data-testid=\"social-context-report\"")
                < index.indexOf("data-testid=\"hand-sign-value\""));
        assertTrue(index.contains("data-testid=\"hand-sign-value\""));
        assertTrue(index.contains("data-testid=\"no-visual-sensing-state\""));

        assertTrue(script.contains("faceapi.nets.tinyFaceDetector"));
        assertTrue(script.contains("cocoSsd.load"));
        assertTrue(script.contains("GestureRecognizer.createFromOptions"));
        assertTrue(script.contains("CAMERA_DEVICE_STORAGE_KEY"));
        assertTrue(script.contains("prometheus.valerian.cameraDevice"));
        assertTrue(script.contains("function refreshCameraDevices"));
        assertTrue(script.contains("navigator.mediaDevices.getUserMedia({ video: true, audio: false })"));
        assertTrue(script.contains("device.kind === \"videoinput\""));
        assertTrue(script.contains("function cameraVideoConstraints"));
        assertTrue(script.contains("video: cameraVideoConstraints()"));
        assertTrue(script.contains("constraints.deviceId = { exact: deviceId };"));
        assertTrue(script.contains("function restartCameraWithSelectedDevice"));
        assertTrue(script.contains("Switching camera input."));
        assertTrue(script.contains("Camera saved for the next camera session."));
        assertTrue(script.contains("Closed_Fist: \"rock\""));
        assertTrue(script.contains("Open_Palm: \"paper\""));
        assertTrue(script.contains("Victory: \"scissor\""));
        assertTrue(script.contains("function mirroredOverlayBox"));
        assertTrue(script.contains("camera.canvas.width - ((x + width) * scale.scaleX)"));
        assertTrue(script.contains("const displayBox = mirroredOverlayBox(box.x, box.y, box.width, box.height, scale);"));
        assertTrue(script.contains("const displayBox = mirroredOverlayBox(x, y, w, h, scale);"));
        assertTrue(script.contains("const EMOTION_EXPRESSION_KEYS = [\"neutral\", \"happy\", \"sad\", \"angry\", \"fearful\", \"disgusted\", \"surprised\"]"));
        assertTrue(script.contains("renderEmotionMetrics(emotion, detection.detection.score);"));
        assertTrue(script.contains("function renderEmotionMetrics(emotion, faceScore = 0)"));
        assertTrue(script.contains("function resetEmotionReport()"));
        assertTrue(script.contains("function setEmotionAffectMarker(valence, arousal, emotion)"));
        assertTrue(script.contains("marker.style.left = `${x}%`;"));
        assertTrue(script.contains("marker.style.bottom = `${y}%`;"));
        assertTrue(script.contains("function renderExpressionBars(expressions)"));
        assertTrue(script.contains("function setEmotionEmitStatus(text, mode = \"idle\")"));
        assertTrue(script.contains("setEmotionEmitStatus(\"Below threshold\", \"idle\");"));
        assertTrue(script.contains("setEmotionEmitStatus(`Emitted ${new Date().toLocaleTimeString()}`, \"live\");"));
        assertTrue(script.contains("function formatSignedDecimal(value)"));
        assertTrue(script.contains("renderSocialMetrics(social, tracked);"));
        assertTrue(script.contains("function renderSocialMetrics(social, tracked = [])"));
        assertTrue(script.contains("renderSocialGroups(view.groups || [])"));
        assertTrue(script.contains("renderSocialPeople(people)"));
        assertTrue(script.contains("const TRACK_MOVING_DISTANCE_NORM"));
        assertTrue(script.contains("const TRACK_DEPTH_AREA_DELTA"));
        assertTrue(script.contains("function deriveTrackMovement(track, detection, frameDiag)"));
        assertTrue(script.contains("function trackArea(box)"));
        assertTrue(script.contains("movementConfidence: Number(track.movementConfidence || 0)"));
        assertTrue(script.contains("function normalizeActivityState(value)"));
        assertTrue(index.contains("data-activity-state=\"moving\""));
        assertTrue(script.contains("token.dataset.activityState = options.activityState;"));
        assertTrue(script.contains("const ATTENTION_CONFIDENCE_THRESHOLD"));
        assertTrue(script.contains("function deriveAttentionSignal(detection, frameWidth, frameHeight)"));
        assertTrue(script.contains("function emptyAttentionSignal()"));
        assertTrue(script.contains("function normalizeAttentionSignal(raw, fallback = {})"));
        assertTrue(script.contains("function normalizeAttentionState(value)"));
        assertTrue(script.contains("attentionState: attention.state"));
        assertTrue(index.contains("data-attention-state=\"attending\""));
        assertTrue(script.contains("token.dataset.attentionState = options.attentionState;"));

        assertFalse(index.contains("data-testid=\"hand-auto-send\""));
        assertFalse(index.contains("Auto-send hand sign"));
        assertFalse(script.contains("hand_auto_send"));
        assertFalse(script.contains("box.x * scaleX"));
        assertFalse(script.contains("x * scaleX, y * scaleY"));
    }

    @Test
    void valerianClientSupportsIndependentRuntimeSensingModes() throws IOException {
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
    void valerianClientEmitsExistingPrometheusObservationContracts() throws IOException {
        String script = Files.readString(SCRIPT);

        assertTrue(script.contains("type: \"obs.user_utterance\""));
        assertTrue(script.contains("type: \"obs.emotion.face\""));
        assertTrue(script.contains("type: \"obs.human.presence\""));
        assertTrue(script.contains("type: \"obs.social.grouping\""));
        assertTrue(script.contains("type: \"obs.social.context\""));
        assertTrue(script.contains("type: \"obs.hand.sign\""));
        assertTrue(script.contains("type: \"obs.weather.current\""));
        assertTrue(script.contains("type: \"obs.weather.forecast\""));
        assertTrue(script.contains("\"visual.facial\""));
        assertTrue(script.contains("\"visual.facial.manual\""));
        assertTrue(script.contains("\"open-meteo.client\""));
        assertTrue(script.contains("submitEmotionSample"));
        assertTrue(script.contains("manualEmotionExpressions"));
        assertTrue(script.contains("sendWeatherCurrent"));
        assertTrue(script.contains("sendWeatherForecast"));
        assertTrue(script.contains("normalizeOpenMeteoWeather"));
        assertTrue(script.contains("source: \"valerian.hand.camera\""));
        assertTrue(script.contains("detectionMode: \"client_camera\""));
        assertTrue(script.contains("source: \"valerian.hand.manual\""));
        assertTrue(script.contains("detectionMode: \"manual\""));
        assertTrue(script.contains("schemaVersion: 1"));
        assertTrue(script.contains("function socialContextPayload(social, tracked, source)"));
        assertTrue(script.contains("function socialContextPerson(person)"));
        assertTrue(script.contains("function socialContextSignature(payload)"));
        assertTrue(script.contains("currentProfileSupportsObservation(\"obs.social.context\")"));
        assertTrue(script.contains("lastSocialContextSignature"));
    }

    @Test
    void valerianClientRendersBehaviourModalitiesAndManualEventShortcuts() throws IOException {
        String index = Files.readString(INDEX);
        String script = Files.readString(SCRIPT);

        assertTrue(index.contains("Signals Sensed"));
        assertTrue(index.contains("Manual Emotion"));
        assertTrue(index.contains("Manual Hand Sign"));
        assertTrue(index.contains("Manual Social Context"));
        assertTrue(index.contains("Conversation Shortcuts"));
        assertTrue(index.contains("data-testid=\"behaviour-state-board\""));
        assertTrue(index.contains("data-testid=\"behaviour-channel-strip\""));
        assertTrue(index.contains("data-testid=\"behaviour-visual-grid\""));
        assertTrue(index.contains("data-testid=\"behaviour-chip-speech\""));
        assertTrue(index.contains("data-testid=\"behaviour-chip-gesture\""));
        assertTrue(index.contains("data-testid=\"behaviour-chip-face\""));
        assertTrue(index.contains("data-testid=\"behaviour-chip-gaze\""));
        assertTrue(index.contains("data-testid=\"behaviour-chip-motion\""));
        assertTrue(index.contains("data-testid=\"behaviour-chip-display\""));
        assertTrue(index.contains("data-testid=\"speech-preview\""));
        assertTrue(index.contains("data-testid=\"gesture-stage\""));
        assertTrue(index.contains("data-testid=\"gesture-icon\""));
        assertTrue(index.contains("data-testid=\"gesture-hint\""));
        assertTrue(index.contains("data-testid=\"gesture-value\""));
        assertTrue(index.contains("data-testid=\"face-value\""));
        assertTrue(index.contains("data-testid=\"face-intensity-value\""));
        assertTrue(index.contains("data-testid=\"face-intensity-meter\""));
        assertTrue(index.contains("data-testid=\"gaze-value\""));
        assertTrue(index.contains("data-testid=\"gaze-focus-value\""));
        assertTrue(index.contains("data-testid=\"motion-value\""));
        assertTrue(index.contains("data-testid=\"motion-energy-value\""));
        assertTrue(index.contains("data-testid=\"motion-energy-meter\""));
        assertTrue(index.contains("data-testid=\"motion-stillness-value\""));
        assertTrue(index.contains("data-testid=\"motion-stillness-meter\""));
        assertTrue(index.contains("data-testid=\"agent-sign-visual\""));
        assertTrue(index.contains("data-testid=\"user-sign-visual\""));
        assertTrue(index.contains("data-testid=\"display-value\""));
        assertTrue(index.contains("data-testid=\"latest-behaviour-event\""));
        assertTrue(index.contains("data-behaviour-tone=\"gesture\""));
        assertTrue(index.contains("data-behaviour-tone=\"motion\""));
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
        assertTrue(script.contains("const GESTURE_UI = {"));
        assertTrue(script.contains("const BEHAVIOUR_CHANNELS = [\"speech\", \"gesture\", \"face\", \"gaze\", \"motion\", \"display\"]"));
        assertTrue(script.contains("resetBehaviourPanels();"));
        assertTrue(script.contains("setGestureVisual(nonVerbal.gesture || \"NONE\")"));
        assertTrue(script.contains("function normalizeGestureToken(value)"));
        assertTrue(script.contains("function asUnitNumber(value)"));
        assertTrue(script.contains("function formatPercent(unitValue)"));
        assertTrue(script.contains("function setBehaviourChannelActive(channel, active)"));
        assertTrue(script.contains("function setBehaviourMeter(id, unitValue)"));
        assertTrue(script.contains("function setGestureVisual(value)"));
        assertTrue(script.contains("icon.className = `bi ${ui.icon}`;"));
        assertTrue(script.contains("el.classList.toggle(\"is-active\", !!active);"));
        assertTrue(script.contains("el.style.width = percent;"));
        assertTrue(script.contains("setText(\"face_intensity_value\", formatPercent(intensity));"));
        assertTrue(script.contains("setBehaviourMeter(\"motion_energy_meter\", energy);"));
        assertTrue(script.contains("setBehaviourMeter(\"motion_stillness_meter\", stillness);"));
        assertTrue(script.contains("setText(`${prefix}_sign_label`, ui ? ui.label : \"-\");"));
        assertTrue(script.contains("setText(`${prefix}_sign_visual`, ui ? ui.symbol : \"-\");"));
        assertTrue(script.contains("motion.handSign"));
        assertTrue(script.contains("renderAgentSign(sign)"));
        assertTrue(script.contains("renderUserSign(sign)"));
        assertTrue(script.contains("latest_behaviour_event"));
        assertFalse(script.contains("latest_event"));
        assertFalse(script.contains("camera_sign_value"));
    }

    @Test
    void valerianClientHydratesTextTranscriptFromEventHistory() throws IOException {
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
    void valerianClientSuppressesImmediateBehaviourResponseAndStreamDuplicates() throws IOException {
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
    void valerianClientConsumesAgentInteractionProfileForUiVisibility() throws IOException {
        String index = Files.readString(INDEX);
        String script = Files.readString(SCRIPT);

        assertTrue(index.contains("data-profile-observations=\"obs.emotion.face\""));
        assertTrue(index.contains("data-profile-observations=\"obs.human.presence obs.social.grouping obs.social.context\""));
        assertTrue(index.contains("data-profile-observations=\"obs.social.grouping obs.social.context\""));
        assertTrue(index.contains("data-profile-observations=\"obs.hand.sign\""));
        assertTrue(index.contains("data-profile-observations=\"obs.weather.current obs.weather.forecast\""));
        assertTrue(index.contains("data-profile-observations=\"obs.user_utterance\""));
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
        assertTrue(script.contains("PROFILE_WEATHER_OBSERVATIONS"));
        assertTrue(script.contains("function profileTokenMatches"));
        assertTrue(script.contains("function currentProfileSupportsObservation(observation)"));
        assertTrue(script.contains("function resetUnsupportedSensorModes"));
        assertTrue(script.contains("PROFILE_SENSOR_OBSERVATIONS"));

        assertFalse(script.contains("applyInteractionProfile(isFeaturedAgent"));
        assertFalse(script.contains("profileElementVisible(isFeaturedAgent"));
    }
}
