# GPT-Live implementation and acceptance evidence

Branch: `feature/gptlive`. Roadmap: [PLAN_GPTLIVE.md](PLAN_GPTLIVE.md).
Implementation authorization includes committing/pushing each milestone and
continuing automatically. A passed synthetic test is not acoustic acceptance.

## GL-09 / project Milestone 192 — 2026-09-27

Added the dedicated Live Multimodal agent and persisted `EmbodimentPolicy`.
Conversation instructions feed the external voice; a separate embodiment prompt
produces all configured non-speech channels in one request. No backend verbal
generation occurs in this policy, with or without Live ownership. Creation is quiet;
raw sensors only update context, while accepted utterances, explicit generation
and derived social changes produce embodiment. The final state also uses this
policy. Existing combined PromptPolicy generation and proactive narration remain
available to the other agents. Location uses existing weather payloads.

Verification:

- Focused 29 Java cases passed, including two real scoped/controller/SQL smoke
  cases, on disposable schema `prometheus_gptlive_8c2ad8a31e`.
- `python tests/gptlive/run_acceptance.py --java-tests all --browser --live-only`:
  **421 Java tests PASS**, zero failures/errors/skips across 104 fresh reports;
  **11 Live-enabled browser cases + one feature-off text/TTS case PASS**.
  The new Live-only case is intentionally skipped on the disabled-feature app.
  Final disposable schema: `prometheus_gptlive_6a1e0ec408`, removed by the runner
  together with its restricted account; owned app processes stopped.
- The new SQL smoke creates/reloads the new policy, projects every supported
  sensor type including weather location, admits a user transcript, verifies one
  embodiment request and records native speech without another generation.
- The real-app browser case selects the new catalog entry, verifies all declared
  modalities, supplies weather context, and renders gesture/face/gaze/energy/hand
  sign/display beside the independently recorded assistant speech without TTS.
- Inspected `live-multimodal-desktop.png` and `live-multimodal-mobile-behaviour.png`
  under `target/gptlive-acceptance-6a1e0ec408/browser-true/`; all channels remain
  visible and the mobile behaviour cards fit without clipping.
- `mvnw.cmd -q -DskipTests package`: PASS.

Initial test-only failures were corrected: a mock needed the existing `decide`
entry point and verification of inference calls rather than configuration reads;
the UI assertion needed the displayed `Acknowledgement` label rather than the wire
enum. No production workaround was introduced. Earlier isolated schemas/accounts
were cleaned up as well. Node modules were unchanged and their suites were not
rerun for GL-09. Provider/model quality, physical acoustics, strict silence and
gesture execution/timing remain unverified; this definition does not add an
on-demand history-query tool or automatic spoken sensor announcements.

Status: automated implementation and acceptance complete; acoustic gates remain
separate. README documents the two additive policy columns and the lack of a
backend speech fallback for this dedicated agent.

## GL-08 / project Milestone 191 — 2026-09-28

GL-07 is committed/pushed as `211f157`. All eight milestones have completed
offline implementation and automated acceptance. Live voice/acoustic acceptance
is still pending. The default flag remains off; no acoustic PASS is inferred.

Added `LiveCockpitSmokeIntegrationTest`, test-classpath provider fixtures, an
application-backed Playwright smoke and `tests/gptlive/run_acceptance.py`. The
runner creates restricted disposable MySQL credentials, uses a free loopback
port, overrides real provider URLs/keys, stops owned processes and removes its
schema/account. The smoke uses real scoped APIs, capture workers, transactions,
state machine, provenance and SSE; only providers and browser hardware are fake.
The built-in RPS agent advances once from recorded speech, receives weather
context and narrates a hand-sign result. Native output is persisted, no TTS runs
during Live, reload hydrates history without capture, and reconnect seeds the
current selector snapshot. Feature-off text and scoped PCM TTS remain usable.

Final verification:

- `python tests/gptlive/run_acceptance.py --java-tests all`: **415 tests PASS**,
  zero failures/errors/skips, disposable schema `prometheus_gptlive_d94dc420c5`.
  Counted only reports written during this run, excluding stale local reports.
- `npm.cmd run test:transcription:unit`, `test:speech:unit`, `test:live:unit`:
  **29 + 21 + 10 = 60 tests PASS**.
- Application-backed browser smoke plus nine Live UI, 24 transcription, one
  lifecycle and five column cases: **40 PASS** on
  `prometheus_gptlive_dd5901c2bf`. After the final Stop-label fix, ten Live cases
  and the feature-off smoke passed on `prometheus_gptlive_141a8ffb2c`:
  **41 distinct browser cases PASS**, with repetitions counted once.
- Java smoke also passed independently on `prometheus_gptlive_be274445e5` before
  the full suite. Every schema/account above was removed after its run.
- `mvnw.cmd -q -DskipTests package`: PASS. Production package inspected for
  test-only fixture routes/classes and retired probe files; none included.
- Final real-app conversation/Stop and feature-off screenshots inspected, plus
  desktop/mobile light/dark Live settings/overlap and timing drawer evidence.
  Controls wrap without clipping; Stop shows closed context/capture. An interrupted
  synthetic assistant capture remains honestly labeled incomplete.

Evidence directories are `target/gptlive-acceptance-<suffix>` for the schemas
above, with logs, Playwright traces and screenshots. The full run was repeated
after three outdated expectations failed: after-commit SSE publication and two
static source checks. The browser reset fixture was corrected to target the
replacement SSE source. The feature-off smoke now obtains the canonical speech
ID from the existing latest-speech API; raw legacy history intentionally omits
IDs. Earlier fixture failures (duplicate controller bean and a guard returning
false) were corrected. No unresolved automated failure remains.

The final official WebRTC guide check exposed missing ICE gathering in the first
client version. Startup now waits with a finite deadline, sends the gathered local
SDP, and Stop cancels gathering before session creation. A focused test verifies
both branches. No documented creation field for frontend provider permissions
was established; the client sends no provider control commands, and application
scope/epoch checks enforce backend task authority. This provider-control setting
remains a live-provider compatibility limit, not a claimed permission guarantee.

### Live and physical gates — NOT RUN

| Gate | Status / next evidence |
| --- | --- |
| Account/model visibility | Prior read-only model lookup PASS; this is not voice access validation. |
| Paid Live WebRTC, sideband and acoustic output | NOT RUN; requires an interactive physical trial. |
| Chrome / Windows / built-in audio | NOT RUN, quiet and noisy-room comparison separately. |
| Chrome / Linux / built-in audio | NOT RUN, quiet and noisy-room comparison separately. |
| Chrome / Windows / Bluetooth input/output | NOT RUN, after the built-in baseline. |
| Chrome / Linux / Bluetooth input/output | NOT RUN, after the built-in baseline. |
| Heard response latency, echo/self-hearing and interruptions | NOT RUN; browser callbacks are not acoustic measurements. |

Follow PLAN_GPTLIVE section 8: same agent/corpus/language/settings, at least 20
ordinary exchanges, five interruptions, five state changes, five sensory events
and two minutes of assistant playback with the human silent per configuration.
Record actual hardware/routes, browser/OS versions, room/noise, requested/applied
capture settings, false accepts, omissions, cutoffs, median/p95 heard latency,
cost/usage and finalization. Start with the agent's language; English/German
remain proposed trial languages rather than certified language coverage.

Continuity across state changes and paraphrased announcements follow the user's
decisions. Exact wording, strict forgetting, acoustic turn boundaries, startup
coverage and Bluetooth echo suppression remain the documented limits. Use the
existing text/transcription-TTS modes when those pilot limits are unsuitable.

## GL-07 / project Milestone 190 — 2026-09-28

