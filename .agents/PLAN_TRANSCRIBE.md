# PROMETHEUS live-transcription migration plan

Status: confirmed implementation plan for `features/transcribe`  
Baseline: `main` through milestone 148  
Target input model: `gpt-live-transcribe`

## Goal

Replace PROMETHEUS's combined OpenAI Realtime speech-to-speech session with a
transcription-first speech boundary:

```text
microphone
  -> gpt-live-transcribe
  -> finalized obs.user_utterance
  -> scoped acknowledge with FULL_PLAN
  -> persisted resp.behaviour_plan
  -> behaviour SSE
  -> canonical speech synthesis
  -> selected speaker
```

The agent runtime remains authoritative. Speech input is an asynchronous sensor,
and speech output is one renderer of the canonical `BehaviourPlan`. The finished
branch must not retain the combined Realtime call, sideband orchestration, or the
speech/complement split as a compatibility path.

Official API constraints should be rechecked against the OpenAI documentation
when implementation begins:

- <https://developers.openai.com/api/docs/models/gpt-live-transcribe>
- <https://developers.openai.com/api/docs/guides/realtime-transcription>
- <https://developers.openai.com/api/docs/guides/text-to-speech>

## Scope

In scope:

- Valerian microphone input, live transcription, transcript diagnostics, and
  agent acknowledgement.
- The bundled multilateral listener's transcription transport and settings,
  without changing its meeting-display purpose.
- A scoped backend contract for typed transcription-session creation.
- Provider, browser-capture, VAD, language, context, keyword, and delay settings
  based on the proven PROMISE implementation.
- General backend-authoritative synthesis of speech from persisted behaviour
  events.
- Valerian playback, output-device selection, exactly-once rendering, and
  explicit microphone/playback coordination.
- Removal of combined Realtime APIs, sideband code, obsolete output profiles,
  client controls, properties, tests, and documentation.

Out of scope:

- Speaker diarization or attributing a transcript to a named person.
- Replacing `Event`, `BehaviourPlan`, agent state machines, acknowledge, or SSE.
- Letting the browser author speech text that differs from the persisted plan.
- Browser-side speech recognition or a non-OpenAI fallback.
- Automatic barge-in in the first cutover. It can be designed later against the
  decoupled input and output transports.
- Changes to non-speech sensing or behaviour renderers except where shared
  lifecycle coordination requires them.

## Confirmed decisions

The user confirmed the following defaults on 2026-08-24:

1. **Turn policy:** keep the transcription connection alive but disable or gate
   microphone turns while PROMETHEUS speech is playing. Clear stale provider
   input before reopening the turn. This favors reliability over barge-in.
2. **Languages:** initialize from the selected agent's language and allow the
   operator to select additional supported languages. Do not always force the
   PROMISE `de,en` default on a monolingual agent.
3. **Group capture:** default provider noise reduction to `far_field`, browser
   echo cancellation/noise suppression/automatic gain control to enabled, and
   voice isolation to disabled so other nearby speakers are not intentionally
   filtered. All remain operator-configurable where the browser/provider
   supports them.
4. **Turn detection:** initially support the PROMISE transcription profile's
   tested `local_vad` and `manual` modes. Do not expose server/semantic VAD until
   it has an equally deterministic transcript-ordering contract.
5. **Output:** reuse the existing OpenAI Speech provider and default
   `gpt-4o-mini-tts`, but generalize the gateway and configuration names beyond
   Talk to Me. Stream provider audio to the browser when the Java HTTP and Spring
   MVC boundaries permit it without buffering the complete response.
6. **Playback authority:** exactly one Valerian page owns audible output for an
   agent at a time. A cross-tab output lease prevents duplicate speech.
7. **SSE delivery:** only newly published live behaviour is audible. Initial
   snapshots, history hydration, and reconnect replay update the UI but never
   speak.
8. **Profiles:** normal Valerian speech acknowledgement uses `FULL_PLAN`.
   `BACKEND_COMPLEMENT` is removed. `REALTIME_SPEECH` is removed as well unless
   milestone 4 proves Talk to Me needs a speech-only generation contract; in
   that case it is replaced, not aliased, by provider-neutral `SPEECH_ONLY`.

