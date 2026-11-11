# Post-Nicole improvements: Coordination / ES-08 continuation

Written 2026-11-11 for Alexandre's next Codex session. This is the latest handoff
after the home/office consolidation and the approval-before-audio implementation.
**ES-08 is incomplete. Do not infer acceptance from a successful build, deployment,
delivery-verifier evaluation, or one successful spoken turn.**

## Start here and establish the actual checkout

1. Read `CODEX.md`, `CONTEXT.MD`, then `../PROJECT.md` from its beginning through
   Current milestone state, stopping before Historical milestones checklist.
2. Read the first section of `ESCALATION_RESULTS.md`, the approval checkpoint and
   next-decision sections of `PLAN_ESCALATION.md`, and this handoff. Those newest
   sections supersede earlier descriptions of missing approval support.
3. Check branch, remote tips, worktrees and dirty files before editing. Preserve
   unrelated work and the application-specific catalog on `agents`.
4. Inspect the relevant code and neighboring tests below, give a concise mental
   model, then implement a focused milestone with proportional verification.
   No parallel agents are required or implied by this handoff.

At writing, shared implementation is committed and pushed on `main` at
`02f25dd` (`Gate Coordination Live speech on approval and observed playback`).
The worktree was clean. Alexandre explicitly requested this handoff committed and
pushed to `main`, followed by merging `main` into `agents` for Heroku redeployment.
That integration is the current session's next operation; inspect the subsequent
Git commits/workflow/release state for its actual outcome rather than assuming a
deployment completed merely because this file exists.

On this Windows machine:

- Shared checkout: `D:\VSCWorkSpace\prometheus` (`main`).
- Deployment checkout: `D:\VSCWorkSpace\prometheus-coordination-agents` (`agents`).
  Initially clean at `3e77683`, behind `origin/agents` by 16 commits. The fetched
  deployment tip was `77efb85`, including the games-only Gala agent. Fast-forward
  the local deployment branch before integrating shared changes.
- Another existing worktree, `prometheus-coordination-refinement`, owns
  `feature/gptlive`; leave it alone.
- Office recovery material remains in `backup/office-es08-20261111`, its retained
  stash and `.git/codex-consolidation-20261111/`. Do not apply or delete it casually.

`main` owns reusable framework/baseline Valerian changes. `agents` adds deployed
catalogs and application-specific behavior. Merge **main into agents**, preserving
the latter's definitions; do not merge those deployment-specific catalogs back
into `main`. Its `.github/workflows/deployment.yml` runs on pushes to `agents`
and deploys the Docker application to Heroku **valerian**. A push initiates that
workflow; it is not itself evidence of successful rollout. No manual Heroku push,
configuration change, production data reset or saved-agent migration is needed
for this handoff request.

## Original objective and what the consolidation preserved

The six classroom codes were **MScF1 through MScF6**, confirmed by Alexandre;
exclude instructor code `coord`. The study used the open multimodal task at
`D:\VSCWorkSpace\haicedu\MSc HCAI\course_1_humancomputerinteraction\week3\multimodality\print\09_open_multimodal_task.pdf`.
That external PDF and the production histories are not portable Git fixtures.
The evidence-derived cases and roadmap are already documented; do not query or
copy private student histories just to resume implementation.

The intended improvement is cross-phase support for confusion, errors and unmet
expectations while preserving explicit task control: truthful explanations,
guidance, guarded resume/restart, useful perception answers, natural acceptance,
and honest supported-task/social-condition limits. Do not replace the procedural
state machine with an unconstrained conversational prompt.

Heroku logs confirmed database `max_questions=36000` exhaustion, HTTP 500s and
later login timeouts. They did not establish exact outage onset or per-student
query attribution. Short histories are not proof of disengagement. Capacity and
deployment-environment selection were subsequently deferred from the behavioral
ES-08 milestone; **the quota issue is not solved by this branch integration**.

