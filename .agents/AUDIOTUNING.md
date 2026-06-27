# Audio Tuning Transfer Specification

This document specifies the Valerian cockpit speech-to-speech audio tuning
changes that should be repeated in the PROMETHEUS repository cockpit.

Use this as an implementation brief for a Codex agent. The target cockpit may
have different file names, component names, or framework structure. Preserve the
contracts and behavior below, but adapt the code to the target repository's
existing architecture.

## Source Of The Specification

The source implementation is Valerian cockpit on June 26, 2026.

Relevant commits:

```text
d95ebd6ade1fccd9e3f3143e8da5ecdcd05e1666
Harden cockpit realtime speech audio handling

79b3d15141d419d128ae0f1e14efe1a5fe6b9396
Expose realtime speech tuning controls

9f2664de57bb5a760c7a89e8002c86afe236ba48
Move cockpit speech settings to sensing accordion
```

Relevant Valerian files:

```text
apps/valerian-cockpit/index.html
apps/valerian-cockpit/static/app.js
apps/valerian-cockpit/static/app.css
apps/valerian-cockpit/tests/test_static_server.py
apps/valerian-cockpit/README.md
README.md
PROJECT.md
```

## Goal

Improve browser realtime speech-to-speech reliability and tunability while
preserving full-duplex interaction by default.

The target cockpit should:

- keep the microphone live during assistant playback by default
- allow user barge-in to cancel an active assistant response
- suppress likely assistant-audio echo transcripts
- expose optional half-duplex microphone muting as a fallback only
- expose advanced realtime session tuning controls before call startup
- surface audio playback and WebRTC inbound-audio diagnostics in the UI
- keep routine speech controls simple by moving advanced controls into a closed
  settings accordion or equivalent low-density UI area

## Non-Goals

Do not implement these as part of this change unless the target repository
already has them scoped:

- a new backend proxy for realtime speech
- robot-side speech or robot microphone/speaker support
- STUN/TURN credential management
- OpenAI Realtime backend mapping if the task is cockpit-only
- a large cockpit redesign unrelated to speech setup
- live PROMETHEUS tests as mandatory unit tests

## Target Assumptions

The target cockpit is expected to already have:

- a browser-side PROMETHEUS access-code session
- a selected or connected PROMETHEUS agent id
- a scoped fetch helper that sends `X-Prometheus-Access-Code`
- a realtime WebRTC speech flow that posts an SDP offer to:

```text
POST /demo/agents/{agentId}/realtime/call
```

- an SDP answer response containing `sdp` and optionally `callId` or `id`
- an optional cleanup endpoint:

```text
DELETE /realtime/calls/{callId}
```

- a browser audio element for assistant playback
- a realtime data channel that receives PROMETHEUS/OpenAI realtime events
- existing activity/status helpers or equivalent UI logging

If names differ, map the behavior to the existing target concepts.

## Implementation Milestones

Implement in this order:

1. Full-duplex resilience and audio diagnostics.
2. Realtime session tuning controls and query parameters.
3. Advanced speech settings accordion or equivalent UI grouping.
4. Static/unit tests and documentation.

Each milestone should update target documentation and tests before stopping for
commit.

## UI Contract

### Main Speech Area

Keep high-frequency controls visible in the main Speech tab or panel:

- microphone selector
- speaker selector
- refresh audio devices action
- start speech action
- stop speech action
- assistant audio element
- speech state
- transport state or transport detail
- transcript or speech preview

Do not bury start/stop, microphone, speaker, or status controls inside advanced
settings.

### Advanced Speech Settings

Move lower-frequency tuning controls into a closed-by-default advanced section.
In Valerian this is a Sensing-column accordion:

```html
<details class="drawer-panel sensing-section"
  data-profile-observations="obs.user_utterance"
  data-testid="advanced-speech-settings">
  <summary>
    <span>Advanced Speech Settings</span>
    <span class="sensing-section-hint">voice, VAD, realtime</span>
  </summary>
  ...
</details>
```

In a different cockpit, place the section wherever advanced operator settings
naturally live. Keep it closed by default if the platform uses accordions.

### Controls To Add Or Preserve

The following controls are the transfer contract. Preserve the semantics even
if the target uses a component framework instead of raw HTML.

