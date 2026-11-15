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

## OS-10 - integrated acceptance and analysis (2026-11-15)

Base `385ce4a3`; complete entry point `python tests/gptlive/run_acceptance.py
--study-acceptance`. It selects all ordinary Java classes, all JS/client and coding
checks, all study browser journeys, dataset rehearsal and configuration-free
production packaging. The separately invoked classroom capacity class is explicitly
excluded (not reported as a skipped study test). Source/material hashes and actual
selection are retained in manifest.json; no source changed during the final run.

Retained iterations under `target/gptlive-acceptance-<suffix>/`:
- `1ba54a3fa6`: 1,073 Java, two failures. Study isolation added a query to ordinary
  HTTP paths (unchanged poll: two instead of one; mixed minute: 2,019 above 2,010).
  Replaced per-request ownership lookup with startup/provisioning ownership index.
- `52d31cb60f`: 18 focused query-volume/isolation/restart checks passed, including
  rollback cleanup of provisional ownership. Existing numeric ceilings unchanged.
- `3895a1b4e8`: 1,074 Java, 54 errors across eight MVC slice fixtures whose mocked
  dependency still named the old repository. Updated those mocks to the registry.
- `5372fac475`: all 1,074 Java and 224 JS/coding passed; 13/14 browser passed.
  Dataset export encountered an agent removed by baseline fixture cleanup and
  returned 500. Export now preserves saved evidence and reports canonical_agent_missing;
  a new DB regression covers this recovery path. This was not a longer-timeout fix.
- **`9aa87dd66e`: final complete command PASS: 1,075 Java across 193 classes,
  224 JS/coding, 14 browser, analysis and package/DB health smoke; zero skips,
  retries or failures.** Runner preflight separately passed all 15 checks.
  `git diff --check` passed.

The final analysis retains 68 synthetic attempts / 66 participant identities,
including two replacements, six COMPLETE statuses, three withdrawals and three
revocations. All five full browser procedures (A/B/C, no agreement and technical
failure) have the expected forms and complete recording; the three activity
journeys each have two accepted snapshots. The aggregate fixture dataset contains
36 incomplete recordings from deliberate record/agent loss and baseline cleanup,
and 65 missing UMUX scores from unfinished/skipped fixture forms. These are retained
and flagged, not participant results or an estimated failure rate. Analysis verifies
counts, replacement identity/task preservation, versions, snapshots and missingness.

Desktop/mobile preparation, information/consent, private choice, prediction,
agreement, pause/revision, failure, understanding, questionnaire, debrief and operator
screenshots were inspected across OS-05 through this run; added all preparation
stage captures here. Local package boot uses the same disposable DB and reports
health UP. The JAR contains participant assets, excludes local application/OpenAI
configuration and test fixtures, and has SHA-256
`5cbd0af90aa11a7dabc5ec523395430ad723e546b7d477bb057c90d4f3aad569`.
No deployment occurred. Single-process operation remains required; human collection
stays closed. See ACCEPTANCE.md for reproduction; OS-11 semantic matrix is next.

## OS-11 - bounded real-provider semantic matrix (2026-11-15)

Base `330d1e8f`; predeclared matrix/caps in PROVIDER_MATRIX.md. Each scenario now
begins with missing requirements and meaningful clarification, then specification,
paraphrased acceptance, one revision, execution probe, private assessments and receipt.
All runs use fresh agents/disposable MySQL, `hri-text-v2-draft`, actual OpenAI model
`gpt-5.6-luna`, effort `none`, 30s timeout and 2,048 completion tokens. No audio/Live.

Retained trials (`target/gptlive-acceptance-<suffix>/`):