ES-01 through ES-07 are implemented and re-audited. The home session added stronger
unit/persistence/semantic checks and advanced ES-08; office-specific work was
preserved during consolidation. See `CONSOLIDATION_COORDINATION_2026-11-11.md` and
the older `HANDOFF_COORDINATION_2026-11-11.md` for provenance, not current acceptance
status. Home raw `target/` evidence did not transfer through Git.

Relevant shared history:

| Commit | Meaning |
| --- | --- |
| `ac1d258`, `057aa55` | Shared current-clock capture fixture and receipt-scoped playback support. |
| `7a5ba2b` | Office/home consolidation checkpoint and retained distinct evidence. |
| `2424691` | Bounded assembly of assistant sentences before task verification. |
| `4c787cc` | Social detector and total-budget limits supplied to native voice before negotiation. |
| `3556fa6` | Retained failed Live trials and proposed approval-before-playback decision. |
| `02f25dd` | Owner-approved buffering, approval/release/observed-playback boundary and tests. |

The model/effort used by an authoring Codex session cannot be established from
commit content or application test model settings. Judge the implementation by
code and evidence; do not claim an inferred authoring-model provenance.

## Mental model and invariants

The backend state machine owns agreement terms, preparation, acceptance, current
run/revision, cue eligibility, action budgets, pause/stop and Reflection. Native
Live speech is a realization of committed meaning, not an independent authority
to change it. There are now distinct stages:

1. Backend commits an ordinary speaking occasion or task response.
2. Native audio/captions arrive as an **unheard candidate** and retained audit data.
3. A whole receipt-matched candidate passes current-authority and semantic checks.
4. A separate release request rechecks authority immediately before playback.
5. Observed playback completion exposes it as delivered conversation/consent
   evidence. Final task playback-cue confirmation remains a separate protocol.

Do not collapse generated, captured, matched, released, heard and task-completed
into one boolean. A release with a lost completion acknowledgement is uncertain
delivery, not permission to repeat the action or speech.

New delivery-aware Coordination instances require approval-capable Live clients.
Other agents retain direct playback. Existing saved graphs are not automatically
migrated; create a fresh Coordination instance for deployment acceptance. Preserve
Text/Continuous behavior, public HTTP/SSE contracts, scoped ownership and history.

Important implementation boundaries:

- Original remote PCM is captured through a silent AudioWorklet. A private muted,
  zero-volume audio sink activates Chromium's remote RTC/WebAudio consumption;
  it is never the audible output. Do not remove it because synthetic oscillators
  work without it: a paid trial measured all-zero remote PCM before this repair.
- At most two clips, 30 seconds per clip, 60-second retention, 2.4 seconds of
  acoustic quiet, one-second onset and 1.5-second end caption/audio margins.
  Require exact whole text/receipt pairing and exclude extra receipts. VAD and
  these margins are fallible segmentation checks, not semantic completion proof.
- Approval is bounded by the original response deadline (at most 45 seconds),
  and overall playback by creation plus 60 seconds. One attempt per request,
  four concurrent ordinary checks and 128 session attempts are enforced.
- Ordinary current-turn drafts may start before backend commitment only after
  the last trusted input receipt, and must be approved against the subsequently
  committed facts. Mute/interruption and older-turn drafts cannot qualify.
  Existing **task** generation-after-request fences remain intact.
- Task speech reuses `TaskResponse` semantic verification. Ordinary/control/help
  speech gets its own check. Proposal checks now include actual human role,
  agent role and success criterion, not just a summary prompt.
- Provider waits run outside database transactions and agent locks. Fresh
  request/owner/epoch/revision/deadline checks follow inference and precede
  release and completion. Stop, input, mute, close and supersession invalidate work.
- Only playback-confirmed native speech enters conversation, prompt, acceptance
  and next-cue views. Raw transcripts remain available for audit. Help cannot
  consume task responses or manufacture proposal-consent evidence.
- Optional sensory remarks have their own scoped, interruptible occasion. They
  do not displace a current reply, consume task work or establish consent. Release
  must occur before their original expiry, at most six seconds; timely playback
  may finish within the overall bound. This short window may expire during
  buffering/verification. No paid sensory-remark acceptance exists yet.