| Setting | Control | Values | Default | Storage key |
| --- | --- | --- | --- | --- |
| Voice | select | `default`, `alloy`, `ash`, `ballad`, `cedar`, `coral`, `echo`, `marin`, `sage`, `shimmer`, `verse` | `alloy` selected, empty means backend default | `speechVoice` |
| VAD mode | select | `server_vad`, `semantic_vad` | `server_vad` | `speechVadMode` |
| Backend complement | checkbox | on/off | on | `speechComplement` |
| Transcript logprobs | checkbox | on/off | off | `speechTranscriptionLogprobs` |
| Barge-in cancellation | checkbox | on/off | on | separate key below |
| Half-duplex fallback | checkbox | on/off | off | separate key below |
| VAD threshold | number | 0 to 1, step 0.05 | blank, placeholder `0.5` | `speechVadThreshold` |
| VAD prefix ms | number | 0 to 2000, step 50 | blank, placeholder `300` | `speechVadPrefixPaddingMs` |
| VAD silence ms | number | 0 to 3000, step 50 | blank, placeholder `500` | `speechVadSilenceDurationMs` |
| VAD eagerness | select | blank, `low`, `auto`, `medium`, `high` | blank, label `default auto` | `speechVadEagerness` |
| VAD creates | select | blank, `true`, `false` | blank, label `default on` | `speechVadCreateResponse` |
| VAD interrupts | select | blank, `true`, `false` | blank, label `default on` | `speechVadInterruptResponse` |
| Input noise reduction | select | blank, `near_field`, `far_field`, `off` | blank, label `backend default` | `speechInputNoiseReduction` |
| Output speed | number | 0.25 to 1.5, step 0.05 | blank, placeholder `1.0` | `speechOutputSpeed` |
| Reasoning effort | select | blank, `low`, `medium`, `high` | blank, label `model default` | `speechReasoningEffort` |
| Max output tokens | number | 1 to 4096, step 1 | blank, placeholder `inf` | `speechMaxOutputTokens` |

Valerian DOM ids and test ids:

```text
speechVoiceInput                  data-testid="speech-voice"
speechVadSelect                   data-testid="speech-vad"
speechComplementToggle            data-testid="speech-complement"
speechTranscriptionLogprobsToggle data-testid="speech-transcription-logprobs"
speechBargeInCancelToggle         data-testid="speech-barge-in-cancel"
speechEchoGuardToggle             data-testid="speech-echo-guard"
speechVadThresholdInput           data-testid="speech-vad-threshold"
speechVadPrefixInput              data-testid="speech-vad-prefix"
speechVadSilenceInput             data-testid="speech-vad-silence"
speechVadEagernessSelect          data-testid="speech-vad-eagerness"
speechVadCreateResponseSelect     data-testid="speech-vad-create-response"
speechVadInterruptResponseSelect  data-testid="speech-vad-interrupt-response"
speechInputNoiseReductionSelect   data-testid="speech-input-noise-reduction"
speechOutputSpeedInput            data-testid="speech-output-speed"
speechReasoningEffortSelect       data-testid="speech-reasoning-effort"
speechMaxOutputTokensInput        data-testid="speech-max-output-tokens"
```

Use compact default hints beside tunable labels. Valerian uses:

```css
.setting-default {
  color: var(--text);
  font-size: 0.72rem;
  font-weight: 700;
  margin-left: 0.25rem;
  white-space: nowrap;
}
```

If the target has an existing design system, implement the same information
with local label/helper-text components instead of copying CSS verbatim.

## Storage Contract

Persist speech choices in browser storage. Valerian uses these localStorage
keys:

```javascript
speechInputDevice: "valerian.cockpit.speechInputDevice"
speechOutputDevice: "valerian.cockpit.speechOutputDevice"
speechVoice: "valerian.cockpit.speechVoice"
speechVadMode: "valerian.cockpit.speechVadMode"
speechComplement: "valerian.cockpit.speechComplement"
speechVadThreshold: "valerian.cockpit.speechVadThreshold"
speechVadPrefixPaddingMs: "valerian.cockpit.speechVadPrefixPaddingMs"
speechVadSilenceDurationMs: "valerian.cockpit.speechVadSilenceDurationMs"
speechVadEagerness: "valerian.cockpit.speechVadEagerness"
speechVadCreateResponse: "valerian.cockpit.speechVadCreateResponse"
speechVadInterruptResponse: "valerian.cockpit.speechVadInterruptResponse"
speechInputNoiseReduction: "valerian.cockpit.speechInputNoiseReduction"
speechOutputSpeed: "valerian.cockpit.speechOutputSpeed"
speechReasoningEffort: "valerian.cockpit.speechReasoningEffort"
speechMaxOutputTokens: "valerian.cockpit.speechMaxOutputTokens"
speechTranscriptionLogprobs: "valerian.cockpit.speechTranscriptionLogprobs"
speechBargeInCancel: "valerian.cockpit.speechBargeInCancel"
speechEchoGuard: "valerian.cockpit.speechEchoGuard"
```