| Iteration/task | Artifact | Calls / tokens | Review |
| --- | --- | --- | --- |
| Initial A | f536b8d995 | 8 / 31,372 | Technical pass; text-output contract failed: unrendered nonverbal metadata |
| Initial B | b4afed876c | 5 / 29,026 | Same metadata issue; accepted/executed terms faithful |
| Initial C | 3f7b8fee13 | 5 / 30,176 | Same metadata issue; accepted/executed terms faithful |
| Speech projection A | a165655ac4 | 8 / 31,792 | Bounded semantic pass |
| Speech projection B | 4ad396c6bc | 5 / 28,857 | Technical pass; clarification offered an unsupported alternative count threshold |
| Speech projection C | b65959223f | 5 / 30,275 | Bounded semantic pass |
| **Final A** | **346266d8dc** | **8 / 31,921** | **Technical + independent bounded semantic PASS** |
| **Final B** | **0c1e27d734** | **5 / 29,553** | **Technical + independent bounded semantic PASS** |
| **Final C** | **eff21edc1c** | **5 / 30,610** | **Technical + independent bounded semantic PASS** |

Corrections were identified before declaring/running the next bounded matrix.
Study interpretation now validates then projects generated replies to speech only;
blank text fails closed. Context guidance distinguishes negotiable example wording
from fixed scene/departure/resumption capabilities. It must explain those limits,
not invite an unsupported threshold. Baseline multimodal Coordination is unchanged.
Offline verification: `2df307acba` passed 36 typed study/definition/shared Coordination
checks after speech projection; `83087a9fff` passed 26 context/shared/executor checks
after the guidance correction. Script syntax and `git diff --check` passed.

Independent final review (separate from raw `semanticReview:PENDING` reports):
- A clarified the budget/answer set without starting, then preserved roles, success,
  answer types and pending question through pause/resume and the 5-to-3 total-limit
  revision. Only the limit and derived dialogue bound changed. Three No answers
  ended with budget exhaustion, not false success. The deliberately negative script
  does not establish guessing quality or consistency with the private secret.
- B asked for exact question wording and explicit agreement to the supported fixed
  policy. The versioned one-two-one probe postponed without repeating private text;
  return/revised acceptance stayed paused until fresh one-person evidence and Resume.
  Only noReply changed, and No delivered exactly that revised response. Departure
  and return after completion produced no restarted question. No identity/privacy
  guarantee, adherence verification or clinical success claim appeared.
- C clarified exact questions, retained all three and every unaffected field across
  the mapping swap, required fresh demonstrations and private predictions before
  acceptance, preserved the second pending question, and consumed fresh revised
  signs once. Final answers remained YES/YES/NO; success required all three answers.

Final exports respectively contain 10/13/16 requests, 37/47/49 records, two accepted
snapshots and 21 answer rows each, with all recording/procedure/configuration/display
checks complete and zero unresolved requests. Display reports are synthetic script
reports, not browser evidence. Provider requests contain none of the private secret,
preference, prediction or assessment markers. Source hashes did not change during
any final run. Some clarification replies bundle related questions and wording has
minor punctuation/grammar issues; human usability remains for the pilot.

Final matrix usage: **18 calls / 92,084 tokens**. All nine retained trials together:
**54 calls / 273,582 tokens**. Across the final 18 inference spans, median provider
latency was **1.795s**, nearest-rank p95/max **3.736s**. Scenarios ran in separate
owned processes with overlapping trials; these are descriptive provider observations,
not an eight-session production capacity test. Aggregate local summary:
`target/hri-provider-matrix-os11.json`. General model reliability and human task
performance are unproven. Create fresh invitations after prompt changes because
agent instructions persist. OS-12 sustained capacity/package/pilot handoff is next;
human collection remains closed.


## OS-12 - capacity and pilot handoff (complete)

Source base `7759a44d` plus recorded working-tree hashes. Predeclared criteria are
in CAPACITY.md. Admission leases, persisted interaction bounds, clear pre-claim
429 feedback, a dedicated capacity runner, production-package restart, and an
operator/schema/pilot runbook are verified within the bounds below. Human collection stays closed.

Early checks and retained probes:

| Artifact suffix | Selection | Actual result |
| --- | --- | --- |
| `dafe39a0d6` | Admission/session/turn local-DB checks | 23 passed, zero failed/skipped |
| `ca00ffdbcd` | First compressed eight-session probe | Failed ambiguous state assertion; driver cleanup required stopping its owned Node process; schema/account cleaned by runner |
| `b5af0f4071` | Probe after forced fresh driver reads | Failed contextual revision prediction; script did not explicitly authorize its protected replacement |
| `e4de8c3574` | Corrected 90-second eight-session probe | All procedures/restart passed; 28,094 queries, projected 56,188/hour for two waves, over the predeclared 28,800 target; not capacity-qualified |
| `9f73198114` | Shared-storage projection, admission/scenario/query regressions plus capacity/limit browser | 23 Java + one browser passed; zero skips/retries; both 390px screenshots inspected |

The failed script used a generic "revise the term" utterance while requesting
REPLACE. The runtime correctly required explicit protected-term authorization;
scenario-specific revision text fixed the fixture without relaxing the guard.
The driver also now waits for an in-progress SSE refresh before forcing its next
canonical state read, identifies the failing scenario/step, and exits cleanly on
failure. The first successful probe's query volume motivated direct reads of the
existing shared Coordination storage instead of repeatedly hydrating the full graph.

The local disposable account had unlimited (zero) per-hour/concurrent account
limits; server max connections 151, max user connections zero. This is not proof
of the hosting quota. The documented 36,000/hour reference retains 20% headroom.
Runner argument/preflight checks: 16 passed. New mobile feedback is readable, the
draft remains intact, and Finish/withdrawal remain accessible. No media is used.

`56d0c416e5` repeated the eight-session 90-second probe after the storage projection:
all procedures passed, then a configuration-free production JAR restarted against
the same schema with `ddl-auto=validate`. All eight completed exports and the ninth
pending contextual question/receipt recovered; the latter completed a technical
ending and assessments. No source hashes changed. Query volume fell to 21,759
(setup 3,573; active 16,620; export 286), still projecting 43,518/hour before the
extra presence renewals of a full-length wave. Eight remains unqualified. The
next candidate is four sessions, as PLAN.md allows after measured quota failure;
the numeric thresholds and 20% headroom remain unchanged.

`5aa31fa847` ran the full integrated selection: all 1,082 Java tests (195 classes)
and 224 JS/coding tests passed, but one of 15 browser cases failed. The new client
cleared uncertain ingress for every HTTP 429, including an unrecognized proxy/rate
response. It now applies pre-claim recovery only to the explicit CAPACITY and
INTERACTION_LIMIT codes; unrecognized 429 keeps the original deliberate receipt
recovery. Fourteen other browser cases passed. Analysis/package were not reached.
The failing trace/screenshot is retained; the full command is rerun after the fix.

Final integrated acceptance `87d364f964` passed: **1,082 Java tests / 195 classes,
224 JS/coding tests, 15 browser cases**, dataset analysis and configuration-free
production package/DB health. Zero failed, skipped, retried or flaky selected cases;
source hashes unchanged. Command: `python tests/gptlive/run_acceptance.py --study-acceptance`.
Package SHA-256: `4854312707f4ff11c8134444ebef1907afc1ebed16b2dddebb6b031d3cb6cca7`.
The five full browser procedure exports (A/B/C plus technical/no-agreement endings)
were complete. The combined fixture dataset has 78 attempts/75 issued identities,
33 accepted snapshots, 44 intentionally incomplete recordings and 75 missing UMUX
forms; baseline cleanup/fault fixtures remain included, not a production failure rate.

