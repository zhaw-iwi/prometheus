# GPT-Live implementation and acceptance evidence

Implementation branch: `feature/gptlive`; deployment integration: `agents`.
Roadmap: [PLAN_GPTLIVE.md](PLAN_GPTLIVE.md).
Each follow-up records its commit/deployment scope below. A passed synthetic
test is not acoustic acceptance.

## GI-11 / Milestone 239 shared persistence efficiency (2026-10-22)

Shared implementation starts on feature/gptlive and follows main -> agents.
Receipt admission now executes one MySQL/InnoDB autocommitted INSERT ... SELECT
with current access-code enablement/link, persisted epoch and duplicate identity
in the same statement. The local ownership snapshot is only a cancellation fence.
There is no authorization cache, fragment buffer, INSERT IGNORE or automatic retry.
Outer transactions are suspended; accepted receipts remain durable if the caller's
unrelated transaction later rolls back. Rejected inserts distinguish duplicates
from obsolete owners with one additional scalar query. The default autocommit pool
is required; a non-autocommit connection fails closed. Segment claim/processing
transactions, original receipt IDs, timestamps/order and receipt ledger stay intact.

Storage's private collection changes from a list to a set over the same join table
and columns. Membership changes remove/add only their own links instead of
rebuilding every link. Eager access, orphan removal, detached merge and same-value
write-version changes remain. A disposable test removes the new schema's composite
primary key to exercise the legacy join-table shape, including reload/update/delete.
No saved instance/history rewrite or production schema migration is required.

Focused disposable MySQL verification: 13/13 cases passed in LiveQueryBudget,
LiveReceiptDurability, StoragePersistence, LiveTranscriptClaim and LiveTranscriptIngress
integration suites. Scoped and trusted receipt admission each use exactly ONE MySQL
Question (formerly seven and six respectively, including transaction control).
Hibernate statistics omit direct JDBC, so these checks measure session Questions.
Tests cover Unicode/null timing, exact timestamps, rollback durability, duplicates,
invalid-write failure, revocation/unlink/reset, restart/no-replay, claim rollback,
storage reload/merge/orphans and bounded link changes with 100 entries.
Evidence: target/gptlive-acceptance-749a049188/java.log.

The first focused run exposed transaction-synchronization connection retention
in a preliminary split JPA/JDBC implementation; the final atomic JDBC admission
uses one connection, including duplicate/rejection handling. Two new test fixture
errors were corrected before the passing rerun. Evidence of that initial failure:
target/gptlive-acceptance-0b34efb786/java.log.

Full shared Java run: 556 cases, 555 passes, one pre-existing static client contract
failure (ValerianClientStaticResourceContractTest:189). HEAD's shared script already
submits agentDefinitionKey plus embodiment, while that unchanged test expects only
agentDefinitionKey; the deployment branch has the matching embodiment contract.
Neither client source nor this test is changed by the persistence work. This is
not a fully green shared suite. Evidence: target/gptlive-acceptance-fff5878d29/java.log.
Both runners removed their disposable schema/account. No production database was
used. Integrated Gala regression and sustained capacity measurement follow on agents;
these focused savings do not establish the fixed-quota capacity gate.

Integrated follow-up: the first agents full run exposed a heap failure in the core
RPS cockpit smoke with the preliminary set mapping. SQL evidence shows storage
links joining the agent/transition graph and, for one policy read, six independent
storage-entry collections. Explicit SELECT fetching preserves eager access while
preventing that row multiplication. The storage regression now rejects such joins
before executing them. The failing owned JVM was stopped and its runner cleaned
the disposable resources; it was not a passing run. Agents evidence:
target/gptlive-acceptance-48520c915b/java.log.

The complete event-history endpoint now selects EventHistory directly instead of
loading the agent state/policies/storage. Existing scoped authorization remains;
IDs, order, state paths, native provenance and complete history remain available.
The regression reads 205 events in at most four scoped Questions, verifies fresh
appends and immediate revocation, and distinguishes missing agents from empty
histories. No history truncation, cache or Live context change is introduced.

The follow-up focused run passed all 16 cases (StoragePersistence, LiveQueryBudget,
LiveReceiptDurability, LiveCockpitSmoke and QueryVolumeIntegrationTest):
target/gptlive-acceptance-d711109c54/java.log. The narrower-history precursor passed
its selected HTTP/history/persistence checks at target/gptlive-acceptance-1f58e148da.
Final integrated full-suite/soak evidence is maintained on agents in the Gala results.

## Local acceptance resource and freshness fixtures (2026-10-22)

The Gala integrated acceptance run exposed two test-harness issues: a full suite
retained enough cached Spring contexts/pools to exhaust local MySQL connections,
and the all-sensors smoke supplied observations without source timestamps despite
expecting fresh context. The disposable acceptance runner now limits the Spring
test context cache to four; the smoke supplies current source times. Production
pool sizes, observation freshness rules and database configuration are unchanged.

Focused verification on feature/gptlive: both LiveCockpitSmokeIntegrationTest
cases passed on disposable local MySQL (target/gptlive-acceptance-ab5dbf66f6/java.log).
Full-suite verification follows on agents with the Gala changes; quota compliance
remains separate from functional acceptance. No production database test was run.

## Gala conversation ownership — Milestone 226 (2026-10-21)

The user authorized the first top-down slice and commit/push. Live now answers
ordinary Gala/event, game-choice/rules, guessing and facial questions from current
facts. The application still classifies activity control/embodiment, chooses the
person, validates hand observations, computes RPS results and controls tasks.
There is no backend YES/NO/UNKNOWN/RULES speech operation. A guessing question can
still incur a background control classification; Live's answer does not wait for it.

`ExternalVoicePolicy.liveActivityContext()` carries bounded model-visible facts
separately from behavioural instructions. Opt-in state/fact refreshes use quiet
thinking updates. An optional persisted speaking occasion distinguishes user-request
continuations from proactive comments; facts precede one commentary append. Gala
uses this for game starts, delayed RPS results and control recovery, not ordinary
answers. Correct name guesses and give-up clear state without another reveal.
Cancellation/departure/reset clear pending occasions. Existing core policies and
the configurable task engine keep their previous contracts. No schema migration,
production database write or saved-instance rewrite is part of this work.

The selected guessing card is now available to Live. Premature name disclosure is
therefore a prompting risk, not prevented by keeping the name outside the model.
Facial guidance permits tentative expression/apparent-mood comments from fresh
camera estimates, accepts corrections and distinguishes estimates from raw video.
This change does not gate audio, prove final-word playback, or fix the derived
face-summary expiry/transport issues observed in the physical trial.

Verification:

- 76 focused unit cases passed: Gala reception/reference/task/delivery plus shared
  activity-context, projection/delivery and voice-default checks. Maximum complete
  instructions: **15,854 bytes**, both embodiments, all states and maximum task goals.
- 17 integration cases passed: Gala persistence and canonical/compact journeys,
  both core-agent representative journeys, Live query/claim contracts. The first
  18-case run had one outdated delivery fixture failure; its storage-copy mistake
  was corrected, and the real storage/quiet continuation test passed in the unit run.
- Seven query-volume cases passed. Mixed Gala minute: **1,996 database questions**
  (budget 2,100); this remains above the plan's hourly capacity if sustained.
- Final 22-case acceptance passed after the repeated-occasion refresh correction,
  including Gala journeys, instruction budget and shared delivery/context checks.
- Main passed 32 focused shared/core cases on disposable MySQL; feature passed 26
  foundational unit cases and a subsequent 13-case delivery regression rerun.
- All 27 real `gpt-5.6-luna` control classifications passed replay through the actual
  Gala action. Ordinary guessing questions select WAIT; reveal/negation/lifecycle
  cases preserve the intended distinction. This tests control, not Live answer quality.
- The final start/reveal prompt refinement passed 32 additional focused unit cases;
  shared voice/default checks passed four cases on feature.
- Packaging and diff checks passed. No frontend change or robot/headset test.

