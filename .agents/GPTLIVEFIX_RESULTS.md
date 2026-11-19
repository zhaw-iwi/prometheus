# GPT-Live repair evidence

## GLF-05 - Integrated acceptance and cleanup (2026-11-18/19)

Status: **software checks and bounded provider happy paths PASS; physical
acceptance NOT RUN**. The owner-authorized agents promotion below deployed
Heroku v170 on 2026-11-19; this is not a claim of physical or production reliability.

Baseline: `3de61cec` (GLF-04), already pushed to main. The current owner authorized
sequential milestones and commits/pushes, plus cumulative paid smoke tests below
$50. That implementation run did not deploy; the subsequent authorized promotion
is recorded below.

Production cleanup removes the unused approval/committed-Speech services, policy
hooks, release/synthesis endpoints, browser players, buffering worklet and unheard
correction/replay path. Dedicated tests for those deleted implementations were
retired. Shared Coordination authority, native semantic association, delivery,
revision/help, capture/provenance and Continuous tests remain and were adapted to
the native route. Historical unplayed provenance is still rejected. The session
response keeps its two legacy output flags as always-false wire fields, while new
claims still clear old saved selection flags. No saved graph/history migration.

The obsolete paid Coordination branches were removed from the other-core-agent
harness. Its selection now rejects before provisioning/billing and directs callers
to the real-cockpit repair harness. Other agent journeys remain. ES-08 labelled
review helpers remain; its obsolete execution code and instructions were retired.

Final offline verification:

- Final source `target/gptlive-acceptance-ecd1a6a2ae`: **484 Java cases and seven browser cases
  passed, zero skips**, including canonical Text/Continuous, the native backend
  barrier, Stop/mode changes, semantic delivery, lost playback acknowledgement,
  incomplete capture/reconnect/reload and the shared scoped Live cockpit.
- **160 JS cases passed**: 110 Live, 29 transcription, 21 Speech. The reduction
  from GLF-04 removes dedicated tests of the retired player/buffer only.
- **16 Python runner/budget checks passed**, including retained failed-attempt
  reservations, exclusive ledger updates, unpriced-model rejection and rejection
  of the obsolete paid Coordination selection.
- Previous `07b94a9f93` passed 481 Java and seven browser cases; `53bd7f4cd4`
  passed 484 and seven. New coverage includes capture boundaries, a shared
  transcription/Live session budget and prohibition of competing native Speech.
- Earlier cleanup run `8ef4c12f1f`: 464 Java and six browser cases passed. The
  focused unit run `target/glf05-unit` passed 249 cases; these overlap the final
  Java selection and are not added to its count.
- Final dead-code cleanup removed one remaining unused capture-rejection overload
  and its dedicated test, plus an unused bridge variable. All 44 focused capture,
  context-delivery and provider-budget cases passed in `target/glf05-cleanup-final`.
  The later six-case budget run in `bec498e450` adds a pre-provider prohibition on
  competing synthesis for native/Text. The final applicable Java set still totals
  484 unique cases; all ran together on the final source in `ecd1a6a2ae`.
