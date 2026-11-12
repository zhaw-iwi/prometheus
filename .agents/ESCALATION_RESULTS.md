# Coordination escalation and classroom reliability evidence

## ES-08 behavioural acceptance COMPLETE (2026-11-12)

The owner approved the existing Speech service for committed Coordination replies.
This supersedes the pending-choice/native-only checkpoint below. Live input, backend
state authority, spent contributions, original deadlines and observed playback are
retained. New sessions use distinct SYNTHESIZED provenance and no native-output
fallback. The concrete decision and changed voice/cost path are in
`ES08_SPEECH_DELIVERY_DECISION.md`. Acceptance used the uncommitted `main` tree
based on `333f0f098425f4e89be2a2df6d28628932e94a59`; the owner subsequently
authorized committing/pushing `main` and merging it into `agents` for deployment.

All three real-provider journeys passed automated authority/delivery assertions
and separate semantic reviews on the same final source configuration. The 74-file
source manifest, journey/PCM hashes, independent local ASR and semantic reviews
are checked together in `target/es08-speech-final-20261112/summary.json`.
Raw journey rubrics remain unchanged; semantic verdicts are separate artifacts.
The backend model was `gpt-5.6-luna`; Live used `gpt-live-1` / Willow with native
output muted. Committed output used `gpt-4o-mini-tts` / Alloy at normal speed.

| Journey | Artifact suffix | Reviewed outcome | Backend requests; prompt + completion tokens | Output Speech calls / characters | Last Live report |
| --- | --- | --- | --- | --- | --- |
| Recovery | `84e0fcead0` | Preparation/help, full terms, supplied perception, ready-help, explicit acceptance at zero actions, one fresh-cue welcome, confirmed Reflection and history-preserving return | 2; 9,295 + 364 | 8 / 1,281 | 148 s |
| Dialogue | `6c4cdaf94c` | No solution leak; help/perception/fragment spend nothing; keyboard feedback correctly rejects piano against the heard space clue; replacement needs acceptance; Stop is CANCELLED | 5; 29,834 + 694 | 9 / 1,590 | 223 s |
| Social | `7d93d0d610` | Explicit at-least-two-visible alternative, count/arrival/identity disclosure, one welcome, fresh-budget restart, preserved pause/resume and Stop without another action | 3; 15,630 + 637 | 7 / 1,078 | 148 s |

Artifact directories are `target/gptlive-acceptance-<suffix>`. All 24 returned
clips have separate playback-confirmed evidence. Visitor audio used the retained
Onyx cache (zero new visitor synthesis). The original per-run backend/input/Live
caps and separate 12-call/6,000-character output caps held. Every runner-owned
application, disposable schema and account was cleaned up. No deployment occurred.

Final verification:

- `python tests/gptlive/run_acceptance.py --java-tests all`: `719afcbfa6`,
  **1,016 Java cases: 1,015 passed, one intentional capacity skip, zero failures
  or errors**. All 180 current JUnit XML reports and totals are archived there.
- All **218 Node tests** and **11 acceptance-runner preflight checks** passed.
- Five focused browser checks passed in `a2ef282518` (four enabled, one disabled;
  three mode-specific skips), including real WebRTC, leading PCM silence,
  delayed input captions, normal Reflection and lost playback acknowledgement.
  The resulting cockpit screenshot was visually reviewed.
- `./mvnw.cmd -q -DskipTests package` and `git diff --check` passed. Logs and
  common source hashes are in `target/es08-speech-final-20261112`.

The three paid commands used `python tests/gptlive/run_acceptance.py --java-tests
none --paid-core coordination --coordination-scenario` with respectively
`escalation-dialogue`, `escalation-recovery`, and `escalation-social`.
The browser command used the focused grep documented in `tests/gptlive/README.md`.

**Limits:** these are synthetic microphone/sensor journeys through real Live,
Speech, application and MySQL. Local `faster-whisper` ASR recovered the required
terms, controls and task content; it did not recover some closing proposal
invitation phrases (and the initial dialogue review's inactive/invitation tail).
The separate reviews record this explicitly; no word-for-word acoustic guarantee
or physical speaker audibility is claimed. Recovery supplied an independently
transcribed Start invitation before acceptance; all journeys required explicit
acceptance and performed no premature action. Native contradictory drafts stayed
inaudible. Provider transcription remains imperfect; the silent structural-fragment
guard is deliberately narrow. Physical camera/microphone/speaker, room/classroom,
pedagogical and sustained capacity acceptance remain **NOT RUN / deferred** under
the owner's agreed scope. Optional ambient remarks have deterministic coverage,
not a separate paid acoustic acceptance. Fresh Coordination instances/current
clients are required for rollout; saved graphs are not migrated.

The ignored artifacts and private configuration do not transfer through Git.
Preserve them without committing raw provider traces or credentials. Stop here
for owner review and commit; deployment and capacity are separate decisions.

## Retained Speech implementation checkpoints and failures

Full Java regression `0a238faf78` passed 1,010 cases (one intentional capacity skip,
zero failures/errors). Focused Java and five browser checks passed in `95f5c50ebe`;
normal playback reaches Reflection, while lost playback acknowledgement does not
replay or claim delivery. The screenshot was visually reviewed. Earlier focused
runs `528b446edd` and `3e8bf320c9` retain a corrected mock-operation mismatch and
PCM content-type/fixture-count failures respectively. No failed evidence is erased.
First Speech recovery `4f4fa9d141` failed at perception playback confirmation.
The complete ready review and two preparation replies played, but the next clip
started with 490 ms of silence and missed the observer's fixed 500 ms onset window.
The clip was released, never confirmed played; no acceptance or action occurred.
Usage: one backend request, 5,306 prompt + 305 completion tokens, four output
Speech calls, zero new visitor synthesis, last Live report 163 seconds. PCM,
local ASR and a separate FAIL review are retained. The observer now derives its
onset bound from the known PCM plus the existing 500 ms margin; it does not trim
audio or extend the response expiry. The regression failed before this fix and
passes afterward. All five browser checks passed in `135e67b9f3`, including a
750 ms leading-silence fixture and the lost-acknowledgement case. Twenty-two
focused Node checks passed.

Recovery `01567dd05c` passed raw state/delivery assertions but **failed separate
semantic review**: ready-help incorrectly required a fresh demonstration before
acceptance even though preparation was complete. Live had admitted “I understand.
Do I need to demonstrate again”, omitting the negation from the synthetic input;
the remaining explicit question still required the authoritative readiness answer.
Usage: four backend requests, 21,081 prompt + 562 completion tokens, eight output
Speech calls, zero new visitor synthesis, last Live report 148 seconds. Local ASR
confirms the incorrect instruction. Readiness questions now use existing SUPPORT
facts, and the shortened “hand sign do you see now” uses supplied-evidence rendering.
Forty focused Java checks and nine journey-fixture Node checks pass; the recovery
assertion now rejects another demonstration before acceptance. Recovery is being
rerun after these causal fixes; no combined acceptance is yet claimed.

Recovery `3fd719b39c` subsequently passed automated and separate semantic review:
three backend requests, 15,261 prompt + 471 completion tokens, eight output Speech
calls, zero new visitor synthesis, last Live report 163 seconds. Independent local
ASR recovered all agreement terms and the explicit ready-help invitation, but not
the long review's closing invitation. No bit-exact acoustic fidelity is claimed.
Initial inactive wording plus ready-help established acceptance before the fresh
cue; one welcome reached confirmed Reflection and return preserved the outcome.

Dialogue `927bd51bd8` failed after a provider-only USER fragment `t: {` over silent
synthetic input interrupted otherwise correct keyboard feedback. The backend had
correctly rejected piano against the heard space/Enter clues; that reply never
played. Native “Correct, piano” output stayed inaudible. Four backend requests,
22,967 prompt + 497 completion tokens, seven output Speech calls (six returned
clips), zero new visitor synthesis, last Live report 178 seconds. Raw/PCM/ASR and
separate FAIL review remain. A narrow guard now retains tiny structural fragments
as incomplete audit evidence only when five seconds of continuous reflected input
was silent; real voice, ordinary words, gaps and missing audio retain normal
admission. The committed player uses its existing microphone worklet for immediate
interruption instead of delayed native captions. Focused Java and five browser
checks passed in `a2ef282518`; 31 focused Node checks pass. Browser coverage injects
the delayed caption during release/playback. The final three scenarios are being
rerun after this shared input repair; the prior recovery pass is historical.

## Retained native-output checkpoint (2026-11-12)

**Historical blocker (superseded by the approved Speech continuation):**
ES-08 was not complete. Earlier dialogue `234999817d` and
social `f963c96aa7` have separate semantic PASS reviews, but recovery is unmet and
the final shared capture/voice changes have no complete three-journey acceptance.
The owner has been asked to choose between using the existing Speech service for
committed Coordination output (retaining Live input and backend authority), or
requiring native-only output. This is a delivery-path decision under the owner's
architecture-preservation instruction, not a new approval rule from a skill.
The concrete seams are in `ES08_SPEECH_DELIVERY_DECISION.md`. No answer or output-path
change is assumed; no further paid run is in progress. All changes remain
uncommitted/unpublished.

**Final offline checkpoint:** `97d595caa0` passed **993 Java cases: 992 passed,
one intentional capacity skip, zero failures/errors**, using real disposable MySQL.
JUnit XML and totals are retained there. Its browser run exposed a malformed
synthetic fixture: caption media had no matching voiced PCM. The fixture now
supplies that interval and nonoverlapping ranges; its three focused Java cases and
all **five browser checks** passed in `2ab407b993` (four enabled, one disabled;
three mode-specific skips). Production runtime is unchanged between these two
offline runs. Corrected-playback screenshots were inspected. All **210 Node tests**,
**11 preflight checks**, packaging and `git diff --check` passed; logs and final
source hashes are in `target/es08-final-20261112`. These are implementation checks,
not completion of the remaining real Live semantic gate. The final literal-support
guard has offline coverage but no paid journey after it.

Latest recovery `307eb96b2c`: **FAIL**, with raw evidence and separate semantic
review/hash retained. All **772 reflected output chunks were timed**, none untimed,
and captured replies were complete. Both ready reviews omitted human judgement
and were withheld. **The initial played preparation reply added an unsupported
"Rock recognition looks good" claim before any demonstration; the semantic
verifier incorrectly approved it.** Do not describe this run as universally
fail-closed. Usage: six backend requests, 9,963 prompt + 645 completion tokens;
last Live report 148 seconds; zero new synthesis. No acceptance/action occurred,
and all owned test resources were removed.

Deterministic support/preparation/return replies now require their committed words
with the existing punctuation/case normalization, checked locally before release.
Extra or changed words reserve at most the same one unheard correction; there is
no provider verdict that can override that literal check. Semantic free wording
remains for proposal reviews and other nonliteral replies. This prevents the
observed unsupported preface but may withhold otherwise truthful paraphrases;
native delivery success remains unverified after this guard. Focused voice/
correction/approval/real-MySQL checks passed in `25d8b1e952`.

The dated repair checkpoints below are a chronological evidence trail, not claims
that all earlier open steps are still running or that later passes erase failures.

Recovery `a7242093ff` passed preparation, both help phases, full ready review,
perception, acceptance, one fresh-cue welcome and confirmed final playback. Return
committed Conversation and preserved LIMIT_REACHED, but its acknowledgement was
withheld: an earlier Reflection draft consumed the ordinary approval attempt as
UNRELATED before the confirmed return reply arrived. The separate FAIL review and
journey hash are retained. Usage: ten backend requests, 16,167 prompt + 943
completion tokens, last Live report 253 seconds, zero new synthesis; owned
resources were cleaned up.

Return now uses the existing compact, verbatim confirmed-support path and its
post-commit generation fence. It preserves history/outcome and original 45/60-second
deadlines without another action. All 84 focused voice/correction/approval/recovery/
real-MySQL cases passed in `74591d4894`. The preceding `3a609acc2e` failed one old
test's location assertion (content moved from quiet context into the atomic
speaking occasion); that assertion was corrected.