GL-06 is committed/pushed as `3935f59`. Added immediate pending-input fencing,
access-code/link revalidation, browser heartbeat expiry, sideband ping/pong,
bounded asynchronous cleanup and scoped close-status retention. Reset and scope
changes invalidate pending browser callbacks; old SSE sources cannot repaint.
Metadata traces and finalization status are available in Interaction Timing/JSON.

Actual verification:

- Eighteen focused Java cases PASS: `ScopedLiveSessionServiceUnitTest`,
  `LiveTranscriptCaptureServiceUnitTest`, `ExternalSpeechOwnershipUnitTest`,
  `LiveSessionGatewayUnitTest`, `ScopedLiveSessionControllerWebMvcTest`.
  Fake time covers abandoned browsers and silent WebSockets. A blocked worker
  exercises mailbox overflow; command/close timeout remains unconfirmed.
- Nine Live Node cases PASS, including scope invalidation, immediate failure
  silence, explicit reconnect, export correlation/redaction and retention limits.
- Six accumulated Live MySQL cases PASS on disposable schema
  `prometheus_gptlive_9b5b2b4b7e`, removed afterward. Added a receipt queued before
  Stop that cannot run its blocking action when committed afterward.
- Nine `valerian-gptlive.spec.mjs` cases PASS. Added network/sideband/track loss,
  explicit recovery, reload, reset, agent switch/delete, logout and stale SSE.
  Timing drawer screenshot inspected; labels and wrapping remain readable.
  These are browser UI tests with application HTTP mocked, not GL-08 E2E.
- Initial overflow fixture hit the segment-fragment bound first; alternating
  speakers isolated mailbox capacity. A shadowed exception variable was corrected
  before the final passing Java run. No failed check is counted as a pass.

An action already admitted before Stop may complete. Finalization is confirmed
only by `session.closed`; hangup success alone does not claim complete transcripts.
Quiet speech is not treated as transport failure. Physical/live gates NOT RUN.

## GL-06 / project Milestone 189 — 2026-09-28

GL-05 is committed/pushed as `053b0c6`. Added the opt-in third cockpit tab and
separate browser client/caption/UI modules. Explicit Start, input-only mute,
immediate local Stop, voice/device/capture settings, shared media leases and
fresh-session reconnect are available. No microphone starts on connect/reload.
Removed the temporary probe. Browser preferences contain no transcripts or keys.

The cockpit hydrates the shared conversation projection and opts into it on
behaviour SSE; ordinary clients retain raw SSE. Native speech leaves current
non-speech display intact. Receipt identity lists survive persistence and reconcile
provisional captions without text-based deduplication. Ambiguous/incomplete input
remains visibly unapplied. README records the additive receipt_ids column.

Actual verification:

- `npm.cmd run test:live:unit`: six cases PASS. Covers startup, mute, synchronous
  local teardown before delayed cleanup, caption identity/repetition, late callbacks,
  permission/routing/scope failures, late creation cleanup and cross-mode leases.
- `npm.cmd run test:transcription:unit` and `npm.cmd run test:speech:unit`:
  29 and 21 cases PASS, respectively. JavaScript syntax checks passed.
- `mvnw.cmd -q "-Dtest=ScopedLiveSessionControllerWebMvcTest,SseBroadcasterHardeningUnitTest,SpeechArchitectureSourceContractTest,LiveContextDeliveryUnitTest" test`:
  23 cases PASS, including scoped feature discovery and opt-in SSE projection.
- The six accumulated Live integration cases passed on disposable MySQL
  `prometheus_gptlive_b41e8c5574`; receipt IDs were checked across context reload.
  Schema/account removed.
- `valerian-gptlive.spec.mjs`: seven browser cases PASS, with desktop 1440x1000
  and mobile 390x844, light/dark, idle/settings, overlapping speech, persisted
  reconciliation, denied microphone, unsupported routing, provider/scope errors,
  keyboard activation, Stop/tab switch, another window and disabled feature.
  The added scope-error branch passed in a focused rerun.
