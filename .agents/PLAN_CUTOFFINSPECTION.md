# GPT-Live received-audio inspection plan

Status: planned, not implemented. Written 2026-10-23. This is an implementation
handoff for a new Codex session, not evidence that the audio cut-off is fixed.

## Goal and operating instructions

Implement an optional browser-local recording of the **received GPT-Live audio
track**, paired with the existing telemetry export. Enable an operator to locate
a reported missing final word in both the saved audio and the event timeline.
Keep normal conversation, playback, task control and immediate shutdown intact.

Before work, read `.agents/CODEX.md`, `.agents/CONTEXT.MD`, and `PROJECT.md` from
the beginning through Current milestone state, stopping before Historical
milestones checklist. Check branch/worktrees and existing changes; inspect the
files below. Give the user a concise mental model and focused implementation
scope before editing. Treat current code as authoritative if this plan has aged.

This is shared Live/cockpit infrastructure, not a Gala prompt adjustment. Follow
the documented shared-change route: implement on `feature/gptlive`, then integrate
through `main` into `agents` when publication is authorized. Use existing clean
worktrees or an isolated checkout; do not switch a dirty worktree or mix Gala-only
definitions into main. The current request authorizes publishing this plan and
the already completed GI-27 changes; it does not itself order implementation of
this plan or changes to the standalone Valerian repository.

Once implementation is requested, complete the milestones below in order. They
need no intermediate physical trial; automated acceptance comes first, followed
by one operator trial. Stop for review unless that session explicitly authorizes
commit/push. Report any required architectural departure before taking it.

## Problem and evidence so far

The operator repeatedly hears GPT-Live omit a final word or part of a reply.
In earlier cases, the missing word appeared in both the live caption preview
and the durable recorded conversation. Complete text therefore does not establish
complete audible output. This occurs with the direct GPT-Live conversation path,
separate from PROMETHEUS's Continuous STT/TTS and streamed-PCM player.

The Willow trial exported as
`C:\Users\Admin\Downloads\prometheus-telemetry-2026-10-22T22-39-49-052Z.json`
was reported to sound intact for a long period and to clip again during guessing.
The operator could not recall an exact clipped utterance. A state association is
a useful hypothesis, not an established cause or proof of a voice-specific fault.

Read-only analysis of that export and its database conversation found:

- The relevant final session was `3c1ed97f-7020-4aef-922b-f10de9548e25`, with
  Willow throughout. There was no recorded local playback stop or provider
  error during the guessing activity. Output stopped at the operator's Stop.
- The sole `context_injection_incomplete` error in that session arrived after
  Stop, during context delivery. Its provider close reason was `remote_hangup`.
  Neither establishes a mid-reply provider truncation.
- WebRTC reported concealment events before and during guessing, despite zero
  reported packet loss. Concealment can reflect late/lost media; these counters
  alone cannot locate or explain the missing word.
- Some transcript problems were already present in provider fragments, and
  overlapping input/output occurred. Those are separate evidence from acoustic
  clipping. Application COMPLETE/INCOMPLETE transcript statuses describe capture
  and segmentation, not provider generation status or hardware playback.
- Two guessing introductions reversed the roles before backend readiness. GI-27
  strengthens roles in shared guidance, start occasions and active card facts.
  That independent correction must not be mistaken for an audio fix.

Existing analysis artifacts, if still available, are under
`target/telemetry-review/20261022-223949-db.*`. They are ignored local evidence,
may contain conversation text, and must not be committed. Do not require another
production database query to implement or test this diagnostic feature.

## Architecture and boundaries

Live owns spoken wording. PROMETHEUS supplies state/facts and deliberate speaking
occasions. In `live/client.js`, `RTCPeerConnection.ontrack` attaches the incoming
MediaStream to the existing HTML audio element. That continuous WebRTC track is
the output path; our code does not assemble output PCM blocks per reply.

The client does not close playback on `response.done` or
`response.output_audio.done`. Those Realtime events do not describe this
GPT-Live protocol. The existing `stop()` intentionally silences playback and
releases resources synchronously, before asynchronous backend cleanup. Context
updates and transcript deltas do not reset the audio element.

Do not add drain delays, final-chunk padding, an alternative player, Web Audio
routing, VAD changes, new speech gates, prompt changes or disabled interruptions
as part of this work. Those would alter the behavior being observed without an
identified failure mechanism. Keep the two-second presence grace and all Stop,
revocation, scope-change and ownership-loss behavior as currently implemented.

