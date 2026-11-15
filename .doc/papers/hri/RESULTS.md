# Study implementation evidence

This records software verification, not human-study findings. Local artifacts
under `target/` are ignored and contain synthetic data. Each milestone is committed
and pushed separately on `experiment/hri-2027-coordination`, as authorized by the
owner on 15 November 2026. Source base: `17244cb1`.

## OS-01 — Foundation (15 November 2026)

Implemented versioned English task/form/rubric materials for `guess`, `context`
and `signs`, all explicitly unready; a neutral `study.coordination` definition
using the existing control graph; a closed text-only landing page; and study
selection/evidence checks in the disposable local MySQL acceptance runner.
No schema changes. Fresh agents are needed to use the new definition. Pilot/main
attempt designation and persistence are OS-02 work; human collection remains closed.

Actual verification:

| Command/check | Result and artifact |
| --- | --- |
| `python -m unittest discover -s tests/gptlive -p test_acceptance_runner.py` | 13 passed, including study selection and remote-DB rejection |
| `python tests/gptlive/run_acceptance.py --study --java-tests StudyMaterialsTest,StudyDefinitionIntegrationTest,CoordinationAgentUnitTest,CoordinationRevisionUnitTest,AgentDefinitionRegistryUnitTest --browser` | 37 Java checks passed, zero skips; initial browser run failed because nested static route returned 404. Retained in `target/gptlive-acceptance-c6653bc41e` |
| `python tests/gptlive/run_acceptance.py --study --java-tests none --browser` | After adding the route, one browser check passed, zero skips. Screenshots at 390 and 1440 pixels visually reviewed: readable, no overflow. `target/gptlive-acceptance-47a14c1e88` |
| `python tests/gptlive/run_acceptance.py --study --java-tests none --paid-core coordination-text` | Three-turn real OpenAI journey passed against disposable local MySQL; `target/gptlive-acceptance-754156fee1` |
| `node --check tests/hri/provider_journey.mjs`, `git diff --check` | Passed |

Independent semantic review of the real-provider transcript: **PASS for the
foundation smoke only**. The first turn preserved roles, success criterion and
limit two without execution; acceptance produced one welcome sentence and a
stored agreement; pause preserved that agreement and contribution count. Success
was correctly not established. This is one synthetic journey, not a reliability
estimate or scenario acceptance. The raw journey intentionally retains its
`semanticReview: PENDING` field; this paragraph is the separate review evidence.

Known observations for later milestones: canonical replies can contain unused
nonverbal fields although the study client renders text only; pause wording still
exposes runtime terminology. Participant sessions, durable submissions, recorder,
task execution, forms and operational qualification are not yet implemented.

## OS-02 — Sessions and access (15 November 2026)

Implemented persisted SYNTHETIC/PILOT/MAIN attempt identity, stable assignment,
consent, staged private forms, hashed participant credentials, atomic agent
provisioning, withdrawal/revocation/replacement, and operator-only provisioning.
Study-owned agents are hidden from legacy lists and blocked on direct legacy
routes. The two new tables are additive. Human collection remains disabled.

Actual verification:

- Initial selected Java run in `target/gptlive-acceptance-96fe388a3d` failed:
  the restart guard used the wrong schema prefix, a test attempted Gson reflection
  on Instant, and MVC slices lacked the new repository dependency. Failed XML/logs
  are retained. Six session tests and 13 baseline scoped tests passed in that run.
- After fixes,
  `python tests/gptlive/run_acceptance.py --study --java-tests StudySessionsIntegrationTest,StudyRestartIntegrationTest,ScopedDemoControllerIntegrationTest,*WebMvcTest`
  passed **76 checks, zero failures/errors/skips**, in
  `target/gptlive-acceptance-429d785256`. This includes actual independent
  application-context shutdown/restart with stable assignment, forms and agent
  identity; simultaneous provisioning; rollback after an injected post-save
  failure; private-answer separation; closed/gated sessions; and legacy endpoint
  isolation. The owned MySQL schema/account were removed after each run.