- Existing lifecycle and transcription browser suites: 25 cases PASS. The first
  combined regression run used a static server, so the column suite's real admin
  setup failed (one failure, four not run). Reran all five column checks plus the
  seven new tab checks on a dedicated real application at localhost:18082 with
  disposable MySQL `prometheus_gptlive_607346e226`: all 12 PASS. App/schema/account
  removed. Browser API/media fakes remain in these UI suites; this is not GL-08's
  real-controller voice end-to-end smoke.
- Screenshots under `target/playwright-gptlive-app` and the first
  `target/playwright-results` run were visually inspected across both sizes/themes,
  idle/active/settings and permission-error states. Added tab-strip captures prove
  mobile wrapping without clipping. Reduced excessive empty-history height based
  on the first inspection; final layout inspected again.
- `git diff --check`: PASS.

Live provider, microphone/speaker quality and Bluetooth remain NOT RUN. GL-07 adds
complete liveness/diagnostic hardening; GL-08 exercises real HTTP/SSE/persistence
through the browser and records the separate physical handoff.

## GL-05 / project Milestone 188 — 2026-09-28

GL-04 is committed/pushed as `790eb5f`. Added identity-only after-commit
notifications, asynchronous/coalesced fresh-context reads, revision/epoch checks,
separate instruction/fact/announcement routing, delegation waiting/reporting and
spoken clarification. Periodic refresh expires observations without a transition.
Provider ACK waits run outside the agent lock. Source identities deduplicate
announcements; ACK failure stops delivery without replay. Long announcements use
quiet context chunks and one commentary request. README records queue/size bounds.

Delegation metadata cannot supply a user utterance. The handler reports existing
committed work and selected context; it never acknowledges, generates an extra
backend model request, or creates a new action. Timing association remains
approximate, and notification-before-transcript waits at most five seconds before
reporting unconfirmed status. A changed revision uses current state; reset closes
the old delivery worker. Native recordings cannot create narration feedback.

Actual verification:

- `mvnw.cmd -q -DskipTests compile`: PASS.
- `mvnw.cmd -q "-Dtest=LiveContextDeliveryUnitTest,LiveDelegationUnitTest,LiveContextProjectionUnitTest,ScopedLiveSessionServiceUnitTest,ScopedLiveSessionControllerWebMvcTest,LiveSpeechExecutionUnitTest,AgentApplicationServiceGenerateOptionsUnitTest,OutputProfileUnitTest" test`:
  29 cases PASS. Includes readiness/routing, multilingual limits, coalescing,
  missing ACK with no retry, reset during a blocked context read, duplicate and
  early delegation, changed revision and bounded pending requests.
- `LiveSessionSmokeIntegrationTest,LiveSpeechPersistenceIntegrationTest,LiveTranscriptIngressIntegrationTest,LiveMultimodalBridgeIntegrationTest`:
  six cases PASS on disposable MySQL `prometheus_gptlive_ec57e5eada`; restricted
  account/schema removed. The new integration uses actual persistence, actions,
  transactions, ingress and session host with a fake sideband. It verifies one
  accepted user/transition, weather without transition, rollback exclusion,
  visual narration with non-speech plan, and no TTS/native-output feedback.
- `git diff --check`: PASS.

Official Live delegation/append documentation was checked again for required
nullable delegation IDs, 500-token append limits and ACK semantics. Live provider
injection timing, speech quality, acoustic coverage and hardware trials remain
NOT RUN. Context acknowledgements do not establish what the user heard.

## GL-04 / project Milestone 187 — 2026-09-28

GL-03 is committed/pushed as `dededed`. Added independent speaker aggregation,
sample-based quiet detection with receipt-coverage checks, stable segment identity,
bounded mailboxes, durable receipt/segment tables and scoped ledger retrieval.
Provider fragments remain provisional until a ledger outcome is committed.
Assistant outcomes use trusted recording and after-commit publication; user
outcomes alone enter serialized acknowledgement. Short replies with uncertain
spoken context are labeled CLARIFICATION for the next delivery milestone.