In PROMETHEUS, use repository-appropriate prefixes. Preserve the logical key
names so behavior is clear.

Default interpretation:

- `speechBargeInCancel` is enabled unless the stored value is exactly `"false"`.
- `speechEchoGuard` is enabled only when the stored value is exactly `"true"`.
- Blank advanced setting values should remove or ignore the stored setting.
- Checkbox values should persist as `"true"` or `"false"`.

## Realtime Call Request Contract

The cockpit must continue to send the browser-generated SDP offer as the
request body:

```http
POST /demo/agents/{agentId}/realtime/call?...query params...
Content-Type: application/sdp

<offer SDP>
```

Do not replace the SDP flow with a placeholder or JSON body.

Always send:

```text
turnDetection
generateComplement
```

Send `voice` only when non-empty. Send optional tuning parameters only when
their parsed values are valid and non-empty.

Query parameters:

```text
voice
turnDetection
generateComplement
vadThreshold
vadPrefixPaddingMs
vadSilenceDurationMs
vadEagerness
vadCreateResponse
vadInterruptResponse
inputNoiseReduction
outputSpeed
reasoningEffort
maxOutputTokens
includeInputTranscriptionLogprobs
```

Parsing and validation:

```text
vadThreshold: number 0 <= value <= 1
vadPrefixPaddingMs: integer 0 <= value <= 2000
vadSilenceDurationMs: integer 0 <= value <= 3000
vadCreateResponse: optional boolean from "true" or "false"
vadInterruptResponse: optional boolean from "true" or "false"
outputSpeed: number 0.25 <= value <= 1.5
maxOutputTokens: integer 1 <= value <= 4096
includeInputTranscriptionLogprobs: send only when checkbox is checked
```

Blank or invalid optional values must be omitted, not sent as empty strings.

PROMETHEUS backend mapping is expected to map these query params to the current
OpenAI Realtime session shape. The cockpit should not mutate Realtime session
objects directly if the existing architecture routes that responsibility
through the PROMETHEUS backend.

Expected backend destinations include:

```text
audio.input.turn_detection
audio.input.noise_reduction
audio.output.speed
reasoning
max_output_tokens
audio.input.transcription
```

Keep `VAD Interrupts` and local `Barge-in cancellation` as separate controls:

- `VAD Interrupts` is the preferred backend/session-level interruption policy.
- `Barge-in cancellation` is a cockpit-local fallback that sends
  `response.cancel` when user speech starts during assistant output.

## Browser Audio Capture Contract

When starting speech, request microphone capture with the selected input device
and these processing hints:

```javascript
{
  echoCancellation: true,
  noiseSuppression: true,
  autoGainControl: true,
  channelCount: { ideal: 1 },
  voiceIsolation: true
}
```

If a selected microphone device id exists, add:

```javascript
deviceId: { exact: selectedSpeechInputDeviceId }
```

Browsers may ignore unsupported constraints such as `voiceIsolation`. Do not
treat ignored constraints as fatal. After capture starts, log the active track
settings when `MediaStreamTrack.getSettings()` is available:

```text
echoCancellation
noiseSuppression
autoGainControl
channelCount
```

## Realtime State Additions

Add or adapt equivalent runtime state fields:

```javascript
realtime: {
  micRestoreTimer: null,
  micMutedForAssistant: false,
  playbackIssueActive: false,
  lastPlaybackWarningAt: 0,
  assistantAudioActive: false,
  lastAssistantTranscript: "",
  lastAssistantTranscriptAt: 0,
  userSpeechActive: false,
  statsTimer: null,
  lastAudioStats: null,
  lastStatsWarningAt: 0,
  lastBargeInCancelAt: 0
}

speechSettings: {
  bargeInCancelEnabled: true,
  echoGuardEnabled: false
}
```

Constants from Valerian:

