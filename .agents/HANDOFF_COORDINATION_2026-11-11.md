# Coordination continuation handoff — 2026-11-11

## Current checkpoint

The owner requested committing and pushing all current work to GitHub so that a
new Codex session on another device can continue. Work is on `main`, based on
ES-07 commit `be3109e9`. This checkpoint includes the subsequent audit, delivery
repairs, verifier/proposal follow-up, tests and documentation together.

**ES-08 remains incomplete.** Passing unit tests and direct-provider checks do
not establish reliable end-to-end Live behaviour. Database capacity is explicitly
deferred by the owner while another deployment environment is considered. Do not
reopen the Heroku query-quota investigation as the next behavioural task. No
deployment or branch promotion is included in this checkpoint.

Start with `CODEX.md`, `CONTEXT.MD` and the opening/current-state section of
`../PROJECT.md`, stopping before the historical checklist. Then read
`PLAN_ESCALATION.md` and the November 11 section of `ESCALATION_RESULTS.md`.
The latter is the authoritative evidence record, including every failed attempt.

## Mental model and changes to preserve

The explicit backend state machine owns agreement terms, preparation, acceptance,
run identity and finite contributions. Native Live speech is a separate delivery
channel: response association and client-observed playback are distinct proofs.
No automatic replay is permitted when delivery is uncertain.

- `CoordinationUpdateAction`, `CoordinationTaskContract`, `CoordinationSupport`,
  `CoordinationAcceptance` and `CoordinationPerception`: narrow help/start/perception
  guards; truthful READY guidance; an explicit current user turn and remaining
  dialogue budget after history/perception in the interpreter request. Earlier
  native answers can precede their user-turn history entries; preserve that
  history, but do not answer an old question again during current feedback.
- `CoordinationPolicy` and `CoordinationSocialContract`: ready count proposals
  carry executable count conditions, the total action limit and acceptance terms
  in one bounded commentary when they fit. A count threshold is not proof that
  another person joined. Long proposals keep the existing full context path.
- `TaskResponse`, `LiveTaskResponseService` and `ExternalSpeechRecordingService`:
  verify the current committed contribution, with explicit communicative-act,
  polarity, exact-word and arming guidance. Trusted fragment receipt times permit
  a complete post-creation sentence suffix. Receipt comparison now mirrors ingress
  outer whitespace trimming; internal characters/timing remain strict. Queued
  candidate offsets survive reload. Keep owner/revision/deadline/completeness
  checks, the bounded unrelated allowance and conservative AMBIGUOUS handling.
- `LiveContextDelivery`, `LiveActivityContext`, `ScopedLiveSessionService` and the
  Live gateway: atomic short commentary and bounded cancellation of automatic
  delegation acknowledgments. Cancellation does not guarantee acoustic silence;
  independent preliminary native speech was still observed.
- `tests/gptlive/escalation_journeys.mjs` extends the real-app runner with recovery,
  dialogue and social journeys. It uses synthetic visitor audio/sensors, real
  OpenAI Live, disposable MySQL and the production playback observer. Natural
  invitation/count/feedback/return matchers were corrected after observed false
  failures. Manual semantic review remains required.

Neighbouring regressions include `TaskResponseUnitTest`,
`CoordinationDeliveryIntegrationTest`, `CoordinationTaskContractUnitTest`,
`CoordinationSocialContractUnitTest`, `LiveContextDeliveryUnitTest`,
`ScopedLiveSessionServiceUnitTest` and `tests/js/live/escalation-fixture.test.mjs`.
Shared Generic/Social Space behaviour must continue working.

## Remaining failures and a useful next scope

1. **Assemble a complete spoken contribution before checking it, within bounded
   authority and time.** In the last dialogue attempt (`ad68229140`), the backend
   required: "Correct—a piano has keys, but they cannot open a door." Native Live
   produced two COMPLETE/observed-silence segments:
   - "That's it, a piano!" at provider offsets 88000–88800 ms;
   - "Correct—a piano has keys you press, not ones that open a door." at
     90200–93200 ms.
   The checker received only the first segment and returned AMBIGUOUS before the
   fuller explanation was associated. Final delivery remained UNCERTAIN at two
   contributions. This is incomplete candidate coverage, not evidence that the
   checker rejected the entire completed answer. Start with a deterministic
   two-segment regression and inspect `LiveTranscriptSegmenter`, ingress, receipt
   timing, response checking and playback association. Any assembly design needs
   explicit bounds and interruption/stop/revision/session fences. Do not simply
   accept AMBIGUOUS, increase every timeout or replay the task automatically.