Receipt persistence, segment claim and agent processing have separate transaction
boundaries. A failed processing transaction retains the claim and marks FAILED;
an interrupted process leaves PENDING. Neither is automatically replayed. This is
duplicate-safe admission, not exactly-once external side effects or a replacement
for the existing in-memory background-action semantics. Reset fences old epochs;
agent deletion cascades ledger rows. README records limits and schema additions.

Actual verification:

- `mvnw.cmd -q "-Dtest=LiveTranscriptSegmenterUnitTest,LiveTranscriptCaptureServiceUnitTest,ScopedLiveSessionServiceUnitTest,ScopedLiveSessionControllerWebMvcTest" test`:
  13 cases PASS. Tests cover observed silence versus network delay, hesitation,
  overlap, reorder/duplicate receipt, repeated words, late/missing timing,
  interrupted/max-duration closure, PCM activity and ordered asynchronous capture.
- `LiveSessionSmokeIntegrationTest,LiveSpeechPersistenceIntegrationTest,LiveTranscriptIngressIntegrationTest`:
  five cases PASS on disposable MySQL `prometheus_gptlive_68518774c7`. Application
  contexts are rebuilt between ingress cases. A persisted blocking-action write
  version proves replay exclusion; a new identity with the same words writes
  again. Failed inference rolls back agent history but retains its ledger claim.
  Schema/restricted account removed. Initial fixture assertions incorrectly used
  RPS round count, then the map view instead of Storage for write versions;
  corrected before this passing run.
- `git diff --check`: PASS.

No paid provider voice or physical trial ran. Quiet reflected PCM coverage and
provisional activity thresholds are acoustically unverified. Missing coverage
produces incomplete capture rather than a guessed end of speech. Bare replies
after an uncorrelated backend question require a self-contained restatement;
the protocol cannot prove which question was heard. GL-05 supplies clarification
and narration transport; GL-06 replaces the temporary probe with the cockpit tab.

## GL-03 / project Milestone 186 — 2026-09-27

Implemented the following association rules and execution boundary. GL-02 was
committed/pushed as `b842d04`.

Backend-generated speech while an external session owns speech is an **intent**;
the original BehaviourPlan remains persisted and inspectable with all modalities.
A native assistant segment is a separate canonical speech BehaviourPlan and never
enters acknowledgement. Ordinary backend generation may supply non-speech
complements but cannot generate competing spoken text. State-entry, final and
sensory-triggered plans retain their authored speech as narration intents.

An association is **confirmed** only when a trusted adapter supplies an explicit
correlation backed by its protocol. This Live protocol supplies no such playback
identity. Its pending source IDs are therefore **possible** associations, marked
ambiguous even when only one exists. Timing, text similarity and append ACK alone
never confirm realization. With no candidates, a native segment is unassociated.
Interrupted/incomplete text is labeled independently of this association.

The shared conversational projection excludes intent speech regardless of whether
an association exists. Each native segment appears once; a later realization
does not rewrite the original intent. Raw event history retains all events;
the additive scoped projected history exposes intent metadata separately while
preserving non-speech channels. Legacy events without provenance remain ordinary
backend conversation. No reset/backfill of existing history is required.

The nullable `event.speech_provenance` TEXT column is additive. A shared
`ConversationProjection` drives prompt exclusion and scoped `/live/history`;
raw history and every non-speech channel remain inspectable. TTS cannot synthesize
an intent as if it were a realized utterance. Startup claim is serialized with
existing agent turns; ordinary generation supplies a validated non-speech-only
complement, and speculation cannot compete. Reset revokes the old owner.

Actual verification:

- `mvnw.cmd -q -DskipTests compile`: PASS.
- `mvnw.cmd -q "-Dtest=LiveSpeechExecutionUnitTest,LiveContextProjectionUnitTest,ScopedLiveSessionServiceUnitTest,ScopedLiveSessionControllerWebMvcTest,OutputProfileUnitTest,PromptPolicyUnitTest,PromptPolicyGestureUnitTest,AgentApplicationServiceGenerateOptionsUnitTest,PromptMessageAssemblerUnitTest,ScopedBehaviourSpeechServiceUnitTest,SpeechArchitectureSourceContractTest" test`:
  49 checks PASS; four new execution cases cover ordinary non-speech response,
  nested start/self-loop/sensory/final/tick plans, actions, native recording without
  acknowledgement and unchanged default generation. A later scoped projected-
  history case brought distinct focused coverage to 50; the final seven execution/
  controller checks passed after that addition.
- `LiveSessionSmokeIntegrationTest,LiveSpeechPersistenceIntegrationTest`: two
  PASS on final disposable MySQL `prometheus_gptlive_a17acc660d`; schema/restricted
  account removed. Persisted intent, confirmed synthetic association, ambiguous
  association and incomplete native segment reload in append order. Repeated native
  segment returns the same persisted event ID; revoked ownership rejects late
  recording. Real Live adapters will never infer confirmed linkage from timing.
- The initial persistence run found a cascade-merge identity bug. Recording now
  returns/publishes the saved event identified by session/segment, and two subsequent
  database runs passed (final one after epoch validation was tightened).
- `git diff --check`: PASS.

Provider transcript ingress, proactive transport, cockpit UI and physical audio
remain subsequent gates. No provider voice request or acoustic trial was run.

## GL-02 / project Milestone 185 — 2026-09-27

Initial sessions now receive immutable selected dialogue/sensory context and the
current composed conversational policy. The adapter checks explicit core pilot
tags and known active state/policy classes; it does not infer voice support for
custom or exact-text policies. `State.ownPolicy()` is application introspection,
not another generation path. RPS guidance defers deterministic results to the
backend. No inference call is required for context projection.

The leaf selector filters original events before adapters/face aggregation run.
IDs, receipt time and source `observed_at`/`ts` survive; earlier working-copy history
does not supply these. Facts remain developer observation data, dialogue retains
user/assistant roles, and full BehaviourPlan output schema is absent from speech
instructions. Current sensory values coalesce; expired values become unknown;
unknown/future source time is explicit. Source times are not refreshed on reads.
Stable revisions change when selected content/policy/freshness changes. Removed
source keys are available to GL-05. Voice continuity remains the agreed policy.

Conservative UTF-8 byte limits protect provider token bounds: startup JSON at most
7,000 bytes/40 messages, per-item text at most 1,000 bytes, instructions at most
12,000 bytes (oversize instructions fail; evidence truncation is marked). Face
summary uses at most eight selected fresh samples. Pilot TTLs are documented in
README and remain subject to physical/context trials.

Actual verification:

- `mvnw.cmd -q -DskipTests compile`: PASS.
- `mvnw.cmd -q "-Dtest=LiveContextProjectionUnitTest,ScopedLiveSessionServiceUnitTest,ScopedLiveSessionControllerWebMvcTest,PromptMessageAssemblerUnitTest,PromptEventContentAdapterUnitTest" test`:
  30 tests PASS, including six new projection cases. Initial service-test setup
  had nested Mockito stubbing; corrected before the successful run.
- Extended `LiveSessionSmokeIntegrationTest`: PASS on disposable MySQL
  `prometheus_gptlive_533cb5aa2a`. Real transaction/reload boundaries retain the
  same epoch/revision and persisted source IDs/times; unauthorized scope is empty.
  Session issuance consumes this context. Schema/restricted account removed.
- `git diff --check`: PASS.

GL-01 is committed/pushed as `c32cea9`. No additional paid provider request or
physical trial was run for GL-02. Asynchronous updates, durable utterance ingress,
the third tab and end-to-end physical acceptance remain later milestones.

## GL-01 / project Milestone 184 — 2026-09-27