Recovery `49d99fd0ac` failed before reaching return: initial preparation/help
played, but the ready review repeated the goal/roles/success in over 100 words and
exceeded the unchanged 30-second capture limit. The incomplete main segment and
unmatched suffix were withheld. Usage: three backend requests, 6,935 prompt + 449
completion tokens; last Live report 133 seconds; zero new synthesis. No activation
or action occurred; owned resources were cleaned up and the FAIL review retained.
The existing 55-word instruction was embedded in review data while surrounding
instructions requested faithful reading. The next repair promotes that constraint
to Coordination voice instructions and explicitly asks to combine duplicated
facts without omitting distinct terms. No capture/deadline bound is extended.
All 49 focused voice/correction/acceptance/real-MySQL cases passed in
`766c9465bd`. Intermediate offline attempts retained: `ec82b09429` caught the
instruction-byte ceiling, resolved by compressing redundant voice prose;
`28ac30efbe` then caught an obsolete literal-string assertion, updated to the
equivalent shorter wording. The next capped recovery is `4f6bfff44a`.

Recovery `4f6bfff44a` failed at a concise ready review marked
INCOMPLETE/audio_coverage_gap. Preparation/help played; help used one correction
after an unsupported rock claim. No ready review, activation or action played.
Usage: four backend requests, 7,741 prompt + 524 completion tokens; last Live
report 148 seconds; zero new synthesis. Owned resources were cleaned up.
The FAIL review retains the distinction between observed aggregate PCM timing
and an unproved exact transport cause. One output window has only 9,600 samples,
followed by larger 28,800-sample windows; the browser retains an audio clip.

A deterministic reproduction then established a capture defect: a delayed packet
permanently poisoned a segment even when all delayed PCM arrived in the following
burst. Capture now tracks the missing sample duration and lets only actually
received catch-up PCM repay it before closure. An unresolved deficit above the
same 500-ms tolerance still yields INCOMPLETE. Silence, 30-second open capture,
45/60-second response/playback, interruption and late-after-commit bounds remain.
The reproduction fails on the old implementation; 35 focused capture/PCM cases
passed after the repair. The new case now covers both speakers, and the integrated
Java/real-MySQL/browser run is `0d1f5d807a`. No gap is forgiven from a caption or
browser buffer alone. Test logs: `target/es08-final-20261112/capture-jitter-*.log`.

That final integrated run passed: 986 Java cases, **985 passed / one intentional
capacity skip**, zero failures/errors; JUnit XML/totals retained in the artifact.
Four enabled and one disabled browser checks passed, with three mode-specific
skips. The corrected-playback screenshot was inspected. All 210 Node tests,
11 runner preflight checks, packaging and whitespace checks passed. Source hashes
and final logs are retained in `target/es08-final-20261112`. Recovery regression
`aaa1d88646` now checks the capture repair on the final integrated runtime.

Recovery `aaa1d88646` passed preparation/help, a concise full ready review and
fresh perception, then failed when ready-stage help was marked incomplete for
audio coverage. No acceptance/action occurred. Usage: six backend requests,
14,806 prompt + 692 completion tokens; last Live report 208 seconds; zero new
synthesis. Owned resources were removed and the separate FAIL review is retained.
The arrival-deficit repair alone did not resolve this run.