Final live OpenAI regression `6b2df9fed9` passed on the OS-12 source with admission
maximum four and the ordinary interaction bounds. Command:
`python tests/gptlive/run_acceptance.py --study --java-tests none --paid-core coordination-text --study-scenario guess`.
Eight actual gpt-5.6-luna/none calls used 31,817 tokens; nearest-rank provider p50
1.306s, p95/max 3.066s. Independent review found clarification before proposal,
separate acceptance, unchanged roles/answer choices/success through a 5-to-3 limit
revision, preserved pending question, and honest QUESTION_LIMIT_REACHED after No
answers. Raw decorative model fields again appeared but were correctly projected
out of canonical speech-only output. Minor doubled punctuation remains cosmetic.
All 37 records, two snapshots, 21 answer rows and 11 display reports were complete;
zero unresolved requests, no private markers in provider requests, source hashes
unchanged. This remains a bounded synthetic contract check, not a guessing-quality
or human-usability claim. OS-11 retains the complete A/B/C live matrix and failures.


### Accepted sustained capacity and production recovery

`b63e461b75` passed the predeclared reduced candidate:
`python tests/gptlive/run_acceptance.py --study-capacity 1800 --study-wave 4`.
Measured duration **1,800.263 seconds**, including **1,440.318 seconds of active
interaction**. Four complete procedures covered two guessing, one contextual and
one sign task, each with two accepted snapshots, revision/probe, full histories,
private predictions, assessments, display evidence and SSE/reconnect. The blocked
fifth invitation retained its preparation state, later acquired a freed slot and
left a committed pending contextual question for recovery. All source hashes stayed
unchanged; no other test/database workload ran concurrently.

| Measurement | Observed result |
| --- | --- |
| Total measured queries, including monitoring and extra recovery preparation | 12,012 |
| Session setup | 1,809 queries / 1.974s |
| Active interval | 8,897 queries / 24.005 minutes |
| Final export | 153 queries / 0.248s |
| Two waves/hour, including two setups/exports | **24,024 queries/hour** |
| Active-interval hourly projection | 22,238 queries/hour |
| Headroom against documented 36,000/hour allowance | **33.27%**, exceeding required 20% |
| Active turn HTTP, including deliberate replays | p50 15.1ms / p95 1,081.8ms / max 2,081.0ms |
| Fresh mutation server HTTP, matched by agent_queue trace | 52 requests; p50 63.4ms / p95 1,131.5ms / max 2,078.9ms |
| Active reads/forms/presence HTTP | p50 6.4ms / p95 12.5ms / max 27.2ms |
| Assessment non-turn HTTP | p95 18.4ms / max 82.0ms |
| Setup / export non-turn HTTP p95 | 361.2ms / 158.9ms |
| Agent queue | p50 0.015ms / p95 0.055ms / max 0.561ms |
| JDBC pool | maximum 10; sampled peak active 4, waiting 0; idle SSE active/waiting 0 |
| SSE | 8 connections including the four reconnects; 352 frames |
| JVM process CPU during measurement | 31.219 CPU-seconds |
| Sampled JVM heap / threads | peak used 222.3 MiB, committed 308 MiB; peak 40 threads |
| Errors | Zero unexpected HTTP failures/timeouts, leaked session markers, duplicate committed effects or missing required snapshots |