Implemented an opt-in scoped typed session gateway, WebRTC SDP exchange, backend
sideband, acknowledged input mute/unmute, graceful close with bounded hangup
fallback, capacity/lifetime bounds and content-free diagnostic counters. Invalid
SDP answers trigger orphan cleanup. Access code and agent bind the opaque local
handle. HTTP response sizes and fragmented WebSocket messages are bounded;
HTTP body deadlines cancel the pending request. No provider credentials, SDP,
captions or audio appear in application diagnostic records.

Temporary probe: `/live/probe.html`. Explicit start, browser capture requests,
shared microphone/output ownership, temporary captions and immediate local Stop.
Task/ingress integration is deliberately absent at this milestone. Probe uses
default OS devices; device settings and cockpit integration belong to GL-06.

### Automated results — PASS

- `mvnw.cmd -q -DskipTests compile`.
- `mvnw.cmd -q "-Dtest=LiveSessionGatewayUnitTest,ScopedLiveSessionServiceUnitTest,ScopedLiveSessionControllerWebMvcTest" test`:
  10 tests, zero failures/errors. Covers loopback payload, malformed/rejected/
  oversized response, stalled response-body deadline, sideband fragmentation,
  orphan cleanup, scope/feature/capacity, mute ACK, finalization timeout, expiry,
  disconnect and redaction/bounded logs.
- `LiveSessionSmokeIntegrationTest`: one passing test against disposable local
  MySQL schema `prometheus_gptlive_ae30f6cdfe`, with an account restricted to that
  schema. Real scoped controllers, access records and persisted agent; only the
  Live and language providers stubbed. Verifies create, cross-code rejection and
  graceful close. Schema/account removed afterward. First run exposed an invalid
  combined-plan stub; fixed before the successful run.
- `node --check src/main/resources/public/live/probe.js`: PASS.
- Synthetic provider shapes retained in `src/test/resources/live/synthetic-events.json`.
  No recordings or real user/provider transcript content were collected.

### Provider access and live session — PARTIAL / NOT RUN

Read-only `GET /v1/models/gpt-live-1` with configured account: HTTP 200 and matching
model ID. No token or full response retained. This proves model visibility only.
Live WebRTC connect/transcripts/finalization and paid voice session: **NOT RUN**;
requires interactive trial. No claims about actual model quality or latency.

Official contracts checked: [WebRTC](https://developers.openai.com/api/docs/guides/voice-webrtc?api=live),
[server controls](https://developers.openai.com/api/docs/guides/voice-server-controls?api=live),
[Live conversations](https://developers.openai.com/api/docs/guides/live-conversations),
[delegation](https://developers.openai.com/api/docs/guides/live-delegation),
[sideband events](https://developers.openai.com/api/reference/resources/live/sideband-websocket),
and [hangup](https://developers.openai.com/api/reference/resources/live/subresources/sessions/methods/hangup).
The provider schema uses `gpt-live-1`, client delegation and `store:false`.
Offered voices are marin/quartz/willow/meridian; these were documented but have not
been auditioned. Local lifetime/capacity limits are not provider SLA guarantees.

Transcript fragments have provider start/end milliseconds and event IDs, but no
utterance-final event. Reflected PCM input has no provider timestamp; output PCM
has start/end milliseconds. Input sample count and receipt time can support a
conservative activity detector, with ambiguity made explicit. Browser and server
clocks cannot be treated as one clock. Mute ACK confirms a command, not whether
earlier reflected audio was consumed. `session.started` on the browser gates
input, after the server has attached; initial sideband observation gaps remain a
live-trial question. Narration ACK is not playback completion and provides no
exact correlation between a backend intent and paraphrased output.

### Built-in microphone/speakers — NOT RUN

Chrome/Windows: NOT RUN. Chrome/Linux: NOT RUN. No physical microphone/speaker
trial or screenshot-based browser acceptance is claimed for GL-01.

### Bluetooth microphone/speakers — NOT RUN

Both OS trials remain for the later physical comparison. Echo cancellation was
requested at browser capture only; no Bluetooth fix or root cause is established.