- No inference code changed in this milestone; provisioning is deterministic and
  tests assert no model calls. The OS-01 real-provider evidence remains scoped to
  that foundation journey. Full participant turn handling follows in OS-03.

Remaining limits: no participant text client or export yet; operator-selected
assignment precedes balanced allocation in OS-09; retention policy and human
information remain unresolved, so no human collection is enabled.
Runner preflight was rerun: 13 passed. `git diff --check` also passed.

## OS-03 — Durable text ingress (15 November 2026)

Added request receipts, canonical event IDs/results, replay and cursor checks,
pending/uncertain reconciliation, explicit submission cancellation and late-commit
fencing. A small shared AgentApplicationService hook permits an application-owned
transaction after inference; task authority stays in the existing agent. Monitor
publication follows commit. The study conversation projection contains committed
agreements and receipt-linked turns, excluding private forms.

Actual verification:

| Run | Evidence |
| --- | --- |
| `--study --java-tests StudyTurnsIntegrationTest,StudyRestartIntegrationTest,StudySessionsIntegrationTest,CoordinationAgentUnitTest` | Initial run `c146675b24`: 29 passed, one rollback-test spy setup error. After correction, `d4cb769fc4`: **30 passed**, no skips, including provider-outside-transaction assertion, concurrent retry/ordering, injected rollback, post-commit response loss, cancellation/revocation during inference, expired receipts and real application restart |
| `--study --java-tests StudyTurnsIntegrationTest,AgentApplicationServiceUnitTest --paid-core coordination-text` | `0589524542`: **six study checks passed** (the additional named class does not exist; service coverage is the separate run below). Real OpenAI three-turn session path passed receipt/state assertions, including repeated IDs. Two provider calls, 7,488 cumulative tokens recorded |
| `--study --java-tests StudySessionsIntegrationTest,AgentApplicationService*UnitTest,AgentTurnSerialiserUnitTest,LiveTranscriptIngressIntegrationTest,LiveTranscriptClaimIntegrationTest` | Mixed-fixture run `6bac9f8f51` exposed existing aggregate-history JSON serialization failure for an unrelated EventSelector policy. Adjusted the list-isolation assertion to the controller boundary, retaining HTTP denial and ordinary-list checks. Rerun `96091b165d`: **28 passed**, zero failures/errors/skips |

Commands above use `python tests/gptlive/run_acceptance.py`; artifact directories
are `target/gptlive-acceptance-<run>`. Failed logs/XML are retained. Disposable
MySQL schemas/accounts were cleaned up by the runner.

Independent semantic review of `0589524542`: task-control and durable-replay
criteria **PASS**; strict wording fidelity **FAIL**. Proposal did not execute;
acceptance preserved roles, success and limit two; one contribution was counted;
pause preserved the accepted terms. However, the requested single welcome sentence
was rendered as “Welcome! We’re glad to have you here.”, two sentences. This
non-deterministic wording deviation is retained for later semantic qualification,
not hidden by a successful HTTP/JSON result. Raw `semanticReview: PENDING` remains
unchanged; this paragraph records the independent assessment. No human-study
finding or general instruction-fidelity claim follows from this smoke.

Limitations: one study application process, scheduled ticks disabled; no claim of
exactly-once provider billing or client display. Full immutable research snapshots
and export are OS-04; participant UI and all three task executors remain pending.

Final targeted rerun after adding rollback-publication coverage:
`--study --java-tests StudyTurnsIntegrationTest`, artifact `1eb58f1427`, **six
passed**, including no monitor publication for a rolled-back mutation. Runner
preflight: 13 passed. Node syntax and `git diff --check` passed.

## OS-04 — Research recorder and export (15 November 2026)

Added append-only `study_record` evidence at procedural/agent commit boundaries,
immutable acceptance snapshots, version/configuration metadata, full canonical
event coverage, separate client rendering reports and private-answer export rows.
Operator-only JSON export compares recordings against canonical state and reports
missing records, uncertainty and procedure/display completeness independently.