The diagnostic recording is a second consumer of the received track, before
physical speaker/device playback. It is not a microphone recording, acoustic
loopback, provider-original file or bit-exact network capture. Browser decoding,
jitter concealment and recorder encoding can all affect it.

## Files and existing patterns to use

Paths below are relative to this repository unless stated otherwise.

- `src/main/resources/public/live/client.js`: session generation fencing,
  `ontrack`, diagnostics callback, immediate Stop and asynchronous finalization.
- `src/main/resources/public/live/audio-diagnostics.js`: existing media events
  and inbound/outbound RTC counters; observes media without recording today.
- `src/main/resources/public/live/diagnostics.js`: bounded, allowlisted session
  metadata, caption receipt IDs, media/context associations and stop causes.
- `src/main/resources/public/live/cockpit.js` and
  `src/main/resources/public/valerian/index.html`: Live controls, connection
  changes and presence-driven starts. Avoid persisting recording consent in
  the existing media-preference storage.
- `src/main/resources/public/live/reception.js`: automatic encounters and the
  two-second departure grace; preserve this behavior.
- `src/main/resources/public/performance/{activity,panel,report}.js`: existing
  `speech_cut_off` issue marker, telemetry snapshot and local download pattern.
- `src/main/java/ch/zhaw/prometheus/application/ScopedLiveSessionService.java`:
  bounded provider event/error/close metadata and audio-activity measurements.
  Inspect as needed; no backend change is expected for this feature.
- `tests/js/live/{client,audio-diagnostics,diagnostics,reception}.test.mjs`:
  injected fakes, lifecycle tests and unchanged-playback assertions.
- `tests/playwright/valerian-gptlive.spec.mjs` and
  `tests/playwright/valerian-gptlive-smoke.spec.mjs`: UI and real local-app smoke.
- `tests/gptlive/run_acceptance.py`: disposable local MySQL/provider fixtures.
  Its default database properties are production-oriented; always explicitly
  select `src/main/resources/application-test.properties` for this work.
- README's GPT-Live diagnostics sections, `tests/gptlive/GALA.md`, and
  `.agents/GPTLIVE_RESULTS.md`: user instructions and verification evidence.

The other cockpit is in `C:\Data\VSCodeWorkSpace\valerian`, under
`apps/valerian-cockpit/static/live`. Record any necessary follow-up there, but do
not modify or publish that repository as part of this plan without instruction.

## CI-01 - Bounded, independent received-track recording

Create a small testable module, for example `live/audio-recording.js`, using
native `MediaRecorder` where supported. Inject recorder/clock factories for unit
tests rather than introducing a recording library.

Required behavior:

1. Recording is off by default and explicitly armed for the **next one Live
   session** only. The choice is not persisted across reloads or silently reused
   for later presence encounters. It waits for that session's incoming audio
   track; it never opens an additional microphone or captures video/input audio.
2. Record an audio-only MediaStream from a clone of the actual received audio
   track. Normal playback retains its original stream/track and selected device.
   Recorder cleanup may stop only resources it owns, never the playback track.
3. Feature-detect `MediaRecorder` and supported audio MIME types. Prefer a
   browser-supported audio container such as WebM/Opus; use a supported fallback
   or report unavailable. Save the actual MIME type and matching extension.
   Never silently fall back to microphone, tab or system capture.
4. Bound recording to ten minutes and 32 MiB of retained encoded data, with
   periodic data delivery requested (for example a one-second timeslice).
   Timeslices are best-effort, not exact duration or allocation bounds. Stop
   recording when a limit is reached; Live conversation continues. Do not evict
   header chunks or truncate encoded chunks to manufacture a file. If a delayed
   chunk/final flush would exceed the cap, release it and report the recording
   unavailable/incomplete rather than export a supposedly complete broken file.
5. Preserve the recorder's final `dataavailable` event before declaring a file
   ready, including a final small chunk. Recording finalization must never be
   awaited by immediate playback Stop, microphone release, peer cleanup or
   authorization handling. Stop further diagnostic capture at session teardown;
   only already captured data may be finalized asynchronously.
6. Fence recorder callbacks by recording ID, session handle and client generation.
   Late callbacks may finalize their own permitted stopped clip, but cannot
   attach to a new session, restart capture or repopulate discarded controls.
   Repeated Stop is idempotent. If the received track is replaced, finalize and
   label the existing recording rather than silently splicing unrelated tracks.