Changing decision 1 to continuous listening/barge-in would materially enlarge
milestone 5: it would require echo correlation, safe synthesis cancellation,
turn arbitration, and real acoustic interruption tests. Changing the other
defaults affects settings and acceptance cases but not the milestone structure.

## Existing patterns that guide the work

PROMETHEUS anchors:

- `ScopedDemoController` and `ScopedDemoService` for access-code ownership and
  agent-scoped contracts.
- `AgentApplicationService.acknowledge(...)` with default `FULL_PLAN` for the
  transcript-to-agent boundary.
- `AgentBehaviourBroadcaster` and Valerian's behaviour SSE cursor/deduplication
  for canonical output delivery.
- `SpeechSynthesisGateway`, `OpenAISpeechSynthesisGateway`, and the Talk to Me
  speech path for provider mapping and deterministic external-boundary tests.
- Valerian's audio-device selection, microphone lease, requested/applied media
  diagnostics, and current static-resource contract tests.

PROMISE reference implementation on `multilateral_brainkicks`:

- `public/realtime/shared/meeting-transcription-client.js`
- `public/realtime/shared/realtime-transport.js`
- `public/realtime/shared/realtime-events.js`
- `public/realtime/shared/realtime-settings.js`
- `public/realtime/shared/meeting-realtime-settings.js`
- `public/realtime/shared/realtime-media-devices.js`
- `public/realtime/shared/realtime-local-vad.js`
- Backend settings normalizer, descriptor, provider-payload builder, session
  issuance service, and their neighboring tests.

Port the concepts and focused code, not repository-specific meeting or
conversation assumptions. PROMETHEUS should expose one shared transcription
client used by Valerian and the multilateral listener.

## Milestone 0 - Lock acceptance criteria and capture the baseline

### Deliverables

- Confirm the decisions above, especially half-duplex versus barge-in and the
  language defaults.
- Define a short, repeatable German/English phrase corpus containing ordinary
  speech, PROMETHEUS vocabulary, names, and deliberately confusable words.
- Prepare the current combined Realtime baseline matrix for the actual Valerian
  wireless microphone and Bluetooth speaker. Physical measurements require an
  operator with access to those devices and are recorded when available.
- Add `.agents/TRANSCRIBE_SMOKE_RESULTS.md` as a result template containing:
  hardware/browser/OS, environment, distance, noise condition, settings,
  expected phrase, transcript, missed/duplicate turns, self-transcription, time
  to final transcript, time to first audio, and notes.
- Define pass/fail targets before tuning the new implementation. At minimum:
  no duplicated acknowledgements, no agent self-transcription, no replayed
  speech, and successful recovery after microphone or network interruption.

### Testable output

- A ready baseline row for each acceptance environment:
  near-field quiet, far-field quiet, outdoor/background noise, wireless
  microphone plus Bluetooth playback, and two or more conversational speakers.
  Rows that cannot be executed in the coding environment are explicitly marked
  `NOT RUN` rather than populated with inferred results.
- A reviewed decision section in this file with no unresolved architecture
  choice.

### Exit criterion

The team has fixed implementation defaults, a repeatable corpus, quantitative
targets, and an honest baseline record. Physical rows remain an explicit manual
verification item when the required hardware is not accessible to the coding
environment.

## Milestone 1 - Typed, scoped live-transcription session contract

### Deliverables

- Replace the weak `POST /realtime/transcription/session?agentId=...` contract
  with access-code-scoped transcription capabilities and session issuance under
  `/demo/agents/{agentId}/transcription/...`.
- Introduce typed request/effective-settings objects with a schema version.
- Support and validate:
  `near_field|far_field|off`, `local_vad|manual`, local silence duration,
  prompt/context, keywords, languages, and transcription delay.
- Derive initial language settings from the scoped agent while permitting an
  explicit validated override.
- Set `gpt-live-transcribe` as the transcription model and issue an ephemeral
  client secret for a `type: transcription` session.
