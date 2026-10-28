# Coordination Agent implementation and evidence

Date: 2026-10-27. Work starts on `main` at `56a3f05`; no commit, integration or
deployment is part of this milestone.

## Scope and implementation

The owner requested a new `CoordinationAgent.java`, eventually replacing both
Generic variants, with their persona, knowledge, humour and perception behaviour
retained. The new definition is `core.coordination`. Existing definitions, saved
graphs and history remain untouched.

The agent has explicit conversation, clarification, preparation, ready, execution,
paused and reflection states. A bounded validated agreement stores the goal,
human/agent roles, success criterion, outstanding questions, optional recognition
checks and either dialogue execution or the existing `TaskSpec`. Model output is
validated before mutation. A replacement suspends previous execution; acceptance
starts the unchanged complete proposal only after required evidence. Discarding
a replacement preserves an unfinished earlier agreement for explicit resume;
completed agreements cannot be revived this way. Accepted preparation evidence
stays with its accepted agreement while a replacement is being discussed.

Preparation reuses `TaskDecision`'s field/confidence/freshness semantics. It accepts
only observations newer than the current proposal/encounter and records each
required check once. Progress is persisted without inference. The last check
invites acceptance and never executes a task action. These are recognition
demonstrations; they do not certify current sensor availability or a person's
identity. Revisions and new Live conversations invalidate pending preparation.

Sensor execution reuses the existing task guards, behaviour validation, cooldown,
response capture, session fencing and failure recovery. Dialogue tasks have their
own bounded contributions without a fabricated sensor rule. Cancellation, action
limits, completion cues and user-reported completion are recorded separately.
Reflection retains a bounded operation log and a labelled user report alongside
durable event history. Model interpretation and subjective success are not
deterministically proven by these records.

The same scoped APIs, behaviour plans, monitor stream and cockpit are used.
The activity footer distinguishes clarification/preparation from ready acceptance.
The new `CoordinationDecision` adds nullable `decision.coordination_condition`
(`varchar(255)`); other new entity types use inherited mappings. Deploy the current
code and additive schema before creating instances. No automatic migration or
replacement of saved instances occurs.

## Adopted behaviour and limits

- Reuses the full Social Space voice, `ValerianPersona`, `CoreAgentReference` and
  shared humour guidance. SIRA/IWI, Alexandre, Marc, Jasmin and Theresa remain in
  both ordinary and Live policy context, as does the fictional Laureline layer.
- Keeps the full Generic observation/speech/nonverbal/hand-pose profile, capability
  awareness, name-addressed conversation and concise spoken style.
- Reuses stable arrival/departure admission, quiet time, cooldown, source expiry,
  task priority and interruptible Live remarks. Other observations inform direct
  questions and agreed task cues. Generic/Social Space did not automatically
  comment on every facial/weather/hand reading; the new agent preserves that
  distinction instead of enabling unsolicited reactions to every modality.
- Supports the existing optional presence-controlled reception through the new
  `prometheus.core.coordination.reception` setting (default false).
- No scored RPS/guessing activity, arbitrary sequence generation, automatic device
  settings, weather fetching, person identification, locomotion or physical
  completion feedback is added. These require separately scoped executors and
  evidence contracts. Preparation currently supports at most three independent
  observation demonstrations; full prerequisite/arbitration languages remain open.
- Agreement JSON is limited to 3,000 UTF-8 bytes. Dialogue contributions and sensor
  actions are bounded; natural-language intent, requested content and character
  delivery still need real-provider/operator acceptance.

## Verification

Tests use controlled model output; no paid provider or physical-device trial has
been run.

- Full Java regression: **673 tests, zero failures/errors**, using the existing
  `tests/gptlive/run_acceptance.py --java-tests all` runner against an owned local
  MySQL instance, a random schema and a restricted test account. Maven used a
  temporary copy of `pom.xml` whose only change was the output directory
  `target/coordination-regression`, avoiding stale classes from another branch.
  Log: `target/gptlive-acceptance-ddcdd22691/java.log`.
- Final focused rerun: **58 tests, zero failures/errors/skips**, after the
  completed-task discard fence, accepted evidence retention, matched-rule
  explanation, sensor role/constraint projection and UTF-8 boundary check.
  Selection: `CoordinationAgentUnitTest`, `CoordinationAgentIntegrationTest`,
  `AgentDefinitionRegistryUnitTest`, `CoreTaskVoiceUnitTest`,
  `GenericMultimodalTaskUnitTest`, `CoreReceptionUnitTest`,
  `GenericMultimodalTaskIntegrationTest`, `CoreTaskVoicePersistenceIntegrationTest`,
  `SocialSpaceAgentIntegrationTest`.
  Log: `target/gptlive-acceptance-d4b9e1e7fb/java.log`.
- `node --test tests/js/performance/activity.test.mjs`: **8 passed**.
- `.\mvnw.cmd --file .coordination-regression.pom.xml -q -DskipTests package`:
  passed after the final focused rerun. The temporary root POM was removed after
  verification; its exact copy is retained in
  `target/coordination-regression/build-pom.xml` with the build artifacts.
