# Coordination escalation and classroom reliability evidence

## Verifier, proposal and integrated follow-up (2026-11-11)

**ES-08 is not yet accepted. Database capacity is deferred by the owner.** This
pass evaluates and repairs behaviour on the existing architecture, using the local
MySQL installation and bounded real OpenAI calls. The owner's configuration and
earlier work are preserved. Verification initially ended with changes uncommitted;
the subsequent publication request is recorded below. No deployment is included.

**Checkpoint update:** the owner subsequently requested committing and pushing
all current work for another device. `HANDOFF_COORDINATION_2026-11-11.md` records
the portable continuation instructions. This publication does not change the
acceptance results or authorize deployment; ignored raw artifacts remain local.

Changes and causal evidence:

- The shared `TaskResponse.Check` prompt now distinguishes the requested act
  (question, answer evaluation, welcome or arming), equivalent wording and exact
  words. It explicitly checks polarity, quantity, conditions and complete delivery.
  It does not answer a requested riddle itself or accept the solution name alone
  as feedback. Identity parsing, deadlines, unrelated allowance, conservative
  AMBIGUOUS handling and separate playback confirmation are unchanged.
- New test-only `TaskResponseProviderProbe` initially used 16 labelled synthetic
  cases, including a second question paraphrase and negative controls. The baseline scored
  **14/16**: it incorrectly accepted opposite feedback and classified an exact-word
  substitution as UNRELATED instead of AMBIGUOUS. The previously rejected riddle
  paraphrase passed this baseline, showing variability rather than a consistently
  reproducible false negative. The revised prompt scored **32/32 across two passes**
  (30 provider calls, two exact-word fast paths). All 45 actual API outputs across
  baseline/revised runs had valid schema and response identities. Malformed output
  cannot score as a correct negative in the collector. This small fixture is
  evidence of improvement, not a general accuracy estimate.
- A subsequent social run rejected valid activation with a conditional future
  action: "starting now; I'll give one brief welcome on a fresh next cue". ARMING
  guidance now distinguishes that confirmation from actually welcoming early or
  merely promising to activate later. Two labelled cases cover the conditional
  confirmation and premature execution. The final **18-case fixture passed 36/36**
  across two passes, using 34 provider calls plus two word-equality fast paths;
  all outputs passed schema/identity validation. This is still a small evaluation,
  and the social journey has not been rerun after this last prompt clarification.
- Ready Live count proposals place the executable numeric condition, distinction
  from arrival/identity, total action bound and acceptance requirement in one
  bounded commentary request. Long proposals retain the prior complete context
  path. No draft is started or counted by this review. Three count-operator cases
  failed before the change (`target/es09-proposal-red.log`) and pass afterwards.
  An initial imperative payload still produced ambiguous wording in Live; the
  final payload states the saved facts declaratively.
- A real dialogue run generated final feedback that reanswered the older scissor
  question and asked for another exchange. The interpreter now receives an
  explicit current user turn and remaining dialogue budget after history and
  perception, without deleting or reordering durable history. A regression
  reproduces assistant speech arriving before the earlier question was committed
  and verifies the actual inference request identifies only the current turn.
- A later run exposed a separate deterministic capture defect: transcript ingress
  trims outer whitespace, while receipt-aware association required character-for-character
  untrimmed text equality. In `072a45c0be`, the first riddle fragment was received
  at **05:44:14.785Z**, after response creation at **05:44:13.370565200Z**; a leading
  space, not an early-speech timestamp, prevented the verifier call. Association
  now permits precisely ingress's outer trim and translates offsets back to the
  persisted text. Internal text, word timing and complete-sentence boundaries stay
  strict. Two regressions failed before repair (`target/es09-trim-red.log`); all
  18 response unit tests pass afterwards. The local MySQL delivery case also checks
  trimmed fragments, preserved history and separate scoped playback confirmation.
- Fixture false negatives were repaired with Node regressions: return wording
  may say "continue chatting", and a count alternative may say "you're already in
  view and a second person is visible". Arrival-only wording still fails the count
  check. Invitations also accept "Ready to start when you say go", and successful
  feedback may say "it fits perfectly". The latter was already captured and
  PLAYBACK_CONFIRMED before the old matcher failed. These repairs do not change
  old run results or replace semantic review.

Direct-provider artifacts: `target/response-verifier-baseline.json`,
`target/response-verifier-revised.json`, `target/response-verifier-format-audit.json`.
Configured route: `gpt-5.6-luna`, effort `none`. Baseline used 15 calls,
7,233 prompt/783 completion tokens; revised used 30 calls, 15,930 prompt/1,576
completion tokens. Probe limits: 32 calls, 512 completion tokens/call, 12,000 input
bytes/call, 30 seconds/call, four minutes overall and 30,000 reported tokens before
another call. No retries, database operations or voice sessions in this evaluation.
The final expanded run is `target/response-verifier-final.json`: 34 calls,
18,500 prompt/1,796 completion tokens. Its request ceiling is 36 for the expanded
fixture; all other limits are unchanged. Across all three evaluations: 79 actual
provider calls, 41,663 prompt/4,155 completion tokens. Historical raw reports remain
unchanged.

Integrated Live evidence (all artifacts are under `target/gptlive-acceptance-<id>`):

| Artifact / scenario | Observed result; why the run stopped |
| --- | --- |
| `5ca8fd3144` dialogue | Question matched; help/perception preserved budget. Final backend intent repeated the older scissor answer and invited another exchange; final delivery uncertain. Motivated current-turn context. |
| `eb905f6d12` recovery | Preparation, help, perception, acceptance, one workshop welcome and browser playback succeeded; return preserved history. Fixture rejected "continue chatting". Preliminary remarks remain a quality concern. |
| `ebfbaf29d3` social | Saved count was correct; spoken proposal mixed "someone joins" with count and "once per occasion". Invitation timeout; no activation. Motivated declarative proposal facts. |
| `072a45c0be` dialogue | Post-creation speech missed association because of outer whitespace, then expired. Question also added an extra clue; feedback not reached. Motivated capture normalization. |
| `be7127e92a` social | Supported two-person paraphrase and one-action total spoken, but fixture required particular numeric words. No activation. |
| `8d9311ea1a` recovery | Preparation, truthful READY help, fresh perception and arming passed. Native cue response was only "Welcome."; checker returned AMBIGUOUS against a workshop welcome, final delivery uncertain. |
| `774a33c723` dialogue | Valid proposal and "Ready to start when you say go" invitation; fixture rejected that phrasing. No activation. |
| `933b1b96bd` social | Count and one-action limit spoken in the final proposal, preceded by unsupported joining language. Valid conditional arming was rejected by the verifier, pausing at zero actions. Motivated the final arming clarification/evaluation. |
| `13fcb54dce` dialogue | One question, help/perception and correct final feedback succeeded, with two contributions and PLAYBACK_CONFIRMED. No repeated old answer or extra invitation. Fixture rejected "it fits perfectly"; replacement not reached. |
| `ad68229140` dialogue | Question/help/perception passed and backend feedback intent was clean. Native feedback split into "That's it, a piano!" and the fuller explanation. Checker saw only the first segment and returned AMBIGUOUS; delivery uncertain at two contributions. Replacement not reached. |