```javascript
REALTIME_ECHO_GUARD_RELEASE_MS = 1200
REALTIME_ECHO_GUARD_MAX_MUTE_MS = 30000
REALTIME_PLAYBACK_WARNING_COOLDOWN_MS = 3000
REALTIME_STATS_POLL_MS = 2000
REALTIME_STATS_WARNING_COOLDOWN_MS = 5000
REALTIME_BARGE_IN_CANCEL_COOLDOWN_MS = 750
REALTIME_ECHO_TRANSCRIPT_MAX_AGE_MS = 45000
REALTIME_ECHO_TRANSCRIPT_MIN_CHARS = 18
REALTIME_ECHO_TRANSCRIPT_SIMILARITY = 0.78
```

## Full-Duplex Barge-In Behavior

Default behavior must be full-duplex:

- microphone remains enabled while assistant audio plays
- user speech can interrupt assistant output
- half-duplex microphone muting is opt-in only

Handle realtime data-channel events:

```text
input_audio_buffer.speech_started
input_audio_buffer.speech_stopped
response.created
response.audio.delta
response.output_audio.delta
response.output_audio_transcript.delta
response.output_text.delta
response.output_audio_transcript.done
response.output_text.done
response.done
response.audio.done
response.output_audio.done
response.cancelled
response.canceled
conversation.item.input_audio_transcription.completed
```

Expected behavior:

- On `input_audio_buffer.speech_started`, mark user speech active.
- If assistant audio is active and `bargeInCancelEnabled` is true, send this
  client event on the realtime data channel:

```json
{"type":"response.cancel"}
```

- Rate-limit local cancel sends with `REALTIME_BARGE_IN_CANCEL_COOLDOWN_MS`.
- When cancellation is sent, clear the local assistant transcript preview and
  mark assistant audio inactive.
- If assistant audio is active and barge-in cancellation is disabled, log that
  user barge-in was detected but cancellation is disabled.
- On `input_audio_buffer.speech_stopped`, mark user speech inactive.
- On assistant response/audio/transcript deltas, mark assistant audio active.
- On assistant response/audio done or cancelled events, mark assistant audio
  inactive and schedule microphone restore if half-duplex fallback muted it.

Sending realtime client events must be defensive:

- require an open data channel
- JSON encode the payload
- catch send errors
- write an activity-log message when send fails

## Half-Duplex Fallback

`Half-duplex fallback` is a difficult-acoustics fallback, not the default.

When enabled:

- on assistant response/audio activity, set outbound microphone audio tracks to
  `enabled = false`
- restore microphone tracks after assistant playback ends
- also restore after `REALTIME_ECHO_GUARD_MAX_MUTE_MS` as a safety timeout

When disabled:

- immediately restore microphone tracks if they were muted
- keep full-duplex barge-in active

Implementation detail:

```javascript
track.enabled = enabled
```

Do not stop and recreate microphone tracks for half-duplex fallback. Only flip
`enabled`, so the WebRTC connection remains alive.

## Assistant Echo Transcript Suppression

Suppress probable assistant echo in completed user ASR transcripts.

Record the most recent assistant transcript when a response transcript completes
or when a final text value is available. For each completed user transcript
candidate:

1. Ignore if there is no recent assistant transcript.
2. Ignore if the assistant transcript is older than
   `REALTIME_ECHO_TRANSCRIPT_MAX_AGE_MS`.
3. Normalize both texts using the cockpit's existing transcript normalization.
4. Do not apply echo suppression if either normalized text is shorter than
   `REALTIME_ECHO_TRANSCRIPT_MIN_CHARS`.
5. Suppress if the normalized texts are equal.
6. Suppress if one normalized text includes the other.
7. Otherwise token-similarity suppress if:

```text
intersection(tokens longer than 2 chars) / max(left token count, right token count)
>= REALTIME_ECHO_TRANSCRIPT_SIMILARITY
```

When all candidates in a flush are rejected and at least one was an echo
candidate, log:

```text
Suppressed probable assistant echo transcript.
```

Keep existing noisy-ASR and duplicate transcript gates. This new gate should be
one additional filter, not a replacement.

## Audio Playback Diagnostics

Register diagnostics on the assistant audio element:

```text
playing  -> clear active playback issue
waiting  -> report buffering/choppy playback warning
stalled  -> report stalled/choppy playback warning
error    -> report media error detail
```

Register diagnostics on remote audio tracks received through `RTCPeerConnection`
`ontrack`:

```text
mute   -> report remote assistant audio track muted
unmute -> clear playback issue
ended  -> report remote assistant audio track ended unexpectedly
```