- Return only the metadata required by the browser: client secret, model,
  WebRTC URL, session type, settings schema version, and effective settings.
- Preserve the safety identifier and actionable provider error handling without
  leaking API keys or provider response secrets.

### Tests

- Unit: defaults, normalization, allowed values, limits, immutability, agent
  language fallback, and rejection of unsafe prompt/keyword/device values.
- Provider contract: exact JSON for default and fully customized transcription
  sessions, including `session.type=transcription` and
  `model=gpt-live-transcribe`.
- Web MVC: valid scoped request, header/query access-code forms, invisible agent,
  disabled/invalid access code, invalid settings, and provider failure mapping.
- HTTP integration: deterministic fake OpenAI endpoint verifies headers and
  returns an ephemeral secret; no live network call runs in the normal suite.
- Configuration: template/prod property defaults and missing-key failure remain
  explicit.

### Exit criterion

A test client can obtain a valid transcription-only WebRTC session for an agent
it owns, and cannot issue one for another scoped agent. The combined call still
exists only as a temporary branch-local fallback until milestone 5 is complete.

## Milestone 2 - Shared browser transcription engine and operator settings

### Deliverables

- Add small ES modules for media acquisition, cross-tab microphone lease,
  WebRTC transport/reconnect, Realtime event ordering, local VAD, settings
  normalization, and the `gpt-live-transcribe` client.
- Adapt the PROMISE epoch/terminal ordering so partials are UI-only and each
  finalized provider turn is emitted exactly once.
- Keep the data channel transcription-only; assistant response/audio events are
  diagnostics and never rendered as output.
- Add exponential reconnect with teardown that stops tracks, timers, audio
  contexts, and stale event epochs.
- Replace Valerian's Realtime settings with transcription settings and expose
  requested versus applied browser capture values.
- Use the same shared engine in `public/multilateral/listen`; keep its existing
  display/report flow and do not acknowledge an agent unless that client already
  did so before the migration.
- Persist only non-sensitive operator preferences in local storage.

### Tests

- Node unit tests for pure settings, media constraints, local VAD segmentation,
  event ordering, duplicate terminal events, stale epochs, and reconnect state.
- Browser contract tests for module wiring, the scoped session URL, no assistant
  output handlers, teardown, and shared use by Valerian and multilateral listen.
- Playwright component smoke with mocked media/WebRTC boundaries:
  permission denied, connect, partial transcript, finalized transcript, manual
  commit, reconnect, changed input device, and stop.
- Playwright visual snapshots at deterministic desktop and narrow viewports for
  the closed settings drawer, open settings drawer, validation error, listening
  state, and reconnect error. Mask timers/device identifiers.

### Exit criterion

Valerian and the multilateral listener can run the same deterministic
transcription client against a mocked session, and every setting has both a
validated provider/browser mapping and visible effective diagnostics.

## Milestone 3 - Feed finalized speech through the existing agent pipeline

### Deliverables

- Serialize finalized transcription turns and call the existing scoped
  acknowledge endpoint as `obs.user_utterance` using `FULL_PLAN`.
- Render a user transcript once, even when provider terminal events repeat or a
  reconnect settles an epoch.
- Preserve current behavior when acknowledge returns no response: explicitly
  request normal full-plan generation only where current Valerian semantics
  require it.
- Keep acknowledgement response rendering and behaviour SSE visually
  deduplicated. Do not synthesize from the acknowledgement HTTP response.
- Surface queued, acknowledging, accepted, rejected, and provider-error states
  in diagnostics without treating failed input as accepted.

### Tests

- Unit/browser-flow: ordered final turns produce ordered acknowledge requests;
  partials, empty terminals, duplicates, stale epochs, and turns gated during
  playback produce none.
- Scoped integration: a final transcript persists one `obs.user_utterance` and
  at most one canonical `resp.behaviour_plan` with all supported modalities.
- Web MVC/browser contract: access code and agent identity are preserved; no
  `REALTIME_SPEECH` or complement profile is requested by Valerian.