185 resource samples were collected; short transient resource peaks can be missed.
Heap is not total process RSS. The local host was Windows 11 Pro 10.0.26300,
Intel i7-9700K (8 cores/logical processors), approximately 32 GiB RAM, Java 24.0.2
(compiling the project's Java 21 target), Node 24.13.1 and local MySQL. GLOBAL
Questions conservatively includes monitoring and any other local-server traffic;
local unlimited account limits do not certify remote hosting. Cold JVM/schema
startup and the separate maintenance restart are outside the per-wave projection.

The synthetic gateway delayed each call one second (28 calls including the extra
recovery session). It did not call OpenAI. Real-provider evidence and demand
estimates are separately recorded above/in RUNBOOK.md. All measured fresh mutation
HTTP requests were below the predeclared five-second p95 bound, as well as the
30-second maximum; readings/forms also passed their two-second bound.

After the 30-minute run, the runner built a configuration-free production JAR,
restarted it on the same DB with **ddl-auto=validate**, verified all four completed
exports, reacquired the fifth session, preserved its exact pending question and
replayed the committed receipt without inference. Its technical ending, assessments
and complete export then passed. The owned app/schema/account were removed.
Final recovery JAR SHA-256:
`3a3edb5b2b053aab1e42e5572b4f30594977093c168d8280e588733073579f1d`.

Evidence is under `target/gptlive-acceptance-b63e461b75/`: `capacity.json`,
`capacity-export.json`, `local-quotas.json`, `capacity-package.json`,
`capacity-recovery.json`, and the supplemental `capacity-review.json` (fresh
mutation timings matched from content-free agent_queue/http trace IDs). Raw fixture
credentials, exports and provider captures remain ignored, not committed.

**Software milestones OS-01 through OS-12 are complete.** The measured operating
envelope is one process, four concurrent text sessions, and no more than two such
waves/hour with this bounded workload. Automatic admission limits concurrency;
operator scheduling must limit intake. Longer human conversations, faster intake,
different hardware/hosting or provider limits require remeasurement/pilot adjustment.
The eight-session candidate failed the query target and is not qualified. Human
collection remains closed: institutional arrangements, reviewed information and
retention rules, deployment checks, actual 4-6-person pilot, issue resolution and
protocol/build freeze are explicitly PENDING in RUNBOOK.md. No physical robot,
clinical effectiveness, universal model reliability or human-usability result is claimed.


## OS-13 — Camera perception and participant handoff (15 November 2026)

**Software complete.** The owner corrected the earlier interpretation of text-to-text:
participants use typed dialogue **and actual camera perception**, including face,
person and hand detectors. The owner also selected a participant-arranged consenting
adult helper for the contextual one–two–one probe. V3 materials and the participant
landing now describe this condition. V2 frozen simulation attempts remain separate.
See CAMERA.md for the complete current contract and release gates.

The study client starts the camera and all detectors after preparation/connection,
subject to browser permission. Images stay local; derived observations enter the
assigned agent through durable, scoped study receipts. Facial labels are fallible
context, not task controls. Person changes fence older output; stale evidence and
camera loss are unknown, and a stale gap cannot silently resume a pending question.
Hand answers require stable recognition, current revision/question/run scope and
neutral release between signs. Low-confidence or unclassified hands cannot count
as release. Capture stops on finish, withdrawal, hidden tab and navigation; visible
permission/model/delivery failures preserve finishing. Capacity rejection stops
sensor retries until readmission and other delivery failures back off.

Activity ending now offers a confirmed questionnaire handoff without equating
initial acceptance with completion. Existing private pre-interaction choices,
open-reference comprehension, two UMUX-LITE items, four exploratory ratings and two
optional comments remain; answers lock before debrief. No database schema change,
raw-media storage, microphone or physical robot control was added.

### Actual verification

| Check | Result and retained evidence under `target/` |
| --- | --- |
| `node --test` across all `tests/js` and `tests/hri/coding.test.mjs` | **230 passed**, including six camera edge/lifecycle checks; `hri-camera-js.log` |
| `python tests/gptlive/test_acceptance_runner.py` | **17 passed**, including camera/model selection and rejection of misleading v2 capacity/integrated combinations |
| `--study --java-tests 'Study*Test' --browser` | **71 Java + 15 legacy browser tests passed**, zero skips/retries; `gptlive-acceptance-c0a97e20a9`. The camera Java class explicitly uses v3 even in this legacy browser run |
| `--study --study-camera --java-tests none --browser` | **4 camera browser tests passed**, zero skips/retries; `gptlive-acceptance-39381501b9`. Real HTTP/DB, generated video and controlled detector outputs: permission/model failure, face context, context postponement/unknown camera, sign demonstration/wait/revision/held-sign exclusion, questionnaire and teardown |
| `--study --study-camera --study-models --java-tests none --browser` | **1 passed**, `gptlive-acceptance-d931c049b1`: actual detector bundles/weights loaded and processed generated blank video, producing zero people; no external non-GET requests or browser errors. This does not establish physical recognition accuracy |
| Final sign-prompt refinement: `--study --study-camera --java-tests StudySignsTest,StudySignsIntegrationTest,StudyCameraIntegrationTest --paid-core coordination-text --study-scenario signs` | **10 focused Java checks and the live journey passed**, `gptlive-acceptance-74b68de6db` |

The four camera cases cover A/B/C and failures, not four participants. Screenshots
at 390 and 1440 pixels were inspected; layout/controls were readable without
horizontal overflow. The real-model screenshot was also inspected. Temporary
apps, random loopback MySQL schemas and restricted accounts were removed by the
runner. Selected acceptance manifests reported `sourceChangedDuringRun: []`.

### Live OpenAI acceptance and retained failures

All paid trials used the camera protocol with **synthetic detector observations**,
local disposable MySQL, `gpt-5.6-luna` / `none`, and the existing per-trial caps of
20 calls / 80,000 tokens. No participant camera recording was sent to OpenAI.
Canonical text, accepted/revised terms, pending questions, outcomes and exports
were inspected independently of executable assertions.

| Final journey | Calls / tokens | Evidence and bounded semantic result |
| --- | --- | --- |
| A: guessing | 8 / 33,180 | `970d9b960e`: PASS; roles, question limit, pause/resume and limit-only revision preserved; exhaustion distinct from success |
| B: contextual check-in | 5 / 30,452 | `0e1e38aed0`: PASS; fresh scene start, one–two–one postponement, explicit resumption, wording-only revision and preserved pending question |
| C: negotiated signs | 5 / 32,388 | `74b68de6db`: PASS; demonstrations, one-question sign scope, wait/resume, mapping-only revision with renewed preparation/acceptance; correct remaining answers and ending |

Each final export had two accepted snapshots, 21 assessment rows and complete
recording/procedure/configuration/display flags with no unresolved requests.
Final matrix: **18 calls, 96,020 tokens**; observed provider-call median 1.407 s,
nearest-rank p95/max 3.563 s. These are bounded scripted outcomes, not a model
reliability rate. A's all-No fixture tests contract fidelity, not secret-consistent
guessing quality. `semantic-review.json` records each review alongside its journey.

Earlier evidence remains available:

- `7655cb97de`: initial Java profile assertion failed because it still expected the
  pre-camera event list. Updated the expected declared capability.
- `f8f5f593e6`: seven Java checks and three camera browser cases passed; the fourth
  reached assessment but queried form controls before the new stage appeared.
  Corrected the test to await the questionnaire heading before filling it.
- `52141c487e`, `794c1cb078`, `b2056641b3`, `14bfcbb8e7`: passing intermediate
  regressions retained; later checks cover subsequent fixes.
- `5ea52b0e1f` and `69dc9869f5`: B/C execution passed, but semantic review rejected
  stale deterministic “simulated” proposal wording. C's fixture also retained a
  contradictory “No camera” sentence and ambiguous Resume role wording. Corrected
  source-neutral contract reviews, the fixture and role guidance.
- `e7ba58c2bc`: corrected C fixture exposed unsupported generic hand checks added
  alongside typed demonstrations. The guard rejected the proposal before activation.
  Strengthened the study-only instruction that `activity.demonstrate` owns this
  preparation and `checks` must remain empty; final C passed afterward.

Across all six paid trials, including retained failures: **30 calls, 169,117 tokens**.
Guards were preserved; failed model output was not silently repaired or accepted.

### Remaining limits

Human collection remains disabled. Physical sign/face/person recognition across
student devices, lighting and occlusion; consenting-helper feasibility; actual
participant usability; reviewed information/retention; hosting/provider limits;
and the human pilot/freeze remain pending. The four-session/two-wave OS-12 result
qualifies **v2 simulations only**. Periodic v3 camera traffic and client detector
CPU need a separate capacity qualification before release. No human, clinical,
physical-robot, causal-comparison or universal reliability result is claimed.