- An unused-field cleanup initially left one assignment: `a1d07b7c03` failed
  compilation before reservation/provider access. Removed the dangling assignment;
  subsequent final compilation and tests passed. Owned apps, random schemas and
  restricted database accounts were cleaned after each run.

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests 'Coordination*Test,Task*UnitTest,Live*Test,ExternalSpeech*Test,ScopedLive*Test,ScopedBehaviourSpeechServiceUnitTest,CoreRps*Test,ProviderCaptureBudgetTest,SpeechArchitectureBrowserClientContractTest,BehaviourSpeechControllerWebMvcTest' --browser --browser-mode enabled --browser-grep 'native Coordination|Coordination Text and Continuous|real scoped cockpit'
npm.cmd run test:live:unit
npm.cmd run test:transcription:unit
npm.cmd run test:speech:unit
python -m unittest discover -s tests/gptlive -p 'test_*.py'
```

Paid evidence uses `tests/gptlive/gptlivefix-paid-plan.json`, recorded before any
provider call: eight attempts maximum, $5 reserved per attempt, $40 cumulative.
Every failure keeps its full reserve in `target/gptlivefix-paid-budget.json`.
Backend requests/input bytes/completion tokens/reported tokens, shared Live or
transcription session count, duration and input/output Speech are bounded. There
are no automatic provider retries. This is a conservative spending envelope,
not an invoice or a claim that every reserved dollar was billed.

The predeclared latency sample is three ordinary turns on the native baseline and
three on native Coordination: median at most baseline +1000 ms, maximum at most
baseline +2000 ms. No p95. Browser `performance.now` measures the synthetic input's
last voiced PCM sample to actual HTML-audio RMS onset; server times are separate.
The first streaming input caption can arrive before speech ends, so its negative
relative time is not a final-transcription latency. The deterministic GLF-02
backend barrier remains the causal proof of native independence.

Observed native ordinary-reply latency (three turns per row, milliseconds):

| Artifact | Per-turn onset | Median | Range | Declared target |
| --- | --- | --- | --- | --- |
| `cf083e2a3c` baseline | 1337, 1637, 1394 | 1394 | 1337-1637 | Reference |
| `34f4b5f506` | 1498, 1371, 1364 | 1371 | 1364-1498 | PASS; later lifecycle failure retained |
| `f386428d0c` | 1327, 1404, 1480 | 1404 | 1327-1480 | PASS; later transport failure retained |
| `e48c073b3f` | 1256, 1160, 1329 | 1256 | 1160-1329 | PASS; role error still a semantic FAIL |
| `27dbf34399` final | 1540, 1564, 1589 | 1564 | 1540-1589 | PASS; full journey and meaning review pass |

Retained paid attempts:

| Artifact suffix | Mode | Outcome |
| --- | --- | --- |
| `6a61511e75` | Baseline | Setup failed: collapsed voice selector; three stimulus TTS calls, no Live session/backend call. |
| `916208ecd1` | Baseline | Setup failed: nested summary selector matched twice; cached stimulus, no Live session/backend call. |
| `fd3dcb4266` | Baseline | Real Live session opened; harness compared CSS-uppercase innerText with mixed case. No user/backend turn. |
| `cf083e2a3c` | Baseline | PASS: three native turns, complete recorded conversation; six backend requests, 4,423 reported tokens. |
| `34f4b5f506` | Live | FAIL after one delivered contribution: feedback misclassified, revision changed protected success; ordinary capture boundary defects also found and fixed. |
| `a8e3679104` | Continuous | FAIL at reflection audio: HTTP 200 but no observed voice; user report persisted. |
| `7820190c4e` | Text | FAIL: harness sent fresh cue before saved canonical arming deadline. |
| `f386428d0c` | Live | FAIL: WebRTC transport lost during help; ordinary complete capture passed, client stopped safely. |
| `5171a7d795` | Continuous | PASS: full journey including microphone gating, one contribution, persisted reflection and unaccepted revision. |
| `bec498e450` | Text | PASS after waiting for the persisted cue deadline; full journey and renewed preparation/consent requirements. |
| `e48c073b3f` | Live | Automated journey PASS; independent semantic review FAIL: early-start refusal assigned human preparation/consent to the agent. |
| `27dbf34399` | Live | PASS: automated full journey and manual meaning review after role-guidance fix; nine backend calls, 42,771 reported tokens. |


The baseline replies identified Valerian/SIRA, offered small conversational
activities and explained planning help. They matched the inputs without task
activation. Actual onset samples were 1337, 1637 and 1394 ms; median 1394 ms,
range 1337-1637 ms. Physical sound was not measured.

The first Coordination native attempt, `34f4b5f506`, reached one contribution,
PLAYBACK_CONFIRMED delivery and Reflection. Its ordinary onset samples were
1498, 1371 and 1364 ms (median 1371, range 1364-1498); the declared latency target
passed. However, the model selected COMPLETE for feedback on an already completed
task, then changed the protected success field in a response-only revision. The
backend correctly rejected both mutations. The current-turn prompt now states the
actual phase and supplies the exact protected revision data; guards are unchanged.
The retest additionally requires the reflection report to be persisted. The
harness now checks retained prior agreement plus an unaccepted revision draft,
consistent with the existing resume/renewed-consent contract.

That trace also exposed a real capture defect: a delayed tail of the same input
turn could split native output; the next adjacent output receipt was classified
late because its start equalled the frozen end. The segmenter now distinguishes
continuing input from a fresh turn (including a provider-time silence gap), and
permits non-overlapping adjacent receipts. Available words stay once, while new
voiced input/overlapping turns still yield incomplete evidence. The initial focused
run caught a missing fresh-turn-gap distinction and a test expectation that trimmed
preserved whitespace; both were corrected. All 205 focused cases and the final
484-case shared suite passed. The native retest requires complete, word-preserving
capture for each of the three ordinary turns.

Continuous `a8e3679104` reached the contribution and persisted REFLECT correctly,
but its final MP3 reply yielded no observed browser voice despite HTTP 200. It is
retained as FAIL, not inferred audible from the HTTP result. The next diagnostic
trial attempts to retain MP3 responses and exports production timings to distinguish
provider output from browser playback. Text `7820190c4e` sent its fresh cue before
`task.after` (the existing three-second canonical arming window); its zero-action
rejection was correct. The harness now waits for that persisted deadline before
creating a fresh cue. Preparation/contribution pacing also waits for actual output
onset before quiet and the next spoken input.

Before the remaining follow-ups, the plan was amended to ten attempts and $46
maximum cumulative reservations: the original $5 reservations remain untouched;
one extra instrumented Continuous attempt reserves $5, and the additional Text-only
retest reserves $1. The latter has no audio calls and a worst-case request/token
bound below $0.81 at the recorded rates, including input cache-write premium.
No unchanged run is repeatedly rerolled; these follow-ups address named failures
with corrected pacing or additional diagnostic evidence. The user ceiling remains
strictly below $50. The dated plan and per-attempt copies preserve both budgets.

The eleventh attempt adds peer connection-state evidence and requires cached
visitor PCM, with provider synthesis blocked. Its worst-case backend/Live envelope
is below $1.11 at the declared rates; its $2 reserve brought that plan version to
$48 across eleven attempts. The original reservations remain intact.

Continuous `5171a7d795` passed the complete scripted journey: canonical backend
speech, observed HTML-audio output, microphone pause/reopen, preparation, blocked
early start, help, accepted zero-action waiting, old-cue rejection, one fresh-cue
welcome, duplicate-cue rejection, persisted user reflection and a revised draft
requiring fresh preparation/consent. The revision changed response content while
retaining all protected agreement fields and the prior accepted agreement. It did
not execute a second contribution. The provider input transcripts and resulting
canonical speech were reviewed for these meanings. This does not certify TTS
phonemes or physical sound word for word. The earlier no-voice reply was not
reproduced; its root cause remains unestablished.

The diagnostic follow-up retained production timing exports for all nine user
turns. The reflection transferred 117,888 MP3 bytes and played for about 7.4 seconds;
revision transferred 319,488 bytes and played for about 20 seconds. Those later
responses had observed voice. Chrome rejected all attempts to retrieve consumed
streamed response bodies through CDP, so no raw MP3 artifact was retained; these
errors are explicit in `evidenceErrors`. The truncated server timing journal has
its own dropped-event count (it does not mean dropped audio bytes).

Text `bec498e450` passed the same authority path with real backend inference and
no audio/session calls.

Native `e48c073b3f` passed all automated lifecycle/capture/delivery assertions,
with nine backend calls and 42,563 reported tokens. Its three ordinary native
replies had median onset 1256 ms, range 1160-1329 ms, meeting the declared target.
However, manual review found a material role reversal in the early-start refusal:
"Not yet—I need to show the scissors sign and accept the proposal first."
Those are the human's duties. The backend correctly kept the task unstarted, and
the later ready review assigned the roles correctly, but neither fact makes this
spoken error acceptable. The run remains a semantic FAIL.

The support explanation now explicitly says "you need to demonstrate" and "you
can then accept". Native guidance distinguishes the human's duties/consent from
the agent's duties when paraphrasing. No authority guard or meaning rubric changed.
The first offline check of that addition (`d69d7668a5`) ran 484 cases and found
23 failures/errors, all due to the existing 16,000-byte instruction budget.
Redundant guidance was shortened without changing authority or increasing the
limit. The final shared run `ecd1a6a2ae` passed all 484 Java and seven browser
cases with zero skips before the last paid call.
A dated fourth plan version reserves $1.50 for one final validation of this fix,
with cached stimuli and zero provider synthesis. The unchanged worst-case bounded
backend/Live cost is below $1.11. All prior reserves are retained, for at most
12 attempts and $49.50 cumulative; no further paid trial follows.

Final native `27dbf34399` passed the automated journey and a separate review of
all spoken output and persisted terms. The early refusal now says the visitor
must show the sign first. Its collective "we can accept" is prospective; the ready
review and help explicitly require the visitor's acceptance. The ready review
preserved the fresh scissor cue, human demonstration/judgement, agent welcome,
one-action limit and acceptance question. Help did not start the task. Acceptance
left zero actions; an old cue was rejected, a fresh cue produced one welcome,
scoped playback was confirmed, and a duplicate cue did not add an action. The
user's positive reflection was stored separately from the finite-action outcome
`LIMIT_REACHED`; it is not a measured success claim.

The final revision is an **unaccepted draft**, requiring new preparation and
consent. The protected human-role/success strings still refer to the original
welcome, as required by this response-only revision contract. This trial does not
qualify a second revised execution or a change to those protected terms; changing
them requires explicit replacement agreement. The prior accepted terms and one
spent action remain preserved.

Every native output word in the final browser caption stream is retained once in
history (1,115 non-whitespace characters match in order). All three ordinary turns
are complete. "Everything's ready," overlaps the new spoken help input and is
correctly retained as an incomplete prefix, followed by the captured continuation;
it is not complete consent or task-delivery evidence. This is distinct from the
fixed same-turn delayed-caption defect. The peer reached connected with no later
transport failure. All eight visitor clips were cached; no provider Speech call
was allowed. Final source hashes match the production sources used by this run.

The final reservations total **$49.50 across 12 attempts**, retaining every earlier
failed reserve. No further paid run was launched. Actual invoiced usage has not
been reconciled; this is the declared conservative envelope.

| Mode | Final software checks | Real-provider happy path | Physical audio |
| --- | --- | --- | --- |
| Text | PASS: canonical authority and persisted journey | PASS `bec498e450` | Not applicable |
| Continuous | PASS: canonical queue, scope, microphone gating and parity | PASS `5171a7d795`; earlier no-voice failure remains unexplained | NOT RUN |
| Native Live | PASS: independent audio, task meaning, delivery, capture and lifecycle | PASS `27dbf34399`; prior transport/semantic failures retained | NOT RUN |

Text/Continuous paid runs precede the final explicit-human-subject wording change;
the final 484-case/seven-browser regression run covers those modes on the final
source. The native paid run uses that final source. Small synthetic trials do not
establish failure rates, room acoustics, Bluetooth behavior or physical audibility.

The final native command was:

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests none --paid-core repair-live
```