Commands used: `mvnw -q -Dtest=... test`; `python tests/gptlive/run_acceptance.py
--database-properties src/main/resources/application-test.properties --java-tests ...`;
`mvnw -q -DskipTests package`. Disposable schemas/accounts were removed. Ignored
artifacts: `target/m226-unit-final.log`, `gptlive-acceptance-870a449062` (initial),
`gptlive-acceptance-715248faf4` (query volume), `gptlive-acceptance-5418a9476e` (final),
`gala-226-control-backend-validated.json`; main `gptlive-acceptance-7f294a4539`.

Provider speech acceptance uses the actual assembled Gala instructions and commands
exported by `target/GalaConversationTrial.java`, synthetic German input through a
real WebRTC `gpt-live-1` session with Marin, and a recording of the received audio.
The application state changes are controlled fixtures, not a deployed full-stack
trial. Two synthetic-input preparation requests timed out before creating a Live
session; resuming only missing clips completed preparation. The first 11-turn
probe had no question echoes, correct Einstein answers (male/alive/Swiss residence),
unknown shoe size, no reveal for hypothetical give-up, one actual reveal, appropriate
happy-face feedback, accepted correction and a correct scissor-versus-paper verdict.
Independent file transcription of received audio confirmed those words. It also
exposed premature starting acknowledgments and a delegated delay before the reveal.
The final prompt explains automatic transcript control, removes game/reveal lookup
and explicitly suppresses starting preambles. Controlled state/quiet delivery tests
remain the authority for mutations; provider style remains probabilistic.

The final 11-turn repeat passed the ordinary-question/no-echo, consistent-card,
unknown-fact, hypothetical-give-up, immediate single reveal, facial-estimate and
visitor-correction cases. Both RPS signs and the winner were spoken correctly.
Recorded-audio transcription independently recovered these answers, including the
final verdict. Neither session reported a provider error; both finalized and were
hung up. Artifacts: `gala-226-v2-webrtc-results.json`, `gala-226-v2-voice-assessment.json`,
`gala-226-v2-audio.webm` and its independent transcription JSON (ignored target files).
The probe used controlled context updates rather than actual camera measurements.

**Remaining voice limitation:** both runs still produced a pre-confirmation game-start
acknowledgment followed by the confirmed introduction. The final RPS run also offered
another game after its correct verdict. One-response-per-question passed for ordinary
questions; strict silence/one utterance during game starts did not. Prompt guidance
cannot guarantee that behavior, and no audio gate was introduced. The user should
use the updated manual script to test actual headset cutoffs and noisy gala sensing.
The old derived face-summary expiry behavior is unchanged. Do not treat these trials
as proof that every final word will be audible on the physical setup.

Shared commits: `7e29479`, `3ce2a6f`, `f930506` on feature/gptlive, integrated through
main (`9a686b8`) into agents. Existing persona and user-capture hooks are preserved.
Publication is authorized; the agents push triggers the existing Heroku workflow.
Deployment completion is checked after publication, separately from this source record.

## Milestones 224–225 release integration (2026-10-21)

The user authorized commit and push after implementation review. Shared database
and application waiting-guidance changes are committed on `feature/gptlive` as
`b56a423`, then merged into `main` as `21b7d1b`. Gala code, load coverage, the
manual test script and full application evidence are committed on `agents` as
`8155ce9`; the following merge brings in the shared main ancestry.

The merge also reconciles main's earlier selective core-agent port (`bb9166a`).
Conflicts retain agents' newer embodiment/persona, Gala configuration contract,
user-capture hook, application catalog and complete test/documentation coverage.
Deployment source, tests and workflow match `8155ce9` exactly after resolution;
only release documentation changes. Main acquires no Gala implementation or
deployment configuration. Existing implementation-phase scope statements below
describe those earlier verification turns, before this authorized release.

Post-merge main verification passed 23 cases on disposable local MySQL:
`LiveTranscriptClaimIntegrationTest`, `LiveQueryBudgetIntegrationTest`,
`GenericMultimodalTaskIntegrationTest`, `SocialSpaceLiveJourneySmokeIntegrationTest`,
`LiveVoiceAcknowledgementUnitTest` and `LiveContextProjectionUnitTest`.
Command: `python tests/gptlive/run_acceptance.py --database-properties
C:/Data/VSCodeWorkSpace/prometheus/src/main/resources/application-test.properties
--java-tests 'LiveTranscriptClaimIntegrationTest,LiveQueryBudgetIntegrationTest,GenericMultimodalTaskIntegrationTest,SocialSpaceLiveJourneySmokeIntegrationTest,LiveVoiceAcknowledgementUnitTest,LiveContextProjectionUnitTest'`.
Artifacts: main worktree `target/gptlive-acceptance-d650e0a62e/`; owned schema/account
removed. Agents retains the previously recorded 83-case acceptance, final six-case
rerun, provider probes and packaging evidence because its tested code is unchanged.
Diff checks pass. Publication order is feature/main, then agents; the agents push
triggers Heroku. This pre-push record does not claim deployment completion.
Strict silence, partial transcription/audio cutoffs and database capacity headroom
remain the limitations recorded below.

## Gala guessing and voice follow-up - Milestone 225 (2026-10-21)

Scope: Gala-owned code on `agents`; a default-true waiting-acknowledgment hook
starts on `feature/gptlive` and is applied equivalently on `agents`, retaining
branch-specific persona/capture hooks. The pending Milestone 224 database patch is
preserved. No transport/default core guidance change, production mutation, commit,
push or deployment. Production reads were bounded to the supplied trial instance.

After quota access resumed, the third trial yielded 60 complete segments (20 user,
40 assistant), 360 receipts and 370 instance events. Dialogue confirms repeated
questions before the separately delivered backend answers and a duplicate game
choice. Some durable input receipts themselves end at `Lebt die`, `Ist es`,
`Ist es Nikolai` or `Hat die Person in der Schweiz`; the assistant's later echo
contains more words. No audio recording establishes the missing words' precise
cause. The earlier database optimisation was not deployed. Do not reconstruct
user intent from assistant guesses or claim that richer cards solve capture.
Local diagnostic files (ignored): `target/gala-round3-dialogue.json` and
`target/gala-20261021-round3-db.json`. Dialogue is not copied into tracked files.

Changes:

- Gala's stable Live policy forbids question repetition/completion and interim
  waiting acknowledgments, including a duplicate game-choice invitation. It uses
  the documented `Generate <|SpawnThinking|>` delegation instruction. This is
  application-specific prompt guidance, not a deterministic audio gate. Two real
  voice probes still spoke waiting remarks with the shared permission present.
  `ExternalVoicePolicy.livePendingAcknowledgements()` lets Gala remove that
  permission from both common voice and task guidance; other policies default to
  the original wording. The shared adapter test verifies both paths.
- Four private fact cards now cover life dates, real/fictional status, occupations,
  known residences and achievements. Date/geography deductions are explicitly
  allowed. Unknown residences are not interpreted as negative evidence. Einstein's
  card includes Swiss residence and his co-invented refrigerator. Source metadata
  is dated in code comments and never enters backend prompts or Live context.
- UNKNOWN supplies a missing-fact response; CLARIFY asks for a complete yes/no
  question; RULES explains play or hypothetical give-up without revealing a name.
- GIVE_UP captures the stored target in one canonical confirmed speech intent
  before clearing the game and returning to reception. Common standalone give-up
  commands bypass inference. Other phrasing uses the existing one-call operation
  classifier. Correct guesses share the same completion helper. Explicit stop,
  silence, departure, idle expiry and unrelated topics do not reveal. A request
  to end without revealing maps to RETURN. No schema or saved-graph migration.
- `tests/gptlive/GALA.md` now checks no echo, unknown versus incomplete questions,
  a hypothetical give-up, an audible reveal, and a separate stop/silence check.

Verification:

- Final focused acceptance command:
  `python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests 'GalaDinner*UnitTest,GalaDinnerReceptionJourneySmokeIntegrationTest,GalaDinnerReceptionIntegrationTest,GenericMultimodalTaskIntegrationTest,SocialSpaceLiveJourneySmokeIntegrationTest,LiveVoiceAcknowledgementUnitTest,LiveContextProjectionUnitTest,LiveContextDeliveryUnitTest'`.
  It passed 83 Java cases, including all three canonical/compact journeys, reveal
  delivery/recording once, duplicate committed-turn replay, hidden target isolation,
  correct guessing, direct/inferred give-up, unknown/clarify/rules, stop/quiet and
  rejection outside GUESS. The disposable database is local; providers are mocked.
  Artifact: `target/gptlive-acceptance-7d84aa76c7/`. A final prompt refinement reruns
  the six instruction-budget/Gala journey cases in `target/gptlive-acceptance-8e31beea66/`;
  all six passed.
- The shared feature worktree passed ten adapter/projection unit cases, without
  a database/provider or Spring application; `target/gala-225-shared-unit.log`.
- Every reachable Gala state, both embodiments and maximum-length simultaneous
  task/proposal goals fit: maximum 15,985/16,000 instruction bytes. The stable
  reference remains identical across activity changes.
- Real backend trial: 27 synthetic questions using captured application prompts
  and the configured production behaviour route (`gpt-5.6-luna`, effort `none`).
  The first run passed 26/27; "give up but do not reveal" incorrectly chose GIVE_UP.
  After making RETURN explicit for that case, the final 27/27 operations passed
  and all outputs were replayed through the real Gala action. This includes dates,
  Einstein's invention/Swiss residence, incomplete inputs, unknown facts, different
  name guesses, hypothetical/negated give-up and all four cards. Artifacts:
  `target/gala-225-backend-cases.json`, `target/gala-225-backend-results.json`,
  `target/gala-225-backend-results-initial.json`, `target/gala-225-backend-validated.json`.
  Harness: ignored `target/GalaGuessingTrial.java` and `target/gala_225_backend_trial.py`.
- Real voice probe: see final result below. Direct primary WebSocket authentication
  was rejected with HTTP 403 before a session started. The WebRTC route used by
  the cockpit accepted the same credentials. An initial harness run stopped on
  an unavailable browser `crypto.randomUUID` and hung up its owned session; this
  was corrected locally without a product change.
- `./mvnw.cmd -q -DskipTests package` and `git diff --check` passed;
  package log: `target/gala-225-package.log`. No production application is started.

Real voice result and limitations:

Four completed headless WebRTC probes used `gpt-live-1`, Marin, client delegation,
continuous synthetic German input audio and the actual assembled Gala GUESS
instructions. The two questions were a gender question and a give-up/reveal request;
confirmed backend results were injected after a five-second post-speech delay.
The probes did not run the full application or database; its persisted journey is
covered separately. Each session closed gracefully with final usage and received
nonzero WebRTC audio energy. Synthesized input used `gpt-4o-mini-tts`, Alloy. No
production records or real guest audio were sent. Owned sessions were hung up.

All four completed probes avoided question repetition and generated the complete
Einstein reveal once after the confirmed result. Two prompt-only variants still
spoke waiting remarks; removing the shared permission also exposed one premature
`Ja` and a sigh in an intermediate probe. The final label-specific guidance
produced only the confirmed `Ja` and reveal as substantive answers, but still said
`Mhm` and `Alles klar, einen Moment` before confirmation. Therefore **strict silent
waiting is NOT verified**. These trials support the reveal/echo improvement in
this small sample, not a guarantee against premature or duplicate speech. Do not
hide the failed variants or treat generated captions/audio packets as a physical
headset playback test. A deterministic speech gate would be a separate transport
change, not another assertion about prompt compliance.

Final evidence: `target/gala-225-webrtc-results.json` and
`target/gala-225-voice-assessment.json`; earlier variants are
`target/gala-225-webrtc-results-initial.json`, `...-prompt-only.json` and
`...-hook.json`. Ignored harness: `target/gala_225_webrtc_trial.mjs`.
The initially rejected WebSocket probe is `target/gala-225-voice-results.json`.
Final instruction budget: 15,985/16,000 bytes; future edits must retain the budget test.

Remaining limits: synthetic voice does not certify room acoustics, physical
headset playback, camera sensing or arbitrary phrasing. Provider-generated audio
and transcript evidence are distinct from a delivery ACK. A fresh Live session
is needed to load the new policy; use a new Gala instance and hard-refresh the
browser for the controlled manual test. Partial transcription and the reported
last-word cutoff remain open issues. The database capacity constraint from
Milestone 224 also remains; these changes do not increase its quota.