2. **Prevent misleading preliminary proposal speech.** A social run first
   promised a welcome "when someone joins", then correctly stated more than one
   visible person and one action total. Saved terms were correct; the spoken
   negotiation still confused the supported alternative. A prompt-only wait
   instruction and acknowledgment cancellation have not eliminated this.
3. **Finish integrated acceptance after a causal correction.** The final arming
   refinement passed direct-provider cases but has no subsequent social Live
   trial. Full dialogue replacement and social cue/restart/pause/resume/stop were
   not reached in the latest batch. Run bounded journeys with transcript review;
   retain failures and avoid rerunning unchanged code until a lucky pass.

There is positive Live evidence: `13fcb54dce` completed one riddle question and
correct final feedback without repeating the older scissor answer or appending an
invitation. It reached PLAYBACK_CONFIRMED at two contributions. Its fixture then
rejected "it fits perfectly"; that matcher is fixed. Another recovery run reached
confirmed welcome playback and history-preserving return. Neither establishes a
whole accepted three-journey result. Physical microphone/speaker/camera trials
remain NOT RUN.

## Verification already completed

The final production/test tree before this documentation/checkpoint pass passed:

- Full Java suite on disposable local MySQL: **871 passed**, zero failures/errors,
  one opt-in capacity test skipped (872 reported cases, 172 suites).
- **187 Node tests**, **11 runner preflight checks**, Maven packaging and diff checks.
- Final labelled verifier evaluation: **36/36 checks** from 18 synthetic cases
  repeated twice; 34 real API calls and two exact-word fast paths. Includes correct
  question/feedback paraphrases, contradictory/partial feedback, conditional
  arming and premature action. This small fixture is not a general accuracy claim.
- Ten latest real Live attempts: no complete journey passed. Full breakdown,
  model routes, usage, bounds and causal fixes are in `ESCALATION_RESULTS.md`.
- Cleanup checked 33 owned runs from this investigation: no schemas/accounts or
  provider fixture JVMs remained. The configured application schema was never a
  test target. No production database was used for these tests.

## Running on another device

Git contains the portable runner, probes, synthetic fixtures and written evidence.
**Ignored local configuration, `target/` raw logs/reports, cached speech and helper
scripts do not transfer through this commit.** In particular, historical commands
using `target/run_es08.py`, `run_response_probe.py` or `es09_regression.py` refer to
machine-local adapters, not required project tools. Do not assume those files or
their evidence artifacts exist in the new checkout; the source fixes and the key
failure details above are portable.

Use a local MySQL administrative configuration, private provider credentials and
the dependencies documented in `../tests/gptlive/README.md` (including Python
`pymysql[rsa]`, Node dependencies and Chromium for browser journeys). The runner
accepts loopback MySQL only and creates/removes its own random schema/account.
If the new device's ignored `application.properties` contains suitable local
administrative credentials, the portable equivalents are:

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application.properties --java-tests all
python tests/gptlive/test_acceptance_runner.py
$nodeTests = @(rg --files tests/js -g '*.test.mjs')
node --test @nodeTests
.\mvnw.cmd -q -DskipTests package
```

For a focused starting regression, replace `all` with
`TaskResponseUnitTest,CoordinationDeliveryIntegrationTest,LiveTranscriptSegmenterUnitTest`.
Inspect current test names before selecting a narrower subset.

The paid runner requires `OPENAI_API_KEY` (or private `openai.key`) and a valid
`OPENAI_MODEL`/`openai.model`; inspect purpose-specific route/effort settings too.
The retained runs used `gpt-5.6-luna`/effort `none` and Live `gpt-live-1`/`willow`.
Choose one scenario after its proposed fix and offline regression are ready:

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application.properties --java-tests none --paid-core coordination --coordination-scenario escalation-dialogue
```

Other names are `escalation-recovery` and `escalation-social`. The explicit
test-only `fixtures.live.TaskResponseProviderProbe` and its compilation/private-key
setup are documented in the same README. Keep its spending bounds and raw reports;
never copy credentials or private student transcripts into fixtures or Git.