Paid runs snapshot `source.json` hashes and their pre-call `paid-plan.json`.
Available provider usage, browser diagnostics, transcript/state history and journey
outcomes are retained, including failures; setup failures have no completed
conversation evidence. The cumulative ledger preserves failed reservations. No
production configuration or credentials are included in these committed notes.

Physical checks are **NOT RUN**;
the executable device/room/settings checklist is
[GPTLIVEFIX_OPERATOR_CHECKLIST.md](GPTLIVEFIX_OPERATOR_CHECKLIST.md).


## Agents promotion and Heroku v170 - 2026-11-19

The owner explicitly authorized merging the repair into `agents` and starting
Heroku deployment. Main `97469a97` was merged into agents `9f3bb418` as
**`dfc1211a`**, then pushed. The deployment catalog, persona/embodiment behavior,
Gala definitions, configuration and workflow remain preserved. No experiment
branch was changed and no new provider trial was run.

The two conflicted files were the acceptance runner and its README. Resolution
keeps the branch's Gala arguments/journey and countdown documentation alongside
the repaired native harness, budget checks and retired-route rejection. Obsolete
approval-player documentation remains removed. Automatic merges retain persona
binding in prompt/Live projection and the cockpit's embodiment/round controls.

Merged-source verification:

- `target/gptlive-acceptance-6cfd51dc69`: 859 Java cases ran, with one stale static
  client assertion failing because it expected the pre-repair transcript-rendering
  call without the new incomplete-capture label. The other 858 cases passed.