7. Keep at most one diagnostic clip in page memory. Normal Stop retains it for
   explicit download/discard. Arming another clip requires explicit replacement.
   Scope/agent changes, logout or access revocation discard the clip and cancel
   pending publication; ordinary confirmed departure may finalize the current
   clip, but must not arm recording of the next visitor. Reload clears it.
8. Unsupported browsers, recorder errors, cap exhaustion or export failures
   produce bounded diagnostic status only. They must not stop or change Live,
   trigger model/API requests, or leave tracks/listeners/object URLs behind.

Unit-test the real branches: disabled/unsupported, received-track-only capture,
final chunk after stop, limits/error, repeated Stop, delayed stale callbacks,
discard and resource ownership. Reuse client lifecycle fixtures to prove normal
output, mute semantics, context updates and immediate revocation still work.

## CI-02 - Explicit operator controls and correlated exports

Add a compact diagnostic control near GPT-Live/Telemetry with clear wording:
record received Gigi audio locally for the next session. Explain that the saved
audio contains spoken content and is not uploaded automatically. Show off/armed,
recording, finalizing, ready, limit/error and unsupported states. Provide explicit
Stop recording, Download audio and Discard controls. Stop recording alone must
leave Live running. Disable downloading until final flush is settled. Do not
introduce automatic audio replay or compete for the current speaker output.

Pair the clip with the existing JSON telemetry, without embedding audio bytes,
base64, Blob URLs or dialogue in ordinary telemetry. Use matching recording IDs
and filenames, plus a versioned, content-free recording manifest in the JSON.
Reuse existing export and issue-marker mechanisms; avoid a second event journal.

The manifest must contain:

- Recording ID, session handle, agent ID, client generation and the audio filename.
- Actual MIME type, byte count, requested limits, coverage/status and fixed stop
  or error reason; clearly distinguish recording stopped from Live stopped.
- Browser `performance.timeOrigin` and `performance.now()` observations for
  recording request, recorder start, stop request, final data and finalization.
- Bounded chunk receipt/size metadata and retention/drop counts, if useful.
  Treat chunk arrival times as delivery observations, not sample timestamps.
- Available model/voice/build metadata from existing telemetry, with unknown
  left unknown. Do not gather device names/IDs, SDP, credentials or raw errors.
- Explicit timing limits: recorder time and provider transcript offsets are
  different clocks. Start offsets, encoding delay and asynchronous event delivery
  make matching approximate. Do not claim sample-exact caption alignment.

Take the matching telemetry snapshot at download after finalization; retain the
existing session metadata needed to associate the clip even if later sessions
occur. If relevant telemetry has been evicted or the operator exports early,
state that coverage is incomplete. Never relabel later-session telemetry as the
recorded session. Existing media/caption/context associations remain observations,
not proven response boundaries.

Test the export contract for correlation, safe allowlisting and absent audio/text
in ordinary JSON. Browser tests should cover default-off, visible consent/status,
unsupported/error, normal Stop/finalization/download, discard, next-encounter
non-rearming and agent-switch cleanup. Inspect desktop and narrow-screen layouts
using the existing UI conventions, including dark mode.

## CI-03 - Acceptance, documentation and physical-trial handoff

Use the smallest meaningful suite covering new branches and the existing lifecycle:

```powershell
npm.cmd run test:live:unit
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests LiveCockpitSmokeIntegrationTest --browser --live-only
.\mvnw.cmd -q -DskipTests package
git diff --check
```

Add the recording cases to the existing browser specs so the acceptance runner
executes them. Start with focused cases while developing; run the above acceptance
once the implementation is stable. Keep legacy Text/Continuous assertions and
the feature-disabled smoke intact. Do not use production MySQL, live API billing
or additional database polling for automated verification.

Include one real browser `MediaRecorder` test using a deterministic synthetic
audio source with a known late tone and a silent tail. Decode the exported file
and verify the late tone survives finalization. This verifies the capture/export
path, not physical audibility or remote-provider behavior; a mocked Blob-size
assertion alone is insufficient. Only tests may create this synthetic audio path.
If headless codec support is unavailable, report that gap and perform a local
browser check rather than claiming mocks prove playable recordings.

Baseline caveat recorded while publishing this plan: the separate Java
`GalaDinnerLiveDeliveryUnitTest` fails its <=2,100 ms simulated delivery assertion
both on published commit `34390c9a` and with GI-27. This is not an audio-playback
test or a new GI-27 regression. Preserve that evidence; do not silently loosen
its threshold or change context delivery as part of recording instrumentation.