- Missing or rejected clips produce visible recovery guidance. **There is no
  automatic corrective realization or task replay in the current implementation.**

## Code and tests to extend

Paths below are relative to the repository; Java paths begin at
`src/main/java/ch/zhaw/prometheus/` unless stated otherwise.

| Concern | Existing anchors |
| --- | --- |
| Coordination permission and consumption | `agentdefs/core/CoordinationSpeechApproval.java`, `CoordinationPolicy.java`, `CoordinationUpdateAction.java`, `CoreTaskVoice.java`, `SensoryReactionAction.java` |
| Scoped approval protocol | `application/LiveSpeechApprovalService.java`, `ScopedLiveSessionService.java`, `controllers/ScopedLiveSessionController.java`, `model/policy/LiveSpeechRequest.java` |
| Committed task and final cue | `model/task/TaskResponse.java`, `TaskCueStatus.java`, Coordination delivery classes, `application/LiveAgentContextService.java` |
| Heard versus audit views | `model/event/SpeechProvenance.java`, `ConversationProjection.java`, `EventHistory.java`, prompt assemblers and speech selectors |
| Context transport | `application/live/LiveContextDelivery.java`, `LiveContextSnapshot.java`, `LiveVoicePolicyAdapter.java`, `LiveContextBridgeService.java` |
| Production browser audio | `src/main/resources/public/live/approved-speech.js`, `speech-buffer.js`, `speech-capture-worklet.js`, `playback-cue.js`, `client.js` |
| Provider journeys | `tests/gptlive/core_backend_journey.mjs`, `escalation_journeys.mjs`, `run_acceptance.py`, `README.md` |

Start with `LiveSpeechApprovalServiceUnitTest`, `CoordinationVoiceUnitTest`,
`CoordinationDeliveryUnitTest`, `CoordinationCaptureUnitTest`,
`CoordinationSupportUnitTest`, `CoordinationRecoveryUnitTest`,
`CoordinationDeliveryIntegrationTest`, `CoordinationAgentIntegrationTest`,
`ScopedLiveSessionServiceUnitTest`, `SensoryRemarksUnitTest` and the neighboring
TaskResponse tests. Browser/JS anchors are `tests/js/live/approved-speech.test.mjs`,
`speech-buffer.test.mjs`, `playback-cue.test.mjs`, and
`tests/playwright/valerian-gptlive-smoke.spec.mjs`.

The browser suite has both a real local WebRTC peer-pair capture/silence/playback
test and a real-application/MySQL Coordination path to Reflection. One does not
substitute for the other. Production and paid journeys use the same approval
player. Their generated-caption logs are not evidence that a student heard them.

## Verification at the shared checkpoint

- Last full disposable-MySQL run: **913 cases, 912 passed, one explicit capacity
  skip, zero failures/errors**, artifact `target/gptlive-acceptance-65ad2c77c2`.
- Four browser checks passed in that run: real local WebRTC, real-app Coordination
  approved playback/Reflection, scoped cockpit and persisted state/SSE. The
  approved-conversation screenshot was inspected.
- Two later changes completed proposal facts and restored sensory-occasion
  approval. After both, **42 focused Java cases passed**: 16 Coordination voice,
  21 approval service and five sensory remark cases. The full/browser run above
  **preceded** these changes. Maven packaging passed again afterward.
- **208 Node tests** and **11 acceptance-runner preflight checks** passed. Whitespace
  checks passed. Logs: `target/es08-approval-20261111/`, notably
  `final-ambient.log`, `package-final.log`, `node-final.log`.
- Initial failures and their repairs remain recorded in `ESCALATION_RESULTS.md`.
  Query regression rose from 1,993 to 2,007 questions per synthetic minute because
  ordinary speaking requests persist; the documented ceiling is 2,010. This
  excludes approval/release/playback requests and is **not capacity acceptance**.