- Updated that existing assertion to check retained event identity and the label.
  `target/gptlive-acceptance-ae58dcc8bb` passed all 14 static client cases and
  all seven real-browser/database cases, zero skips. Across the broad/focused
  results, all **859 applicable Java cases pass**. This includes Gala, catalog,
  persona, Coordination and shared Live authority/delivery coverage.
- **161 JS cases passed** (111 Live, 29 transcription, 21 Speech), plus
  **17 Python runner/budget cases**. Logs: `target/gptlivefix-agents-release/`.
- The shared test correction is also carried to main; its focused result is
  `target/gptlive-acceptance-8f98ff83bf`. No production change was needed for it.
- The disposable apps, schemas and restricted accounts were cleaned. No paid
  API calls or production agent interactions were part of these release checks.

The expanded merged Java selection used the GLF-05 command above plus
`Gala*Test,*PromptContractTest,AgentDefinitionRegistryUnitTest,AgentEmbodimentUnitTest,PromptMessageAssemblerUnitTest,Aisha*Test,ValerianClientStaticResourceContractTest`.
The focused command was:

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests ValerianClientStaticResourceContractTest --browser --browser-mode enabled --browser-grep 'native Coordination|Coordination Text and Continuous|real scoped cockpit'
```

[GitHub deployment run 44464206991](https://github.com/zhaw-iwi/prometheus/actions/runs/44464206991)
succeeded for `dfc1211a`. Heroku **valerian v170** succeeded on
**2026-11-19 at 06:19 CET** (05:19:07 UTC), container `b15e26827dce`.
The [deployed cockpit](https://valerian-e613edef27a0.herokuapp.com/valerian/)
passed read-only checks:

- Health is **UP**. All four changed client files (`live/captions.js`,
  `live/client.js`, `live/playback-cue.js`, `valerian/script.js`) match the deployed
  commit. All four retired player/buffer/worklet URLs return **404**.
- The nine cockpit/robot asset hashes also match. The existing deployment browser
  verifier rendered a synthetic local plan with real WebGL, switched Robot/Details,
  preserved speech/latest event and static replay, and recorded **zero page errors
  and zero attempted writes**. Its screenshot was inspected.
- Evidence: `target/gptlivefix-agents-release/{workflow,heroku-releases,gptlivefix-deployed}.json`
  and `target/gptlivefix-agents-release/browser/{verification.json,deployed-robot.png}`.

Release evidence and the test-only correction are synchronized with `[skip ci]`
audit commits; the deployed runtime remains v170. The paid reservation ledger
remains $49.50. Physical microphone/speaker acceptance is still **NOT RUN**;
reload the cockpit before using the new client and follow the operator checklist.

## GLF-04 - Recording and native interruption (2026-11-18)

Baseline: `973629b5`, GLF-03 committed and pushed to main.

Interrupted native prefixes were already persisted. Their projected payloads now
remain visible with an **Incomplete capture** label in both conversation views,
while ordinary/condensed prompt history and canonical Speech exclude them. All recorded external speech also blocks canonical TTS replay and fallback to
an older utterance: a complete capture can still have uncertain task delivery. Complete
canonical Text/Continuous speech and deliberate latest-speech resume are unchanged.

Caption reconciliation now distinguishes a durable settled receipt from its first
browser receipt: a ledger arriving first suppresses a late preview without losing
playback correlation. Duplicate browser receipts remain inert. Stop reconciles its
final ledger; the next session refreshes history even with an empty current ledger.
The current Live client no longer imports or selects the approval/committed player,
requests approval capability, filters out native assistant history, or invokes its
microphone RMS interrupt logic. A stale server requiring that player fails before
input opens. Legacy paid harness/API retirement remains GLF-05 work.

The browser records a bounded content-free `input_boundary` diagnostic when a new
user caption resets playback evidence. Existing receipt/segment/cue correlation,
clock-domain labels and dropped counts remain distinct; no waveform, speech content
or word-level audibility is inferred. Native barge-in belongs to the provider.

Verification and retained failures:

- `0b0af40bd1`: 88 Java cases, one failure from the former expectation that an
  incomplete capture was an ordinary prompt message. Updated that assertion to
  require preserved projected text and exclusion from complete prompt history.
- `32c262ee7f`: all 88 Java cases passed. Three browser cases passed; two final
  playback cases exposed the first reconciliation fix discarding a late receipt
  needed by playback. A third failure was the new reload fixture resubmitting an
  already-restored access code. Both causes were fixed explicitly.
- `7fa702a113`: all six browser cases passed after those fixes.
- `ed01129c9c`: eight canonical Speech cases and all six browser cases passed
  with the external-recording replay guard. The last JS-only late-input fence
  then passed all 132 Live cases: ledger-settled old input cannot interrupt a
  newer response. Across the affected Java selections there are **89 cases**.
- All 12 acceptance-runner checks and `git diff --check` passed. Owned apps,
  disposable schemas and accounts were cleaned.
- All **182 JavaScript cases** passed: 132 Live, 29 transcription, 21 Speech.
  Includes ledger-before-caption, duplicate receipts, Stop and scope fencing.

The Java selection covers complete/incomplete/late/overlapping capture, durable
claim/restart duplicate prevention, native projection, canonical Speech rejection
and Coordination delivery uncertainty. The browser selection covers native output
before backend work, mode switches, final playback and lost acknowledgements,
interrupted-prefix persistence across Stop/reconnect/reload and Continuous gating.
The new browser case injects loud synthetic input separately from an intentional
user-caption interruption. It proves that input amplitude alone does not trigger
the removed local player; it does not classify real room noise or provider echo.

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests 'LiveTranscript*Test,LiveSpeechExecutionUnitTest,LiveSpeechPersistenceIntegrationTest,ScopedBehaviourSpeechServiceUnitTest,CoordinationDeliveryIntegrationTest' --browser --browser-mode enabled --browser-grep 'native Coordination|Coordination Text and Continuous'
npm.cmd run test:live:unit
npm.cmd run test:transcription:unit
npm.cmd run test:speech:unit
```