Source audit, checked 2026-10-21:
[Nobel Einstein biography](https://www.nobelprize.org/prizes/physics/1921/einstein/biographical/),
[Einstein/Szilard patent US1781541A](https://patents.google.com/patent/US1781541A/en),
[Nobel Curie biography](https://www.nobelprize.org/prizes/chemistry/1911/marie-curie/biographical/),
[Curie questions/answers](https://www.nobelprize.org/prizes/physics/1903/marie-curie/questions-and-answers/),
[official Chaplin biography](https://www.charliechaplin.com/en/biography/articles/21-Overview-of-His-Life),
[Science Museum Lovelace record](https://collection.sciencemuseumgroup.org.uk/people/cp38764/ada-lovelace).
The Nobel pages returned HTTP 403 on direct opens; indexed official source extracts
supplied the checked facts. The other primary records were accessible.
Voice guidance follows [official GPT-Live prompting](https://developers.openai.com/api/docs/guides/live-prompting)
and [delegation documentation](https://developers.openai.com/api/docs/guides/live-delegation).

## Complex-agent database load - Milestone 224 (2026-10-21)

Investigation after the third Gala trial found the documented shared JawsDB
allowance of 36,000 questions/hour exhausted. Retained logs begin after the trial,
so the onset and any effect on that conversation are unknown. No production database requests
were retried during this follow-up. Earlier reductions (`70f9a41`, `b9e693d`) are
still present; their query-budget regressions pass. Gala revealed another cost:
loading its state machine queried 23 action-key collections separately, plus
transition/decision collections. This grows with definition complexity rather
than one query per historical event.

Shared implementation starts uncommitted on `feature/gptlive` in the
`prometheus-database-query-20261021` worktree. The identical shared patch is also
uncommitted on `agents`, which owns the Gala workload regression. No branch merge,
commit, push, deployment, schema migration or saved-agent change is included.

- Shared `application.yaml` sets Hibernate collection batch fetching to 64. It
  retains eager/detached graph semantics and avoids a cross-turn entity cache.
- Transcript receipts persist their already-validated new identity directly,
  avoiding merge's redundant lookup. Every received fragment still commits
  durably with current access scope and persisted epoch checks.
- The initial segment claim validates scalar identity instead of loading the
  full agent graph. It reads bounded receipt identities together, verifies
  completeness/epoch/speaker/ownership, preserves their original order, and
  conditionally assigns unclaimed receipts in one update. A failed assignment
  rolls back the entire claim. The committed PENDING boundary remains separate
  from processing; processing revalidates ownership and never retries uncertain
  actions automatically. Duplicate segment outcomes remain durable across restart.
- Sensor cadence, freshness, state-machine decisions, speech policy, permissions
  and cockpit lifecycle are unchanged. This does not fix the separately reported
  repetition, restrictive guessing cards or absent give-up reveal.

Measured on disposable local MySQL with synthetic providers:

| Measurement | Before | After |
| --- | ---: | ---: |
| Representative Gala minute, MySQL Questions | 4,731 | 2,031 |
| Scoped fragment, Hibernate statements | 5 | 4 |
| Unscoped fragment, Hibernate statements | 4 | 3 |

The mixed-minute test replays 12 social batches and facial/context refreshes,
60 combined polls, 12 lease checks, four user turns and eight native speech
segments with 72 receipts. It advances operations without sleeping; all other
background workers are mocked. It includes transaction commands in MySQL Questions,
checks the guessing state and exactly-once capture, and enforces a 2,100-question
regression ceiling. This is a synthetic query budget, not a production throughput
or acoustic test. Baseline `target/gptlive-acceptance-e554e8e6b0/` correctly fails
that new ceiling; the implementation run passes at 2,031 (57% reduction).

Verification:

- Feature worktree: 17 focused Java cases passed, including receipt bounds,
  restart/replay protection, failed processing, native recording, query volume,
  bulk ordering, invalid/cross-session receipts, assignment rollback and immediate
  revocation/epoch rejection. Artifact:
  `target/gptlive-acceptance-e41e49d974/` in that worktree.
- Agents: the new load test plus Gala, Generic and Social Space persisted journeys
  passed nine cases. Artifact: `target/gptlive-acceptance-bb6641b74e/`.
- Full agents run exercised 738 cases in 151 suites: 735 passed initially. One
  deployment-catalog assertion predated Gala's existing capability-awareness opt-in;
  the assertion now explicitly includes core and Gala definitions (production
  behaviour unchanged). Two replay contexts hit the local MySQL connection limit
  from cached test pools, unrelated to the production query quota. All six cases
  in those three suites passed on a fresh disposable database. Thus all 738 cases
  have passing evidence, with zero skips. Artifacts:
  `target/gptlive-acceptance-4175b93c1d/` (full run and report counts) and
  `target/gptlive-acceptance-2fe51065c2/` (focused rerun).
- `./mvnw.cmd -q -DskipTests package` and `git diff --check` passed. Build log:
  `target/database-query-224-package.log`. No client scripts changed; no new
  browser, provider or physical-audio test was run.

Commands use `tests/gptlive/run_acceptance.py --database-properties
src/main/resources/application-test.properties --java-tests ...`; the feature
worktree points to the original checkout's local test properties by absolute path.
The runner accepts only loopback MySQL and creates/removes a restricted disposable
schema/account. No paid provider calls or production mutations are made.

Capacity remains a deployment constraint: sustaining the tested workload would
extrapolate to 121,860 questions/hour, above 36,000. The test ceiling prevents
regressions; it does not certify this database plan for the gala. Production must
have measured capacity/headroom for the actual sensing and speech workload before
an extended event. Stopping Live does not stop every camera/stream request; no
lifecycle behaviour was changed implicitly to hide load.

## Gala game choice and non-speech control - Milestone 223 (2026-10-21)

The second headset trial on Heroku v112 exposed two control errors: an unspecified
play request selected RPS, and a subsequent `[laugh]` turn ended the round before
the first valid hand observation. No result was generated. Persisted `quiet=true`
and the absence of another user turn strongly identify a model-selected QUIET;
raw model JSON was not retained. The provider's `context_injection_incomplete`
arrived after local Stop and server delivery shutdown, not before the lost round.
A repeated opening also appeared within the same voice session. The prior delivery
fix was effective: the small state update and invitation were acknowledged 1,415 ms
after announcement commitment, with no reference resend. This is delivery evidence,
not a physical-audibility measurement.

This authorized follow-up remains local on `agents`:

- `GalaDinnerInput` recognizes common German/English unspecified play requests;
  reception asks "Personenraten oder Schere, Stein, Papier?" without inference or
  choosing a game. Other wording can use the new validated CHOOSE_GAME operation.
  Capability examples are explicitly not treated as invitations.
- Known standalone sound annotations and written laughter bypass task inference
  in every Gala activity. They do not renew idle time, clear game/task facts or
  undo silence. Original transcripts remain in history; real words accompanying
  annotations, including explicit stop/silence, still reach control.
- WAIT gives reception and the two games a silent, neutral continuation operation.
  Stable Live guidance asks for a game choice and reinforces same-encounter
  continuity. The graph, schema, shared Live transport and core agents are unchanged.
- Unit cases deliberately configure harmful model decisions behind deterministic
  guards. The real scoped/persisted Gala journey now covers generic request ->
  guessing selection -> explicit stop -> RPS -> laughter -> waiting -> valid hand
  -> one provider-bound result, plus duplicate-sign suppression and both visitors.
  The manual script adds the selection question, laughter and repeated-greeting checks.

Verification:

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests GalaDinner*UnitTest
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests GalaDinnerReceptionJourneySmokeIntegrationTest,GalaDinnerReceptionIntegrationTest,GenericMultimodalTaskIntegrationTest,SocialSpaceLiveJourneySmokeIntegrationTest
.\mvnw.cmd -q -DskipTests package
```

All **54 Java cases in eight classes** passed with no failures, errors or skips:
44 focused cases in `target/gptlive-acceptance-0d0d9f3027/`, then ten integration
cases in `target/gptlive-acceptance-5c1eb8cf66/`. Both disposable schemas/accounts
were removed. Each directory contains the log and fresh report counts. Packaging
passed (`target/gala-223-package.log`); only the existing Maven Unsafe warning
was emitted. Complete Live instructions remain **15,846/16,000 bytes** with two
maximum task goals, in every reachable state and both embodiments.

No paid provider, browser/media or physical-robot trial was run. Scripted Live
receipts establish delivery/recording, not audible speech or model compliance.
Unrecognized free-form language remains model-dependent; the deterministic
request forms and sound annotations are deliberately bounded. Repeated greetings
and ordinary speech cutoffs still require listening verification. No new commit,
push, deployment or production data mutation. Hard-refresh the browser after
deployment and use a fresh Live session; saved graphs/prompts are not migrated.

## Gala narration and user-turn boundaries - Milestone 222 (2026-10-21)

The user authorized this follow-up after the first Gala headset interaction on
Heroku v111. Work remains on the Gala `agents` checkout; shared capture plumbing
contains no Gala-specific constants or state rules. No production writes, paid
provider calls, commit, push or deployment are part of this implementation turn.

Read-only diagnosis of the supplied telemetry and durable session found:

- Four activity changes each sent 23 instruction chunks, taking 16.0-16.8 seconds.
  Gala embedded its current mode in the large policy/reference section, defeating
  the existing section-change delivery optimization. RPS computed a draw on the
  first received hand event, but its result waited behind this guidance. Its first
  caption reached the browser about 52 ms before local Stop (same browser clock).
- The opening's final word was delayed about five seconds in provider transcripts;
  reflected output and microphone input were both quiet during part of the gap.
  Browser playback continued unmuted and reported no packet loss. Routine context
  injection is a candidate for a controlled retest, not an established cause.
- Visitor questions split into independent accepted turns across transcript gaps
  of 770, 1,200 and 1,708 ms. One application-level response rejection followed an
  unfinished request; its exact invalid field was not captured. Simple reference
  questions also triggered unnecessary delegation.

Changes:

- All Gala activity policies now expose identical reference/voice/task guidance.
  The existing state section supplies the activity and task phase/goals. No
  special transport, extra model call, parallel ACK or relaxed result authority
  is introduced. Reference answers explicitly avoid lookup/delegation filler.
- `ExternalVoicePolicy.liveUserSilenceMs()` selects a bounded, session-fixed
  backend user quiet window. The immutable snapshot carries it into capture;
  default/assistant capture stays at 800 ms, Gala selects 2,000 ms. There is no
  schema change, additional DB query or entity retained in a worker. Stop/mute,
  coverage-gap, late-fragment, scope and duration fences remain in force.
  Status and the browser export include the effective `userSilenceMs`.
- Reception accepts a valid CLARIFY operation without activating a game or
  returning a failure apology. Rejection logs identify the processing stage,
  mode and exception class without recording model output or visitor text.
- The existing Gala journey asserts no reference resend and the session setting.
  A separate controlled-clock regression exercises all Gala activity boundaries
  with 700 ms per ACK: one state append plus one result takes 1,400 simulated ms
  including both ACKs. Input/capture regressions keep the observed pause lengths
  in one backend turn and retain default timing and conservative failure cases.

Verification:

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests GalaDinner*UnitTest,LiveTranscriptSegmenterUnitTest,LiveTranscriptCaptureServiceUnitTest,LiveContextDeliveryUnitTest,ScopedLiveSessionServiceUnitTest
node --test tests/js/live/diagnostics.test.mjs
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests GalaDinnerReceptionJourneySmokeIntegrationTest,GalaDinnerReceptionIntegrationTest,GenericMultimodalTaskIntegrationTest,SocialSpaceLiveJourneySmokeIntegrationTest,LiveTranscriptIngressIntegrationTest,LiveSpeechPersistenceIntegrationTest,LiveMultimodalBridgeIntegrationTest,LiveContextProjectionUnitTest,AgentCapabilityLiveContextUnitTest,LiveQueryBudgetIntegrationTest,LiveSessionSmokeIntegrationTest
npm.cmd run test:live:unit
.\mvnw.cmd -q -DskipTests package
```

All **108 Java cases in 19 classes** passed with no failures, errors or skips:
71 focused cases in `target/gptlive-acceptance-4b25c2623f/`, then 37 context,
persistence, ingress, query-budget and scoped Live integration cases in
`target/gptlive-acceptance-5b00f0ba67/`. Each directory contains its own fresh
report counts and log. The latter run includes canonical/compact journeys for
Generic, Social Space and Gala. Both owned local schemas/accounts were removed.
All **39 Live JavaScript cases** passed, including the telemetry export field;
the three-case initial telemetry subset also passed. Production packaging passed
(`target/gala-222-package.log`); no new browser acceptance run was made. Maximum
complete Gala instructions with two maximum-length task goals are
**15,725/16,000 bytes** for both embodiments.

Limits: the two-second window is a conservative acoustic boundary, not language
understanding; it can add 1.2 seconds before backend actions and longer pauses
still split turns. Live itself may respond before backend admission. The delayed
ACK regression models transport cost and does not prove audible latency. No raw
audio, physical headset, camera or robot retest has been performed. The root cause
of ordinary mid-sentence cutoffs remains unresolved. Routine sensor transmission,
freshness and presence lifecycle were preserved. README describes the microphone
on/off camera-cue comparison and hand-detection/paused-question checks. Start a
fresh Live session after deployment; saved graphs and action prompts are not migrated.

## Live announcement latency and audio diagnostics - Milestone 204 (2026-10-02)

The user authorized implementation on `feature/gptlive` after the v104 facial-joke
trial, then reviewed the handoff and authorized commit/push, integration through
`main` into `agents`, and Heroku deployment verification.

Read-only investigation of the 20:34:23 timing export and durable conversation
found backend-result to first native-transcript delays of 15.0 s (activation),
5.0 s (bicycle joke), 6.3 s (computer joke), and 16.1 s (completion). Completion
sent 17 instruction chunks sequentially, followed by evidence, before requesting
speech. Both reported missing punchlines were present in native transcript
receipts; physical output loss could not be located. Heroku's retained logs had
already moved beyond the incident. These intervals are not acoustic latency.

Changes:

- Split voice guidance into stable voice/policy/capability sections and changing
  state guidance. Only changed sections are appended. Generic task state supplies
  phase/revision/goals, with validated rules and decisions retained in PROMETHEUS.
  The existing single combined inference per task turn/reaction is unchanged.
- Committed spoken-intent identity wakes the existing scoped context worker
  immediately, bypassing the sensor read window. Repeated notification identities
  are ignored. Normal sensor/native commits keep the five-second read coalescing,
  existing deadlines, freshness, scope checks and one worker per session.
- Necessary guidance and committed narration precede routine observations. Pack
  adjacent compatible factual updates up to 480 UTF-8 bytes immediately before
  transmission. Recheck expiry after each ACK; expired values never gain a new
  lifetime. Keep uncertain ACK failures visible with no blind retry.
- Add bounded, content-free browser playback/track events and RTC audio statistics;
  timed reflected input/output RMS windows; and append byte counts/sequences/source
  identities. Keep media traces separate from caption eviction, merge server
  journals across polls, and suppress repeated unchanged ledger diagnostics.
  No audio capture/storage, new HTTP polling, DB schema, model request, or sensor
  cadence/threshold changes are introduced.

Validation:

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests all --browser
npm.cmd run test:live:unit
```

- All **487 Java tests in 115 fresh suites** passed without failures/errors/skips.
  Counts include only reports produced by this run, excluding stale reports from
  previous branches. Logs/counts: `target/gptlive-acceptance-e2e248cacc/`.
- All **24 Live JavaScript tests** passed, including media/RTC lifecycle,
  non-overlapping stats, Stop fencing, privacy, bounded retention and ledger dedup.
- All **45 Live-enabled browser cases** and **one Live-disabled compatibility
  case** passed. One additional Live-only case was intentionally skipped in the
  disabled configuration. The real-controller/MySQL/SSE browser case verifies
  audio counters reach the export without text or device IDs; providers/media
  are simulated. Existing Text/Continuous and classroom coverage also passed.
- Initial focused Java acceptance passed in `target/gptlive-acceptance-7bb28ee55b/`.
  The final targeted rerun after replacing an announcement-notification monitor
  with atomic identity tracking passed all **15 tests in three suites**, including
  committed task integration and query budgets. Logs:
  `target/gptlive-acceptance-9f03e5c737/`.
- After separating export omissions from server ring evictions in the timing
  panel, the focused browser rerun passed **14 enabled cases and one disabled
  compatibility case**, with the same intentional feature-off skip. The timing
  drawer screenshot was visually inspected. Logs/artifacts:
  `target/gptlive-acceptance-68420fbe70/`.

The compact Generic activation fixture now needs one state instruction append
and one commentary append, without repeating voice policy or capabilities. Tests
also cover announcement priority, compatible fact packing with source identities,
expiry after slow ACKs, duplicate/lost ACK handling, reset fences, persisted task
activation/reaction/stop, and unchanged routine query budgets. Every test database
is disposable and local; the runner removes its owned process/schema/account.
No paid provider calls or production mutations were made.

Limits: an already running append batch still precedes newly arriving work.
ACKs measure context injection, transcripts measure generated text, and neither
proves playback. RMS activity is a coarse amplitude gate, not an echo/VAD model.
Browser counters depend on platform support. Bounded exports can still omit old
records or intervals missed between polls. No provider or physical acoustic
latency/cutoff improvement is claimed before retesting. Repeat the facial-joke
trial with ordinary input and then microphone muted after activation; retain the
camera cues and export each run. Deployment verification does not replace this
physical trial. Preserve the deployment branch's embodiment prompt binding when
merging the Live adapter.

Provider contracts checked using the OpenAI Docs skill during investigation:
[context injection and transcripts](https://developers.openai.com/api/docs/guides/live-conversations)
and [append types and limits](https://developers.openai.com/api/docs/guides/live-delegation#send-the-right-kind-of-update).

### Milestone 204 branch integration and deployment

Implementation `52cda3e` was pushed to `feature/gptlive` and fast-forwarded into
`main`. Main was merged into `agents` as `2ef3b9d`, retaining the deployment
catalog, workflow and Valerian/Gigi persona binding. The merge combined adjacent
task tests and documentation changes. One deployment-only persona assertion
previously selected all text after the policy heading; it now checks the explicit
policy section, excluding separately named state metadata.

The first merged run passed 70 of 71 focused Java tests and exposed that obsolete
assertion. The corrected rerun passed **71 tests in 12 suites**, **14 Live-enabled
browser cases** and **one Live-disabled compatibility case**, with one intentional
Live-only skip when disabled. It covered task activation, context delivery,
persona/catalog contracts, audio diagnostics, query budgets and real scoped
HTTP/MySQL/SSE flows. Artifacts: `target/gptlive-acceptance-8591bdacc4/`; initial
run: `target/gptlive-acceptance-160291d56a/`. Both used disposable local MySQL and
synthetic providers and removed their owned resources.

The deployment push triggered
[workflow 37067478599](https://github.com/zhaw-iwi/prometheus/actions/runs/37067478599).
It succeeded for `2ef3b9d`. Heroku `valerian` released **v105** at
2026-10-02 21:34:00 UTC; `web.1` is up and startup is confirmed in the logs.
Heroku CLI and bounded HTTP/read-only database checks verified:

- Health returned HTTP 200 / UP; `/valerian/` and access-code login returned 200.
- Served `live/client.js`, `live/audio-diagnostics.js`, `live/diagnostics.js` and
  `performance/panel.js` exactly match the merged sources after line-ending
  normalization. Reload the cockpit before the next physical trial.
- The catalog retained 84 types including Generic Multimodal Behaviour. The
  saved-agent count remained **25 before and after** deployment. Verification
  created no agents, conversations or Live sessions and made no paid API calls.
- The 922 sampled log lines since the release contained no ERROR, R14/R15/H10,
  quota, schema or startup-failure markers. This is a startup sample, not sustained
  load or physical acoustic acceptance.

One initial pre-deployment database probe returned MySQL error 1226; its resource
name was not retained. An immediate SELECT 1 and the bounded inventory/login
recheck succeeded before deployment, as did the post-deployment checks. This
transient observation is not attributed to or claimed fixed by v105.

Sanitized evidence is in `target/live204-rollout-{workflow,heroku,assets}.json`,
`target/generic-rollout-live204-before-recheck.json` and
`target/generic-rollout-live204-after.json`. Credentials and `codexpython.md` were
neither changed nor committed. Completion records follow main -> feature/gptlive
and main -> agents with documentation-only `[skip ci]` commits/merges to retain
the verified v105 runtime. Audible cutoff and response-delay improvement still
require the physical trial described above.

## Database query reduction on main - 2026-10-01

This follow-up uses the user's main-first workflow. `main` was fast-forwarded to
`origin/main` (41d2921) before editing. After local acceptance and review, the user
authorized commit/push, normal main-to-feature/gptlive then main-to-agents merges,
and verification of the resulting Heroku deployment. Integration is complete;
evidence follows below.

The production investigation found JawsDB rejecting queries at the shared
36,000-questions/hour limit. Local measurements identified repeated graph reads
in authorization/heartbeat requests, detached save merges, individual sensor
requests and redundant cockpit state/storage refreshes.

Implemented:

- Scalar access-code/link checks retain immediate disable/unlink detection and
  existing 401/404 distinctions. An unchanged combined status/ledger heartbeat
  performs one SQL statement and loads zero entities. After-commit ledger
  revisions avoid polling an unchanged transcript table; reads racing a commit
  are refreshed on the next heartbeat. Legacy status/history endpoints remain.
- A serialized acknowledge/generate turn retains managed entities across short
  load/save transactions. Loading includes speech ownership, and the explicit
  Hibernate connection handling releases JDBC after each transaction. Providers
  do not retain that connection. Existing outer Live ingress transactions retain
  their durable claim/processing/failure semantics.
- A camera sample can contain up to four ordinary sensor events. Valerian batches
  presence/grouping/context; each item retains ordered processing, authorization,
  commit, derived events and publication. Failed in-memory changes are cleared
  before the next item. Partial results advance only successful sensor signatures.
- Ready monitor SSE snapshots replace post-turn state/storage reads. Initial
  hydration, reset and stream-failure fallback remain. Sensor cadence, confidence
  thresholds, five-second Live refresh, 15-second freshness and speech contracts
  are unchanged. No schema migration, permission cache or cross-turn entity cache.

Measured HTTP boundary costs (Hibernate prepared statements):

| Path | Multimodal Behaviour before / after | Live Multimodal before / after |
| --- | --- | --- |
| Idle Live poll cycle | 39 / 1 | 25 / 1 |
| Ordinary face observation | 38 / 20 | 23 / 12 |
| Live face observation | 42 / 21 | 27 / 13 |

The before measurements are from the initial local investigation on agents
6213dcb5, retained in `target/query-volume-20261001/measurements.txt`; after values
use main plus this change. Both use real MVC, services, Hibernate and disposable
MySQL, with providers/context/capture workers mocked and no outer test transaction.
Statement counts are identical with 5 and 205 seeded history events; graph entity
loads still grow with history for actual agent work. Initial combined polling
costs two statements; unchanged polling also costs exactly one MySQL `Questions`.
Live face processing costs 27/19 MySQL questions respectively, including transaction
control commands, versus 21/13 Hibernate statements. Scheduled/background work is
excluded from these per-request numbers.

The social comparison uses actual presence/grouping/context and the resulting
derived social event. It compares three already-optimized individual requests
against one batch, so it isolates batching from the other improvements.
Multimodal Behaviour uses 65 versus 35 Hibernate statements, or 83 versus 53 MySQL
questions. Live Multimodal uses 42 versus 26 statements, or 60 versus 44 MySQL
questions (including its additional embodiment event). State/storage/event
equivalence is verified separately, including a real blocking extraction action,
state transition, persisted event IDs and paths.

Verification uses the existing isolated runner with
`--database-properties src/main/resources/application-test.properties`. It reads
only local administration settings, creates random restricted schema/accounts,
overrides application datasource/provider endpoints and cleans up after execution.
It never targets the configured application schema or production database.

Final acceptance:

- `python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests all --browser`:
  **471 Java tests PASS**, zero skipped; **46 browser cases PASS**, one expected
  Live-only skip in the feature-disabled run. Artifacts:
  `target/gptlive-acceptance-d5730db34c/`. Generated class directories were archived
  before regression to prevent stale tests from the deployment branch being run.
  The final run includes real classroom/SSE concurrency, Live ingress/history,
  Text/TTS with Live disabled, and a check that monitor-driven state/storage
  updates require no duplicate detail requests after the turn.
- `node --test tests/js/live/*.test.mjs tests/js/transcription/*.test.mjs tests/js/speech/*.test.mjs tests/js/performance/*.test.mjs`:
  **89 PASS**; log `target/query-volume-node.log`. Tests include unchanged-ledger
  polling, actual batch serialization and partial failure, late-response fencing,
  unchanged sensor freshness and monitor failure/reconnect fallback.
- `node --check src/main/resources/public/valerian/script.js` and
  `git diff --check`: PASS.
- The new six-case MySQL suite covers bounded costs, actual persisted
  state/storage/derived events, failed-item isolation, invalid batches and access,
  real committed transcript revisions, and blocked provider work with zero
  checked-out connections in a one-connection pool. It caught the need for explicit
  Hibernate release-after-transaction handling on retained persistence contexts.
- Installed existing locked browser assets with `npm ci --ignore-scripts --no-audit
  --no-fund` after the first browser attempt found missing Bootstrap dependencies.
  The final run above passed after installation; manifests/lockfile are unchanged.
  Earlier new test-fixture errors were corrected before final acceptance.
- All runner-owned applications, disposable schemas and restricted users were
  removed after execution. Production configuration/data and local application
  schemas were not modified. No paid provider or physical audio trials were run.

### Branch integration and Heroku verification

Implementation commit `b9e693d` was pushed to main and fast-forwarded into
feature/gptlive. Main was then merged normally into agents as `26c4572`, preserving
the deployment catalog, Gigi/Valerian persona binding and Heroku workflow without
conflicts. The merged tree passed **607 Java**, **89 JavaScript** and **46 browser**
cases before the agents push; one Live-only browser case is intentionally skipped
when Live is disabled. Fresh-build artifacts are in
`target/gptlive-acceptance-5790ba7cf0/`; Node output is in
`target/query-merge-node.log`. Disposable schemas/accounts and owned test apps
were removed. Local property files were not committed.

[Deployment workflow 36916316101](https://github.com/zhaw-iwi/prometheus/actions/runs/36916316101)
succeeded for `26c4572`. Heroku app `valerian` released **v101** at
2026-10-01 19:44:11 UTC; web.1 is up on v101. Startup completed at 19:44:39 UTC.
The verification used the Heroku CLI and bounded HTTP/database checks:

- Health returned HTTP 200 / UP and `/valerian/` returned 200.
- Served `live/client.js` and `valerian/script.js` match the merged sources after
  normalizing line endings, confirming the new client is deployed.
- A real enabled access code returned HTTP 200 from `POST /demo/session`;
  scoped Live capabilities returned 200. The code and user content were not logged
  by the verification scripts.
- A nonexistent handle on the new updates route returned its empty HTTP 404;
  an empty sensor batch returned HTTP 400 before processing. These checks created
  no sessions, events or agent mutations.
- The read-only saved-agent inventory remained **25 before and after** rollout.
  Login and health already returned 200 before rollout; their recovery therefore
  is not attributed to this deployment alone.
- Inspection of 902 retained application-log lines after 19:43 UTC found zero
  ERROR lines and zero `max_questions` markers. This is a bounded startup/verification
  observation, not sustained production load acceptance.

Completion records are synchronized main -> feature/gptlive and main -> agents
with normal ancestry. Their documentation-only commit/merge uses `[skip ci]` to
retain the verified v101 runtime without a redundant deployment. Reload Valerian
before a live trial so the browser uses the newly deployed scripts.

Limits: this is a query reduction, not a guarantee that the existing shared hourly
quota can support sustained sensing, speech and multiple clients. One idle Live
session now accounts for about 3,600 heartbeat questions/hour, but active events,
transcript receipts and context refreshes add real work. Even one continuously
sensing session can exceed 36,000/hour; deployment needs measured headroom or a
larger quota. Long histories still load for runtime turns. Production load and
real microphone/speaker/provider trials remain separate from automated deployment
health and read-only HTTP checks.

## Compact context delivery and bounded refreshes - 2026-09-28

The follow-up failed/successful trials both contained selected fresh social
evidence. The failed export included two 11-command batches taking about 7.7/7.9
seconds; its next batch withdrew older evidence before replacement social facts
arrived. The successful run had smaller 7/8-command batches taking about 4.7/5.3
seconds, but still lagged a changed camera count. Append ACKs establish receipt,
not which facts the model used, and the bounded exports omit earlier traces.
This supports fixing a delivery hazard, not claiming a proven sole root cause.

Same-type replacements now arrive without a separate removal command. Rich social
context precedes other sensor values and derived face summaries. Compact content
keeps type, freshness, observation/expiry times and existing adapter text; immutable
snapshots and content-free command traces retain source/revision identity. Actual
selector removals explicitly become unknown. Native history churn alone no longer
adds a revision marker. Long content still chunks, and instructions/narration keep
their existing routing, bounds and no-blind-retry behavior. Before each send,
expired remaining chunks become one unknown notice per type, without a DB read.

Commit-driven graph reads coalesce over five seconds; the one-second scheduler
wakes deferred work without occupying workers. Freshness/delegation deadlines
bypass the interval; the idle fallback remains 30 seconds. This is deliberately
not immediate task delivery: commits can wait for that window and any in-flight
ACK. It bounds commit-driven reads, not all database work or provider response
latency. Shared observation adapters, state control, BehaviourPlan and ordinary
Text/Continuous processing are unchanged. No schema/provider contract change.

Focused acceptance passed on disposable MySQL:
`target/gptlive-acceptance-84c65fa836/`. Four new deterministic cases cover compact
replacement ordering/true removals, native-history churn/state markers, a
6,000-commit burst with fast ACKs (13 reads over 60 seconds including startup), and
expiry of unsent chunks behind a delayed ACK. Existing idle expiry, delegation
deadline, epoch/reset, lost-ACK and multilingual bounds remain covered. SQL costs
remain 4 unscoped / 6 scoped receipt statements and 9 context statements for both
5 and 205 history events. All 67 Live/transcription/speech Node cases passed.

The feature-branch full suite passed 438 Java cases in 105 suites. All 44 enabled
browser cases passed in `target/gptlive-acceptance-34a8721de7/`. The initial
disabled smoke sent text while connection history was still hydrating: the
backend acknowledged the turn successfully, but late initial rendering replaced
the round display. The smoke now waits for the real `Behaviour Live` status
before submitting text (no production UI change). A focused rerun passed all 14
enabled Live browser cases plus the disabled Text/TTS smoke, with one intentional
Live-only skip: `target/gptlive-acceptance-d5e7c33f31/`. The existing early-input
hydration race remains outside this delivery change.

Final focused Java checks also passed in `target/gptlive-acceptance-6fd5390cf9/`
and `target/gptlive-acceptance-f554fe0c3a/`; integration timeouts now allow the
coalescing window plus scheduler/persistence time, while controlled-clock tests
assert the actual cadence. Expiry suppression emits `expired_before_send` in the
bounded content-free trace. The obsolete event-ID removal helper was removed.
The final full Java rerun after all cleanup/concurrency checks again passed all
438 cases in 105 suites: `target/gptlive-acceptance-d5a38162c2/`.

Deployment-tree verification (local `agents` merge `0399a74`) passed all 572 Java
cases in 126 suites and all 67 Node cases. The first Java run passed 571 cases but
one context could not start because cached test pools exhausted local MySQL
connections (`target/gptlive-acceptance-9f1eba1044/`). Rerunning with test-process
`SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=4` and
`SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0` passed the full suite:
`target/gptlive-acceptance-8b12e296ba/`. These are local test settings, not Heroku
configuration or evidence of a production query-quota error.

That small pool was insufficient for the browser app's concurrent SSE requests:
42 browser cases passed but reconnect/next-fixture requests exhausted its four
connections. The browser-only rerun used maximum pool size 10 and minimum idle 1;
all 14 enabled Live cases and the disabled Text/TTS smoke passed, with one
intentional Live-only skip: `target/gptlive-acceptance-4d0a6f8f1d/`. Across the
full/focused deployment-tree runs, all 45 distinct browser cases passed. Run Java
and browser acceptance separately when using reduced pools for cached Java test
contexts. Owned fixture apps stopped and all disposable schemas/accounts were
removed. Deployment-only catalog/persona code and production settings are intact.

Tests use random local schemas/accounts and loopback or stubbed providers, never
the configured production database. Physical camera grounding, paid voice quality
and sustained production query consumption still need a new interaction trial.
The pre-deployment Heroku log sample contained no query-quota errors but continued
to contain R14 memory warnings; neither is an hourly usage measurement, and this
change does not resize or tune the dyno.

## Perception freshness and database query follow-up — 2026-09-28

The Heroku investigation reproduced a mismatch between change-only cockpit
emission and Live's 15-second sensory freshness window. Stable readings were
never refreshed. Production also exhausted its 36,000-query hourly allowance;
retained quota errors did not prove the quota was already exhausted during the
reported conversation, and the stored trial observations could not be read.

During active Live capture, eligible unchanged face/presence/grouping/social
readings now refresh every five seconds, only when the detector observes them
again. Slow configured face/social intervals are capped for Live. Failed sends
retain their previous successful signatures/timestamps and retry after cooldown.
Manual samples and text/TTS deduplication retain their existing semantics.

Transcript receipts validate current scope plus persisted epoch with scalar
queries, then persist through an agent reference. They no longer load state and
history for every fragment. Eager event state paths use a subselect, preserving
detached history while eliminating one SELECT per event. Context timer ticks
only inspect deadlines in memory: reads occur on committed changes, sensory or
delegation deadlines, and a 30-second fallback. Native speech recording also
publishes an identity-only notification after commit. Revocation, reset, receipt
uniqueness, segment claims and no-automatic-action-retry guarantees remain intact.

Measured on disposable local MySQL with the same new regression cases before and
after the fixes:

| Operation | Before | After |
| --- | ---: | ---: |
| Unscoped transcript receipt, 200 history events | 211 statements; 214 entities loaded | 4 statements; 0 entities loaded |
| Scoped transcript receipt, 200 history events | Not measured | 6 statements; 0 entities loaded |
| Context refresh, 5 history events | 13 statements | 9 statements |
| Context refresh, 205 history events | 213 statements | 9 statements |

Baseline failures: `target/gptlive-acceptance-5dbb379629/`. Initial fixed query
checks: `target/gptlive-acceptance-81d96864b9/`; focused integration/deadline checks:
`target/gptlive-acceptance-579ec662a3/`.

The full feature-branch suite passed 434 Java cases in 105 fresh suites on an
isolated MySQL schema. All 67 Live/transcription/speech Node cases passed,
including six controlled-clock sensor cases. All 44 enabled browser cases and
the disabled-mode text/TTS smoke passed; one Live-only case was intentionally
skipped with Live disabled. The new browser case verifies real cockpit emitter
requests refresh stable face evidence during Live and stop refreshing after Stop.
Desktop light and mobile dark Active screenshots were inspected. Owned fixture
apps stopped and the disposable schema/account were removed. Full-run artifacts:
`target/gptlive-acceptance-f8d5f7a470/`.
The local runner explicitly used the commented loopback administration settings
in the developer's properties to provision random restricted fixtures; the active
production datasource and its records were not used by tests.

No schema migration or provider contract change. Sequential provider append/ACK
latency remains unchanged; the previous export showed approximately five seconds
per complete context update. Physical camera grounding and sustained production
query consumption still require a new trial. Query reductions do not guarantee
the shared plan's allowance under every workload. No paid voice trial was run.

Deployment integration: merged `70f9a41` from main into agents, retaining the
application catalog, capability exclusions and embodiment support. Only the
context/results documentation required conflict resolution; both records were
preserved. All 568 Java cases in 126 fresh suites passed against disposable
local MySQL (`target/gptlive-acceptance-4b02bab01d/`), including the query budgets.
The runner removed the fixture schema/account. Before deployment, an external
health request timed out and a direct database connection failed; these checks
do not establish current production readiness or prove a new quota reset.

## Agents deployment integration - 2026-09-27

Merged main through `c699e5f` into agents at `103bab1`, retaining the application
catalog, scored RPS, embodiment selector and production configuration. Only the
twelve validated core/use-case conversational definitions declare Live support;
all other registered definitions default to false, including scored RPS and
Talk to Me. Existing saved profiles without the field remain opted out.

Live authored instructions and nonverbal plan instructions use the existing
Valerian/Gigi embodiment resolver. Quoted dialogue is preserved. Runtime merging
retains both per-agent prompt assembly and external speech ownership; reset
revokes ownership before restarting with the selected embodiment.

Actual verification:

- `python tests/gptlive/run_acceptance.py --java-tests all --browser` ran 561
  Java tests. One stale scoped-controller assertion still expected the hidden
  countdown sign to be emitted. Corrected that assertion, then added a robot
  runtime/reset regression case and ran
  `python tests/gptlive/run_acceptance.py --java-tests ScopedDemoControllerIntegrationTest,LiveSessionSmokeIntegrationTest --browser`
  successfully. The 125 fresh report files across these runs contain 562 tests,
  zero failures/errors/skips; this is combined full/focused coverage, not a claim
  that the first full invocation passed. Artifacts are respectively
  `target/gptlive-acceptance-70000b4814/` and
  `target/gptlive-acceptance-3052cad8dd/`.
- Catalog coverage uses the real Spring registry and checks definition, persisted
  profile and policy eligibility for every registered definition. Scoped MySQL
  smoke rejects a scored-RPS Live request before contacting the provider, reloads
  robot voice instructions and exercises ordinary generation, Live nonverbal
  generation and reset with the Gigi persona. Unit tests also cover both personas,
  deterministic RPS voice guidance and preservation of quoted names.
- All 61 Live/transcription/speech Node tests passed; log:
  `target/agents-live-node.log`.
- 43 feature-enabled browser cases and one feature-disabled text/TTS case passed.
  One Live-only case was intentionally skipped for the disabled app. Inspected
  fresh 1440px/390px unsupported-agent screenshots: readable explanation, disabled
  Start, existing modes retained. Provider/media responses are synthetic.
- Existing deployment prompt fixtures were aligned with selected persona names
  and combined-plan JSON. The scoped RPS countdown assertions now match existing
  behavior. Production definitions were not modified to accommodate these tests.
- Both acceptance invocations removed their disposable MySQL schemas/accounts;
  the successful runner stopped its owned apps. Browser disconnects produced
  socket-aborted log entries while closing SSE; assertions passed.

Heroku CLI authentication was unavailable, so production config, dyno topology,
database privileges and provider balance were not inspected. The deployment
workflow and global Live flag are unchanged. No paid provider, physical audio,
production schema-upgrade or production agent interaction trial was run here.
The earlier successful user-reported local voice trial remains separate evidence.

## GL-10 / project Milestone 193 ? 2026-09-27

Added the explicit, default-false `externalRealtimeSpeech` profile field.
Definitions declare compatibility individually; profile tags/shared factories
cannot grant it. Twelve main conversational definitions opt in; the exact-text
Talk to Me utility stays out. Full reachable state/policy validation rejects
partly supported definitions before provider creation. Older persisted JSON
without the field stays false, requiring deliberate new-instance creation for
Live. The instruction budget is 16,000 UTF-8 bytes to fit existing therapy prompts;
oversized instructions still fail without truncating task rules.

Verification completed:

- `python tests/gptlive/run_acceptance.py --java-tests all --browser`: 428 Java
  tests in 104 fresh suites passed on a disposable MySQL schema; 43 enabled
  browser cases and one disabled text/TTS case passed. One Live-only case was
  intentionally skipped in the disabled run. Owned apps stopped and the random
  schema/account were removed. Artifacts: `target/gptlive-acceptance-443a8eca5c/`.
- All 61 Live/transcription/speech Node unit tests passed. Log:
  `target/live-capability-node.log`.
- The five catalog tests were rerun successfully after making their expected
  opt-in list explicit, so future deployment catalog additions default to false.
- Desktop/mobile unsupported-agent screenshots inspected: disabled Start, clear
  explanation, existing modes available, no microphone capture or session request.
- Persistence/API smoke verifies the exposed true capability, reload of legacy
  JSON without the field, ineligible capabilities and direct POST rejection before
  any provider/inference call. Unit checks reject an unsupported future result
  state before transition and prevent a reused agent from inheriting opt-in from
  another definition.

No paid-provider or physical-device trial was run for this change. The user
previously reported a successful local Live trial after replacing the API key;
this is separate from the synthetic acceptance above. Gigi persona selection is
an agents-branch feature; adapting its Live instructions belongs to that merge.
No production schema or Heroku configuration was changed.

## Provider failure diagnosis follow-up — 2026-09-27

A local user trial reported the generic provider-unavailable message and
unconfirmed finalization after reportedly reaching Active. The original failure
was not retained with sufficient provider detail to establish its cause.
A read-only model-access check returned HTTP 200 for `gpt-live-1`. One subsequent
session-creation probe, using a Chromium-generated SDP offer, synthetic silent
audio and no agent history, returned HTTP 429 with `credit_balance_exhausted` /
`insufficient_quota`. No provider session was created. This establishes the
current billing blocker, not the exact cause of the earlier active-session loss.

The gateway now classifies known HTTP billing, rate-limit and access failures
without retaining provider messages. Scoped 502 responses carry only an
application-owned code for these cases; unknown failures keep the empty response.
The cockpit supplies corresponding guidance and still releases media without
automatic retries. Server warnings contain only category and HTTP status.

Verification: 11 focused Java gateway/controller tests, all 11 Live Node tests,
and one Playwright billing-error case covering desktop/mobile passed. The browser
case uses synthetic provider/media responses and verifies cleanup and redaction.
No database migration or persistence change was made. A successful paid voice
session and physical audio quality remain unverified until credits are available.

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