Further reference review found that **reflected sideband output** carries
`start_ms`/`end_ms`, unlike primary WebSocket output. The previous capture code
ignored those fields. The [official sideband reference](https://developers.openai.com/api/reference/resources/live/sideband-websocket)
and [server-control guide](https://developers.openai.com/api/docs/guides/voice-server-controls)
describe ranges and dropped-frame gaps. The generic conversations guide's untimed
output description must not be applied to reflected sideband events.
Capture now uses valid reflected ranges to check coverage across the transcript,
with bounded merged ranges. Packet arrival delay and gaps outside the utterance
cannot poison fully covered speech; actual gaps, invalid ranges and overlap still
fail, and replayed PCM cannot count as fresh silence. Untimed input/compatibility
events retain the received-sample deficit check. The same 500-ms tolerance,
silence, open-capture and response/playback limits apply. Scoped capture diagnostics
count timed/untimed output chunks without retaining audio. Focused capture/PCM/
real-MySQL delivery tests passed in `96d81fab07`; the earlier full regression
predates this range-aware change and will be rerun after Live verification.

Ready-review verifier refinement passed all 26 labelled cases in
`target/es08-final-20261112/verifier-ready-review.json`: 25 provider requests plus
one exact-word fast path, 21,229 prompt + 1,353 completion tokens. The retained
complete review matches, while premature activation, immediate execution instead
of a fresh cue, missing human judgement and repeated preparation remain rejected.
The focused 78-case verifier/correction/approval/real-MySQL suite passed in
`294ec38b7e`; packaging and whitespace checks passed again. The probe's published
two-pass ceilings are now 52 requests and 60,000 reported tokens; this run used
one pass only. Live scenario limits are unchanged.

Recovery `de092f721a` failed earlier, before the refined ready-review path: native
speech invented a rock observation without supplied recognition evidence, and its
correction omitted the demonstration request and judgement role. Both were
withheld. Usage: three backend requests, 7,501 prompt + 451 completion tokens;
last Live report 88 seconds; zero new synthesis. No speech played, activation or
action occurred; owned resources were removed and the FAIL review is retained.

Pending preparation now receives an explicit proposal review too: recognition is
unconfirmed for the outstanding checks, demonstration precedes acceptance, and
all saved roles/limits remain available. Long reviews retain this projection in
the bounded full context instead of reverting to broad agreement JSON. Shared
remaining-check rendering also guides support. All 87 focused
proposal/preparation/support/correction/acceptance/social/real-MySQL cases passed
in `2fd2f4ef7b`. A further capped recovery journey is checking this concrete repair.

Recovery `f5eeb16699` played its complete initial pending proposal and help, but
then withheld two ready-stage responses that reduced the requested repeated full
review to readiness/cue status. Usage: six backend requests, 15,038 prompt + 701
completion tokens; last Live report 148 seconds; zero new synthesis. No action or
activation occurred. Owned resources were cleaned up and the FAIL review remains.

The pending full-review prototype was replaced with a simpler sequence: a complete
proposal requiring preparation first gives a short deterministic demonstration
request, with no acceptance invitation. Once observations satisfy preparation,
the full saved terms are reviewed before acceptance. The same existing states,
checks, original deadlines and finite budgets apply. This also avoids presenting
the whole proposal twice. Long ready reviews still retain their explicit projection
in bounded context. All 86 focused cases passed in `fb96c22e0e`; a further capped
recovery run checks the final sequence. No broader voice-path change is involved.

Latest batch: **dialogue PASS** in `234999817d`, with separate semantic review.
The played keys/no-door question and piano feedback match; help explicitly reports
active state, perception/fragment support preserve authority, final delivery enters
Reflection, and replacement/renewed acceptance/new question/Stop complete. Usage:
15 backend requests, 36,242 prompt + 1,351 completion tokens; last Live report
283 seconds; zero new synthesis. Earlier wrong/uncommitted native drafts were
withheld. Raw journey, PASS review/hash and source manifest are retained.

Recovery `e9658d3d65` passed complete preparation help, then failed when the verifier
rejected a semantically complete ready review twice. The review states completed
checks, fresh rock after acceptance, one workshop welcome, usefulness judgement,
one-action total and an acceptance question. Usage: five backend requests, 8,896
prompt + 586 completion tokens; last Live report 148 seconds; zero new synthesis.
No activation/action occurred. The retained candidate is being added as a positive
review fixture with negative activation/cue/role contrasts. Social regression `f963c96aa7` also passed full semantic review: explicit count
alternative, exactly one played welcome with confirmed delivery, fresh restart,
pause/resume and Stop. Usage: 12 backend requests, 22,191 prompt + 1,236 completion
tokens; last Live report 223 seconds; zero new synthesis. One welcome correction
remained within the same spent contribution. All three runs closed their sessions
and removed owned app/schema/account resources. Recovery remains the only open
scenario; none of the two passes certifies physical operation.

Latest integrated result: **social PASS** in `20c42b6d65`, including separate
semantic review of played speech, durable state and final playback. The exact
visible-count alternative was accepted; a nonmatching cue spent nothing, one
matching cue produced one welcome, and restart/pause/resume/stop preserved their
run/budget contracts. Earlier incorrect drafts remained unheard. Usage: 10 backend
requests, 20,801 prompt + 1,103 completion tokens, last Live report 208 seconds,
zero new synthesis. This is one synthetic pass, not a reliability-rate estimate.

Recovery `e78026068f` failed at preparation help: both native candidates omitted
that recognition alone cannot start the task. They were withheld, and later steps
were not exercised. Usage: four backend requests, 7,561 prompt + 521 completion
tokens, last Live report 118 seconds, zero new synthesis. The next repair will
make this help a concise complete instruction and explicitly request its wording.
Both runs retained raw journeys plus semantic reviews/hashes and cleaned up their
owned sessions/apps/disposable database resources. Dialogue `a036a052d2` subsequently passed automated lifecycle/playback assertions
but **failed semantic review**. The backend itself committed a keys/space/enter
question (keyboard), then generated incorrect positive feedback for piano. The
verifier correctly matched each committed response; this is factual feedback
failure, not verifier drift. Played help also omitted an explicit active-state
answer. Usage: 16 backend requests, 36,549 prompt + 1,418 completion tokens, last
Live report 298 seconds, zero new synthesis. The raw journey and separate FAIL
review remain retained; the successful harness exit is not acceptance.

Feedback now receives the previous actually delivered contribution as explicit
current-turn data, selected by the existing response/owner/revision/segment and
playback fences rather than the latest ordinary help. Receipt-aware speech offsets
remain respected. Guidance evaluates all defining clues and rejects partial fits;
no new task engine or additional per-turn inference was added. The fixture accepts
an evaluation of either polarity, but factual correctness remains a required
semantic review of the actual question/answer/feedback triple.

Preparation help is now a compact complete instruction with explicit acceptance
after recognition. Deterministic support requests its complete wording, including
the same text on its one correction. Focused verification passed 78 Java/real-MySQL
cases in `c0c546f043` and eight Node fixture cases. The earlier `28d2f4d226` caught
outdated test wording and a synthetic played=false fixture; the runtime represents
pending playback by absence of played, so the fixture now uses that contract.

A three-request factual-feedback probe used the actual current-turn builder and
retained production request context. All three reviewed responses were correct:
partial-fit piano versus the multi-clue question, matching keyboard, and matching
piano versus the single-clue question. Total: 17,472 prompt + 314 completion tokens
(17,786 reported); no Live/database work. Detailed requests/results and review are
in `target/es08-final-20261112/feedback-guidance-probe.json`. This probe does not
replace the integrated journey. All 210 Node and 11 runner preflight checks also
passed after these repairs; full Java/browser regression `c274cc1144` passed 980 Java cases with one
intentional capacity skip, four enabled-browser cases and one disabled-browser
case (three mode-specific skips). JUnit XML is retained in that artifact; the
corrected-playback screenshot was inspected. Packaging and whitespace checks
also passed. A final capped three-scenario batch is running on this tree.

Full offline checkpoint `26ed6ce58a` passed 973 Java cases with one intentional
capacity skip, four enabled-browser checks and one disabled-browser check (three
mode-specific skips). JUnit XML is retained inside the artifact. All 210 Node and
11 runner preflight checks passed. These results precede the final prompt repairs
below and do not close the Live gate.

A two-request `low`-reasoning comparison preserved both the description-like
welcome and the riddle rejection (4,293 prompt + 259 completion tokens; no Live,
database or configuration mutation). Inspecting the actual cue request then found
contradictory instructions: Generic asked for a description, "not a finished
utterance", while Coordination asked for actual words. Coordination now overrides
that inherited guidance through a protected specialization; Generic keeps its
existing behavior. Focused Generic/Coordination/verifier/DB tests passed in
`884a1efab9` after fixing a missing test import exposed by `62760bf3f2`. All review
tests passed in `edbfc816b7`.

One controlled replay replaced only that guidance with the current implementation:
the same Luna/none model now returned "Welcome, everyone." (3,749 prompt + 52
completion tokens). This is causal diagnostic evidence, not integrated acceptance.
The 20-case verifier check passed 19, still rejecting omission of optional "many"
in the retained keys riddle (19 provider requests; 11,597 prompt + 1,014 completion
tokens). Guidance now explicitly distinguishes that paraphrase from lost numeric
counts or distinctive multi-clue content; the fixture includes both negatives.
Proposal reviews also request combining repeated facts within 55 words, without
dropping roles/success/budget or changing production capture/deadline limits.

Artifacts for these small probes are in `target/es08-final-20261112/`. The broader
speech-path question is deferred while these concrete repairs are checked within
the existing architecture; the alternative remains unimplemented.

The refined verifier passed all 21 labelled cases in
`target/es08-final-20261112/verifier-explicit-emphasis.json`: 20 provider requests,
13,031 prompt + 1,067 completion tokens and one exact-word fast-path case. Numeric
and distinctive-clue loss remain rejected; only optional emphasis was clarified.
Support replies now share the post-commit generation fence with proposal reviews,
addressing `3649b59a63`'s early acknowledgement consuming correction before the
confirmed help. Other ordinary conversation retains the prior allowance. Focused
capture/support/correction/approval tests passed in `a31db06a93`. A further capped
three-scenario batch tests these concrete changes.

Captured request inspection also found that `TaskMemory.messages` bypasses the
shared heard-speech projection and includes native drafts whose playback is false.
The current batch remains unchanged for attributable evidence. The implemented repair
now filters those drafts before applying the dialogue-history bound, so rejected
speech cannot be treated as conversation or crowd out the heard question.

## Post-prompt batch and causal repairs (2026-11-12)

The subsequent capped batch also retained three failed journeys, each with a
separate semantic review and hash of its raw journey. No new synthesis was needed;
all sessions requested closure and their owned app/schema/account were removed.

| Scenario / artifact | Reviewed result | Backend usage | Last reported Live usage |
| --- | --- | --- | --- |
| Dialogue `6fdad58f6a` | All turns through feedback played and final delivery confirmed. Feedback borrowed an unheard space clue; replacement then failed with `context_delivery_unconfirmed`. | 10 requests; 26,885 prompt + 981 completion tokens | 238 seconds |
| Recovery `8a3c1d95c3` | Initial preparation/help played; ready review implied checks remained, omitting their confirmed completion. Both candidates withheld. | 6 requests; 14,411 prompt + 691 completion tokens | 133 seconds |
| Social `867b9afcc6` | Count alternative, arming and actual "Welcome, everyone!" played; final delivery confirmed. Exact echoed agreement caused RESTART validation to reject. | 7 requests; 18,402 prompt + 898 completion tokens | 163 seconds |

`TaskMemory.messages` now excludes explicitly unplayed drafts before its 20-turn
bound, using the shared projection rule. Legacy speech/backend-intent handling
and the raw audit history remain unchanged. The new regression places 25 rejected
complete/incomplete drafts after a heard question and verifies the question stays
in the actual task prompt.

The Live sideband now serializes text writes with a dedicated lock and shares its
existing send deadline across queueing and transport completion. Inbound callbacks
remain independent; queued commands cannot send after closure. The observed
replacement failure coincided with concurrent delegation/context sends; that
causal attribution remains an inference. The underlying transport race is real:
[JDK WebSocket](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/WebSocket.html#sendText(java.lang.CharSequence,boolean))
rejects overlapping text/binary writes. Mock-transport regressions exercise overlap,
inbound delivery during a pending send, closure and stalled-send timeout.

Ready reviews now explicitly retain completed preparation, which their former
compact projection dropped. RESTART tolerates only an exact redundant echo of the
accepted agreement; changed terms are still rejected. Questions, quoted or deferred
restart requests cannot activate a new run. Focused tests initially caught a test
expectation confusing the COMPLETED phase with its CANCELLED outcome (`5c3ab25f98`);
the expectation was corrected without changing lifecycle behavior. All 82 focused
cases then passed in `900308c81f`, including real MySQL delivery coverage.

## Retained ES-08 scenario batch before final prompt repairs (2026-11-12)

ES-08 remains **unaccepted**. The final native-output batch preserves every failed
attempt, with synthetic Onyx visitor audio, Live `gpt-live-1`/Willow and backend
`gpt-5.6-luna`/`none`. No provider settings or production application were changed
by this batch. Each scenario retained the declared 360-second session, 12-turn,
24-request, 2,048-completion-token/request, 80,000-input-byte/request,
200,000-reported-token cutoff, 12-clip/6,000-character synthesis and 420-second
child-wall caps. The reported token cutoff can be crossed by its final request.

| Scenario / artifact | Reviewed result | Backend usage | Last reported Live usage |
| --- | --- | --- | --- |
| Dialogue `deaaab2cc2` | Full proposal reviewed and played; first question omitted "many", was rejected, and no usable correction arrived. PAUSED/UNCERTAIN with one spent contribution. | 4 requests; 12,206 prompt + 447 completion tokens | 148 seconds |
| Recovery `35b5b3a3fc` | Initial preparation and help played; acceptance review exceeded capture duration and remained unheard. No activation/contribution. | 4 requests; 12,212 prompt + 565 completion tokens | 148 seconds |
| Social `e6379034d8` | Correct count alternative and arming played; backend committed "A brief welcome to the visible group", which Live repeated. Both drafts were rejected as an actual contribution; final delivery UNCERTAIN with one spent action. | 6 requests; 11,794 prompt + 630 completion tokens | 148 seconds |

All three runs used cached synthesis, requested session closure, stopped the owned app
and removed their disposable database/schema account. Later scenario steps were
not exercised; these are not partial acceptance passes. Review considers
`trial.speech_played` separately from all captured/unheard assistant drafts.
Each artifact also contains `semantic-review.json`, with a FAIL assessment and
SHA-256 of the unchanged raw journey. The near-equivalent riddle pair needs a
labelled verifier check; omission of "many" is not by itself proof of a materially
different riddle. The required playback still failed.

A broader output-path alternative and its concrete implementation/test seams are
prepared in `ES08_SPEECH_DELIVERY_DECISION.md`. A scope question is pending because
backend-written synthesized replies would change the selected native Live output
path; the standing task instruction calls for preserving the architecture.
The social failure concerns backend content as well as delivery: the existing cue
guidance already requires actual words, but the returned wording is description-like.
Both verdicts were AMBIGUOUS; their exact rationale is not returned. Synthesizing
that same text would not by itself establish a useful welcome.
The alternative is not implemented. Physical trials remain NOT RUN and deployment
capacity remains deferred.

## Ordinary committed-reply correction (2026-11-12, implementation and investigation)

Under the owner's authorization to finish ES-08, one bounded correction now also
applies to an ordinary current Coordination reply. The persisted response retains
its original identity, owner/revision, content and 45-second expiry; at least ten
seconds must remain. The replacement must start after reservation. Release is
persisted before permission is returned, fencing replay after in-memory window
loss. A valid second rejection, malformed verdict, provider error or interruption
cannot reserve another correction. No task action is executed or refunded.
Sensory remarks and help attached to a pending task response remain excluded.

This addresses retained dialogue `3920e39007`: the labelled review produced an
unheard draft that quoted the riddle before acceptance and omitted the invitation.
Approval correctly rejected it, but the task-only correction scope left the
ordinary proposal without a delivery path. Two requests used 6,257 backend tokens;
last Live usage was 58 seconds, no new TTS clips, and owned cleanup completed.
The scope extension uses the existing response, approval service, speaking occasion
and player rather than replaying a task or weakening the semantic check.

Focused Java and real disposable-MySQL checks passed in `2e3362a1d3` and
`9cdeedca8b`. Final approval/correction unit checks and both production-browser
delivery paths passed in `08548b505b`. The ordinary reservation remains recorded
in the approval window so concurrent stale candidates cannot trigger extra checks;
an already reserved correction does not consume a second window-capacity slot.

Dialogue `78343a186e` stopped before approval: the proposal segment was INCOMPLETE
with `audio_coverage_gap`, and no reply was played. Reflected-audio telemetry shows
a temporary output-sample shortfall followed by catch-up, but those audio chunks
have no provider timestamps that could prove continuous coverage. The conservative
capture guard is unchanged. One backend request used 5,233 prompt + 210 completion
tokens; last Live usage was 58 seconds, no new TTS clips, and owned cleanup
completed. This run did not exercise ordinary correction. The next trial moves to
the independently required recovery scenario on the same implementation.

Recovery `742a65602f` delivered preparation help and a truthful scissors report,
then withheld the ready-state help before verification. Its final USER receipt
preceded the first ASSISTANT receipt in the same millisecond; capture persisted
millisecond precision while the approval window compared a finer-resolution
Instant. Approval now uses bounded, deduplicated sideband receipt order only to
disambiguate that millisecond for ordinary pre-commit drafts. Earlier speech,
missing order, later input and task output still fail closed. Seven requests used
9,272 prompt + 703 completion tokens; last Live usage 178 seconds, four new TTS
clips, owned cleanup complete. `90e26b1d11` passed the focused approval/session
checks and real-MySQL delivery regressions, including seven timestamp/order cases.
The run also demonstrates ordinary correction of two unheard drafts, but its
heard preparation invitation did not review the proposed terms; it is not a
complete semantic acceptance pass.

Prepared sensor invitations now review the saved roles, success criterion, total
action budget and fresh-cue requirement before acceptance. The generic identified
proposal review serves both dialogue and sensor agreements; count alternatives
retain their specialized disclosure. The first focused run (`c1ffabe526`) passed
78/79 cases and exposed one assertion still looking for the review in quiet facts.
After updating it to the identified speaking occasion, all 20 voice cases passed
in `b54c72b86c`.

Social `0d9df29d22` rejected an unheard proposal that first suggested a verbal cue
and then described the count alternative. Its one correction was captured, but
the harness's 40-second heard-response wait ended before approval/playback could
finish within the existing 45/60-second production limits. No speech was played.
The ES-08 wait is now 65 seconds to observe those limits; neither production
deadlines nor the 360-second session / 420-second child cap changed. Syntax and
all seven escalation-fixture tests passed. Usage: two requests, 6,248 prompt +
354 completion tokens, last Live usage 58 seconds, five new TTS clips, cleanup
complete. A further social run tests this harness repair; the failed run is retained.

Social `8f6896c81e` reached the second verification under the corrected wait, but
both drafts were rejected. The correction stated the count/limit without the
agreed welcome, roles or success criterion: the specialized count-review message
itself omitted those fields. That message now combines its executable count
disclosure with the full saved proposal review. All focused social/voice/context
checks passed in `1c5c565227`. The failed run used three requests, 7,100 prompt +
430 completion tokens; last Live usage 88 seconds, no new synthesis and complete
owned cleanup. No speech played and no acceptance/action was inferred.

Dialogue `19dfb480b5` delivered a corrected invitation, then stalled after START.
Task association checked a sentence suffix from an early mixed segment and left
the response REJECTED; whole-audio approval correctly discarded that segment,
but the later complete requested question could no longer claim the response.
It expired into PAUSED/UNCERTAIN with one spent contribution and no task audio
played. Five requests used 12,896 prompt + 517 completion tokens; last Live usage
133 seconds, no new synthesis, cleanup complete. Approval-enabled task association
now considers whole segments only: early mixed segments stay audit evidence and
leave the response pending for a later authorized whole clip. Other agents retain
receipt-aware suffix association. The changed real-MySQL regression verifies
that the later whole reply reaches confirmed Reflection without another action.
Focused task/approval checks and real-MySQL delivery passed in `5c047bac0b`, followed
by both production-browser delivery paths (direct approval and rejected/corrected
speech to Reflection). All 209 Node and 11 runner preflight checks also passed;
Node output is retained in `target/es08-final-20261112/node.log`.

Dialogue `0a668e312f` confirmed the whole-clip repair: its proposed two-contribution
round and first riddle were both approved and observed playing, with one action.
The harness then falsely treated two occurrences of "keys" as two questions in
"I've got keys but no locks. What object has keys that cannot open a door?"
There is one question. The assertion now counts question boundaries, with a
regression accepting this exact setup/question and rejecting repeated questions
or a promise to ask later. Semantic clue/leak review remains mandatory. All eight
escalation fixture tests pass. The retained incomplete journey used four requests,
12,050 prompt + 410 completion tokens, last Live usage 58 seconds, no new TTS clips,
and complete owned cleanup. The next dialogue run tests this harness correction.

Dialogue `4f30a7188c` delivered the proposal but joined an early, unauthorized
riddle to the later confirmed question without an acoustic boundary. Whole-clip
association and the browser withheld it; the response expired to PAUSED/UNCERTAIN
with one spent action. Three requests used 11,518 prompt + 358 completion tokens;
last Live usage 118 seconds, no new synthesis, cleanup complete. The bridge now
waits for an already active assistant draft to finish before planning a required
approval-enabled speaking occasion. This uses capture's in-memory activity and
the existing scheduler, with no sleeping worker or repeated graph reads while
busy. Quiet, supersession, closure and original expiry are separate boundaries;
the response deadline and action budget never restart. Other playback modes and
optional ambient comments retain their existing behavior.
The focused bridge/capture/segmenter and real-MySQL tests passed in `c5fcba5f07`,
as did both browser delivery paths. The quiet-wait state publication ordering was
then tightened before the browser compilation. The correction screenshot from
`5c047bac0b` was reviewed: only approved speech appears in delivered conversation;
controls and status are readable. This is synthetic UI evidence, not physical audio.

Dialogue `960c5a7650` passed proposal, task question, help and current perception,
including one checked task correction. It then rejected both incomplete-answer
support drafts. The corrected words requested completion and preserved the
agreement, but omitted the separately required state-unchanged reassurance. The
committed clarification now simply asks to finish the request; state/agreement/
budget preservation remains enforced and tested rather than narrated. The run
used ten requests, 15,630 prompt + 854 completion tokens; last Live usage 208 seconds,
no new synthesis and complete cleanup. Feedback/replacement/stop were not reached.

Dialogue `ca20ba119a` failed at proposal delivery before exercising the shorter
clarification. Both reviewed drafts omitted terms or performed the riddle early;
neither played. Three requests used 6,678 prompt + 329 completion tokens, last
Live usage 88 seconds, no new synthesis, complete owned cleanup. The backend
already generates a proposed spoken review, but the Live path discarded it and
reconstructed speech from task instructions. Approval-enabled ready proposals now
retain that bounded wording and ask Live to realize it; semantic verification
still uses the complete saved agreement. The backend guidance requires all review
terms and forbids performing the riddle. Oversized/absent wording retains the
existing bounded review fallback. Focused runs `ffc309aa42` and `bd8fa6e345`
exposed an outdated assertion and the deterministic proposal overwrite; after
fixing both, all 64 voice/correction/context cases passed in `3ffcc5a0c3`.

Dialogue `017c6615ad` played a reviewed, inactive two-part riddle proposal without
the solution. The harness failed to recognize "Say if you want me to start" as
an invitation, so acceptance and later turns were never attempted. The saved
agreement retained both roles and the question-before-feedback success criterion;
the spoken review described that exchange and its two-part bound. The pacing
expression now accepts this explicit invitation, with all eight fixture tests
passing. Two requests used 6,091 prompt + 270 completion tokens, last Live usage
88 seconds, no new synthesis and complete owned cleanup. This is retained as an
incomplete journey; a further dialogue run tests the harness repair.

Dialogue `3291366c40` rejected an early task-performing draft and then the
backend-generated review, which omitted the saved human responsibility. No
speech played. Three requests used 6,873 prompt + 370 completion tokens, last
Live usage 88 seconds, no new synthesis, cleanup complete. The proposed-speech
optimization is removed: ready reviews now render all four saved agreement fields,
the total limit and any count condition as explicit review text. This avoids
depending on an independently generated summary to retain required terms. The
original bounds and oversized-context fallback remain. The first focused run
(`7379492546`) passed 79/80; the remaining assertion was corrected to inspect the
existing full-context path for a review larger than the atomic-message limit.

All 80 focused review/correction/context checks passed in `0b0e801bb7`.
Dialogue `72841d5726` captured the complete saved review, but an earlier short
"Ready when you say go" draft reserved correction while the confirmed review
was already being delivered. That review lacked a matched whole audio clip;
the duplicate correction completed beyond the original window. Nothing played.
Two requests used 5,928 prompt + 250 completion tokens, last Live usage 88 seconds,
no new synthesis, cleanup complete. Proposal review requests now explicitly
require post-commit generation, so an early acknowledgement cannot consume the
check/correction before the confirmed review. Other ordinary current-turn replies
retain their existing pre-commit allowance. Approval, voice, correction and real
MySQL delivery regressions passed in `b1c0d1c44b`.

Dialogue `3649b59a63` played the proposal and first riddle with one contribution.
Both help drafts omitted the active-state answer and were withheld; later turns
were not reached. Six requests used 13,190 prompt + 567 completion tokens, last
Live usage 133 seconds, no new synthesis, cleanup complete. Spoken support now
starts with its human-readable explanation instead of an internal state label;
active dialogue help does not mention independent sensor reactions, and Pause
versus Stop is distinguished. Focused support/capture/voice/approval checks passed
in `5f1b1741bd`.

## ES-08 completion continuation (2026-11-11)

The owner authorized continuing through all three integrated behavioural journeys.
Short confirmed support replies now use the existing compact speaking occasion:
content and permission arrive together, while background state remains quiet.
Approval, task authority, deadlines and the task-only correction scope are unchanged.

Focused checks: 122 cases across capture, voice, support, perception, context
delivery and approval. The initial run (`6ff8523ac9`) passed 121 and found one old
assertion expecting help text in quiet facts; it now checks the speaking occasion.
All 20 support cases then passed (`6e651523e3`).

Paid dialogue `1c5d882726` stopped before reaching the clarification change. The
proposal was approved and played, but synthetic "Let's start" produced no USER
transcript. The provider emitted those words as ASSISTANT output instead; reflected
input telemetry confirms voiced input reached the server. No consent was inferred,
and the task stayed inactive. This is retained as a failed input-attribution
attempt, not a pass or evidence about the clarification fix. Two backend requests
used 6,279 tokens; last reported Live usage was 104 seconds. Owned cleanup completed.

The next controlled attempt changes only the synthetic visitor voice from Alloy
to Onyx to distinguish it from Live Willow; scenario words and USER-transcript
requirements remain unchanged. This tests a possible synthetic-voice ambiguity,
not a proven diagnosis or a production input-reliability fix. Stimulus model/voice
are now recorded in each journey artifact. No unchanged reroll is authorized.
Dialogue `fcd428a805` then passed input attribution and the repaired clarification:
"Go on, please; the agreement and state stay the same." A first feedback draft
omitted the committed piano explanation, was rejected, and reserved the one
unheard correction. The corrected piano feedback was approved, released and
played exactly once; the original response/deadlines and two-contribution budget
were preserved, and final delivery reached PLAYBACK_CONFIRMED/COMPLETED.

The harness then failed on its obsolete `trial.playback_ack` assertion: it recorded
the direct player's final HTTP acknowledgement but not the approval player's.
Production diagnostic and persisted state both confirmed acceptance. The harness
now records the actual production-player HTTP result and awaits an acknowledgement
matching the current delivery ID and segment, rather than any historical cue.
Revision/stop were not reached; this remains an incomplete attempt. Usage: ten
backend requests, 20,596 prompt + 872 completion tokens, last Live usage 164
seconds, eight new Onyx stimulus clips. Owned cleanup completed. A rerun is
justified by this harness repair; no production completion check was relaxed.
Dialogue `c692869b6b` admitted explicit "Let's start" correctly, but inference
labelled the first riddle contribution STEP while the draft was READY. Validation
rejected it, preserved the inactive proposal and gave truthful recovery guidance;
the journey failed. Four backend requests used 12,534 tokens; last Live usage 88
seconds; no new TTS clips. Owned cleanup completed. Onyx is a controlled stimulus
choice, not a guarantee of input attribution.

Explicit acceptance of a ready proposal now selects START in the backend. The
existing inference supplies its first contribution; START/STEP labels cannot
change that selected operation, and proposed replacement terms or other operations
still fail closed. Negation, questions, quotation, future intent and incomplete
fragments retain their existing guards. Focused acceptance/lifecycle/capture/agent
regression passed in `e6f3f56ee2`; no extra provider request or task engine was added.
Dialogue `9caad663b7` failed before acceptance: its abbreviated proposal said only
"two lines total" and omitted the human role and success condition. Approval
rejected it; no proposal audio played. Two backend requests used 6,234 tokens;
last Live usage was 58 seconds, no new synthesis, and owned cleanup completed.
The ready-dialogue review now puts its exact goal, both roles, success criterion,
limit and inactive/acceptance status together in the identified speaking occasion.
Long reviews use the existing bounded fact chunks; oversized ones retain full
context without dropping terms. This changes realization guidance, not verification.
Integrated results and final review remain pending.

## One bounded unheard task correction (2026-11-11)

Owner-authorized continuation after reviewing the ES-08 commits. **ES-08 remains
unaccepted.** Implementation permits one corrective realization only for a valid
semantic rejection of an unreleased, approval-enabled Coordination task response.
The scoped player's complete audio match and current interruption/owner checks
precede reservation. The response identity, contribution count and deadlines are
unchanged; ten verification seconds must remain. Correction has a separate
speaking-occasion identity, while playback keeps the original response identity.
Release is persisted before its acknowledgement, preventing replay after window
or acknowledgement loss. Provider/capture failures and a second mismatch retain
recovery. No ordinary/help/sensory retry or new task engine was added.

Verification:

- **109 focused Java cases passed** in `target/gptlive-acceptance-9aa6d688ed`,
  including real disposable-MySQL reload/ownership and release uncertainty.
  Its selected JUnit reports are retained under that artifact's `junit/` directory.
  A later late/incomplete-candidate regression and the final ordinary commit
  notification mapping are included in the full regression recorded below.
- **Three production-browser checks passed** in
  `target/gptlive-acceptance-04245ec0b8`: real local WebRTC capture/silence,
  the existing approved-playback path, and rejected-first/corrected-second playback
  with one contribution and confirmed Reflection. The correction screenshot was
  visually inspected: only the checked replacement appears in conversation.
- **209 Node tests and 11 runner preflight checks passed.** Node output is retained
  in `target/es08-correction-20261111/node.log`.
- Full disposable-MySQL regression: **943 cases, 942 passed, one explicitly gated
  capacity case skipped, zero failures/errors**; artifact
  `target/gptlive-acceptance-019cfb4cc1`, with reports preserved under `junit/`.
- Final browser regression: **five checks passed** in
  `target/gptlive-acceptance-ece516fc9b`: four with Live enabled (WebRTC silence/
  release, original and corrected Coordination playback, neighboring scoped
  cockpit) and the legacy scoped cockpit with Live disabled. The three Live-only
  cases were explicitly skipped in the disabled run. Owned apps and disposable
  schema/accounts were cleaned up after both runners.
- `mvnw.cmd -q -DskipTests package` and `git diff --check` passed. These runs
  include the final response-identity mapping and late/incomplete-candidate fence.

Retained failures: the first focused run needed corrected test setup for the
approval opt-in and the Storage helper's package boundary. The first browser
correction attempt (`37a827c300`) exposed missing PCM lead-in after the synthetic
provider's idle interval (`audio_coverage_gap`). Review also identified that a
correction occasion ID must not replace the browser's response ID. After those
repairs, `9aa6d688ed` confirmed both playback paths but continuously emitted
synthetic silence kept the activity footer at “Live output activity.” The fixture
now supplies the next utterance's PCM lead-in and its complete quiet tail without
inventing idle output activity. The subsequent three browser checks passed.
Production capture timing, semantic verification and the footer assertions were
not relaxed. Failed artifacts remain intact; no paid rerolls were made.

At the owner's request, a read-only Heroku config inspection found no model/effort
overrides on Valerian. The deployment branch's `openai-prod.properties` routes all
five inference purposes to `gpt-5.6-luna` / `none`, with `gpt-5.2` as the fallback.
Once the owner supplied a private local key, the ignored local provider file was
set to Luna / `none`, preserving the key and other settings. The paid trace below
confirms those effective settings. No deployment configuration was changed.

### Paid dialogue: clarification withheld, journey incomplete

Command: `python tests/gptlive/run_acceptance.py --java-tests none --paid-core coordination --coordination-scenario escalation-dialogue`.
Retained artifact: `target/gptlive-acceptance-74a86512da`, including `journey.json`,
`provider.jsonl` and a separate `semantic-review.json`; the automated report is
unchanged. This was one run, with the previously declared ES-08 caps and
`gpt-live-1` / `willow`, backend Luna / `none`.

- Four complete replies passed approval and observed playback: an inactive,
  bounded proposal; one keys/no-door riddle after acceptance; truthful running
  status/help; and a fresh scissor-sign report. The riddle preserved its committed
  wording and concealed the answer. Help/perception spent no contribution.
- On the incomplete input "My answer is", the backend committed "Please finish
  that request; the agreement and state are unchanged." Live instead generated
  "I'm still waiting for your answer." The verifier returned AMBIGUOUS and the
  player rejected it. The required completion prompt was never heard; the runner
  timed out. This is ordinary clarification speech, outside the task-only
  correction scope. The response was correctly withheld; the required user
  experience still failed.
- Final task state remained RUNNING with one contribution, the original captured/
  released/played riddle response and unchanged authority. No correction was
  reserved in this paid run, so it supplies no real-provider evidence for the new
  correction path. Final answer/feedback, Reflection, replacement and stop were
  not reached. Semantic acceptance is FAIL/incomplete, not a passing subset.
- Usage: five visitor turns, seven backend requests, 13,698 prompt + 638 completion
  tokens (14,336 total), one Live session (last reported usage 148 seconds), and
  one new TTS synthesis; other stimulus clips came from the existing cache. No
  total dollar cost is inferred from incomplete provider telemetry. Session close
  was requested; the owned app stopped and disposable schema/account were removed.
- Recovery/social were not run after this failed dialogue gate. Physical trials
  remain unrun and capacity remains deferred. No unchanged paid reroll was made.

Next focused step: inspect how the ordinary clarification occasion conveys its
current communicative act to Live, using this exact incomplete-answer case. Make
the request to finish the fragment explicit before retrying dialogue; preserve
the semantic approval rule and the task-only correction boundary. This follow-up
is not implemented in the present review handoff.

## Approval before audible speech - implementation checkpoint (2026-11-11)

**Owner-authorized scope implemented; ES-08 is NOT accepted.** The owner accepted
added latency and the buffered playback change. No deployment or physical trial
was performed. The current state machine, agreement/budget authority and task
speech generation fences remain intact. No task verification was weakened to turn
an unsuccessful Live journey into a pass.

### What changed

- Delivery-aware Coordination Live sessions require an approval-capable client.
  Original remote PCM is captured through a silent worklet, with two bounded clips,
  a 30-second clip limit, 2.4-second acoustic quiet and 60-second retention.
  Receipt matching uses one-second onset / 1.5-second end margins, below the
  grouping gap, exact whole text/receipt identity and exclusion of extra receipts.
  These are conservative acoustic checks, not perfect speech/ASR correspondence.
- A separate scoped approval/release/played protocol checks current agent/session/
  epoch/revision and deadlines. Task contributions reuse their existing semantic
  verifier. Ordinary/control/help speech has one semantic check per current
  request, at most four concurrent checks and 128 attempts per session. Provider
  waits occur outside agent locks and database transactions.
- Optional sensory remarks use their existing scoped occasion and facts. They
  cannot displace a current reply, establish consent or consume a task response.
  Release must still occur within the original observation/occasion expiry (at
  most six seconds); playback admitted in time may finish within the overall
  60-second bound. Supersession and user input cancel the occasion. This short
  admission window can expire during buffering/verification; no paid sensory
  remark trial was performed.
- An ordinary draft may precede backend commitment only if it follows the last
  trusted receipt of the current user turn and is then reviewed against committed
  facts. Earlier-turn drafts, mute and interruption cannot qualify. Task content
  retains its post-request generation fence and original 45-second deadline.
- Approval does not prove delivery. Only observed playback exposes native speech
  to conversation/prompt/consent views and opens the next cue. The existing final
  task playback cue remains separate. Help cannot consume a task response or
  manufacture acceptance evidence. Missing/rejected speech gives visible recovery
  guidance; there is no automatic replay or new contribution.
- The production player uses a private muted, zero-volume remote sink for
  [Chromium's remote-stream consumption issue](https://issues.chromium.org/issues/40094084).
  The original stream is never audible. A real local WebRTC peer-pair regression
  checks nonzero capture, silence on rejection and one approved playback.
  Buffered playback binds receipts at observed audio onset after media warmup;
  a later interruption/stall still invalidates proof.

### Verification and retained offline failures

- Full disposable-MySQL regression: **913 cases, 912 passed, one explicitly gated
  capacity case skipped, zero failures/errors**; artifact
  `target/gptlive-acceptance-65ad2c77c2`.
- **Four Playwright checks passed** in that same run: real local WebRTC
  capture/rejection/approved playback, real-app Coordination Reflection, neighboring
  scoped cockpit and persisted state/SSE. The approved-conversation screenshot was
  visually inspected; it contains the delivered contribution without draft leakage.
- Final review completed the verifier's proposal facts with actual human/agent
  roles and success criteria, then restored the optional sensory-occasion adapter.
  **42 focused Java cases passed after both changes** (`CoordinationVoiceUnitTest`,
  `LiveSpeechApprovalServiceUnitTest`, `SensoryRemarksUnitTest`). These cover
  current-reply priority, owner scope, consumption after playback, cancellation,
  and expiry between approval and release versus completion after timely release.
  The full suite and browser checks above preceded these two final changes.
- **208 Node tests**, **11 acceptance-runner preflight tests**, `mvnw -DskipTests
  package` and `git diff --check` passed. Logs are under
  `target/es08-approval-20261111`. No configured application database was used.

The initial full run `target/gptlive-acceptance-de3a2de656` had 908 cases:
900 passed, one capacity test skipped, six failures and one error. Six older
fixtures treated capture as delivery/consent; they were updated to exercise the
new approval/playback boundary. The other fixture needed the client handshake.
The subsequent query regression measured **2,007 vs the prior 1,993** questions
per synthetic minute because ordinary replies now persist speaking requests.
The ceiling is now 2,010, with the reason recorded in the test. This is **not quota
acceptance** and excludes approval/release/playback calls: capacity remains deferred.

The first three browser checks passed in `3474b06b4d`; paid capture diagnostics
then exposed the zero-PCM issue that synthetic media did not cover. The new local
WebRTC regression checks the repair. Run `f7edc39af5`
passed the WebRTC check but failed the application playback acknowledgement after
media warmup erased early receipt evidence. The bounded onset binding regression
and both browser paths then passed in `d73b66f455`. All failures remain retained.

### Real-provider attempts ? generated is not heard

All runs used the real local application, disposable loopback MySQL,
`gpt-live-1`/`willow`, backend `gpt-5.6-luna` with effort `none` (run-only settings),
synthetic visitor audio/sensors and the production approval player. Per-run caps:
12 visitor turns, 24 backend requests, 2,048 completion tokens/request, 80,000
input bytes/request, 200,000 reported backend tokens, one Live session/360 seconds,
12 synthesis clips/6,000 characters and 420 child wall seconds. No configured
application database or model defaults were changed.

| Artifact under `target/gptlive-acceptance-*` | Result and actual coverage | Backend requests / tokens | Last reported Live seconds |
| --- | --- | --- | --- |
| `dbe35c23e6` ? social, initial capture | No heard speech or approval check. Generated draft incorrectly promised a count increase/once per arrival; later diagnostics identified the capture-path problem. | 1 / 5,648 | 60 |
| `d193041426` ? recovery, diagnostic capture | Zero voiced PCM frames. No heard speech or approval check; no recovery steps reached. | 1 / 5,532 | 59 |
| `6690c35a38` ? recovery, capture repaired | Nonzero real audio; complete proposal discarded by the newly introduced generation cutoff. Led to the current-turn ordinary-draft correction, without changing task fences. | 1 / 5,547 | 58 |
| `102583eaef` ? dialogue, reviewed drafts | Proposal MATCH, released once and observed. Question withheld: backend locks/space/enter riddle differed from native keys/no-doors riddle; verifier AMBIGUOUS. One contribution remained spent; task paused/uncertain. Help/feedback/replacement/Stop not reached. | 4 / 12,390 | 88 |
| `6a49f65690` ? social, reviewed drafts | Generated count-alternative proposal; no heard speech or semantic check. First caption was 601.33 ms before audio onset, outside initial 600 ms margin. Final one-second margin is unit-verified, not provider-rerun. | 1 / 5,646 | 58 |
| `8caa01d8b3` ? recovery, reviewed drafts | Generated rock-preparation proposal; no heard speech or semantic check. Final caption was 1,260 ms before voice end, outside initial 1,200 ms margin. Final 1,500 ms margin is unit-verified, not provider-rerun. | 1 / 5,539 | 58 |

**Zero complete journey passes.** The sole confirmed proposal had **4,720 ms**
between captured voice end and playback release; it took additional time to play
and settle. This is one observation, not a latency distribution. Total backend
usage: **9 requests, 40,302 tokens (38,383 input + 1,919 output)**. All synthesis
clips were cached: **zero new TTS requests**. Live seconds above are the last
reported telemetry values, not billing totals. Each artifact retains raw generated
captions, observed speech, provider results, buffer diagnostics where available,
`semantic-review.json` and `approval-usage.json`. Every owned session/app/schema/
account/MySQL process was cleaned up. Earlier failed runs were not overwritten;
repeats followed concrete capture/authority repairs, not unchanged rerolls.

### Remaining decision and unverified areas

The approval stage prevents unverified speech from becoming delivered task or
consent evidence, but it does not make independently generated Live wording
reliably match a committed task. The final audio-margin fixes and full proposal-role/criterion verifier payload
have not been tested in another paid journey; all later recovery/social steps and final dialogue feedback
remain unverified on this path. Physical audibility, microphone interruption/echo,
camera accuracy and classroom usability remain unrun. Database capacity remains
a separate, explicitly deferred gate.

A next milestone needs an owner decision: permit **one bounded new realization
of a rejected task response that was never released to the audible player**, keeping its request identity, spent
budget and original deadlines, or choose backend-controlled speech for committed
task content. Recommendation for the smaller continuation: one bounded unheard
retry, with no second task action and no retry after release (even when completion is unconfirmed). This
changes the present fail-closed recovery contract and is not implemented or
assumed authorized by the current checkpoint. Do not simply widen verifier meaning,
relax task generation fences, increase action budgets or repeat paid runs for luck.


## ES-08 continuation C — three bounded Live attempts (2026-11-11)

**ES-08 remains incomplete. All three attempts failed whole-journey acceptance.**
The combined code checkpoint was `4c787cc`, including assembly commit `2424691`.
Each used the real local application, disposable loopback MySQL, real OpenAI Live,
synthetic visitor audio/sensors and the production browser playback observer.
No failed run was retried. Semantic review includes preliminary native speech,
not only the associated final response. Each artifact has `journey.json`,
`provider.jsonl`, logs and a manually assessed `semantic-review.json`.

### Actual findings

1. **Recovery — `target/gptlive-acceptance-c6d15c8a41`: FAIL.** Correct proposal,
   rock preparation, help before/after preparation and protected-state assertions
   passed. The perception turn nevertheless said "I see a rock sign" before
   "I see a scissor sign", despite fresh scissors being supplied: the lexical
   check accepted the correction, but semantic review rejects the initial error.
   After acceptance, the only arming sentence began "Got it, it's active...".
   Its first trusted receipt was `15:06:33.247Z`, the response was created at
   `15:06:33.708145400Z`, and the confirmed commentary was sent at `15:06:34.498Z`.
   There was no eligible complete sentence beginning after response creation.
   The original 45-second deadline ended UNCERTAIN and paused the task with zero
   actions. No welcome, final playback or return step was reached. The authority
   fence behaved correctly; acoustic grouping cannot authorize earlier speech.
2. **Dialogue — `target/gptlive-acceptance-d0c0646196`: FAIL.** A two-contribution
   dialogue proposal and acceptance succeeded without revealing the solution.
   Backend question: "What has many keys but cannot open a single door?" Native
   question: "Here we go. I have keys, but no locks; what am I?" A single complete
   segment was checked and the verifier returned AMBIGUOUS. This is a recognizable
   variant, but the changed clue does not explicitly preserve the door-opening
   condition; it is not verified faithful delivery. The task paused with one
   consumed contribution and no replay. Help, unfinished-answer support, final
   feedback assembly/playback and replacement were **not reached**. Do not label
   the acoustic fix as real-Live feedback acceptance based on this run.
3. **Social — `target/gptlive-acceptance-6f3bd05d2d`: FAIL, with a confirmed welcome.**
   Spoken proposal explicitly denied newcomer detection, disclosed "more than
   one person is visible" and "One welcome total", and required acceptance.
   This is positive evidence for continuation B. A count of one did nothing;
   two triggered "Welcome, everyone." Exactly one contribution reached
   PLAYBACK_CONFIRMED/Reflection. Restart created a new run and fresh budget;
   pause/resume preserved it. Stop correctly persisted COMPLETED/CANCELLED with
   zero actions in that second run, but native speech said "Okay, pausing it here"
   and "Paused and outcome recorded. What's next?" Backend facts explicitly
   contained CANCELLED and prohibited further actions. This is a real spoken
   control mismatch, not a matcher defect. Restart also mixed "propose the same
   terms again" with an already-running fresh task. No whole-journey pass.

The assembly regression and earlier offline checks remain valid. The live
remaining failures concern speaking before authority, stale preliminary
perception, changed task wording and incorrect control confirmations. Neither
retroactive association, a longer timeout, weaker verification nor retrying for
a luckier result would establish the required contract.

### Configuration, bounds, usage and cleanup

This device's ignored backend default was `gpt-5.2`; run-only environment
overrides reproduced the home comparison settings: all inference routes
`gpt-5.6-luna`/effort `none`, Live `gpt-live-1`/`willow`. Recorded inference spans
confirm the route and effort. No private or production configuration changed.
These are application models, not a claim about the coding agent's model.

Per run: 12 visitor turns, 24 backend requests, 2,048 completion tokens/request,
80,000 input bytes/request, further-inference cutoff at 200,000 reported tokens,
one 360-second Live session, 12 synthesis clips/6,000 characters and a 420-second
child timeout. The final inference may cross the token threshold. The same
previously authorized bounds were declared before each invocation.

| Scenario | Backend requests | Prompt / completion tokens | Last reported Live seconds | New synthesis requests |
| --- | ---: | ---: | ---: | ---: |
| Recovery | 1 | 5,227 / 308 | 133 | 5 |
| Dialogue | 3 | 11,273 / 362 | 43 | 4 |
| Social | 6 | 17,082 / 645 | 149 | 5 |

Total reported backend usage: **10 requests / 34,897 tokens**. Last Live usage
updates are not complete billing totals; synthesis and Live usage are separate.
All three requested session closure and the runner confirmed owned app, random
schema/account and private MySQL cleanup. Recovery additionally recorded
`context_injection_incomplete` during teardown after the primary failure.
No physical trial, deployment, remote database write or capacity investigation.

The authorized A/B/C sequence is now recorded, but the acceptance milestone is
not complete. Further implementation stops at the new product/architecture
decision in `PLAN_ESCALATION.md`: whether to add a bounded approval stage before
native speech becomes audible, accepting latency and a separate playback path.
Current playback code observes audio that is already playing; it cannot prevent
an incorrect utterance from being heard. The provider also distinguishes
[acknowledgment cancellation](https://developers.openai.com/api/docs/guides/live-delegation#suppress-automatic-acknowledgments-while-tools-run)
from [checking buffered speech before playback](https://developers.openai.com/api/docs/guides/voice-server-controls?api=live#check-speech-before-playback).

## ES-08 continuation B — early social capability guidance (2026-11-11)

The backend already knew that arrival is empty-to-occupied and that an extra
person requires an explicitly reviewed count alternative. Native startup speech
did not have those limits before the backend produced a proposal. Coordination
now supplies that distinction, accurate numeric terms and the run-wide action
limit in its existing voice guidance. No new state, transport or per-turn request
was added. The new regression failed before the change (`proposal-before.log`).

The existing 16,000-byte instruction bound remains. An initial wording exceeded
it; shorter wording passed ordinary cases, and an additional optional-correction
case then exposed a remaining overrun. The final wording fits both. This checks
prompt availability and bounds, not probabilistic speech compliance. Preliminary
native speech is explicitly included in the Live semantic-review rubric.
Provider acknowledgment cancellation cannot stop independent or already generated
speech; see [the official acknowledgment guidance](https://developers.openai.com/api/docs/guides/live-delegation#suppress-automatic-acknowledgments-while-tools-run).
No audio-buffering/muting architecture was added in this step.

Verification on top of continuation A:

- Full disposable-MySQL suite: **891 cases, 889 passed, one intentional capacity
  skip, one prompt-size failure** described above. Artifact
  `target/gptlive-acceptance-019460a91b` preserves its log and JUnit XML.
- After shortening, **all 46 affected Java cases passed** (Coordination voice,
  social contract and Live context projection), including optional correction
  guidance. **Three real-app browser cases passed**: delayed cue/Reflection,
  durable scoped conversation, and persisted state/monitor SSE. Artifact
  `target/gptlive-acceptance-2687ebd4e0` retains logs, XML and screenshots.
  The recorded-conversation and RPS-result screenshots were visually inspected;
  content and controls remained readable. Owned app/schema/account/MySQL cleanup
  completed after both invocations. The whole suite was not repeated after the
  final wording-only shortening; the affected cases were.
- **193 Node and 11 runner checks passed; packaging and whitespace checks passed.**
  Local command logs: `target/es08-continuation-20261111/`. No paid journey was
  used to select the wording. Real Live acceptance is the next step; ES-08 is open.

## ES-08 continuation A — bounded assistant sentence assembly (2026-11-11)

Started from clean `main`, incorporated the subsequent shared receipt-selection
and test-clock commits through `057aa55`, then reproduced the home feedback split.
The deterministic baseline closed "That's it, a piano!" after 800 ms, before the
explanation 1.4 seconds later. The new regression failed before the change;
`target/es08-continuation-20261111/assembly-before.log` retains that result.

Delivery-aware Coordination policies now opt into a 2,400 ms **observed assistant
silence** boundary through the existing session snapshot/capture path. Nearby
sentences stay in one native segment with their original receipt IDs/times. No
completed history is rewritten and no second semantic inference/retry is added.
Default/legacy policies retain 800 ms; user capture remains at its existing setting.
Fresh user audio/transcript closes the assembly boundary: nonoverlapping speech
with the original minimum observed quiet may complete; overlap/interruption stays
incomplete. Delayed older input and muted input cannot cut the current response.
Coverage gaps, close, receipt/character bounds and the original 30-second maximum
open duration remain. Response verification still has 45 seconds, final playback
60 seconds, and the existing owner/revision/session fences. AMBIGUOUS is unchanged.

This is bounded acoustic assembly, not proof that every generated answer is
semantically finished. A longer pause may still split an answer; missing or
incorrect content still fails verification. Physical/noisy-room behavior is not
established. The provider's continuous audio and absence of an output-done event
are documented in [the Live event guide](https://developers.openai.com/api/docs/guides/voice-websockets?api=live#send-and-receive-events).

Verification:

- **88 focused Java cases passed** in segmenter, capture, context projection,
  scoped sessions, Coordination voice and TaskResponse tests. Covers the exact
  split pattern, original receipts, repeated receipt suppression, interruption,
  muted/old/overlapping input, coverage/close/time bounds, policy opt-in and existing
  response identity/deadlines. Command: `mvnw.cmd -q -Dtest=LiveTranscriptSegmenterUnitTest,LiveTranscriptCaptureServiceUnitTest,LiveContextProjectionUnitTest,ScopedLiveSessionServiceUnitTest,CoordinationVoiceUnitTest,TaskResponseUnitTest test`.
  Final log: `target/es08-continuation-20261111/assembly-focused-final.log`.
- **38 local-MySQL cases and three real-app browser cases passed** in artifact
  `target/gptlive-acceptance-7d1a686f48`, through the owned local helper. Selection:
  Coordination agent/delivery, transcript ingress/claim, speech/receipt durability,
  task-voice persistence, core RPS and Social Space smoke. Browser cases cover
  delayed playback to Reflection, durable scoped conversation and real state/SSE.
  The fake provider now supplies three seconds of measured assistant quiet, rather
  than ending synthetic PCM after one second. This run preceded the final narrow
  muted-input guard, which is covered by the final unit run above. Combined final
  regression follows the proposal correction before paid acceptance.
- **193 Node tests** and **11 runner preflight checks** passed. No paid provider
  run yet; no physical trial, deployment or capacity investigation. Owned local
  schema/account/app/MySQL cleanup was reported by the runner.

The assistant-assembly implementation is ready for the next authorized step.
ES-08 acceptance remains open. Next: make social-condition limits available to
native speech before a proposal, then review the three bounded real Live journeys.

## Office/home consolidation (2026-11-11)

The home checkpoint `02df87b` is the integration base. The office's 19 changed or
untracked files were preserved in a verified local backup, retained stash and
`backup/office-es08-20261111` branch before fast-forwarding `main`. Reconciliation
and reassessment: `CONSOLIDATION_COORDINATION_2026-11-11.md`. Distinct historical
office attempts, including the incorrect piano/Enter-key feedback, are retained
in `ESCALATION_OFFICE_2026-11-10.md`; their old halt/status and runner settings are
not current instructions.

The consolidated implementation retains the home verifier, receipt-aware capture,
atomic speech updates, current-turn context, acceptance/perception guards and
modular bounded Live harness. Office unfinished-answer SUPPORT handling and its
five added cases are restored. Proposal/arming guidance avoids invented supplied
lines and false failed-recognition claims. The operator checklist is now tracked.
The home harness gains office unfinished-answer coverage and final-feedback review
of the identified segment, respecting the new suffix offset and leaving later
Reflection speech separate. All captions remain available for broader review.

Retained regression failures and correction:

- Restored Capture tests against the home checkpoint: **9 cases, five failures,
  zero errors**. The failures establish why the office guard is still needed.
  Log: `target/consolidation-20261111/capture-before-merge.log`.
- First full merged-tree local-MySQL run: **877 cases, 875 passed, one failed,
  one opt-in capacity skip, zero errors** (`gptlive-acceptance-8ad43b2831`). The
  longer office arming facts exceeded the home's 340-byte atomic update bound;
  `CoordinationVoiceUnitTest` correctly detected loss of the self-contained
  speaking request. Shortened the facts, preserving the bound and test. All
  **21 Capture/Voice cases then passed**, including the five restored failures
  (`target/consolidation-20261111/capture-voice-after-merge.log`). Original full-run
  JUnit XML, summary and failure log remain in its artifact directory.

Verification after the arming correction:

- Full Java suite on disposable loopback MySQL: **876 passed, zero failures/errors,
  one opt-in capacity test skipped** (877 cases, 172 suites). Artifact
  `target/gptlive-acceptance-15bfc67fc0` retains copied JUnit XML and its summary.
  Command through this device's owned-MySQL adapter:
  `python target/run_playback_mysql.py --java-tests all --browser --browser-mode enabled --browser-grep "Coordination reaches Reflection|real scoped cockpit|state view tracks a persisted transition"`.
  The Java phase passed; the combined command failed in the browser phase below.
- That first browser phase passed two cases and failed the Coordination playback
  case before it received a playback cue. The retained trace shows complete
  `First fixture observation.` speech, but no association. The fake provider
  matched an obsolete verifier prompt sentence; the home prompt rewrite made it
  return a generic behavior response instead of verdict JSON. Updated only the
  test fixture to recognize the structured response-check payload. Two new fixture
  cases use the real `TaskResponse.Check.request()` and verify MATCH/UNRELATED,
  including response identity; both pass. No production verifier or playback
  requirement was relaxed.
- All Node tests: **190 passed**, including three new contribution-selection
  cases. **11 runner preflight checks**, both journey syntax checks, Maven
  packaging and whitespace checks pass. Logs:
  `target/consolidation-20261111/*-final.log`, `package.log`.

- Corrected browser rerun: **three passed**, using real local HTTP/MySQL/SSE,
  scripted provider media and the production playback observer. It covers delayed
  playback to Coordination Reflection, scoped durable conversation, and persisted
  transitions in the state view. Artifact `target/gptlive-acceptance-15b27f1148`
  also retains the two new fixture unit results. Command: the same adapter/browser
  flags above, with `--java-tests LiveSmokeConfigurationTest`. Inspected the
  persisted-conversation and state-popup screenshots. This is a focused browser
  smoke, not a new full desktop/narrow/theme visual matrix or real OpenAI trial.
- Both full-suite runs and the browser repair run report removal of their owned
  disposable schemas/accounts and stopped application/MySQL processes. Final
  process inspection found no owned fixture JVM or private MySQL server remaining.
  No configured application schema or production database was targeted.

No paid OpenAI or physical trial was run during consolidation. The merged
prompt/fixture combination therefore has no new integrated Live acceptance claim.
The home direct-provider evaluation below remains historical evidence for its
verifier. It does not independently validate factual feedback. ES-08 remains
incomplete; capacity/environment work remains deferred. No deployment is included.

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