GLF-04 software exit is verified. No paid calls or deployment in GLF-04. Physical acceptance remains NOT RUN.


## GLF-03 — Native Coordination meaning and authority (2026-11-18)

Baseline: `807f60cb`, GLF-02 committed/pushed to main. Native task speech is
associated asynchronously after capture. Ordinary speech is recorded without a
classifier or a new task-speaking occasion. The verifier and cue guidance now
explicitly permit equivalent wording while preserving signs, roles, counts,
conditions, negation, defining clues and outcomes. An explicitly requested literal
still has its narrow exact-content contract. No approval-before-audio was added.

Native help needed a real repair: complete help speech could become the saved
proposal's consent context when the old approval guard was bypassed. Help now
has a separate bounded occasion, preserves the pending task identity/deadline,
and cannot set proposal-consent evidence. A duplicate help capture cannot later
become that invitation. Support-only responses also cannot establish consent.
Ambient capture consumes only its own current occasion; it cannot establish a
task response or consent. Captures remain distinct from playback confirmation.
Support guidance preserves meaning rather than demanding verbatim delivery.

Reworked `CoordinationDeliveryIntegrationTest` to exercise native capture directly
instead of calling approval/release APIs. It covers ordinary exploration,
proposal, observed preparation, premature acceptance rejection, acceptance with
zero actions, arming, stale/fresh/duplicate cues, one contribution, separate
scoped playback, reflection, help and revision requiring preparation/consent
again. Persisted tests retain wrong-session/segment rejection, no transaction or
agent lock during semantic inference, one queued unrelated candidate, incomplete
capture uncertainty, explicit-literal handling and no replay after owner loss.
The nine predeclared meaning examples exercise MATCH versus AMBIGUOUS outcomes
and authority. Their verifier replies are scripted: they are not evidence of real
model judgement, which remains GLF-05 work.

Two native browser cases now replace the retired synthesized final-delivery
cases. They observe the actual native audio element, match captured receipt
identity and send exactly one scoped playback cue. Capture alone stays Finishing;
observed playback reaches Reflection. A lost acknowledgement is not retried and
Stop preserves UNCERTAIN delivery with one contribution spent. Neither case makes
a synthesis/approval/release request or records a SYNTHESIZED event.

Retained runs and fixes:

- `b3662c79be`: focused native persisted/authority tests passed.
- `9f4cf7c6a3`: broader suites exposed the native help/ambient issue and old
  committed-speech assertions; no browser run followed the Java failure.
- `2b7bfb288a`: one old test expected help to reuse the task-speaking identity.
  It now checks a distinct help occasion while keeping task authority/deadline
  byte-for-byte unchanged.
- `c22c4049cd`: 235 Java cases, three failures: an obsolete verbatim-support
  assertion and two new fixture issues (unconfigured free-form help inference,
  equal clock ticks at an ambient boundary). The fixtures now use the supported
  deterministic help request and explicit post-occasion capture times.
- `target/gptlivefix-glf03-unit`: the 56 affected unit cases passed after those
  fixes. `27aff4504c`: both native delivery browser cases passed.

Final source verification: `4a28aadbcd` passed **235 Java tests and five browser
cases**, with no failures/skips. This includes Continuous parity, both native
backend-barrier/Stop/mode-switch cases and both native final-delivery cases.
`git diff --check` passed. Owned applications, schemas and accounts were cleaned.

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests 'Coordination*Test,TaskResponse*Test,Sensory*Test' --browser --browser-mode enabled --browser-grep 'native Coordination|Coordination Text and Continuous'
```

GLF-03's software exit is verified. Legacy approval/synthesis harness and API
cleanup remains with the recording/player and integrated-harness work; those
paths are not native acceptance. Paid-provider and physical checks remain unrun;
cost in this repair session remains $0. Proceed to GLF-04.

## GLF-02 — Native conversation independence (2026-11-18)

Implementation baseline: `f7c1033c` (GLF-01 committed and pushed to `origin/main`
on the owner's explicit instruction). The owner now authorizes sequential
milestones and per-milestone commits/pushes, plus paid OpenAI happy-path smoke
tests below $50 cumulatively. No paid calls or deployment occurred in GLF-02.

Coordination now selects the existing native audio route. Its session claim
clears both legacy persisted output flags before projecting startup context;
there is no saved-graph/history migration. Ordinary KEEP/REFLECT turns request
nonverbal backend output and create no competing speech occasion, even if a
provider supplies draft speech. Deliberate task/support context remains on the
existing asynchronous bridge. Text/Continuous keep canonical event Speech.

`valerian-gptlive-native.spec.mjs` runs the actual cockpit, HTTP/SSE and disposable
MySQL with a controlled provider transport. A server inference barrier stays
closed while a real PCM tone passes through the native HTML audio element
(captured output amplitude > .02, playing and unmuted). After release it verifies
nonverbal rendering, exactly one admitted user event despite repeated receipts,
and no synthesis/approval/release requests. Separate cases cover immediate local
Stop with pending backend work and Continuous -> Live -> Continuous on one agent,
including a new finalized transcript and canonical Speech after returning.
This establishes browser routing, not provider latency or physical audibility.

The old two committed-Coordination browser scenarios were removed: requiring
native muting and synthesized playback is now the wrong contract. Their shared
task-budget/uncertainty obligations remain in Java coverage and are the native
persisted journey work of GLF-03. Approval/synthesis service and player contracts
are still consumed by the legacy provider harness/tests; they are not selected
by repaired Coordination. Migrate those consumers and retire their obsolete
branches during semantic/integrated acceptance, without treating them as native
acceptance or claiming a green full suite at this intermediate milestone.

Verification:

- `977631d05e`: 30 Java tests passed; existing Continuous browser parity passed.
  The new native mode-switch fixture dispatched before the replacement
  transcription channel was ready; Stop/native audio passed.
- `49f8ca7c3c`: 16 persisted Coordination tests passed, including fresh claim of
  old flags; the native Stop case passed. The fixture still waited only for the
  UI's early Listening label. It now waits for transport Connected and a live
  microphone track before sending committed/final events.
- `1a68b68596`: both native browser cases passed after that fixture correction.
- `npm.cmd run test:live:unit`: 131 passed. `test:transcription:unit`: 29 passed.
  Acceptance-runner unit checks: 12 passed. Final combined run recorded below.
- `8e229be573`: all 34 Java and ten enabled-browser cases passed. Disabled
  Continuous parity passed; the old feature-off test counted all earlier fixture
  Speech calls rather than its own request. Corrected that test to assert its
  call-count delta. Three Live-only cases were intentionally skipped when disabled.
- `a322b8ba1b`: both affected disabled-browser cases passed together, proving the
  count no longer depends on test order. `fe0a269ad9`: both existing native RPS
  and Multimodal cases passed after removing their unused buffered-audio helper.

Final selected coverage: **34 Java, 160 Node, 12 browser mode/cases and 12 runner
checks passed**. This is focused milestone coverage, not the full legacy suite.
`git diff --check` and browser-spec syntax validation passed. No implementation
change was needed for the retained fixture-order/readiness failures.

Combined verification command:

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests CoordinationAgentIntegrationTest,CoordinationAcceptanceUnitTest,CoordinationTaskContractUnitTest,LiveSmokeConfigurationTest --browser --browser-mode both --browser-grep 'native Coordination audio|Coordination Text and Continuous|real scoped cockpit|Live Multimodal|stopped resume lookup|mocked WebRTC emits partial|Stop and synthesis failure'
```