**No whole journey passed in this batch.** The successful riddle round and recovery
flow are useful partial evidence, not a completed three-journey acceptance result.
Every attempt retains the raw `journey.json`, `provider.jsonl`, logs and a separate
`semantic-review.json`. Failed raw reports were not relabelled after fixture repairs.
Subsequent runs followed a specific code or fixture correction; no automatic
retry-until-pass loop was used. The final dialogue run uses the final verifier,
proposal and capture code. The final arming clarification has direct-provider
evidence but no subsequent social Live run.

All ten attempts together used **32 backend calls, 116,232 prompt/4,108 completion
tokens, 835 reported Live seconds and zero new synthesis calls** (cached synthetic
visitor audio). Raw usage and per-run totals: `target/es09-paid-summary.json`.
Each run retained the existing 12-turn/24-request/2,048-completion-token/80,000-input-byte
bounds, 200,000 reported-token threshold, one 360-second Live session, at most
12 synthesis clips/6,000 characters and 420-second child deadline. Routes remained
`gpt-5.6-luna`/effort `none` and `gpt-live-1`/`willow`. Reported usage is not a billing
estimate. All ten sessions requested closure and their owned schemas/accounts
were removed by the runner.

Remaining behavioural work is concrete: native preliminary speech can misstate
the reviewed condition; a complete-looking first feedback segment can be checked
before the later explanation arrives. The latter needs a bounded speech-assembly
decision within the original authority/deadline/playback contract, not treating
AMBIGUOUS as success or automatically replaying the task. Complete replacement and
social restart/pause/resume coverage remain unestablished in paid Live. Physical
microphone/speaker/camera and classroom trials were not run. Capacity is deferred;
none of these behaviour failures is attributed to Heroku's database quota.

Final regression verification:

- Full Java suite through `python target/run_es08.py --java-tests all`: **871
  passed, zero failures/errors, one skipped** (the explicitly opt-in
  `ClassroomCapacityIntegrationTest`, deferred by the owner). Artifact
  `target/gptlive-acceptance-719bfbff73` contains the log, copied JUnit XML for all
  172 suites and `test-summary.json`. Includes shared Generic/Social Space,
  transcript persistence/ownership, Coordination and playback coverage using a
  disposable restricted local MySQL schema/account and mocked inference.
- Before broad validation, 46 focused response/current-turn/count/persistence
  tests passed in `target/gptlive-acceptance-cc89d3148a`. Earlier local persistence
  selection `0f20208390` and unit logs are retained; their counts are not added to
  the full-suite total.
- All Node tests: **187 passed**, `target/es09-node.log`; runner preflight:
  **11 passed**, `target/es09-preflight.log`. Package and `git diff --check` passed
  (`target/es09-package.log`, `target/es09-diff-check.log`).
- Final direct-provider check: **36/36 labelled checks passed**, including two
  exact-word fast paths, as detailed above. These are separate from the Java suite.
- `target/es08-cleanup.json` checks all **33 owned runs** from this investigation
  and its earlier passes: zero schemas/accounts remain. No provider fixture JVM
  remains. The configured application schema was never a test target. Credentials
  were supplied privately, never added to evidence or source.

The implementation and bounded verification pass is ready for continuation under
the owner's checkpoint publication request above. The ES-08 acceptance milestone
remains incomplete; no deployment is included.

## Focused speech-delivery follow-up (2026-11-10)

**Implementation verified locally; ES-08 acceptance is still unmet.** This pass
addresses the sequencing/capture defects identified below. It does not change the
task state machine, budget, database schema or client playback contract. Existing
uncommitted audit fixes and the owner's local configuration are preserved.

- Short pending Coordination task replies carry their confirmed content and
  speaking instruction in **one** commentary append. The reply no longer arrives
  first as quiet facts followed by a separate speaking trigger. Response identity,
  expiry and no-retry semantics remain. Proposal/help/control context, long replies,
  and other agents retain their existing delivery path. The duplicate-publication
  regression failed on the prior implementation (`target/es08-delivery-red.log`).
  A maximum UTF-8-size test caught the generic chunker splitting this supposedly
  atomic append; the direct bounded append now passes.
