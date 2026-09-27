# GPT-Live implementation and acceptance evidence

Branch: `feature/gptlive`. Roadmap: [PLAN_GPTLIVE.md](PLAN_GPTLIVE.md).
Implementation authorization includes committing/pushing each milestone and
continuing automatically. A passed synthetic test is not acoustic acceptance.

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