Final reruns used `--java-tests none --browser --browser-mode disabled
--browser-grep 'Coordination Text and Continuous|real scoped cockpit'`, and
`--browser-mode enabled --browser-grep 'real scoped cockpit|Live Multimodal'`.
GLF-02's exit is verified; proceed to GLF-03 under the current authorization.

Artifacts are under `target/gptlive-acceptance-<id>/`; each owned app, schema and
account was cleaned. Shared local MySQL services were left running.

## GLF-01 — Mode contracts and Continuous parity (2026-11-18)

Status: **verified; stopped for review**. GLF-02–05 remain open. This milestone does not
restore native Coordination speech or establish provider/physical acceptance.

Baseline: clean `main` at `e66aa3cd` (the roadmap/handoff commit, after `cff6818a`).
Work stays in the existing checkout. No production configuration, study code,
commit, push, deployment or paid provider call is included.

### Contract and implementation scope

Continuous uses the shared `TranscriptionEventRuntime` and
`ScopedTranscriptIngress`: only finalized, ordered provider items become scoped
`obs.user_utterance` requests with `profile=full_plan`. The ordinary backend owns
the agreement and response. Behaviour SSE supplies persisted IDs to the existing
Speech queue; `ScopedBehaviourSpeechService` resolves the exact canonical text.
PCM/MP3 streaming, output settings, deduplication, intentional startup replay and
microphone gating retain their existing implementation.

Live is a different contract: native ordinary speech must eventually play while
backend work proceeds asynchronously, with meaning-preserving wording variation.
Agreement, preparation, consent, budgets, response identity and outcomes remain
backend-owned. GLF-01 establishes that distinction in acceptance scenarios and
the assertion map below; the November committed-Speech route still runs until
GLF-02 changes it. No existing strict assertion has been weakened.
No Continuous runtime defect was demonstrated by these checks; production
speech routing and task code are unchanged. The repaired gap is executable
Coordination Continuous coverage, plus acceptance-build isolation.

Added `valerian-continuous-smoke.spec.mjs` to the real-app acceptance runner. It
uses the real cockpit transcription adapter, HTTP controllers, state machine,
disposable MySQL, SSE, canonical Speech endpoint and PCM AudioWorklet. The
external transcription SDP/data channel, microphone and model/Speech providers
are synthetic. The Speech provider emits a tone and records its input text;
actual PCM rendering is observed, not physical speaker audibility.

The same scripted proposal is submitted through the Text UI and finalized
Continuous provider events on separate persisted agents. Both paths must:

1. Store identical goal, roles, success, sign, preparation and one-action limit.
2. Reject starting before observation-based preparation; speech alone cannot
   supply that evidence.
3. Retain one preparation sample and accept with zero actions.
4. Reject reusing the preparation sample as an execution cue.
5. Produce one canonical contribution on a fresh cue and no extra contribution
   for a repeated cue; record `LIMIT_REACHED`, not achieved success.
6. Preserve the same agreement, accepted preparation, count, phase, outcome and
   canonical contribution after separate database reads.

Continuous additionally proves partials stay local, repeated terminal receipts
acknowledge once, startup replay precedes microphone opening, each speech request
uses a unique persisted event ID with no text body, every provider input equals
that event's speech, and input is disabled/reopened around actual PCM playback.
Stopping releases the microphone track. The journey runs with the Live feature
both enabled and disabled; it is not a mocked acknowledgement relabelled as
Continuous acceptance.