- `git diff --check`: passed. Temporary database schemas/accounts and the owned
  MySQL process were removed/stopped. The existing application database and
  compiled output directories were not modified by cleanup.

The new tests cover partial dialogue agreements, inability to start incomplete
preparation, stale/future/low-confidence/duplicate observations, invalid output
atomicity, pause/resume/completion, discarded revisions, budget exhaustion,
reset, shared knowledge/persona in every state, stable social comments, completed
Live invitation checks, response capture and harmless acknowledgements, Text-to-
Live session binding, explicit autonomous sensor tasks, context byte limits,
scoped creation and persistence/reload. Dialogue and preparation use the real
application/persistence boundary in the integration tests; provider output is mocked.

The initial compile exposed an incorrect EventHistory method name, and the first
test compile exposed a package-private timestamp setter in a fixture; both were
corrected before the passing unit run. The configured application database was
rejected by the local-only acceptance runner. Template local credentials also
failed authentication. Persistence checks therefore use an independently owned
temporary MySQL instance with disposable schemas and restricted accounts; the
configured application database is never a test target.

The first persistence run failed one new session-close assertion because the
fixture wrote to `Agent.getStorage()`'s copied map. It now writes the policy's
persisted Storage and verifies the saved association before session close; the
corrected integration checks pass. Review also found and corrected pending Live
response replacement by polite acknowledgements and accidental revival of a
completed agreement after discarding a replacement. Dedicated assertions cover
both. Automatic review rejected recursive compiled-directory cleanup; isolated
Maven output supplied fresh classes without deleting those directories.

## Remaining acceptance and rollout

No real-provider, classroom, physical audio/camera/robot, production-load or
deployment acceptance is claimed. Natural-language interpretation, conversational
quality, character delivery, sensor acquisition and actual rendered/audible output
remain operator checks. Preparation is deliberately bounded; arbitrary workflows
and the dedicated game executors are not part of this version. The existing
Generic variants remain available until comparative acceptance supports retirement.

To repeat the focused integration checks with normal build output, supply a
local administration properties file to the existing runner, for example:

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests CoordinationAgentUnitTest,CoordinationAgentIntegrationTest,CoreTaskVoiceUnitTest,GenericMultimodalTaskIntegrationTest
node --test tests/js/performance/activity.test.mjs
```

The runner creates its own random schema/account; the configured schema must
never be used directly by the integration tests.

## Release integration (2026-10-28)

The owner authorized committing and pushing main, then merging main into agents
and pushing agents to trigger the existing Heroku deployment workflow.
Coordination implementation is committed as `293c987`. Merge `585d399` also retains
upstream main's Live playback-readiness fix `3454fcb`; the only merge conflict was
the current-status list in PROJECT, resolved by retaining both entries.

Main release verification used fresh Maven output in
`target/coordination-release-main`, the existing acceptance runner and the owned
loopback MySQL instance. All **71 Java tests passed, with no failures/errors/skips**:
the previous nine-class focused selection plus
`LiveTranscriptCaptureServiceUnitTest` and `LiveAgentContextRefreshUnitTest`.
Log: `target/gptlive-acceptance-9e10643aae/java.log`. The disposable schema was
removed by the runner. All **8 activity-footer Node tests passed** and packaging
with the isolated build POM passed. No paid provider or physical-device trial
was run. Deployment-branch integration and release checks are recorded separately
after the merge; these main checks do not establish production acceptance.

## CR-01: refinement baseline and harness (2026-10-28)

Consecutive CR-01 through CR-09 implementation and per-milestone publication are
authorized by the owner. Work uses feature/gptlive based on current main; final
integration follows main -> agents. The refinement plan is
`PLAN_COORDINATION_REFINEMENT.md`.

Added bounded Coordination Live/Text selections to the existing disposable-local-
database runner, a synthetic regression manifest and three characterization tests.
Those tests reproduce terminal outcome replacement, sensor-contract loss on a
style revision, and Reflection before final Live capture. They document defects,
not passing acceptance of the intended behavior. The manifest assigns the other
observed contradictions to their acceptance milestones; no production transcript
or identifiers are included. No application behavior or schema changed.

Verification: runner unittest discovery passed **5 tests**. The disposable MySQL
selection CoordinationAgentUnitTest, CoordinationAgentIntegrationTest and
CoordinationRefinementBaselineUnitTest passed **17 tests**, no failures/errors/skips
(`target/gptlive-acceptance-8de3943141/java.log`). The paid selection
`--java-tests none --paid-core coordination` passed its real Live identity/lab and
current-perception conversation (`target/gptlive-acceptance-a85cc3c6a3/journey.json`).
The runner stopped both owned applications and removed their schemas/accounts.
Provider journeys have a 420-second process deadline; the Text journey additionally
bounds itself to 300 seconds and 12 turns. Future scenario names are reserved and
fail explicitly until implemented. This baseline makes no physical-device claim.