These counts describe the shared checkpoint, not a future merged `agents` build.
Read the subsequent integration commit/CI results for deployment-branch checks.

## Latest paid trials: six attempts, zero complete ES-08 passes

All used real local runtime/MySQL/OpenAI Live and the production player with
synthetic visitor audio and sensors. The run-only configuration was
`gpt-live-1` / `willow`, backend `gpt-5.6-luna` / effort `none`; it did not alter
production defaults or prove the Codex author's model/effort.

| Ignored artifact suffix under `target/gptlive-acceptance-*` | Actual result |
| --- | --- |
| `dbe35c23e6` | Initial social trial: no heard speech/check; generated wording incorrectly promised count increase/per-arrival behavior. |
| `d193041426` | Recovery: 3,433 captured frames, zero voiced frames. Diagnosed the remote-stream capture path; no recovery steps reached. |
| `6690c35a38` | Capture repaired; complete ordinary proposal discarded by the new overstrict generation cutoff. Led to current-turn draft approval, without weakening task fences. |
| `102583eaef` | One proposal MATCH, released once and observed playing. Subsequent riddle diverged, was AMBIGUOUS and withheld; one contribution remained spent and task became paused/uncertain. |
| `6a49f65690` | Social proposal never paired: caption preceded audio by 601.33 ms, just outside the original 600 ms onset margin. |
| `8caa01d8b3` | Recovery proposal never paired: final caption preceded voice end by 1,260 ms, outside the original 1,200 ms end margin. |

The final margins (1,000 / 1,500 ms), full proposal-fact payload and sensory
adapter are offline-tested but **not paid-rerun**. Do not present their repair as
a complete provider success. Later recovery/social steps, dialogue feedback and
replacement/Stop were not reached in these attempts.

The specific remaining dialogue failure is useful for a regression: the backend
committed a riddle with **keys but no locks, space but no room, enter but not go
inside**; native speech said **many keys but open no doors**. This is not a harmless
paraphrase of the same clues. Keep the verifier strict. An older office failure
also credited a piano with an Enter-key clue: correct delivery of wrong committed
feedback remains factually wrong. Delivery matching is not factual assessment.

The sole heard proposal took **4,720 ms from captured voice end to release**, plus
playback/settling. This is one observation, not a latency distribution. Six-run
backend usage was **nine requests / 40,302 tokens** (38,383 input, 1,919 output);
cached synthesis meant zero new TTS requests. Reported Live seconds are telemetry,
not billing totals. Raw generated captions, observed playback, usage and semantic
reviews were retained; every owned app/session/schema/account/MySQL was cleaned up.
Never overwrite failed artifacts or repeat an unchanged paid run until lucky.

## Next continuation and authority

The previous session recommended **one bounded corrective realization of the
same committed response, only if it has never been RELEASED**. Alexandre replied:
"Ok. First create a handoff markdown ... This handoff should be committed and
pushed in the main branch. Then, merge main into the agents branch so we can
initiate a redeployment to heroku."

This turn prioritizes that handoff/integration request; no retry implementation
has been done. The earlier roadmap's "not authorized" heading records the state
before this latest reply. Preserve this instruction history rather than treating
an older document as overriding the user's latest direction. The bounded retry
is the proposed next scope; backend-controlled task speech is a different,
broader alternative and was not selected or implemented.

If continuing with the bounded retry, keep the scope concrete:

- At most one corrective realization, tied to the same committed response,
  request/run/revision/owner/epoch, spent contribution and original deadlines.
- Never create another task action, grant extra budget, broaden semantic meaning
  or extend expiry. Preserve the post-request task-generation fence.
- Only never-released speech is eligible. Missing PLAYED acknowledgement after
  RELEASED cannot justify a retry. Interruption, mute, Stop, supersession, timeout
  or a second rejection must return truthful recovery guidance, not hidden replay.
- Review whether the remaining original deadline leaves time for correction;
  an honest failure is acceptable. Do not make every failure retryable by default.