- Policies requesting silent waiting now request the provider's 800 ms automatic
  delegation acknowledgment delay. A bounded worker sends an empty thinking update
  with the same delegation ID without waiting for backend context. It deduplicates
  IDs, checks ownership, requires the existing command acknowledgment and never
  blocks the socket receiver awaiting that acknowledgment. This cancels pending
  automatic acknowledgments where timely; it does not prevent all independent
  model speech. The live probes accepted this setting without provider errors and
  still produced preliminary remarks. [Provider contract](https://developers.openai.com/api/docs/guides/live-delegation).
- Native response association now receives the existing trusted transcript-part
  receipt times. A complete segment spanning the response's creation boundary can
  contribute only its later complete sentence suffix. Early words, a mid-sentence
  tail, mismatched receipt text and future timestamps cannot establish delivery.
  All later sentences stay together for checking. Full native history is unchanged;
  the bounded queued candidate persists an offset, not a second copy of speech.
  Owner/revision/identity, original deadline, one unrelated allowance and conservative
  AMBIGUOUS handling remain. A local MySQL regression verifies that the same original
  segment must still receive a separate client playback acknowledgment.

Verification:

- `python target/run_es08.py --java-tests "Coordination*IntegrationTest,GenericTask*IntegrationTest,TaskResponseUnitTest,Coordination*UnitTest,LiveContext*UnitTest,LiveActivityContextUnitTest,LiveSessionGatewayUnitTest,ScopedLiveSessionServiceUnitTest,LiveTranscript*UnitTest,CoreTask*UnitTest"`:
  **266 passed**, including 21 Coordination persistence cases. Artifact
  `target/gptlive-acceptance-ceb4115d5a` includes copied JUnit XML and a test summary.
  Earlier local attempt `571015b385` retains the maximum-size chunking failure.
  The `GenericTask*IntegrationTest` selector matches no current class; explicit
  shared persistence/Generic/Social Space coverage is recorded separately below.
- `python target/run_es08.py --java-tests "CoreTaskVoicePersistenceIntegrationTest,LiveSpeechPersistenceIntegrationTest,LiveTranscriptIngressIntegrationTest,LiveTranscriptClaimIntegrationTest,LiveReceiptDurabilityIntegrationTest,SocialSpaceLiveJourneySmokeIntegrationTest,GenericMultimodalTaskUnitTest,SocialSpaceAgentUnitTest"`:
  **42 passed**, including real MySQL persistence, receipt/claim durability and
  the scripted Social Space journey. Artifact `target/gptlive-acceptance-aefae00287`
  includes copied JUnit XML and a summary. Together these selections cover
  **308 distinct passing Java cases**; the full Java suite was not rerun in this follow-up.
- All Node tests: **184 passed**, `target/es08-delivery-node.log`. The additional
  acceptance-pacing regression accepts “We can begin when you say so” while still
  rejecting preliminary checking/proposal speech and “ready” alone.
- Runner preflight: **11 passed**, `target/es08-delivery-preflight-fixed.log`.
  The first invocation failed to import the locally isolated `pymysql`; reran
  with `PYTHONPATH=target/es08-python`, without changing the runner or installing
  global dependencies. `mvnw.cmd -q -DskipTests package` passed
  (`target/es08-delivery-package.log`); `git diff --check` passed.

Paid probes used the same enforced budgets and configured models documented in
the initial audit. There were **three attempts**, not three passes:

| Artifact (`target/gptlive-acceptance-…`) | Actual result | Requests / prompt + completion tokens / reported Live seconds |
| --- | --- | --- |
| `c6d7d41f92` social | FAIL: saved `humanCount > 1`, but native speech described someone new appearing and omitted the numeric condition. Stopped before acceptance; arming/cue/control sequence NOT REACHED. | 1 / 5,182 + 290 / 28 |
| `17c1ec2863` dialogue | Fixture failure: a valid “We can begin when you say so” invitation was rejected by the old waiter. No activation. Fixed the matcher, added the Node regression, then made one bounded follow-up attempt. Preliminary native speech remains. | 1 / 5,038 + 206 / 58 |
| `dc3083c0da` dialogue | FAIL: one complete question, no answer leak or duplicate observed. The verifier returned AMBIGUOUS for “What has many keys but can’t open a door?” against “What object has keys that cannot open a door?” Backend safely paused with one contribution. Help/perception/final feedback/replacement NOT REACHED. | 3 / 10,906 + 369 / 58 |

Total additional usage: **5 backend requests, 21,126 prompt + 865 completion
tokens, 144 reported Live seconds, zero new synthesis calls** (cached audio).
Raw failures remain unchanged; separate semantic reviews record the judgments.
This is evidence of a response-verifier false negative, not justification for
treating AMBIGUOUS as success or skipping playback. The earlier split-feedback
failure has not yet been cleared by an end-to-end Live pass. Automatic delegation
acknowledgment suppression does not resolve the independent spoken-proposal failure.
No physical trial, production deployment, or capacity optimization was performed.
All three additional Live sessions requested closure; all owned app processes
stopped. A read-only cleanup audit covering 20 known test runs found **zero**
remaining owned schemas/accounts (`target/es08-cleanup.json`). The configured
application schema was not a test target. Changes remain uncommitted for review.

The next evidence gap is specific: evaluate question/feedback paraphrases versus
genuinely partial/unrelated responses in the existing verifier, and make supported
proposal terms survive native delivery. Do not weaken the conservative verdict or
playback gates to force a green journey. Only then repeat the complete three-journey
ES-08 acceptance matrix; classroom capacity and physical acceptance remain separate.

## ES-08 integrated review on local MySQL (2026-11-10)

**Acceptance remains unmet.** Re-read the complete escalation roadmap against the
seven commits ending at `be3109e9` and the prior text-probe review. The original
goal was recovery throughout the existing lifecycle, truthful supported-task
negotiation, client outage handling and measured classroom capacity, followed by
real Live acceptance. The earlier in-memory provider checks covered only part of
that goal. The committed architecture does not need wholesale replacement, but
passing deterministic tests is insufficient evidence for native speech quality.

| Milestone | Integrated review finding |
| --- | --- |
| ES-01 diagnostics | Bounded, content-free rejected-update facts and synthetic fixtures remain appropriate; they cannot reconstruct an unknown historical provider failure or the outage onset. |
| ES-02 recovery | Explanation preserves authority, run, preparation and budget. Real Live exposed incorrect repeat-preparation advice despite correct READY facts; guidance was tightened. Native waiting remarks remain. |
| ES-03 acceptance | Prepared natural acceptance works. A provider-selected combined propose/start could bypass an explicit instruction not to start; added a guard and a persisted rejection regression. |
| ES-04 perception | Fresh questions work across task states. Questions about the agreed trigger were wrongly intercepted by current-sign recognition; they now use task interpretation. |
| ES-05 task contracts | Question/feedback budgets and explicit replacement are represented by the existing dialogue form. Actual Live still duplicates/splits speech and can leave final delivery uncertain; this is not accepted as success. |
| ES-06 social semantics | Additional-person requests must become an explicitly reviewed count alternative, not `arrival`. Existing detector semantics and guards are retained; the prior text-review fixes improve compilation/disclosure. |
| ES-07 service/capacity | Read-only client recovery and late-response fences pass local checks. The six-client capacity result was reproduced and remains far above the database allowance. |

Additional implementation and fixture corrections:

- Reproduced two failures before changing production code: combined
  `PROPOSE_AND_START` on "do not start", and a question about the agreed hand
  trigger answered with the current scissors observation. The positive
  "Start now: when rock ..." case remains supported. Language guards are narrow
  heuristics, not a universal intent parser. Baseline: `target/es08-audit-baseline.log`.
- READY facts and Live guidance now distinguish persisted preparation evidence
  from expiring current sensor samples and the fresh cue needed after acceptance.
  A real Live baseline had requested rock again while the backend remained READY.
- Dialogue response association now freezes the current committed contribution,
  following the existing sensor-response pattern. The checker no longer receives
  the entire agreement and conflicting-looking FINISHING lifecycle text as its
  content reference. Identity, deadline, semantic matching and separate playback
  requirements remain intact. A regression failed before this change; 40 focused
  voice/contract/delivery/response tests passed afterwards. This reduces irrelevant
  context; it has **not** established reliable final dialogue delivery with Live.
- Final-feedback interpretation guidance prohibits appending another invitation
  after the budget is exhausted. The native policy retains the general silent-wait
  instruction and extends it to support explanations. Prompt guidance is not an
  acoustic gate and is not claimed to eliminate the retained failures.
- Fourteen broad Node failures came from two VM sensor fixtures omitting the
  production `captureConnection`/`currentConnection` functions. Updated those
  fixture boundaries; the 129 Live/performance cases then passed without changing
  the client implementation (`target/es08-node.log`, `target/es08-node-fixed.log`).

The existing acceptance runner now has `escalation-recovery`,
`escalation-dialogue` and `escalation-social`. They use real HTTP/runtime/JPA/MySQL,
real Live transport, synthetic visitor audio/sensors and the production received
audio playback observer. No manual playback acknowledgement substitutes for that
observer. The harness waits for durable visitor admission and a real proposal
invitation, distinguishes those from preliminary speech, and retains failed
turns. Duplicate riddle wording and an appended feedback invitation are explicit
checks. Terminal delivery uncertainty fails the run instead of spending the
remaining time waiting for an impossible completion.

Each paid invocation declares 12 visitor turns, 24 backend requests, 2,048 maximum
completion tokens/request, 80,000 UTF-8 input bytes/request, a 200,000 reported-token
ceiling before further inference, one Live session/360 seconds, 12 synthesis
clips/6,000 characters, and a 420-second child-process deadline. Request, input,
token and session limits are enforced in the test-only provider wrapper. Three
budget tests passed; a standalone Spring Binder check confirmed the configured
completion and session limits bind to 2,048 and 360. The final request can cross
the reported-token threshold; this is not a monetary cap. Synthesis is cached.

Local database credentials were read privately from the owner's ignored
`application.properties` as explicitly requested, passed as local administrator
environment settings, and used only to create random restricted test accounts and
schemas. The configured `prometheus` schema was never the test target. The ignored
`target/run_es08.py` adapter supplies this machine's settings to the portable
runner; `pymysql[rsa]` was installed under ignored `target/es08-python`. The
configured inference routes were `gpt-5.6-luna`, effort `none`; Live used
`gpt-live-1`/`willow`, synthetic input used `gpt-4o-mini-tts`/`alloy`. Provider
credentials were supplied privately; no production database was queried.

Deterministic/local evidence already completed:

- Focused local runner selection
  `Coordination*UnitTest,CoordinationAgentIntegrationTest,CoordinationDeliveryIntegrationTest,QueryVolumeIntegrationTest,SocialSituationChangeDetectorUnitTest`:
  **160 cases passed** before the additional voice/reference and persisted
  negative-start regressions. Artifacts `target/gptlive-acceptance-d86ecaff87`;
  earlier integrated pass `target/gptlive-acceptance-2be6976474`.
- **11 runner preflight tests passed**, including rejection of ES-08 with the text
  transport before database provisioning. No paid call is made by these tests.
- **10 focused static Playwright cases passed**, covering unavailable service,
  late replies, login, Coordination recovery, perception, dialogue limits and
  social condition. Desktop/narrow outage and recovery screenshots and the count
  condition screenshot were visually inspected; no clipping/overlap found.
  Artifacts/log: `target/es08-browser-static`, `target/es08-browser-static.log`.
- **Two real-app/local-DB browser cases passed** with scripted provider media:
  actual observer-based Reflection and scoped cockpit/durable conversation;
  `target/gptlive-acceptance-0718b840d4`. These are not real OpenAI acceptance.
- Dedicated six-client/12-stream capacity repeat:
  `target/gptlive-acceptance-5909098c38`, functional **PASS**, capacity **FAIL**.
  Creation/streams 2,010 Questions; warm idle 0; proposal/acceptance 534;
  active 60.201-second workload 7,776; separate six controlled ticks 192;
  recovery 871; cleanup 2,712. Projected 466,560 active Questions/hour plus 6,127
  fixed = **472,687/hour**, versus 36,000 allowance and 28,800 headroom budget.
  All 12 streams survived, no slot overrun. This is a short synthetic local
  projection, excluding sustained history growth, automatic tick traffic, Live
  receipts and physical jitter. It blocks classroom-readiness claims.

Broader final verification:

- `python target/run_es08.py --java-tests all`: **857 cases**, 855 passed, one
  failed stale static assertion and one intentionally skipped opt-in capacity
  test. Full-run artifacts: `target/gptlive-acceptance-ba987b2529`. The assertion
  expected `demoAgentPath("/reset")`; ES-07 correctly changed reset to the captured
  connection's agent ID. Updated the assertion, and all **14** cases in
  `ValerianClientStaticResourceContractTest` passed (`target/es08-static-contract.log`).
  Across that full run and the focused repair rerun: **856 unique cases pass**,
  with the capacity case verified separately as functional PASS/capacity FAIL.
  This includes **128 Coordination unit and 20 Coordination persistence cases**.
  The original full-run failure is retained, not relabelled as a green run.
- All JavaScript tests under `tests/js`: **183 passed**, zero skipped;
  `target/es08-node-all.log`. Python runner preflight: **11 passed** again.
  Both journey modules passed `node --check`.
- Packaging passed (`target/es08-package.log`). After the last paid run exposed
  an invented "line you supplied", the existing instruction was made explicitly
  conditional on a person actually supplying a line. This last prompt-only
  clarification passed the **11** voice tests and packaging together:
  `mvnw.cmd -q -Dtest=CoordinationVoiceUnitTest package`,
  `target/es08-final-voice-package.log`. No further paid run was made; its model
  effectiveness is unverified. `git diff --check` passed.

### Real Live attempts and semantic review

Artifacts below are `target/gptlive-acceptance-<suffix>/`. Each contains raw
`journey.json`, provider evidence and a separate manually assessed
`semantic-review.json`; the initial PENDING semantic field in the raw journey is
not itself acceptance. All nine attempts requested session closure and the local
runner removed its owned app/schema/account. Aggregate cleanup independently
checked all **14** local database runs: **zero remaining owned schemas/accounts**,
no owned fixture JVMs (`target/es08-cleanup.json`).

| Scenario / artifact suffix | Result and reason for a subsequent attempt | Backend requests | Prompt / completion tokens | Live seconds | New synthesis calls |
| --- | --- | ---: | ---: | ---: | ---: |
| Recovery `c1c636eba1` | FAIL: repeated satisfied preparation. Tightened READY/support guidance before retry. | 1 | 5,004 / 316 | 103 | 6 |
| Recovery `64dca062dc` | Fixture failure: checked a preliminary reply before durable proposal processing. Added committed-turn wait. | 1 | 5,005 / 310 | 15 | 0 |
| Recovery `154ee744a5` | Automated PASS; semantic PARTIAL: correct recovery, acceptance, one welcome, confirmed playback and return, but preliminary checking speech/unfinished proposal wording remain. | 6 | 20,867 / 710 | 103 | 0 |
| Dialogue `57d71b6656` | FAIL: substantive feedback classified AMBIGUOUS, final invitation after budget. Applied compact association reference and final-feedback guidance. | 6 | 22,978 / 631 | 148 | 5 |
| Dialogue `01410adf2d` | Fixture failure: acceptance audio overlapped the delayed formal proposal and was not transcribed. Required actual acceptance invitation. | 1 | 5,038 / 200 | 73 | 0 |
| Social `240e3c8073` | Same proposal-pacing fixture failure; valid saved/spoken count alternative, acceptance not admitted. Applied invitation wait to this scenario too. | 1 | 5,181 / 281 | 73 | 5 |
| Dialogue `bd99fea673` | FAIL: duplicated riddle and fragmented feedback; checker saw only "Piano.", then UNCERTAIN. Full feedback arrived later. No further dialogue retry. | 6 | 22,641 / 628 | 148 | 0 |
| Social `9565d88c16` | Fixture rejected the valid "Say start, and we'll try it" invitation. Broadened that matcher; native duplicated invitation also retained. | 1 | 5,182 / 268 | 59 | 0 |
| Social `7789116aa9` | FAIL: arming speech began before commit, was never associated, then timed out and paused. Native proposal also invented a supplied line. No further paid retry. | 1 | 5,183 / 286 | 88 | 0 |

Total for this ES-08 investigation: **24 backend inference requests**, **97,079
prompt + 3,630 completion tokens**, **810 reported Live seconds**, **16 new
synthesis requests**. These are separate usage measures, not a calculated bill.
They exclude the earlier text-probe totals recorded below. Attempts ran against
incremental corrections; this is not three passing journeys on one final tree.
Every rerun followed the specific correction in the table, never automatic
retry-until-pass. Broader Java/browser jobs were kept separate from paid Live runs.

What the captured words actually establish:

- Recovery: "No, you don't need to demonstrate again. Just say 'start'" agreed
  with retained preparation. Fresh scissors perception preserved authority.
  One subsequent rock produced "Welcome to the workshop!", one action and
  `PLAYBACK_CONFIRMED`; return preserved the recorded outcome. Preliminary
  "checking" remarks still violate the intended silent-wait interaction.
- Dialogue: the first run withheld the solution, preserved the budget during help
  and perception, and produced correct piano feedback. In one SUPPORT inference,
  the model proposed revealing the answer; authoritative support wording prevented
  that hint from becoming the native response. Nevertheless final association
  failed. The later run repeated the question and split feedback; "Piano." was
  insufficient to fulfil the authorized feedback. The backend safely kept two
  contributions and UNCERTAIN. Replacement/renewed acceptance was **not reached**
  in real Live; it remains covered by scripted DB and prior text checks only.
- Social: the actual `humanCount > 1` condition and one-action limit were saved
  and described. In the final run the arming segment's first receipt was
  **21:24:44.672 UTC**, before response creation at **21:24:45.688 UTC**. The segment
  combined "Okay, starting it now" and later "Started. I'm waiting for a fresh
  cue." `LiveTranscriptIngressService` passes that first-receipt time to
  `TaskResponse.claim`, which deliberately requires speech to begin after the
  response was created. No candidate was claimed or sent to the checker; the
  capture deadline expired and the task paused with **zero actions**. Matching cue,
  final delivery and restart/pause/resume were **not reached** in real Live.

The next voice investigation should address segmentation/sequencing of premature
remarks and later confirmed content, plus semantic matching of complete feedback.
Do not fix these failures by accepting pre-commit speech, treating AMBIGUOUS as
delivered, extending deadlines indefinitely or sending a synthetic playback ACK.
Database capacity needs a separately measured optimization/provisioning decision.
Neither issue is fixed by a successful text-only interaction or a larger model
chosen without evidence. ES-08 acceptance and classroom rollout remain unmet.

No schema migration, stored-graph rewrite, production data change or deployment.
Use fresh instances for policy/prompt acceptance; existing saved agreements are
not rewritten. Physical microphone/speaker/camera and robot acceptance is **NOT
RUN**. The operator script in `tests/gptlive/CORE_AGENTS.md` covers ordinary,
challenging and after-change trials, explicit stop and capture teardown.

## Post-ES-07 review and real-provider text smoke (2026-11-10)

Reviewed the seven commits from `3675aa72` through `be3109e9` on `main` after a
clean fast-forward pull. The architecture remains coherent: deterministic task
authority and delivery boundaries, with interpretation/content delegated to the
provider. The primary missing evidence was real-provider behaviour; scripted
success cannot establish that natural language compiles into the intended terms.

Two concrete findings and repairs:

- The direct-help matcher intercepted "Please help me set up a ... task" before
  interpretation. A new regression failed READY versus CONVERSATION on the
  pulled implementation. Narrowed the phrase boundary and clarified negotiation
  guidance; concrete English/German task requests now reach the existing
  interpreter. Standalone help and compound controls keep their existing path.
- In two paid runs, the configured model mapped an additional person joining an
  occupied scene to `arrival`. The existing guard correctly rejected both; it did
  not execute the wrong condition. Added a concrete, conditional count-alternative
  compilation example. Complete text proposals now disclose the executable count
  numeric condition and its limitation regardless of goal wording. This is still
  an explicitly reviewed alternative, not a relative-change detector.

Added `--coordination-cases` to the existing `fixtures.live.CoreTaskProviderProbe`
entry point. `CoordinationProviderSmoke` uses actual production graphs, actions,
canonical behaviour plans and `OpenAILanguageModelGateway`, with synthetic user
and sensor inputs. It reads only provider settings, never starts Spring/JPA or
connects to a database. Request cap 14; completion cap 2,048/request; timeout up to
30 seconds/request within five minutes. No automatic retries. Each case starts
fresh and retains its failure independently; the overall exit fails if any case
fails. Artifacts contain synthetic content and content-free provider usage, never
credentials. This supplements rather than replaces the isolated full Live runner.

Actual verification:

- `mvnw.cmd -q '-Dtest=Coordination*UnitTest' test`: **124 passed**, zero skipped.
  Includes all Coordination unit classes and the added negotiation/disclosure
  cases; `target/coordination-review-regression.log`. JVM 24 emitted existing
  Mockito dynamic-agent/Unsafe warnings; compilation and tests succeeded.
- `node --test tests/js/performance/*.test.mjs`: **33 passed**;
  `target/coordination-review-js.log`.
- Manual preflight checks rejected missing paid opt-in and missing credentials
  before provider access. `git diff --check` passed.
- Real configured extraction/behaviour routes: **gpt-5.6-luna, effort none**.
  Final accepted diagnostic `target/coordination-provider-reviewed.json`:
  **three scenarios passed**, eight requests, 39,049 prompt and 1,344 completion
  tokens. Verified recognition without activation, help/perception without
  contributions, natural cue-task acceptance without inference, one exact cue
  response, history-preserving return; withheld riddle solution, correct feedback
  at the two-contribution bound and explicit replacement/new run; visible count
  terms, pause/resume retaining run, no response to one person and one response
  to two people. Canonical replies were also inspected, including the exact
  greeting, piano feedback and welcome. These are text outputs, not audible proof.
- Retained earlier artifacts under `target/coordination-provider-*.json`:
  `baseline` (help interception and rejected arrival), `help-fixed` (preparation
  and dialogue pass, arrival rejected), `social-fixed` (three pass), `final`
  (two pass; social produced a valid partial clarification before the test's
  complete-proposal assertion). The fixture now permits that bounded clarification
  before requiring visible completed terms and explicit acceptance; it still
  rejects wrong conditions or premature execution. The accepted final run used
  a complete proposal directly, so its optional clarification branch has not been
  exercised end to end with the provider. Total review: 35 requests, 174,528 prompt
  and 6,196 completion tokens. No paid retry was automatic.
- After the accepted provider run, singular/plural disclosure wording and help
  whitespace/phrase-boundary cases were tightened and the 124-unit suite rerun.
  Provider prompts and the exercised task semantics were unchanged.

Limits: language guards remain narrow heuristics, and these short runs do not
establish broad paraphrase/content quality. The riddle question is supplied by the
synthetic fixture, so spontaneous riddle variety is not tested. No running local
MySQL or local admin configuration was available on this device; no persistence,
HTTP, full Realtime, browser visual or physical tests were run in this review.
No production agent data was read or modified, no schema/graph migration,
deployment or database provisioning. Existing shared action implementations gain
the fixes without saved-agreement rewrites. ES-08 remains pending, and ES-07's
capacity failure still blocks classroom-readiness claims.

## ES-07 - Service recovery and measured capacity (2026-11-10)

Valerian now retains last-known views and shows a connection-scoped notice for
network/429/server failures. Uncertain mutations stay uncertain even after a
successful connection check. Manual recovery performs bounded scoped GETs for
state/storage/history, rehydrates the view and reconnects streams; it never
replays task mutations. Agent switches, same-agent reconnects and reset fence
late replies. Login distinguishes rejected credentials from service outages.
Buffered monitor snapshots cannot establish recovery. Capture can stop locally
without falsely confirming a backend stop. No new polling or provider requests.

Integrated review also tightened numeric perception confidence typing and gave
unambiguous compound pause/stop-plus-help requests priority. Contradictory
controls ask for clarification; frustration alone never restarts a task.
No schema/graph replacement or production write is needed.

Verification and retained evidence:

- Focused Support/Perception/Acceptance/Delivery Java checks passed (49 cases),
  including compound controls and invalid confidence typing.
- Node performance suite: 33 passed, including four pure recovery-state cases.
  Python acceptance-runner suite: ten passed, including capacity opt-in isolation.
- Local MySQL query/SSE regression: eight passed, artifact
  `target/gptlive-acceptance-81287e1afe/`; the existing single-client synthetic Live
  workload remains 1,993 Questions/minute under its regression ceiling. This is a
  separate workload, not an additive measured Live classroom projection.
- Two independent six-client capacity runs functionally passed, final artifact
  `target/gptlive-acceptance-38d375003e/` (earlier `c2301028fa`). Owned loopback
  server, restricted fresh schema/account and cleanup confirmed. All 12 original
  SSE readers survived the active minute, with no idle held JDBC connections.
  Final global Questions: create/login/streams 2,010; warm idle five seconds 0;
  proposal/acceptance 534; active minute 7,776; six-agent manual tick probe 192;
  recovery/reload/reconnect 871; cleanup 2,712. Active wall time 60,210ms,
  zero slot overruns; 361 nonstreaming HTTP requests and 24 stream opens.
  Active projection 466,560/hour; one fixed burst adds 6,127 = 472,687/hour.
  Functional acceptance **PASS**, numeric 20%-headroom/capacity gate **FAIL**.
  Both runs produced identical Questions counts. Global counters include direct
  JDBC and transactions; measurement SHOW commands are excluded.
- Browser focused recovery checks passed five cases: desktop/narrow unavailable
  and read-only recovery, buffered snapshots, awaited late old mutations and
  service-error login. Screenshots inspected. Broad runs retained in
  `target/es07-browser-regression.log` (46 passed, two timing-export failures,
  three skips) and `target/es07-browser-integrated-final.log` (47 passed, page-load
  timeout and settings-initialization timeout, three skips). The timing-export
  assumption also failed against HEAD baseline: observation-clock entries may
  enable JSON, while ordinary-turn CSV remains disabled; the fixture now checks
  CSV. Both later transient failures passed focused rerun in
  `target/es07-two-recheck.log`. Final combined rerun: 49 passed, three expected non-static/clock skips,
  `target/es07-browser-final-recheck.log`. No product change was made for the
  two transient failures. Playwright emitted its NO_COLOR/FORCE_COLOR warning;
  PowerShell redirection reports NativeCommandError despite the passing test summary.

Operating envelope: six clients, 12 streams, five-second sensor slots (face,
hand and three-event social batches), bounded accepted cues/questions, synthetic
language provider, short histories and Hikari pool 10. Automatic ticks disabled;
the separately measured six-agent tick cost is not included in the active rate.
Real OpenAI/Live receipts, physical detectors, jitter, long production histories
and a sustained 60-70 minute run are **NOT RUN**. No classroom-readiness claim.
At least the measured rate plus headroom/fixed traffic must fit a provisioned
allowance, or equivalent measured savings must be established. These are rollout
choices, not authorization for remote load, paid provisioning or deployment.

## ES-06 — Faithful social conditions and reviewed alternatives (2026-11-10)

Coordination interpretation now receives the actual first-arrival, crowd-threshold
and grouping-change semantics. Recognised additional-person promises cannot be
saved or accepted as zero-to-present arrival; ambiguous immediate-start requests
cannot silently execute a substituted count condition. Instead, a specific visible
count alternative is proposed for review, without pretending to identify a person
or establish physical entry. Complete unsupported legacy drafts receive accurate
clarification guidance in help, controls and acceptance wording; repeating sensor
demonstrations is not presented as a fix for invalid terms. Partial proposals may
still ask clarification questions. No detector, raw event schema, saved agreement
or shared Generic/Social Space semantics were changed; no new relative/compound
condition engine was needed.

Verification:

- 64 Java cases passed across CoordinationSocialContractUnitTest (10),
  SocialSituationChangeDetectorUnitTest (8), CoordinationAcceptanceUnitTest (6),
  CoordinationRevisionUnitTest (11), CoordinationAgentUnitTest (16) and
  CoordinationSupportUnitTest (13). Cases include zero-to-one, one-to-two,
  two-to-three, zero-to-crowd precedence, departure, unchanged scene and
  grouping-only changes, plus atomic rejection and one bounded count response.
- `python target/run_coordination_mysql.py CoordinationAgentIntegrationTest,CoordinationDeliveryIntegrationTest`:
  final 19 cases passed, including persisted reviewed count terms, zero actions
  on arming, no action for unrelated/nonmatching evidence and one action after a
  matching fresh count. Initial smoke correctly stayed in Execution because the
  cue was before the existing arming cooldown. The fixture now waits for the
  recorded bounded cue window rather than bypassing it. Both attempts and cleanup
  are retained; final artifacts `target/gptlive-acceptance-ab45977de4/`, prior
  failed attempt `target/gptlive-acceptance-fd9e4a8669/`.
- One Playwright count-condition/sensed-count/status case passed; the updated
  singular-limit screenshot was inspected. `git diff --check` passed.
- Language guards cover recognised forms, not every possible paraphrase; real
  Live explanation/compilation quality remains ES-08. No provider or production
  writes, schema migration or graph rewrite.

## ES-05 — Supported task forms and visible limits (2026-11-10)

Runtime negotiation guidance describes the existing output-only sensor and
dialogue contracts. Deterministic guards reject recognised sensor promises to
invite/evaluate answers or start a riddle game, and a question-plus-feedback
dialogue without two contributions. Exact literal output and a one-contribution
feedback-only activity remain supported. Unsupported sensor STEP attempts now
explain the task-form mismatch and offer explicit alternatives without changing
terms or budget. This is a bounded language guard, not semantic proof for every
possible phrasing or a new game engine. New complete proposals disclose the
finite limit and early Stop option.

Dialogue guidance withholds the riddle solution, counts question/feedback
separately, avoids starting unfundable rounds and uses recent history to avoid
repetition. Cue and Live guidance prohibit output-only answer-loop promises.
Generated content quality still needs ES-08's actual provider checks. No mixed
cue-to-dialogue protocol was introduced; the roadmap's supported-alternative
default suffices. Saved agreements are not rewritten; unsupported saved drafts
must be explicitly replaced rather than silently converted. No schema migration.

Verification:

- 71 Java cases passed across CoordinationTaskContractUnitTest (4),
  CoordinationAgentUnitTest (16), CoordinationRevisionUnitTest (11),
  CoordinationVoiceUnitTest (9), CoordinationSupportUnitTest (13),
  CoordinationDeliveryUnitTest (12) and CoordinationAcceptanceUnitTest (6).
  A final 20-case contract/agent run also passed after narrowing the two-contribution
  requirement to question-plus-feedback rather than feedback-only activities.
- Initial regression caught a Live instruction-budget overflow; compacted the
  voice guidance and retained the existing 16,000-byte cap. It also found an older
  delivery test using an epoch-dated invitation; updated that synthetic invitation
  to be recent under ES-03's new expiry contract. Expired-invitation rejection
  remains covered separately; production expiry was not weakened.
- `python target/run_coordination_mysql.py CoordinationAgentIntegrationTest,CoordinationDeliveryIntegrationTest`:
  18 cases passed. A scripted riddle round persists question, answer, feedback and
  LIMIT_REACHED as distinct facts, and an explicit sensor-to-dialogue replacement
  reloads without executing before acceptance. Generated question quality is not
  inferred from scripted output. Owned cleanup confirmed; artifacts:
  `target/gptlive-acceptance-0a882d246e/` and `target/es05-db.log`.
- One Playwright case verified visible limits, separate question/feedback and
  Reflection state; its proposal screenshot was visually inspected. Whitespace
  checks passed. No provider calls or production writes.

## ES-04 — Grounded questions and safe optional-expression fallback (2026-11-10)

Recognisable direct hand/face questions now read supplied source-timed observations
without inference, in all nine phases. Missing/stale/future/invalid evidence does
not become a current estimate; face absence and poor confidence remain uncertain.
Replies explicitly distinguish estimates from direct camera access or feelings.
Other phrasing uses the existing interpreter. Narrow ordinary capability questions
cannot grant an inferred task operation. Optional invalid expression styling may
be discarded for ordinary/support and START replies, without relaxing speech,
hand motion, agreement or control validation.

Live pending questions preserve task identity/deadlines and expose bounded
request-time perception facts as historical evidence, not proof of current input
or task completion. This adds no inference, polling, schema or saved-graph rewrite.
The original classroom failure's exact model result remains unknown; these are
independently verified failure boundaries, not a claim of reproducing its cause.

Verification:

- 53 focused Java cases passed across CoordinationPerceptionUnitTest (14),
  CoordinationAcceptanceUnitTest (6), CoordinationSupportUnitTest (13),
  CoordinationVoiceUnitTest (9) and CoordinationRevisionUnitTest (11). Coverage
  includes nine phases, source ages, invalid styling versus invalid motion,
  dialogue contribution preservation and pending Live identity. An initial local
  edit introduced a UTF-8 BOM into a new Java test; removed it before verification.
- `python target/run_coordination_mysql.py CoordinationAgentIntegrationTest,CoordinationDeliveryIntegrationTest`:
  17 cases passed, including real persisted fresh scissors recognition without
  inference and provider-failure recovery preserving the saved proposal after
  reload. Owned schema/account/server cleanup confirmed. Artifacts:
  `target/gptlive-acceptance-dc97ffe0a4/` and `target/es04-db.log`.
- One Playwright sensed-input/reply/unchanged-status case passed; its screenshot
  was inspected. No client rendering changed. `git diff --check` passed.
- No production writes or provider calls; actual Live wording remains ES-08.

## ES-03 — Natural acceptance and truthful preparation (2026-11-10)

Unchanged ON_CUE starts accept explicit English/German forms and the measured
minor phrase omission without model inference. Contextual short assent includes
Ready/Bereit but requires a completed invitation less than two minutes old;
matching native capture remains required in Live. Stale/missing invitation asks
for explicit acceptance instead. Negated, future, quoted and question-form START
updates are rejected even when the scripted interpreter proposes START. Repeated
starts preserve run and budget. Dialogue starts retain the existing inference
for the actual contribution, with runtime guidance against redundant confirmation;
provider behavioural acceptance remains ES-08.

Verification:

- 37 Java cases passed across CoordinationAcceptanceUnitTest,
  CoordinationSupportUnitTest, CoordinationVoiceUnitTest and
  CoordinationLifecycleUnitTest. Includes nine explicit-start phrasings and unsafe
  inferred START variants, missing/expired invitations and repeated starts.
  The initial contextual-assent unit fixture had no persisted event timestamp;
  corrected its synthetic completed invitation timestamp, not production logic.
- `python target/run_coordination_mysql.py CoordinationAgentIntegrationTest`:
  ten cases passed, including persisted preparation followed by Bereit after
  reload with zero inference calls, zero actions and retained accepted evidence.
  Owned schema/account/server cleanup confirmed. Artifacts:
  `target/gptlive-acceptance-5661b79ce6/` and `target/es03-db.log`.
- Existing desktop/narrow preparation/acceptance recovery browser cases passed
  again (two cases); no client rendering changed in this milestone.
- `git diff --check` passed. No provider or production writes; no schema or saved
  graph migration. Configurator guidance is appended at runtime for saved actions.

ES-02's final context-budget adjustment also passed an additional 22 focused
support/voice cases before its commit.

## ES-02 — Shared state-aware support (2026-11-10)

All nine phases handle direct help/confusion requests through the shared action;
other phrasings can use SUPPORT in the existing interpretation request, including
saved configurators via runtime guidance. The reply is derived from current state,
remaining preparation and applicable rejection facts, not generated claims of
state changes. Help consumes no contribution and changes no task authority.
Explicit existing pause/resume/stop controls remain available.

Live support preserves pending response/occasion IDs and capture/playback deadlines.
During a pending contribution it adds bounded quiet explanation facts without a
new speaking permission; once no contribution is pending, it uses an ordinary
non-task user-response occasion. It is not an acoustic gate or a claim that the
pending response has finished. Reset, next user turn and new Live conversation
fence old help. Control-only background keeps explanation context bounded without
replacing the current task contribution's purpose. No new inference/polling or
schema/saved-graph migration is introduced.

Verification:

- Final focused Java run: 51 cases passed across CoordinationSupportUnitTest,
  CoordinationRecoveryUnitTest, CoordinationAcceptanceUnitTest,
  CoordinationDeliveryUnitTest, CoordinationVoiceUnitTest and
  CoordinationLifecycleUnitTest. Includes nine-phase authority preservation,
  partial preparation, pending Live identity/deadline preservation, expired help,
  session replacement and error fallback. One initial test incorrectly inspected
  the consumed reply-storage slot; corrected to inspect the actual response event.
- `python target/run_coordination_mysql.py CoordinationAgentIntegrationTest,CoordinationDeliveryIntegrationTest`:
  15 cases passed; help survives in history across reload, preserves terms/budget,
  and existing pause/resume and delivery paths pass. Owned schema/account/server
  cleanup confirmed; artifacts `target/gptlive-acceptance-29696e4bf9/`.
- `python tests/playwright/run_static.py tests/playwright/valerian-lifecycle.spec.mjs --grep 'Coordination recovery guidance'`:
  two cases passed, desktop/light and narrow/dark; wrapping and disconnect clearing
  checked. Both screenshots visually inspected. Initial screenshots exposed an
  open test-fixture Diagnostics drawer; the fixture now dismisses it before capture.
- Real OpenAI Live behavioural acceptance remains ES-08; synthetic browser/media
  and unit checks do not establish physical audibility or natural-language quality.

No production writes or provider calls. No additional task engine or state graph.

## ES-01 — Bounded recovery evidence and synthetic cases (2026-11-10)

Implementation on main adds one content-free `coordination.lastFailure` value to
ordinary Storage. It records safe rejection category/operation, source position,
time, phase, run/revision and action count, explicitly marking no committed task
mutation. Provider text, exception details and user wording are excluded. The
record is applicable for 30 minutes only while task identity/progress still match;
successful control updates clear it and reset removes it. Unknown/malformed legacy
records cannot be interpreted as current failure. A failed diagnostic write does
not replace the original recovery reply or claim successful persistence.

Six synthetic classroom-derived cases cover rejected activation after preparation,
perception questions, unsupported riddle-answer promises, extra-person semantics,
unaccepted cues and bounded replacement. They contain no student transcripts or
production IDs. The existing isolated acceptance runner validates the manifest
before database access and copies it into each run's artifacts. Baseline cases are
explicitly not claimed as behavioural acceptance; later milestones implement them.

Verification:

- `mvnw.cmd -q -Dtest=CoordinationRecoveryUnitTest,CoordinationAcceptanceUnitTest,CoordinationLifecycleUnitTest,CoordinationRevisionUnitTest test`: 28 cases passed, including the final rerun after adding action-count scoping.
- `python tests/gptlive/test_acceptance_runner.py`: eight cases passed.
- `python target/run_coordination_mysql.py CoordinationAgentIntegrationTest`:
  eight integration cases passed against the owned loopback MySQL and a fresh
  restricted schema/account. Rejection facts survived reload with unchanged
  proposal, then cleared on accepted progress. Cleanup confirmed. Artifacts:
  ignored `target/gptlive-acceptance-4da9f295d2/` and `target/es01-db.log`.
- `git diff --check`: passed. The default DB invocation was safely rejected for
  lacking a local administration URL; it made no database changes. A first Maven
  invocation was rejected by PowerShell's comma parsing and was rerun with its
  test selector quoted.

No provider call or
production write is part of ES-01. Existing saved graphs use the updated shared
action implementation; no fresh graph or schema change is required for diagnostics.
Conversational escalation remains ES-02 work.