Actual verification:

- `--study --java-tests StudySessionsIntegrationTest,StudyTurnsIntegrationTest`:
  **14 passed**, artifact `3433a81b0a`, after inserting recorder boundaries.
- `--study --java-tests StudyRecorderIntegrationTest,StudyRestartIntegrationTest`:
  **six passed**, artifact `c7b973524b`. Tests retain exact initial/revised
  agreements, exceed 12 journal entries/eight outcomes, inject post-insert recorder
  failure, check display scope/deduplication and detect missing evidence.
- First complete-study run `83e1fbc365` had **23 passes and one failed assertion**:
  OS-03's broad “no broadcaster calls” check raced with scheduled activity metrics.
  Narrowed it to agent-state publication; metadata activity is a separate channel.
  Failure XML/logs retained; no provider trial was reached in that run.
- `python tests/gptlive/run_acceptance.py --study --paid-core coordination-text`:
  **24 Java checks passed**, zero failures/errors/skips; real OpenAI journey/export
  also passed. Artifact `target/gptlive-acceptance-4325eed8f0`. Two provider calls,
  7,449 cumulative tokens. Owned MySQL schema/account cleaned up.

Independent semantic review of `4325eed8f0`: **PASS for this bounded smoke**.
The proposal preserved roles/success/limit without executing; explicit acceptance
produced one short welcome sentence and one counted contribution; pause preserved
the accepted terms. Export retained exactly that agreement with source/run/revision,
three committed requests and four idempotent synthetic display reports (including
the starter). The export correctly reported procedure incomplete and excluded the
participant token. Display evidence here was supplied by the test script, not an
actual browser or proof of reading. The OS-03 wording deviation remains part of
the evidence; this successful run does not establish a reliability rate.

No human data collected. Source metadata records the tested base commit with a
dirty-checkout flag. Research materials remain draft and collection closed.
Participant UI, full instruments, task executors and operational qualification
remain subsequent work.
Final runner preflight: 13 passed. Node syntax and `git diff --check` passed.

## OS-05 — Resumable text interface (15 November 2026)

Added invitation entry, staged private forms, canonical conversation/term review,
native SSE reconciliation, refresh-safe drafts/requests, help/finish/withdraw and
mobile layouts. Finish durably enters assessment without asserting task success.
Cancellation before request arrival now creates a persisted rejection tombstone.
Receipt timestamps use database-compatible microsecond precision. Study control
wording is concise while baseline controls and agreement authority are preserved.

Actual verification (runner: `python tests/gptlive/run_acceptance.py`):

| Run/artifact suffix | Result |
| --- | --- |
| `62f87bd03f`, targeted Java + initial browser | 18 Java and one initial browser check passed |
| `5cfb24b6ab`, expanded browser | Seven passed, reconnect failed: current SSE cursor delayed response headers until heartbeat. Added immediate connection comment |
| `325ba009be`, browser rerun | Seven passed, offline error text failed: raw fetch error overwrote the friendly message. Fixed shared error presentation |
| `91938ac843`, browser | All eight passed; visual review prompted mobile collapsing panels and removal of transient smooth-scroll snapshots |
| `1ed3160c81`, all study Java | 25 passed, one cancellation replay assertion failed on nanosecond/microsecond timestamp round-trip. Normalized receipt times |
| `34ed98e80f`, `--study --browser` | **26 Java and eight browser checks passed**, zero failures/errors/skips/retries |
| `e2ed7ad847`, session/turn/shared Coordination and revision Java + browser | **43 Java and eight browser checks passed**, including final participant Pause wording |
| `8b234132c0`, paid text foundation | Three committed/replayed turns and export passed; two provider calls, 7,483 cumulative tokens |
| `ee485f0a6f`, final material checks + paid text foundation | **Three Java checks passed**; three committed/replayed turns and export passed; two provider calls, 7,401 cumulative tokens |