Throttle playback warnings with `REALTIME_PLAYBACK_WARNING_COOLDOWN_MS`.

Media error messages should distinguish, where available:

```text
playback aborted
network error
decode error
source not supported
media error <code>
unknown media error
```

Surface diagnostics in the same place the cockpit shows speech transport
detail. Do not rely on the browser console.

## WebRTC Inbound Audio Stats Diagnostics

After the remote SDP answer is set, start polling:

```javascript
peerConnection.getStats()
```

Polling interval:

```text
REALTIME_STATS_POLL_MS = 2000
```

Stop polling during realtime teardown.

Extract from `inbound-rtp` audio reports:

```text
packetsLost
jitter
concealedSamples
jitterBufferDelay
jitterBufferEmittedCount
```

Extract from selected or nominated successful `candidate-pair` reports:

```text
currentRoundTripTime
```

Warn when any of the following are true compared with the previous sample:

```text
packet loss delta > 0
concealed samples delta > 960
jitter > 80 ms
jitter buffer delay per emitted sample > 120 ms
RTT > 800 ms
```

Warning text shape:

```text
Realtime audio stats warning: <issues>.
```

Throttle stats warnings with `REALTIME_STATS_WARNING_COOLDOWN_MS`.

Stats warnings reveal likely network/browser audio symptoms. They do not fix
backend generation, network jitter, hardware DSP, or TURN/STUN reachability.

## Realtime Setup And Teardown Integration

At realtime setup:

1. Reset realtime diagnostic flags.
2. Create `RTCPeerConnection`.
3. Register peer connection diagnostics that the target already supports.
4. Register remote audio track diagnostics in `ontrack`.
5. Create data channel or use the existing target data channel.
6. Register data-channel close/error/message handling.
7. Acquire microphone with the browser audio constraints above.
8. Log active microphone processing settings.
9. Add microphone tracks to the peer connection.
10. Create and set local SDP offer.
11. POST SDP offer to `/demo/agents/{agentId}/realtime/call`.
12. Store `callId` or `id` when returned.
13. Set remote SDP answer.
14. Start WebRTC stats diagnostics.
15. Mark speech live using the target cockpit's existing status model.

At realtime teardown:

- close data channel
- close peer connection
- stop stats diagnostics
- restore microphone if half-duplex fallback muted it
- reset playback, assistant-audio, user-speech, and stats state
- stop microphone tracks
- clear assistant audio `srcObject`
- call the existing realtime DELETE cleanup endpoint if a call id exists
- reset transcript gates consistently with the target implementation

## Control Enablement Rules

Preserve the target cockpit's existing speech route/profile gating.

Valerian behavior:

- Start disabled when profile-disabled, no agent, or call active.
- Stop disabled when profile-disabled or no call active.
- Microphone selector disabled when profile-disabled, audio selection
  unsupported, or call active.
- Speaker selector disabled when profile-disabled, audio selection unsupported,
  or output selection unsupported.
- Advanced session-negotiation controls disabled when profile-disabled or call
  active.
- `Barge-in cancellation` and `Half-duplex fallback` disabled only when
  profile-disabled, so they can be changed during an active call.
- Refresh audio devices disabled when profile-disabled or audio selection is
  unsupported.

## CSS/Layout Guidance

Valerian used:

```css
.speech-grid {
  display: grid;
  grid-template-columns: minmax(10rem, 1fr) minmax(10rem, 1fr);
  gap: 0.75rem;
  align-items: end;
}

.speech-settings-grid {
  grid-template-columns: 1fr;
}

.speech-device-grid {
  margin-top: 0;
}

.assistant-audio {
  width: 100%;
}
```

Adapt these to the target design system. The important UX requirement is:

- advanced controls are visually grouped and lower priority
- routine microphone/speaker/start/stop controls stay immediately visible
- long labels and default hints do not overflow their containers

## Documentation Requirements

Update target documentation with:

- how to start speech
- how to choose microphone and speaker devices
- where advanced speech settings live
- which optional realtime parameters are forwarded
- that blank optional controls preserve backend defaults
- that full-duplex is the default
- what `Barge-in cancellation` does
- what `Half-duplex fallback` does and when to use it
- what audio/WebRTC diagnostics can and cannot prove
- backend dependency on PROMETHEUS forwarding `input_audio_buffer.speech_started`
  and supporting `response.cancel`

Suggested operator wording:

```text
Speech is full-duplex by default. The microphone stays live while assistant
audio plays so the operator can interrupt. Barge-in cancellation is enabled by
default; when PROMETHEUS forwards input_audio_buffer.speech_started during an
assistant response, the cockpit sends response.cancel over the realtime data
channel. Half-duplex fallback is disabled by default and should only be enabled
when speaker-to-microphone echo is worse than losing interruption.
```

## Test Requirements

Add focused high-value tests. Do not require live PROMETHEUS or a real
microphone in automated tests.

Minimum test coverage:

- advanced speech settings section exists and is placed outside the main
  high-frequency Speech controls
- all new control IDs or component test ids are present
- controls expose/persist the expected storage keys
- default labels and supported select options are present
- `Barge-in cancellation` defaults on
- `Half-duplex fallback` defaults off
- microphone constraints include echo cancellation, noise suppression, AGC,
  mono channel preference, and voice isolation
- realtime call URL includes the new optional query params when controls are
  set
- blank optional controls are omitted
- invalid range values are omitted
- `includeInputTranscriptionLogprobs` is sent only when enabled
- data-channel barge-in sends `response.cancel`
- half-duplex fallback toggles `track.enabled`, not track stop/recreation
- echo suppression uses recent assistant transcript comparison
- playback diagnostics register audio element and remote track listeners
- WebRTC stats diagnostics poll `getStats()` and warn on packet loss, jitter,
  jitter-buffer delay, concealed samples, or RTT
- session-negotiation controls are disabled during an active call
- documentation mentions full-duplex default, barge-in cancellation, half-duplex
  fallback, tuning query params, and diagnostics

Recommended local validation commands, adapted to target repo:

```bash
python -m pytest <cockpit tests>
python -m compileall <python cockpit server path>
node --check <cockpit js entrypoint>
```

If the target uses TypeScript or a bundler, run the target's normal typecheck,
lint, and build commands instead of `node --check`.

## Manual Smoke Test

After implementation, run a manual cockpit smoke against a PROMETHEUS instance
that supports realtime speech:

1. Open the cockpit.
2. Connect a PROMETHEUS access-code session and agent.
3. Select microphone and speaker devices.
4. Leave `Barge-in cancellation` enabled.
5. Leave `Half-duplex fallback` disabled.
6. Start speech.
7. Confirm microphone capture starts with the selected input.
8. Confirm assistant audio plays through the selected output when supported.
9. Speak over assistant output and confirm the local cockpit sends
   `response.cancel`.
10. Confirm user transcripts do not repeatedly contain assistant playback text.
11. Enable `Half-duplex fallback`, trigger an assistant response, and confirm
    microphone tracks disable during assistant output and re-enable afterward.
12. Inspect transport/status details for any playback or stats warnings.
13. Stop speech and confirm tracks, peer connection, data channel, stats timer,
    and backend realtime call cleanup are released.

## Backend Coordination Notes

The cockpit changes are only fully effective when PROMETHEUS supports the
corresponding realtime behavior.

Required or expected backend behavior:

- accept SDP offer bodies on `/demo/agents/{agentId}/realtime/call`
- return an SDP answer
- return a call id when cleanup is supported
- forward realtime data-channel events including
  `input_audio_buffer.speech_started`
- accept client data-channel event `response.cancel`
- map `turnDetection`, VAD timing/eagerness, VAD response policy, input noise
  reduction, output speed, reasoning effort, max output tokens, and transcript
  logprobs query params to the current OpenAI Realtime session configuration

If backend support is incomplete, still implement the cockpit controls and
guard behavior defensively:

- omit blank params
- show operator-visible status when cancellation cannot be sent
- keep half-duplex fallback available
- document which backend params are currently no-ops

## Acceptance Checklist

The implementation is complete when:

- full-duplex remains the default speech mode
- barge-in cancellation sends `response.cancel` during active assistant output
- half-duplex fallback is present, disabled by default, and reversible during a
  live call
- assistant echo transcripts are suppressed without suppressing short legitimate
  user utterances
- advanced tuning controls persist and are forwarded as query params
- optional blank tuning controls preserve backend defaults
- audio playback and WebRTC inbound-audio diagnostics are visible in the UI
- routine Speech tab controls remain immediately visible
- tests cover the new UI contracts, request contract, and critical realtime
  behavior
- docs tell operators how and when to use the new controls
- the change stops for commit after tests and docs are updated