Update README, `tests/gptlive/GALA.md`, current PROJECT status and relevant
CONTEXT guidance. Record commands/results/artifacts and any failures in
`.agents/GPTLIVE_RESULTS.md` (or a dedicated linked results file if clearer).
Never commit real speech, database transcript extracts, telemetry exports or
credentials. Describe recording as opt-in content capture separate from existing
content-free diagnostics. No server schema or persistence changes are expected.

Handoff must state what passed, which browser/codec was actually tested, known
timing limits, whether standalone Valerian has been adapted, and whether any
physical trial has occurred. Completing this feature is **not** fixing cut-offs.

## Operator trial and interpretation

1. Use a consenting adult for this diagnostic trial. Refresh the deployed client,
   choose a known voice/device setup (initially Willow and a headset), arm recording
   and start one fresh session. Keep setup unchanged within that trial.
2. Cover ordinary reception answers, guessing entry, short yes/no answers, a
   longer factual answer and give-up/reveal. Keep the trial below the time cap.
3. Immediately mark any perceived cut-off with the existing Speech cut off issue
   marker. Note the exact expected/missing word, whether it appears in captions,
   and whether the user spoke or a lifecycle change occurred. Do not press Stop
   while the suspect reply is still playing just to export it.
4. After the reply has settled, stop recording or stop Live normally. Wait for
   **recording finalization**, then download the audio and corresponding telemetry.
   If the finalization fails, retain the status/metadata and report the gap.
5. Replay the file after Live is stopped, preferably also on a second output
   device. Match the marked occurrence approximately using browser/session IDs
   and relative times. Where necessary, inspect only the matching durable
   transcript/receipts; database access is optional for this comparison.
6. If needed, repeat with recording disabled to check for instrumentation effects,
   then change one factor at a time (voice, headset versus Bluetooth/speakers).
   A physical microphone recording of the speakers is a separate explicitly
   arranged experiment, not part of this implementation.

Interpretation must remain conditional:

| Evidence | What it supports / what remains open |
| --- | --- |
| Saved clip contains the final word, but the operator did not hear it live | Focus on the playback/output-device path after the received-track capture point; inspect local stop/media events. It does not identify the exact device fault. |
| Saved clip also lacks the word, with complete coverage and a validated recorder | Focus on provider generation, WebRTC delivery/concealment or browser media processing before the capture point. Recorder artifacts still need consideration; this alone does not isolate the server. |
| Stop, departure, revocation or transport loss overlaps the occurrence | A known lifecycle interruption is a candidate; preserve its intended semantics and determine whether it was expected. |
| Provider error/session closure overlaps the occurrence | Inspect the actual code/reason and timeline; post-Stop cleanup errors do not explain an earlier cut-off. |
| Concealment rises near the occurrence | Investigate transport/jitter as a candidate; zero packet loss does not exclude late packets. Correlation alone is not causation. |
| Only captions look incomplete, or a tiny reply has low activity samples | Text and RMS evidence alone cannot establish audible clipping. Listen to the clip. |
| Clip/telemetry is missing, capped, malformed or timing is uncertain | Mark the result inconclusive and repeat a bounded trial; do not infer a cause. |

## Protocol references and cautions

Re-check official documentation when implementing; do not import Azure Realtime
or third-party framework event assumptions into this GPT-Live client.

- [OpenAI Live migration: connection and audio lifecycle](https://developers.openai.com/api/docs/guides/live-migration#adapt-the-connection-and-audio-lifecycle)
  explains continuous audio and the lack of a Realtime-style per-response done
  event in Live.
- [OpenAI Live transcript deltas](https://developers.openai.com/api/docs/guides/live-conversations#transcript-deltas)
  explains approximate transcript intervals; captions do not prove playback.
- [OpenAI Live usage and graceful close](https://developers.openai.com/api/docs/guides/live-conversations#usage-and-graceful-close)
  describes session-level close reasons. A safety-related `content` closure is
  not a per-reply token-exhaustion status. Nested Responses events describe
  delegated work, not automatically the continuous spoken answer.
- [W3C MediaStream Recording](https://www.w3.org/TR/mediastream-recording/)
  is the primary reference for recorder lifecycle, final data and timeslices.
- [W3C WebRTC statistics](https://www.w3.org/TR/webrtc-stats/#dom-rtcinboundrtpstreamstats-concealedsamples)
  defines concealment counters and their interpretation limits.

The previous Realtime forum suggestions about draining a custom PCM speaker or
padding a short final block do not match the currently inspected playback path.
Revisit a playback fix only when a reproducible recording/event pair identifies
the boundary at which the word disappears.