The runner now isolates every Maven build under its owned
`target/gptlive-acceptance-<id>/build`, using `prometheus.build.directory` for both
tests and the fixture application's classpath. Previously only embodiment runs
were isolated from IDE output. It still creates/removes only a random loopback
schema/account and its own application process. Existing local
`src/main/resources/application-test.properties` was inspected for a loopback URL
without printing credentials; production properties were not used for tests.

### Assertion map and disposition

These are concrete assertion families, not a permission to delete whole suites.
Where a test mixes authority with an obsolete delivery rule, retain the former
and replace only the mode-specific expectation in its named later milestone.

| Group | Existing tests/assertions | Disposition |
| --- | --- | --- |
| Shared | `CoordinationAcceptanceUnitTest`: explicit negation rejects combined start; inference cannot replace accepted terms; preparation is required; repeated starts keep the run/budget | Retain for all modes. |
| Shared | `CoordinationTaskContractUnitTest`, lifecycle/revision/support/social/perception/timing suites: supported task forms, protected terms, fresh evidence, finite limits, grounded sensing and help without extra actions | Retain. GLF-01 runs the acceptance/task-contract and persisted Coordination suites; other suites are mapped, not claimed rerun. |
| Shared | `ExternalSpeechOwnershipUnitTest`, ingress claim/capture tests: scope, session/epoch fencing, duplicate-safe admission, no assistant acknowledgement, no automatic retry of uncertain claims | Retain. Recheck affected consumers in GLF-02/04. |
| Shared | `CoordinationVoiceUnitTest.ordinaryQuestionsDoNotAuthorizeTaskContributions`, `controlOccasionIsStableConsumedOnceAndCannotCrossSessionOrRevision`, obsolete-response rejection | Retain authority and once-only consumption; adapt the speech route in GLF-03. |
| Shared | `CoordinationDeliveryUnitTest.finalSpeechNeedsAssociationAndCurrentPlaybackAcknowledgementBeforeReflection`, uncertainty cases; delayed receipt tests in `playback-cue.test.mjs` | Retain separate association/playback evidence and no replay/spend on uncertainty. Captions alone must never finish Live work. |
| Strict Text/Continuous | `events.test.mjs`: commit ordering, UI-only partials, terminal dedup, stale epochs; `ingress.test.mjs`: serialized scoped FULL_PLAN, one legitimate fallback, rejected input stays rejected | Retain unchanged. The real-app parity test now exercises this adapter against persistence. |
| Strict Text/Continuous | `ScopedBehaviourSpeechServiceUnitTest.resolvesCanonicalPersistedSpeechWithoutRewritingIt` (PCM and MP3); foreign/malformed/empty events rejected; controller contract | Retain exact provider-input equality. This is a TTS input guarantee, not a physical transcription guarantee. |
| Strict Text/Continuous | `playback.test.mjs`: ordered IDs, duplicate/replay suppression, explicit repeatable resume, Stop abort/queue discard/lease release; PCM/progressive cancellation and fallback tests | Retain the existing streaming queue; do not add whole-response approval buffering. |
| Strict Text/Continuous | `valerian-transcription.spec.mjs`: real adapter with mocked APIs, ordered final input, input gating/reopening, held startup lookup after Stop/restart | Retain; add switching to Text while the lookup is held. Label these mocked-API checks separately from the new real-app journey. |
| Strict Text/Continuous | `CoordinationDeliveryUnitTest.publicationModeSilentOutputAndOldGraphsKeepTheirContracts`: Text completion occurs at canonical publication | Retain. Continuous shares this backend contract, not Live's separate final-playback gate. |
| Obsolete for repaired native Live | `ScopedLiveSessionServiceUnitTest`, `CoordinationPolicy.beginLiveConversation`, Live client tests selecting `speechApprovalRequired`/`committedSpeechOutput`, muted native route | Replace ordinary Coordination routing/handshake assertions in GLF-02. Keep ownership, capability, scope and teardown assertions. |
| Obsolete for repaired native Live | `CoordinationSpeechCorrectionUnitTest.supportCorrectionKeepsTheSameCompleteSpokenInstructionAndAuthority`: `Speak these words verbatim`; literal-support/exact candidate checks in approval tests | Replace universal literal wording and unheard correction requirements with the documented meaning cases in GLF-03. Keep response identity, authority and deadlines wherever still consumed. |
| Obsolete for repaired native Live | `approved-speech`, `speech-buffer`, `committed-speech` suites and old browser ES-08 journey: full capture/approval/synthesis/release before any ordinary audio | These prove the old route only. GLF-02 must test native audio while backend work is held, with no competing synthesis. Remove modules/tests only after checking remaining consumers. |
| Obsolete for repaired native Live | Conversation projection/caption assertions excluding all unconfirmed output | GLF-04 must distinguish inspectable partial/uncertain speech from completed delivery and consent evidence; do not expose muted drafts as heard speech. |

### Synthetic meaning rubric for GLF-03/05

`tests/gptlive/gptlivefix-scenarios.json` contains the executable sensor proposal,
labelled equivalent/materially different wording pairs, and dialogue, revision
and interruption journeys. It is synthetic and contains no private interaction.
The labelled pairs are a review corpus, not an implemented semantic classifier.

Equivalent: “show scissors” / “make a scissor sign”; a riddle paraphrase preserving
keys, space bar and no locks. Material differences: wrong sign, swapped roles,
changed count, removed/negated fresh-cue condition, invented preparation, premature
success, or dropping a defining riddle clue. Minor grammar, contractions and
politeness may vary only while every operative fact remains intact.

Review real speech against the backend facts **at that speaking occasion** and
inspect the resulting persisted state separately. Classify missing/ambiguous
capture as uncertain, not equivalent. Keyword presence alone cannot pass a
changed condition or negation. Generated words cannot establish consent, spend
an extra action, verify preparation or prove playback. No blocking classifier
before ordinary native audio is authorized by this rubric. Provider and physical
review remain separate from fixture outcomes.