- Playwright smoke: mocked final transcript -> acknowledge -> behaviour UI, with
  exactly one displayed user turn and one displayed assistant plan.

### Exit criterion

Live-transcription output is indistinguishable from typed user input at the
PROMETHEUS application boundary and cannot cause duplicate agent turns.

## Milestone 4 - Canonical behaviour-speech synthesis boundary

### Deliverables

- Generalize the existing Speech provider configuration and gateway so Valerian
  and Talk to Me share provider infrastructure without sharing client-specific
  application policy.
- Add a scoped endpoint that synthesizes by persisted behaviour event identity,
  for example:

  ```http
  POST /demo/agents/{agentId}/behaviours/{eventId}/speech?voice=cedar&speed=1.0
  ```

- Resolve the event only inside the scoped agent's history; require
  `resp.behaviour_plan`, parse canonical speech, and reject missing/empty speech.
- Never accept browser-authored speech text on this endpoint.
- Validate voice and speed using one shared contract and stream uncached audio
  with an explicit content type. Abort the upstream request when the browser
  disconnects where the HTTP stack exposes cancellation.
- Keep Talk to Me's agent-tag restriction and exact-text behavior intact while
  moving reusable synthesis mechanics out of Talk-to-Me-specific classes.

### Tests

- Unit: event ownership, event type, malformed plan, empty speech, canonical
  text extraction, voice/speed validation, and provider error translation.
- Provider contract: exact model/text/voice/speed mapping and streamed response
  metadata against a fake OpenAI server.
- Web MVC: valid scoped audio response plus invalid access code, foreign agent,
  unknown event, non-behaviour event, no-speech plan, invalid settings, and
  provider failure.
- Talk to Me regression: lifecycle, exact persisted text, synthesis mapping,
  Stop, and deletion remain green.

### Exit criterion

Given an SSE behaviour event ID, an authorized client can obtain audio for that
exact persisted speech and cannot synthesize arbitrary or foreign-agent text.

## Milestone 5 - Valerian playback and explicit turn coordination

### Deliverables

- Make SSE delivery semantics explicit enough to distinguish live publication
  from initial/history/reconnect replay. Update all bundled SSE consumers and
  contract documentation together.
- Trigger synthesis only from a live SSE behaviour carrying non-empty speech.
  Acknowledge responses and replay deliveries remain visual-only.
- Add an ordered playback queue keyed by behaviour event ID, with completed,
  failed, and deliberately skipped IDs tracked separately.
- Route output through the selected speaker and expose loading, speaking,
  stopped, and failed states.
- Add a cross-tab output lease so one agent behaviour is not spoken by multiple
  Valerian windows.
- During playback, disable/gate microphone input, discard stale transcription
  buffer content, and reliably reopen input after completion, Stop, error, or
  disconnect.
- Stop cancels queued/current audio and releases resources. It does not cancel
  or roll back the persisted `BehaviourPlan`.

### Tests

- Node unit: ordered queue, event-ID deduplication, replay suppression, lease
  conflict, cancellation, failure recovery, and microphone gate state machine.
- SSE unit/integration: live/replay labeling, cursor replay order, heartbeat,
  and unchanged persisted event payloads.
- Playwright end-to-end with fake provider/media boundaries:
  final transcript -> acknowledge -> live SSE -> one TTS request -> playback;
  acknowledgement plus SSE does not duplicate; initial/replayed SSE is silent;
  two pages produce one audio owner; Stop and TTS errors reopen the microphone.
- Playwright visual snapshots for listening, processing, speaking, stopped, and
  synthesis-error states on desktop and narrow layouts.
- Real smoke: quiet near/far field and Bluetooth speaker playback verifies no
  agent self-transcription before legacy removal.

### Exit criterion

Valerian completes the new STT -> PROMETHEUS -> TTS loop without the combined
Realtime assistant output, with exactly-once live speech and deterministic input
gating.

## Milestone 6 - Remove the combined Realtime architecture and legacy profiles

### Deliverables