- Unit-test duplicate/concurrent approvals, late candidates, release races and
  owner/revision/budget/deadline invalidation. Add one real-MySQL reload/ownership
  path and production-browser rejected-first/approved-second playback proving
  exactly one contribution and no audible rejected draft.
- Then run the three capped Live journeys after a concrete causal change and
  review all generated and heard wording. Keep unsuccessful attempts and mark
  unmet critical checks. Do not close ES-08 from deterministic tests alone.

ES-08's three required journeys are preparation/struggle/recovery/one-shot return;
bounded riddle question/answer/feedback plus supported revision; and a supported
social count condition plus explicit recovery controls. See `PLAN_ESCALATION.md`
for exact assertions and `ESCALATION_OPERATOR.md` for physical trials. Physical
audio, microphone interruption/echo, camera accuracy, classroom usability and
robot effects remain separate unrun gates. Database capacity remains deferred.

## Portable execution and evidence hygiene

Read `../tests/gptlive/README.md` before running. Dependencies include Java/Maven,
Node/Playwright Chromium, Python and `pymysql[rsa]`, and loopback MySQL. Use the
checked-in runner and isolated random schema/account; never aim tests at the
configured application or Heroku database. Supply private loopback administration
through ignored `application-test.properties` or `GPTLIVE_MYSQL_ADMIN_URL`,
`GPTLIVE_MYSQL_ADMIN_USER`, `GPTLIVE_MYSQL_ADMIN_PASSWORD`. Do not print credentials
or put them in CLI arguments. Ignored `application.properties` on this device may
contain Heroku credentials; do not assume it is a safe local test configuration.

Typical offline commands (configured isolated local administration required):

```powershell
python tests/gptlive/run_acceptance.py --java-tests all
python tests/gptlive/run_acceptance.py --java-tests none --browser --live-only --browser-mode enabled
python -B tests/gptlive/test_acceptance_runner.py
$nodeTests = @(rg --files tests/js -g '*.test.mjs')
node --test @nodeTests
.\mvnw.cmd -q -DskipTests package
git diff --check
```

The real-WebRTC browser check needs Chromium's real media stack; mocked transport
alone missed the original zero-PCM defect. Inspect retained screenshots/traces
and the real application state, not only a green title. Use proportional focused
checks during development, then integration coverage for shared-path changes.

Explicit paid scenario selection, once a bounded causal continuation is ready:

```powershell
python tests/gptlive/run_acceptance.py --java-tests none --paid-core coordination --coordination-scenario escalation-recovery
python tests/gptlive/run_acceptance.py --java-tests none --paid-core coordination --coordination-scenario escalation-dialogue
python tests/gptlive/run_acceptance.py --java-tests none --paid-core coordination --coordination-scenario escalation-social
```

Supply provider credentials privately per the runner README. Per invocation:
12 visitor turns, 24 backend requests, 2,048 completion tokens/request, 80,000 input
bytes/request, cumulative reported backend-token stop threshold 200,000, one
Live session/360 seconds, 12 synthesis clips/6,000 characters, 420-second child
deadline. The last request may cross the token threshold; it is not a money cap.
Do not run heavy builds/browser jobs concurrently with paid audio trials.

`target/` logs, raw reports, speech cache, helper scripts, private properties and
local MySQL data **do not transfer through Git**. On this office device only,
`target/run_playback_mysql.py` starts an owned MySQL 8.0 instance and calls the
portable runner. Use one such helper at a time. It derives its checkout root from
its file location, so do not accidentally test `main` while claiming an `agents`
result. Avoid `mvn clean`: it would delete ignored evidence, helpers and local
database data. Never commit provider traces, transcripts, credentials or generated
database files. Preserve failed runs and verify cleanup of only owned resources.

Update `PLAN_ESCALATION.md`, `ESCALATION_RESULTS.md`, current `PROJECT.md` and
operator/API documentation when behavior or status changes. Report exactly what
ran, what failed, what remains unverified and which branch/commit was deployed.
Keep the main/agents distinction and the owner's commit/push instructions intact.
