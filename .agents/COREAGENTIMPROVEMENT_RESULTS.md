# Core-agent improvement evidence

## Current status

CA-01 complete and pushed as 55738757. CA-02 complete and pushed as d30196ff.
CA-03 implementation/local verification complete; paid sensory trials pending.
CA-04 through CA-08 not implemented.
Baseline: main 19d2d51c, clean feature/gptlive fast-forwarded on 2026-10-26.
No main, agents or standalone Valerian runtime changes in this series yet.

## CA-01 — baseline and acceptance harness

Added the tracked eight-milestone plan and paid `--paid-core live` runner. It uses
the real production app, scoped APIs, MySQL, control inference and Live WebRTC;
only visitor audio/sensing is synthetic. Default database properties now select
the local test configuration. Loopback enforcement, unique schema/account,
provider preflight, subprocess deadlines/tree cleanup and failure artifacts bound
the run. Ordinary offline acceptance remains available.

Actually verified (2026-10-26):

- 45 Java tests: GenericMultimodalTaskUnitTest, SocialSpaceAgentUnitTest,
  LiveMultimodalUnitTest, ValerianCorePromptContractTest,
  HealthcareUseCasePromptContractTest, GenericMultimodalTaskIntegrationTest,
  SocialSpaceAgentIntegrationTest, SocialSpaceLiveJourneySmokeIntegrationTest.
  Disposable MySQL artifact: `target/gptlive-acceptance-3de3428350`.
- Three Python boundary checks: reject remote database, missing model before paid
  calls, and mixing paid/mocked providers. Node syntax and git diff checks pass.
- Paid baseline passes: `target/gptlive-acceptance-38526dd0ae`. Actual configured
  control model `gpt-5.6-luna`, effort none; Live `gpt-live-1`, Willow; synthetic
  visitor `gpt-4o-mini-tts`, Alloy. SIRA answer, supplied one-person evidence,
  persisted paper hand sign, retained state and attached session all checked.
  Each invocation stopped its app and removed its owned database/account.

All unsuccessful trials are retained, not counted as passes:

- `b8a32b383c`, `401d8771df`: new worktree lacked ignored openai.properties;
  backend route validation failed before control inference. Temporary content-free
  exception instrumentation identified this and was removed. Preflight now rejects
  missing model/key before billing. No production runtime fix was needed.
- `39453c92bc`: test incorrectly required speech for a gesture-only request. The
  existing agent policy requires silence and persisted the paper sign correctly.
  Corrected the test to check execution intent, then ran the successful trial.

Baseline limitation: synthetic speech occasionally splits into short durable user
segments and can cause repeated embodiment plans. Preserve this evidence for
CA-04/08; no capture timing changed. Physical audio/robot execution and full browser
visual regression are unverified at this milestone. No main/agents deployment.

## CA-02 — decorative gesture cadence

New Generic/Social Space and Live Multimodal instances share DecorativeGestures:
12-second spacing, 60-second same-label suppression, five published requests of
history, explicit request/agreed-action exemptions, and reset cleanup. Publication
only replaces the decorative label with NONE; other channels are retained.
Generic's configuration prompt fingerprint and legacy factory default remain.
Saved instances are not silently upgraded. EmbodimentPolicy adds one nullable
storage relationship; README records managed-schema requirements.

Verified so far:

- 43 Java checks (helper boundaries, explicit/task exemptions, existing core
  units and Generic/Social Live journeys) on disposable MySQL
  `target/gptlive-acceptance-1ba8d68547`. The new persistence case covers all three
  agents across reload, repeat suppression and explicit reset.
- Paid Live Multimodal journey passes with the cadence code:
  `target/gptlive-acceptance-4194b9bc66`. SIRA answer, current person count,
  requested paper sign, retained state and attached session verified.
- Shared reset/policy regression passes (`target/ca02-reset-regression.log`),
  covering oblivious history clearing, transitions, regulation reset, nested/outer
  routing and PromptPolicy. Physical movement remains unverified.

## CA-03 — meaningful sensory remarks

New Social Space instances opt into stable 2s counts, 4s conversation priority
and 15s remark cooldown. Face/social demos use 1s stability, 2s conversation
priority and 8s cooldown. Source order/15s freshness remains independent of receipt
refresh. A suppressed crossing is dropped, not queued. Rich social signatures
ignore tracker IDs and confidence jitter but retain confident movement/attention.
Explicit facial corrections/opt-outs suppress automatic interpretation until
resumed/reset; ordinary direct questions remain available.

Live uses expiring, interruptible PROACTIVE occasions and owns wording; these
sensor turns make no language-model call in the backend. Different/invalid newer
evidence or a user turn cancels a pending comment. Text/Continuous generate a
validated plan, discarded if its source expires or continuation is cancelled.
Existing saved Social Space and generic Multimodal Behaviour semantics are retained;
new opt-in policy classes/nullable storage association require a new instance.

- 41 Java/local MySQL checks pass in `target/gptlive-acceptance-9ebcf402e6`:
  helper rules, new agent graphs, existing saved-instance contracts, legacy
  canonical/compact Social Space journeys, Generic task regressions, gesture and
  sensory storage reload. Core sensory persistence covers all three definitions.
- Initial integrated run `b848ece2c7` failed due test fixtures mutating the read-only
  Agent storage projection and counting gateway configuration reads as inference.
  Corrected the fixtures/verification without loosening behavioural assertions.
- Eight focused helper/agent checks also cover conversation priority, source expiry
  and the quiet context after an occasion expires (`target/ca03-final-units.log`).
- Paid face `40e3471bf4`, social `dad7800a17` and Social Space `249b50d7f1`
  journeys pass: ordinary questions, direct current sensing, one stable-change
  comment, retained state and attached session. Face also persists a correction
  and withdraws its pending comment.
- Social trial `42e873c98a` failed an overly narrow test wording check: Live said
  "a second pops in", correctly describing the supplied second visitor. Added
  that semantic alternative and reran; no production behaviour changed for it.

Conversation priority uses committed dialogue timestamps. A just-starting user
utterance can overlap an already admitted remark before its transcript arrives;
the face trial exhibited this at the first question. This is no hard audio gate.
No physical audio/movement or UI rendering change is claimed. All paid runs used
disposable local databases and cleaned up their app/schema/account.