Artifacts are under ignored `target/gptlive-acceptance-<suffix>`. Disposable local
MySQL schemas/accounts were cleaned up. An attempted combined browser/paid command
was rejected at preflight before DB/API use, as intended; runs were separated.
Runner preflight: **13 passed**. JavaScript syntax and `git diff --check` passed.

Browser evidence covers preparation, acceptance, pause, refresh, lost HTTP reply,
429/503/400 recovery, held inference/withdrawal fencing, offline reconnect and long
untrusted text. It asserts no media/Speech/Live traffic, canonical single rendering,
keyboard controls and preserved drafts. Screenshots inspected at 1440px and 390px:
proposal, agreement, long-message, pending, error and terminal views. Final mobile
agreement, error-429, pending desktop and finished mobile were inspected in
`34ed98e80f`; the final run additionally asserts the concise pause text.

Independent semantic review: `8b234132c0` passes proposal/acceptance/pause and
recording criteria but **fails strict one-sentence fidelity** (“Welcome! We're glad
you're here.”). Final `ee485f0a6f` **passes this bounded text smoke**, with “Welcome!”,
unchanged roles/success/limit, one contribution and a truthful paused response.
Both exports retain one exact agreement, three requests, four synthetic display
reports and explicit incomplete procedure. Nonverbal hints can remain in canonical
behavior plans but are not rendered or executed by this text interface. Display
reports in paid trials are script-generated, unlike the separate browser checks.
Raw semantic-review fields remain PENDING; this paragraph is the independent review.
Retained failures preclude treating the passing trial as a reliability estimate.

No human data collected. Assessment remains a procedural placeholder; all three
task executors and later operational qualification remain pending. Human collection
is disabled and scenarios remain unready.

## OS-06 — Bounded guessing-game execution (15 November 2026)

Added an optional typed guessing contract, study-owned executor through the shared
Coordination graph, persisted pending questions/answers/counters, explicit success
and exhaustion outcomes, revision with retained progress, immutable private
predictions and participant cards. Research TURN records include full game state.
The model supplies bounded QUESTION/GUESS proposals; the backend owns their budget,
answer consumption, final confirmation and control transitions. Baseline agreement
format and graph remain supported. Scope and coding notes: [SCENARIOS.md](SCENARIOS.md).

Verification used `python tests/gptlive/run_acceptance.py`; artifact suffixes below
refer to `target/gptlive-acceptance-<suffix>`. All schemas/accounts were owned local
MySQL fixtures and cleaned up. Failures and raw provider captures are retained.

| Run | Actual result and follow-up |
| --- | --- |
| `70f0f589e1` | Compile failed on the acceptance helper's package/visibility. Exposed the existing helper explicitly and corrected the reference |
| `20a83df051` | 35 Java passes, one test failure: fixture tried changing protected terms using PROPOSE. Corrected it to an explicit replacement |
| `f99b8b8153` | 38 checks attempted, 36 errors from transient editor-produced incomplete class files. No passing acceptance claim. Isolated study Maven output under each artifact directory through an optional build-directory property |
| `cb73adede5` | 37 passes, one fixture assertion failure: the early acceptance recognizer did not match the compound test sentence; its mock still returned PROPOSE. Corrected the direct-command fixture and added a separate inferred-acceptance commit-gate test |
| `4ff46c4fea`, `--study --browser` | **34 Java and nine browser checks passed**; included full persisted game/revision/export and paused reload |
| `54c7d21481`, paid guessing | Failed on START after two committed turns: generated blank speech rejected before executor takeover. Two calls, 8,507 tokens. Added a study-specific START reply seam; unused interpreter wording/modalities cannot bypass the executor |
| `884981cc8f`, focused shared/game tests + paid guessing | **37 Java checks passed**. Paid trial reached revision but failed exact unchanged-success assertion: the earlier success string repeated the old numeric limit. Four calls, 14,404 tokens. Centralized numeric limits in typed fields and added a limit-only revision guard |
| `43320f77e0`, same focused tests + paid guessing | **37 Java checks passed**; complete nine-turn paid journey passed. Seven calls, 22,009 tokens |

Independent semantic review of `43320f77e0`: **PASS for this bounded script**.
No task question preceded acceptance. Roles, answer choices and confirmation were
faithful; initial five and revised three-question budgets were explicit. Only
`activity.questionLimit` and its derived `dialogueLimit` changed between the two
exact snapshots. Pause/resume and revision preserved the same pending question ID
and one spent question. The subsequent questions were “Is it primarily used
outdoors?” and a book guess; after three NO answers the backend reported exhaustion
without claiming a correct guess. Actual provider requests contained neither the
private object form nor prediction text. This is not a guessing-accuracy estimate.

The export retained nine requests, two snapshots, ten synthetic display reports,
31 records and ten private-answer rows, with recording/configuration/display
complete and procedure incomplete. Paid display reports are synthetic; separate
browser evidence establishes actual DOM rendering. Retained failed trials prevent
interpreting one successful run as a general reliability rate.

Desktop prediction and mobile revision/ended screenshots were inspected in
`4ff46c4fea`. Review led to collapsing study-step cards on mobile, removing the
internal derived contribution budget from the participant review and adding
padding around game progress. Readiness flags and human collection remain closed.
Full assessments, adaptive revision cards and B/C executors remain later milestones.

Final `--study --browser`, artifact `5bd6fe78aa`: **36 Java and nine browser checks
passed**, zero failures/errors/skips/retries. This includes inferred acceptance
fencing without a prediction, invalid-move recovery, reset and executor-owned START
wording. Final mobile revision/ended screenshots were inspected after the visual
adjustments. Runner preflight: **14 passed**; JavaScript syntax and
`git diff --check` passed. The separate 37-check shared/game regression and live
trial above used the same final executor source.

## OS-07 — Fictional contextual check-in (15 November 2026)

Added the typed one-question encounter, exact accepted answer/support wording,
fresh controlled scene ingress, privacy postponement, explicit resumption,
departure ending, preserved pending-question revision and a timestamped user-paced
1–2–1 probe. Scene claims fence earlier in-flight work before the agent lock;
participant event projections and rendering receipts distinguish withheld,
already displayed and late-reported content. No camera or clinical verification
is involved. New fields/transitions require additive schema update/fresh agents.

Actual local MySQL runner evidence (artifacts in ignored
`target/gptlive-acceptance-<suffix>`; owned schemas/accounts cleaned up):

| Run | Result |
| --- | --- |
| `2035257049`, context/game/material/shared Coordination | 30 Java checks passed |
| `4ba6a4924a`, context/turn/recorder integration | 18 Java checks passed, including a held real application turn fenced by a concurrent scene claim |
| `1172536c55`, `--study --browser` | 42 Java and ten browser checks passed; initial visual review led to moving scene controls into the conversation panel |
| `a7c8cc7ffb`, `--study --java-tests none --paid-core coordination-text --study-scenario context` | 12 committed/replayed requests, four OpenAI calls, 21,359 cumulative tokens; bounded script passed |
| `6802db5e8b`, expanded shared/context/probe tests | 36 passes, one fixture failure: the newly strengthened privacy guard correctly blocked a fresh proposal while two people were still visible after a finished encounter. Made the fixture establish one person before negotiating again |
| `fb840163d3`, final `--study --browser` | **44 Java and ten browser checks passed**, zero failures/errors/skips/retries, including ordered-probe persistence and explicit Stop/status consistency |
| `29d41bfa29`, final context/shared Coordination/revision regression | **32 Java checks passed**, zero failures/errors/skips |

Independent semantic review of `a7c8cc7ffb`: **PASS for this bounded script**.
Acceptance performed no private question; a fresh one-person scene asked the exact
agreed question. Two people produced nonsensitive postponement and return remained
paused. The two exact accepted snapshots differ only in `activity.noReply`, changed
to “A reminder may help.” A new prediction/acceptance, fresh one-person scene and
Resume retained the original pending question ID; NO delivered exactly the revised
support and recorded `FICTIONAL_NO_REPORTED`. Subsequent absence/return did not
restart it. Actual provider captures contain neither private preference nor
prediction markers. The live script used manual scene controls; the subsequent
planned-probe control is verified by local DB/browser tests, not that paid run.

Its export contains 12 requests, two acceptance snapshots, 41 records, ten private
answer rows and 11 synthetic display reports. Recording/configuration/display
are complete, procedure incomplete. Paid display reports are synthetic; actual
DOM withholding/rendering is independently exercised in Playwright. The raw
semantic-review flag remains PENDING; this paragraph records the separate review.

Final desktop ended and 390px mobile postponed screenshots in `fb840163d3` were
inspected: scene controls are visible beside the conversation, wrap without
overflow, and disclose persistent controlled simulation. A scene cannot erase
previously displayed text or prove physical privacy. Late client reports remain
explicitly ambiguous. Incoming commands have a 15-second freshness bound; the
selected simulation persists until another accepted command changes it.

Runner preflight: **14 passed**. JavaScript syntax and `git diff --check` passed.
Human collection remains closed; signs, full assessments and capacity qualification
are subsequent milestones.

## OS-08 — Negotiated simulated sign answers (15 November 2026)

Added an exact question-list/sign contract, optional observation-backed preparation,
question/run/accepted-revision scope, persistent observation deduplication, wait
and explicit resumption, opposite-mapping revision with retained answers, and
participant simulation controls. A study-only observation leaves the existing
rock/scissor/paper contract intact. Fresh study agents are required. The ordinary
durable receipt and transactional research recorder own persistence and export.

Actual evidence uses owned disposable loopback MySQL schemas/accounts (cleaned up)
and artifacts under ignored `target/gptlive-acceptance-<suffix>`:

| Run | Result and follow-up |
| --- | --- |
| `cab17f4851`, focused sign/material/shared tests | 33 passes, two failures and one error. “Swap” was not recognized by the replacement guard; added a study-only extension. A second fixture setup invoked an existing Mockito stub with null while restubbing; corrected it to `doAnswer` |
| `8e14cbb83e` | Compilation failed on a misplaced package-private normalization-helper reference; used local study request normalization |
| `815987e3f3`, focused Java + browser | 36 Java checks passed; ten browser passes and one final export-assertion failure (`payload` instead of actual `data`). The sign journey itself reached completion; corrected the assertion |
| `a1269efb4c`, `--study --browser` | **50 Java and 11 browser checks passed**, zero failures/errors/skips/retries; includes observation deduplication across demonstration and execution |
| `a27694dd4f`, paid signs | Failed at mapping revision after nine requests. Three OpenAI calls, 17,346 tokens. The generated replacement also rewrote the human-role wording; the activity guard preserved the prior agreement. Strengthened exact-copy guidance and the full unaffected-field guard; retained a regression |
| `a625c90558`, focused shared/all-executor checks + paid signs | **45 Java checks passed**; 15-request paid script passed technical assertions, four calls, 24,672 tokens. Independent semantic review **failed** the success string, which included stopping early as an alternative to recording all answers. Executor cancellation/completion remained distinct; clarified the prompt |
| `80f30c6d76`, final focused sign browser | **One browser journey passed**, zero skips/retries; final plain-language sign labels and complete desktop/mobile flow |
| `2316d51feb`, final sign unit/DB + paid journey | **Six Java checks passed**; all 15 requests passed, four OpenAI calls, 24,723 tokens; independent bounded semantic review passed |

The live scripts use synthetic display reports, distinct from the actual browser
rendering checks. Both failed provider trials are retained; HTTP/schema success
alone is not semantic acceptance. Core Coordination and revision regressions passed
in the 36- and 45-check selections. General model reliability, gesture recognition,
human understanding and clinical usefulness are not established by these scripts.

Independent review of `2316d51feb`: **PASS for this bounded script**. Exact question
order, mapping, observation preparation, private prediction and acceptance were
preserved. Open palm spent no question; Resume and revised acceptance kept the
same pending question ID. Only `activity.yesSign/noSign` changed between exact
snapshots. The first original-mapping answer and the two revised-mapping answers
remain YES, YES, NO. Success now requires every agreed question to have an answer;
early Stop is separate. Actual provider requests contain no private intention,
preference or prediction markers. The export retains 15 receipts, two snapshots
with three demonstration proofs each, 43 records, ten private-answer rows and
16 synthetic display reports. Procedure remains incomplete pending OS-09.

Desktop demonstration and mobile wait/ended screenshots were inspected, including
the final desktop and ended views in `80f30c6d76`: controls wrap, show simulation
provenance and disable answers while paused/ended. Final runner preflight: 14
passed; JavaScript syntax and `git diff --check` passed. The final prompt-only
correction was verified by the six sign checks and paid trial after the 50-check
study and 45-check shared/executor regressions. All human collection stays closed.

## OS-09 - assessments, allocation and operations (2026-11-15)

Base `6d0f583f`, branch `experiment/hri-2027-coordination`; v2 draft materials,
source recorded as dirty while testing. Complete private understanding/post forms,
debrief/receipt, frozen materials/information, assigned executor, first-agreement
revision card, balanced randomized batches, replacement identity, assistance,
operator UI, JSON/CSV and explicit missingness/UMUX-LITE scoring are implemented.
See INSTRUMENTS.md for protocol/coding choices and primary instrument citation.

All artifacts below are `target/gptlive-acceptance-<suffix>/` and retained locally.
- `d04da37d22`: 14 attempted, 13 context errors caused by a duplicate new material
  ID. Renamed the understanding ID to `.unchanged`; original failure retained.
- `df40fc82e6`: 14 Java and 11 prior browser journeys passed after that correction.
- `2f67e92a71`: seven new scoring/allocation/assessment DB checks passed.
- `eb3d5c11a7`: 23 focused Java passed; 13/14 browser passed. The operator mobile
  viewport overflowed. Corrected the grid minimum width; no journey bypass.
- **`4161eee5d2`: `python tests/gptlive/run_acceptance.py --study --browser`:
  58 Java and 14 browser checks passed, no skips/retries.** All A/B/C journeys
  now finish understanding/post/debrief/receipt. No-agreement and technical-failure
  journeys do too; operator allocation/assistance/revocation/export passes.
- **`232a957f2e`: `--study --java-tests none --paid-core coordination-text
  --study-scenario guess`: nine committed/replayed turns, seven real OpenAI
  requests, 26,280 tokens, technical and independently reviewed bounded semantic
  PASS.** Only question/dialogue limits changed (5 to 3); roles/conditions/success
  stayed exact. Pending question survived pause/revision; exhaustion reported no
  confirmed guess. Final export: 35 records, two agreements, 21 answer rows,
  ten synthetic display receipts; recording/procedure/configuration/display all
  complete, zero unresolved requests. Private preparation/prediction/assessment
  markers absent from provider requests. Raw report retains semanticReview PENDING;
  this paragraph is the separate independent review, not a model-judge result.
- `node --test tests/hri/coding.test.mjs`: five labeled synthetic cases passed,
  distinguishing instruction fidelity, understanding and execution, including
  unspoken preference and no agreement. Runner preflight: 14 passed. JS syntax
  and `git diff --check` passed.

Inspected desktop/mobile understanding and questionnaire screenshots, mobile
validation/focus, debrief/receipt and operator layouts. Participant forms wrap
without horizontal overflow; operator table scrolls within its card. Assessed
answers are open-reference, not unaided recall. Browser fixtures supply synthetic
answers, not evidence of human comprehension. Allocation is procedural random
assignment, not randomized treatment. Human collection, deployment, retention
approval and pilot remain external pending gates. OS-10 integrated acceptance next.