### Saved flags and mode-switch decision

Current code persists `live.speechApproval` (`LiveSpeechRequest.ENABLED`) and
`live.committedSpeech` (`LiveCommittedSpeech.ENABLED`) at
`CoordinationPolicy.beginLiveConversation` when `coordination.deliveryEnabled`
is set. They survive session release; `voiceInstructions()` can still select the
silent committed-output guidance from those flags. Fresh sessions reassert them.
The two-second user and 2.4-second delivery-aware assistant silence windows are
capture policy, not an intended ordinary-native-playback prerequisite.

Actual backend speech ownership is the volatile session/epoch grant in
`ExternalSpeechOwnership`; flags alone do not grant it. The new persisted
`savedLiveSpeechFlagsDoNotOwnCanonicalOutputAfterSessionRelease` regression starts
the policy's Live lifecycle, reloads both flags, releases ownership and verifies
ordinary proposal/acceptance, one contribution and exact canonical PCM synthesis.
It also inspects both actual inference requests to ensure the Live mute directive
does not reach backend generation. A suspected prompt leak was disproved:
`CoordinationUpdateAction` and `TaskUpdateAction` own separate stored
`PromptPolicy` instructions, rather than using the active state's
`CoordinationPolicy.describe()`. No speculative prompt change was made.
It does not claim a browser Continuous → Live → Continuous handshake test.

GLF-02 must set the repaired output choice at session initialization **before**
context/handshake snapshots, including an agent with old true flags. Retire or
locally neutralize the old route selectors for repaired Coordination; never use
their mere presence to select muted/committed output. Preserve the separate
delivery capability, existing graphs, history, task response records and uncertain
delivery outcomes. Stop must release ownership and fence pending callbacks;
returning to Continuous must use FULL_PLAN and canonical event Speech again.
Test the complete three-mode transition on one saved agent, with late prior-session
work and zero automatic replay of uncertain task contributions. No global
backfill/reset or rewriting of old provenance is part of this decision.

### Verification and retained failures

Final focused suite: **64 Java cases, 50 Node cases, seven browser cases and 12
runner checks passed**, with no failures/errors/skips in the final selections.
The final saved-flag prompt assertion additionally passed its one-case MySQL
rerun. The seven browser cases are two real-app parity runs (Live enabled/disabled)
and five mocked-API ingress/lifecycle checks. No provider or physical acceptance
is claimed.

Exact final commands:

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests ScopedBehaviourSpeechServiceUnitTest,BehaviourSpeechControllerWebMvcTest,SpeechArchitectureBrowserClientContractTest,CoordinationAcceptanceUnitTest,CoordinationTaskContractUnitTest,CoordinationAgentIntegrationTest,CoordinationDeliveryUnitTest,LiveSmokeConfigurationTest --browser --browser-mode both --browser-grep 'Coordination Text and Continuous|stopped resume lookup|mocked WebRTC emits partial|Stop and synthesis failure'
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests 'CoordinationAgentIntegrationTest#savedLiveSpeechFlagsDoNotOwnCanonicalOutputAfterSessionRelease'
npm.cmd run test:transcription:unit
npm.cmd run test:speech:unit
python -m unittest discover -s tests/gptlive -p test_acceptance_runner.py
node --check tests/playwright/valerian-continuous-smoke.spec.mjs
git diff --check
```

Final combined artifacts: `target/gptlive-acceptance-3530917ab6/` contains the
Java/browser logs and all eight fresh JUnit XML reports under
`build/surefire-reports/`. This final journey explicitly requires the welcome
and its nonverbal plan after the cue, and forbids delivering it before the cue.
`target/gptlive-acceptance-84f61c583f/` contains the final prompt probe (one pass).
`target/gptlivefix-glf01-verification.json` records selections, counts and final
source hashes. All owned app processes, schemas and accounts were removed.

- `npm.cmd run test:transcription:unit`: **29 passed**, no skips.
- `npm.cmd run test:speech:unit`: **21 passed**, no skips.
- `python -m unittest discover -s tests/gptlive -p test_acceptance_runner.py`:
  **12 passed**.
- First isolated run `3ae6f9ec0d`: **48 Java passed**, no skips. Browser stopped
  after the successful Text path because the new test used a nonexistent
  `disconnect-agent` selector; corrected to the existing `connect-agent` toggle.
- Second run `d020745b25`: **five lifecycle/ingress browser checks passed**,
  including the new held-lookup Text switch. The new parity journey reached its
  final canonical-ID assertion, then failed because raw `/eventhistory` omits
  IDs. Corrected the fixture to read the existing `/live/history` projection,
  which retains IDs and ordinary canonical speech. No production API changed.
  Both failed browser traces/logs remain under their artifact directories; each
  owned app/schema/account was cleaned up.
- Intermediate corrected run `9e45b40565`: **30 Java and two real-app browser
  cases passed**, no skips. The final run above strengthens the scripted cue
  content from the generic fixture reply to an actual welcome and checks that it
  was absent before the cue. No production inference/prompt change was needed.

### Remaining gates

Native Coordination audio, no-backend-wait proof, semantic native happy path,
interrupted history and native acoustic handling are GLF-02–04. Real-provider
wording/latency, sample plan, baseline comparison and physical microphone/speaker
trials are **NOT RUN** and belong to GLF-05. Other task variants, actual recognition,
Bluetooth/echo/noise and physical robot execution are not proved by this one
scripted sensor journey. Historical ES-08 evidence remains valid for its old
route and does not accept the repaired native contract.