- Delete the SDP call and close-call endpoints, views, settings, orchestration,
  sideband session/service, call info/configuration, and their tests.
- Remove Valerian's remote assistant media track, response creation/cancellation,
  assistant transcript path, combined-session barge-in/echo heuristics, and
  response-model tuning controls.
- Rename the remaining session client/controller around live transcription so
  `Realtime` no longer ambiguously means speech-to-speech.
- Delete properties used only for assistant Realtime generation and sideband
  responses. Retain/rename the client-secret and WebRTC calls URLs still needed
  to establish transcription WebRTC.
- Remove `BACKEND_COMPLEMENT` and its policy/runtime branches.
- Move Talk to Me to `FULL_PLAN` and remove `REALTIME_SPEECH`, or replace it with
  the explicitly justified provider-neutral `SPEECH_ONLY` decided in milestone
  4. Do not keep aliases.
- Delete `AssistantBehaviourPublishedEvent` and application publication plumbing
  if no non-sideband consumer remains.
- Remove old API Workbench actions, CORS cases, README examples, configuration
  comments, and static client assertions. Do not rewrite historical milestone
  records in `PROJECT.md`.

### Tests

- Compile/static contracts prove bundled clients contain no combined call,
  sideband, complement-generation, or assistant Realtime output path.
- Web MVC asserts removed endpoints are absent and the new scoped transcription
  and behaviour-speech contracts remain protected.
- Prompt/policy tests cover the final output-profile set.
- Focused suites from milestones 1-5, followed by the full Maven suite and all
  Playwright suites.
- A source audit outside historical records finds no obsolete class, property,
  endpoint, profile, or UI label.

### Exit criterion

There is one supported speech architecture. No production code or documented
public contract can start or service the former full-duplex Realtime session.

## Milestone 7 - Acoustic acceptance, resilience, and documentation

### Deliverables

- Execute the milestone-0 matrix with the new path and record actual settings,
  transcripts, missed/duplicate turns, self-transcription, and observed latency.
- Exercise session expiry/reissue, transient network loss, device unplug/replug,
  page refresh, agent switch/delete/reset, hidden tab, and two-tab conflicts.
- Tune defaults only from recorded results; keep environment-specific tuning in
  operator settings rather than hard-coded client branches.
- Update README setup, properties, public API, architecture, UI instructions,
  limitations, and test commands.
- Update `.agents/CONTEXT.MD` current capabilities/boundaries and append the
  completed milestone records/current state in `PROJECT.md`.
- Document that transcription quality does not imply speaker identification and
  that overlapping speakers remain an acoustic/model limitation.

### Verification matrix

Run and report the exact commands that exist after implementation. The intended
top-level gates are:

```powershell
.\mvnw.cmd test
npm run test:transcription:unit
npm run test:valerian:transcription
npm run test:valerian:visual
npm run test:talktome:visual
npm run test:apiworkbench:visual
```

The live OpenAI smoke and acoustic matrix are explicit manual tests requiring
configured credentials and real devices; they must never be implied by mocked
CI results.

### Exit criterion

Automated regression gates pass, the real-device results meet the targets fixed
in milestone 0 or have documented deviations accepted by the user, documentation
matches the shipped contracts, and all remaining risks are explicit.

## Definition of done for the branch

- `gpt-live-transcribe` is the only PROMETHEUS live microphone transcription
  model and is configured through the scoped typed contract.
- Valerian sends finalized transcripts through normal `FULL_PLAN`
  acknowledgement and receives all modalities through canonical behaviour.
- Audible speech is synthesized only from persisted behaviour event identity.
- Live speech plays once; history/replay and parallel windows do not duplicate
  it; microphone state always recovers after output.
- Valerian and the multilateral listener share the transcription transport and
  settings implementation.
- Talk to Me remains functionally intact on the generalized Speech provider.
- Combined Realtime calls, sideband behavior, complement profiles, obsolete
  properties/UI/tests/docs, and unneeded compatibility code are removed.
- Focused tests, full regression tests, Playwright tests, and actual hardware
  smoke results are reported honestly.
