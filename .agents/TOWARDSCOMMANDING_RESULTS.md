# Towards commanding: implementation and verification

Started: 2026-11-19. Plan: [PLAN_TOWARDSCOMMANDING.md](PLAN_TOWARDSCOMMANDING.md).
The owner authorizes sequential implementation with a commit and push after each
completed milestone. No new deployment is authorized. The owner explicitly
authorized an additional $40 across eight attempts, failures included, on
November 19, then a separate $10 across two retests and a final $5 HP-03 follow-up.
All eleven reservations are consumed. The completed GPT-Live repair ledger is unchanged.

| Milestone | Unit | Local DB | Browser functional | Visual review | Provider | Physical | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TWC-01 | PASS: 332 affected unit cases | PASS: 102-case Java acceptance run | PASS: seven cases, then two strengthened native-ingress reruns | PASS: both new screenshots inspected | PASS: HP-02 | NOT RUN | Complete |
| TWC-02 | PASS: 508 affected + 47 focused cases (overlap); 16 Python | PASS: four variants, reload/reset/silent rejection | PASS: two real-app Text journeys | PASS: rock/question screenshots inspected | PASS: HP-01/03 | NOT RUN | Complete |
| TWC-03 | PASS: 218 Java, final 40-case subset; 175 JavaScript | PASS: seven Java cases | PASS: two mode journeys + two independence cases | PASS: stable light/dark/narrow poses inspected | PASS: HP-01–04 | NOT RUN | Complete |
| TWC-04 | PASS: five JS + 16 harness cases | PASS: three same-type scoped instances | PASS: two real-app/fixture cases + five final static/regression cases | PASS: desktop/narrow light/dark labels inspected | N/A: labels need no provider | N/A | Complete |
| TWC-05 | PASS: 356 affected Java; 184 JS/evidence; final 27 Python; focused follow-ups below | PASS: 24-case integrated run and focused follow-ups | PASS: ten integrated cases and focused follow-ups | PASS: local layouts and fresh provider gesture screenshots | PASS: HP-01–04; failed attempts retained | NOT RUN | Complete; eleven/$55 reservations consumed |

## TWC-01 — Current task announcements

Implementation uses the existing persisted speaking-occasion identity, which is
derived from current task revision/state and scoped by the Live session epoch.
Coordination marks control/proposal/support narration as current-only; committed
contributions retain their delivery contract. The bridge scopes associated state
facts and speaking instructions together and rechecks before each send and after
each acknowledgement wait. Unrelated context revisions do not invalidate them.

Already-sent obsolete instructions receive a single quiet withdrawal, with an
instruction to correct only wording that conflicts with the latest state. The
adapter currently sends acknowledged context appends; this implementation does
not invent a response-specific provider cancellation operation or buffer native
audio. Provider compliance and physical speech remain separate acceptance items.
Content-free supersession traces retain old/new occasion identities for diagnosis.

The first focused test run exposed two regressions during implementation:

- Additional voice guidance exceeded the existing instruction budget. Replaced
  redundant wording with shorter withdrawal guidance; kept the budget unchanged.
- Making every cleared occasion urgent changed optional ambient refresh cadence.
  Restricted the urgent invalidation to current-only task narration; existing
  ambient scheduling retains its behavior.

The nine new deterministic bridge cases passed, including control changes during
an acknowledgement wait, already-sent narration, unrelated context updates and
a committed contribution entering FINISHING. All 332 affected unit cases passed
with no failures/errors/skips. The local-MySQL runner passed 102 selected Java
cases, including those shared unit anchors, then seven browser cases. Counts
overlap; they are not 434 distinct Java tests.
The persisted smoke passes creation/proposal/acceptance/reload, zero-action
activation, unchanged agreement, old-proposal suppression, stop and no reconnect
replay. New browser cases use actual MySQL/runtime/SSE and synthetic provider ACK
holds/audio to distinguish queued from in-flight instructions. The two cases
were subsequently strengthened to use native transcript ingress for the user
proposal and acceptance and to inspect the recorded conversation; both passed.
Their successful full-page screenshots were inspected. Native playback remained
unmuted/unpaused, there were no competing synthesis or fabricated playback-cue
requests, and zero actions were spent at acceptance. Provider wording/physical
audibility are not established by this synthetic transport.

A final compatibility check preserves older pending responses without a purpose
tag. The 32 targeted voice/narration unit cases passed after this refinement.
The final diagnostic refinement also records the superseding occasion ID; it
was included in the 332-case unit run. No schema/graph/history migration.

Commands and artifacts so far:

```powershell
.\mvnw.cmd -q '-Dtest=LiveContextDeliveryUnitTest,LiveTaskNarrationUnitTest,CoordinationVoiceUnitTest,AgentCapabilityLiveContextUnitTest' test
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests 'LiveTaskNarrationUnitTest,LiveContextDeliveryUnitTest,CoordinationVoiceUnitTest,CoordinationAgentIntegrationTest,CoordinationDeliveryIntegrationTest,AgentCapabilityLiveContextUnitTest,LiveSmokeConfigurationTest' --browser --browser-mode enabled --browser-grep 'native Coordination'
.\mvnw.cmd -q '-Dtest=Live*UnitTest,Coordination*UnitTest,CoreTaskVoiceUnitTest,Sensory*UnitTest,AgentCapability*UnitTest' test
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests none --browser --browser-mode enabled --browser-grep 'withdraws obsolete'
.\mvnw.cmd -q '-Dtest=CoordinationVoiceUnitTest,LiveTaskNarrationUnitTest' test
```

Ignored artifacts: `target/twc01-unit.log`, `target/twc01-regression.log`,
`target/twc01-acceptance.log`, `target/twc01-native-ingress.log`,
`target/twc01-final-unit.log`, `target/gptlive-acceptance-dd4e845363/` and
`target/gptlive-acceptance-f037455cb6/`. Both owned applications were stopped and
both disposable schemas/accounts removed. New screenshots are in the latter
run's `browser-true/` case directories.
No paid provider calls have been made for this roadmap.

## TWC-02 ? Shared direct requests

Existing task/embodiment inference now returns optional internal command metadata.
A shared validator gates the finite vocabulary on persisted profile modalities,
blocks overlapping task outputs, and emits ordinary canonical BehaviourPlans.
It never creates/starts/changes a task or spends its contribution budget. Silent
requests have no backend speech. A bounded handled-turn ledger uses the admitted
history position (the event UUID is assigned only when flushed), survives event
selection and database reload, and is cleared on reset. Distinct new turns can
repeat an action. Existing transport receipt deduplication remains authoritative
for native/Continuous retries; this is not a new general HTTP idempotency API.
Saved profile tags opt in without graph or schema migration. Owner loss after
inference prevents late plan publication. Silent decisions persist even when no
behaviour event is emitted; otherwise an unsupported request could be retried.

Validation: 508 affected unit cases passed; after the silent-persistence fix,
47 focused service/command cases passed (overlapping, not additive unique totals).
The Python harness passed 16 tests. A local-MySQL run passed ten Java cases
(four persisted agent variants plus six Coordination contract tests); the final
run repeated the four integration variants with silent rejection persistence.
Two final real-app browser journeys passed through Text, DB and SSE, checking
rock, question gesture, deliberate repeat, unsupported request, reload and no
animation replay. Successful rock/question WebGL screenshots were inspected.
No assistant output was injected into the UI; inference was synthetic. These
fixtures prove validation/routing/rendering, not model language understanding.
Native speech and paid model interpretation remain TWC-03/TWC-05 work.

Implementation failures retained: initial event UUID assumptions corrected to
history position; EventSelector wrapper and foreign-scope Optional contract fixed
in tests; Mockito restubbing fixed; final-current-turn prompt position restored.
The browser correctly exposed a lost silent-result persistence path, now fixed
and covered in the four-variant integration test. Its initial SSE readiness wait
was moved after the first output because an empty stream has no event to flush.

Artifacts (ignored): `target/twc02-regression-final.log`,
`target/twc02-service-regression.log`, `target/twc02-python.log`,
`target/gptlive-acceptance-89c4066634/` (Java pass, browser setup failure),
`target/gptlive-acceptance-1181077693/` (silent-result failure), and
`target/gptlive-acceptance-50512df0f0/` (final four Java/two browser pass).
Earlier failures remain in `target/twc02-*.log` and their runner directories.
Owned apps and disposable schemas/accounts were cleaned. No paid calls yet.
TWC-01 was committed/pushed as `e5a8b6e4`.

## TWC-03 — Native voice and command alignment

Voice guidance now describes declared digital hand signs/gestures positively and
separates pending validation from execution. Scoped saved instances retain direct
command guidance even with general capability awareness disabled. Shared inference
still supplies backend command interpretation; no second ordinary inference or
Live speech synthesizer was introduced. Existing instruction limits are unchanged;
redundant guidance was shortened after budget tests caught overflow.

Command results are quiet deltas, bounded to 380 UTF-8 bytes before transport
framing. They name the persisted user event and are available only when that event
is the latest user turn, under 30 seconds old, and has a COMPLETE USER transcript
record in the current session/epoch. Stop/owner checks exclude old work. Startup
omits these facts; reset/new turns/expiry withdraw them. They never create a new
speaking occasion, request replay or claim client/physical execution. This keeps
ordinary native speech independent and avoids command follow-up announcements
that could outlive their request. Provider compliance remains a separate gate.

Verification so far: 218 affected Java unit cases passed; the final guidance
subset passed 40 cases. Seven local-MySQL Java cases passed (four command variants
and three transcript ingress tests), followed later by six result/fixture unit
anchors in the disposable runner. JavaScript passed 110 Live, 15 embodiment,
21 speech and 29 transcription cases (175 total). Both existing native
blocked-backend browser cases passed, including Stop fencing. The two new mode
journeys passed Continuous -> Live -> Continuous -> reconnect, with actual DB,
production ingress/SSE and WebGL pose assertions; the strengthened duplicate
packet replay and stable reduced-motion screenshot rerun is recorded below.

Failed attempts are retained. Initial prompt overflow was corrected without
raising limits. Existing mocked projection tests needed the unchanged overload
when no command facts exist. Repeated synthetic user captions exposed missing
PCM lead-in after idle gaps; the fixture now provides it before USER captions as
it already did for ASSISTANT captions. Runtime coverage-gap rejection is intact.
The duplicate-packet browser check was strengthened to decode fixture captions
and assert successful replay transport, rather than ignoring a 400 response.

Artifacts: `target/twc03-regression-final.log`, `target/twc03-final-guidance.log`,
`target/twc03-*-js.log`, `target/gptlive-acceptance-0302d54977/` (seven Java cases,
two independence cases and Live Multimodal journey pass; Coordination fixture
failure), `target/gptlive-acceptance-89ddcd5294/` (fixture-gap failures), and
`target/gptlive-acceptance-684d5004d6/` (six Java/two mode journeys pass before the
strengthened replay assertion). No paid calls. TWC-02 commit: `aac0cce7`.

Final strengthened rerun: both mode journeys passed in
`target/gptlive-acceptance-f758afc4a1/` / `target/twc03-modes-replay.log`.
The duplicate packet was actually accepted by the fixture transport and produced
no second action. Stable representative WebGL poses were captured with reduced
motion enabled; light scissors, dark question and narrow dark paper screenshots
were inspected alongside normalized render-state assertions. TWC-02 supplies
the rock screenshot. No layout overflow or browser errors. All owned applications
and disposable schemas/accounts were cleaned. Provider/physical acceptance pending.

## TWC-04 — Distinguishable instance labels

Valerian appends an always-present eight-character ID prefix, extending collisions
until unique. Ordering, full request/selection IDs, creation, deletion and stored
names/personas remain unchanged. Long selector names shorten with an ellipsis so
the suffix stays visible; full formatted labels wrap below the selector and in
instance details. The full ID remains available in the existing editable field
and option title. Names are assigned through textContent, never HTML. Visual
review also led to an opaque dark diagnostics drawer for readable labels.

Five focused JS tests and 16 Python harness tests passed. The disposable-MySQL
browser test created three Coordination instances, selected and connected to each
full ID, reloaded the deep-linked instance, and deleted one without changing the
other labels/names. A separate UI fixture checked long Unicode/HTML-like names,
deliberate prefix collisions and no markup execution. Both cases passed in
`target/gptlive-acceptance-2c23277358/` (`target/twc04-browser-final.log`).
The first static fixture omitted transcription capabilities and failed login;
that fixture contract was corrected. Its failure is retained in
`target/gptlive-acceptance-a2ac8b7674/`.

After the dark-drawer refinement, five static/browser lifecycle cases passed in
`target/twc04-static-final/`; the real-DB case is intentionally skipped by that
static runner and had already passed against MySQL. Desktop light and narrow
dark screenshots, including full labels and collision suffixes, were inspected.
The final dark screenshot has no underlying cockpit text bleeding through.
Other evidence: `target/twc04-unit.log`, `target/twc04-python.log`,
`target/twc04-static-final.log`. Owned app/schema/account cleanup completed.
TWC-03 commit: `9c37cfe8`. No paid calls yet.

## TWC-05 — Integrated provider acceptance

TWC-04 was committed/pushed as `cb2d921b`. The owner authorized a separate
$40/eight-attempt envelope on November 19. The new commanding plan reserves
$5 before every attempt, retains failed reservations, and does not reset or
extend the old repair ledger. Authorization, exact models, limits, local audio
hashes and ledger validity are checked before provider access. The account's
model catalog includes `gpt-5.6-luna` and `gpt-live-1`.

Official pricing checked November 19: Luna input $0.20/M, output $1.20/M,
conservative cache-write multiplier 1.25; Live $0.05/minute. The 36-request,
80,000-byte input, 2,048-output-token and six-minute bounds total $1.1084736,
below the unchanged $5 reserve. Sources are pinned in
`tests/gptlive/commanding-paid-plan.json`. Thirteen synthetic visitor clips were
generated locally with Windows SAPI, then hashed as 24 kHz mono PCM. No provider
stimulus, grading or output Speech/TTS calls are allowed in these journeys.

Preflight verification: 23 Python tests passed, including an actual owned fake
process/child timeout cleanup; six existing Java provider-budget tests passed.
The integrated affected Java selection passed 356 cases and the Live,
embodiment, speech, transcription and label JS suites passed 180 cases.
Artifacts: `target/twc05-python.log`, `target/twc05-budget-java.log`,
`target/twc05-java.log`, `target/twc05-js.log`, `target/twc05-audio.log`.
Real-provider acceptance subsequently passed through the attempts documented
below. Physical microphone/speaker and robot execution remain NOT RUN.

### Physical operator checklist — NOT RUN

- Select the intended one of several same-type instances using its visible suffix;
  verify the full ID in details.
- With a real microphone/speaker, ask capability, request rock and a question
  gesture twice on separate turns; check understandable continuous speech and
  the visible requested actions.
- Request paper without speaking; check the pose and actual acoustic silence.
- Propose the one-joke/fresh-rock task and promptly accept while the invitation
  is speaking; check that no renewed consent is requested, paper does nothing,
  and rock produces exactly one joke.
- Press Stop during output; check that speech stops and reconnect does not replay
  an obsolete invitation or action. Record device/browser and observed outcome.

These observations are separate from browser audio meters and WebGL rendering.

### Integrated local gate and paid attempts

`target/gptlive-acceptance-4bfd612f23/` passed 24 local MySQL cases and all ten
browser cases: Text and Continuous/Live command parity on both agents, native
independence/Stop, queued/in-flight invitation withdrawal, and instance labels.
The owned JVM/schema/account were removed. Narrow paper and dark question
screenshots were inspected and show the expected poses without clipping.

1. `a2a39a6fbf` — baseline INCONCLUSIVE, $5 reserved. Four backend requests,
   3,563 reported tokens, one 66.465-second Live session. The harness mistakenly
   used `/eventhistory`, which omits IDs, and failed to detect the second durable
   user turn. Both replies were appropriate on full-caption review; the third
   turn was never attempted. Changed to `/live/history` (all events, persisted
   IDs) with a fail-fast identity assertion. No runtime change or rubric relaxation.
2. `b3e568564f` — baseline PASS, $5 reserved. Six backend requests, 5,517 tokens,
   one Live session (38 provider-reported usage seconds; 46.472-second journey). Full native replies correctly introduced the
   digital agent, offered word association and described planning assistance.
   Three persisted USER IDs, observed native audio, no competing speech route.
   Matched existing onset measurements: 1,247.142, -431.833 and 40.900 ms;
   median 40.900 ms, maximum 1,247.142 ms. As in the preceding repair harness,
   the measurement selects an RMS onset no earlier than 500 ms before visitor
   speech end; earlier conversational backchannels remain in raw browser events.
   This small observational sample makes no p95 or general performance claim.

Each artifact retains `source.json`, `paid-plan.json`, complete `journey.json`,
provider captures and `manual-review.json`. Sources were unchanged during these
attempts. Reservations are ceilings, not provider invoices. Old repair-ledger
SHA-256 remains `bb0caf1e0534f02d51aa7bd8f70fbcce581f0933ee33b636075431df9ce54cf8`.

3. `af7e0c6883` — three ordinary Coordination turns, $5 reserved; three backend
   requests, 17,734 tokens, one session (38 provider-reported usage seconds;
   46.544-second journey). Full native captions were
   reviewed: appropriate introduction, user-selected activity and planning help.
   The inherited near-end-window gate initially FAILED by 25.567 ms on median
   (maximum passed). Raw evidence exposed a measurement defect: the window
   discarded the actual first onset when it preceded visitor speech end by more
   than 500 ms, measuring a later syllable instead. The original failing report
   is retained unchanged. `commanding_evidence.mjs` now takes the first actual
   audio onset after the current input starts; silence before each ordinary turn
   excludes preceding replies. Three offline regression cases cover early onset,
   missing evidence and the unchanged exact 1,000/2,000 ms boundaries.
   Reanalysis of the same complete, matched raw captures gives baseline median
   -431.833/max 40.900 ms and Coordination median -618.700/max 1,074.642 ms:
   differences -186.867/+1,033.742 ms, both within the original limits. Negative
   values mean overlapping visitor/agent speech, not negative processing time.
   HP-04 passes corrected onset analysis; the old window result remains visible
   in `journey.json` and the correction is separate in `onset-analysis.json`.
4. `1701784771` — HP-01 FAILED at capability, $5 reserved. One backend request,
   5,847 tokens, one 56.628-second session. The model returned CAPABILITY plus
   a competing reply; strict validation rejected it, so no command receipt was
   persisted. Native speech initially named the signs correctly, then followed
   the backend's irrelevant task-error response. The prompt now explicitly
   requires an empty reply for every non-null command, with a capability example
   and priority over ordinary KEEP guidance. Validation remains strict; three
   new rejection cases protect it. The affected focused Java rerun passed 62 cases.
5. `c6574f1d51` — HP-01 FAILED at repeat and voice meaning, $5 reserved. Four
   backend requests, 23,656 tokens, one session (89 provider-reported usage
   seconds; 98.669-second journey). Rock and the first
   question gesture were accepted/rendered, but the backend interpreted one
   repeat on a new turn as CLARIFY. Full native speech also falsely excluded
   other gestures and required task setup/agreement for immediate demonstrations.
   Fixed the shared interpretation wording and native guidance; supported digital
   capabilities are now stated directly, one immediate action requires no second
   consent, and complete requests must be heard to honor silence. The first
   revised guidance exceeded the unchanged 16,000-byte bound by 44 bytes; wording
   was shortened, then all 66 focused Java cases passed.
6. `dd9c7c2ce2` — INCONCLUSIVE at optional action acknowledgement, $5 reserved.
   Five backend requests, 30,254 tokens, one session (90 provider-reported usage
   seconds; 99.367-second journey). Three ordinary
   replies and capability were appropriate. Rock was accepted at source position
   19, but native Live stayed silent and the harness incorrectly required audio
   before checking rendering. Direct demonstrations do not require a spoken ack:
   the harness now awaits receipt/rendering, captures any speech and checks its
   meaning. Ordinary/capability/unsupported questions still require native audio;
   explicitly silent commands still prohibit it. This is a harness correction,
   not permission to ignore contradictory speech or missing visual output.

Before attempt 7, the remaining sessions were explicitly combined within the
unchanged caps: Coordination uses three fresh-session ordinary turns, five direct
command turns, then four task/return turns (12 total); Live Multimodal uses three
ordinary turns then four command turns. This repeats HP-04 on the final native
guidance without additional sessions or automatic retries. Six-minute sessions,
390-second watchdog, 36 requests and all other bounds remain unchanged.

7. `1259340c92` — HP-01 PASS; HP-02 INCONCLUSIVE, $5 reserved. Eleven backend
   requests, 62,292 tokens, one session (185 provider-reported usage seconds;
   194.436-second journey). Full captured speech was reviewed: capability named
   all three signs, with a repeated partial prefix; the demonstrations made no
   false spoken execution/consent claims. Rock, question, repeat and silent paper
   each had one correlated accepted plan and observed pose. Silent paper had no
   native audio. The task proposal was saved and accepted with zero actions.
   Its already-started proposal speech continued with future acceptance wording,
   then said it was waiting for the cue; effectiveness of that correction still
   needs a completed task retest. The harness injected paper/rock before semantic
   arming confirmation moved `task.after` to 08:38:53.120Z; the rock timestamp was
   08:38:52.432Z. Ignoring it and retaining zero actions was correct. No contribution,
   duplicate or return was tested. The harness now requires the matching current
   ARMING/CAPTURED receipt and its final freshness boundary; an offline race test
   covers pending, changed boundary, exact boundary and stale identity.
8. `e0830960e1` — HP-03 FAILED before commands, $5 reserved. Four backend
   requests, 3,641 tokens, one session; 15 reported provider usage seconds before
   failure, a 23.296-second retained server interval, 33.530-second journey.
   The second ordinary turn returned optional command metadata with intent:null.
   Its strict parser exception escaped EmbodimentPolicy and closed capture with
   `capture_processing_failed`. This was a backend failure, not evidence of an
   external provider outage. Malformed command metadata now rejects the entire
   complement without publishing movement, retrying inference or ending native
   speech. Ordinary inference guidance also explicitly requires null/absent
   command, never an object with a null intent. The initial test restubbing error
   was fixed; 57 focused units, seven local-DB cases and both browser mode journeys
   pass. The browser injects that malformed result, observes the retained native
   session/no action and then successfully executes the next valid command.

Final local artifacts: `target/twc05-malformed-unit-final.log`,
`target/gptlive-acceptance-e42ab02e2a/`, `target/twc05-malformed-browser.log`,
`target/twc05-evidence-unit-final.log` (four offline evidence tests).
Longer sessions evicted their initial event from bounded server diagnostics;
those retained intervals are not full session durations. Provider usage snapshots
and overall journey wall times are recorded separately, without claiming invoices.

Visual review: paid images show the requested hand/arm poses, but lack facial
details visible in deterministic screenshots. Served robot/Three module hashes
matched current source. Empty-history/minimal-command probes reproduced this
intermittently with local audio, both with and without the paid meter; the meter
alone is not established as the cause. Explicitly redrawing the unchanged settled
pose and synchronously reading GPU pixels before capture produced complete faces
in all six repeated local cases (`target/twc05-robot-gpu-probe/`). The shared
capture helper now asserts visible default iris pixels and no GL error, and the
two final extracted-helper cases pass (`target/twc05-robot-capture-final/`). No
production renderer was changed or assistant plan injected into paid acceptance.
Retests must confirm the same capture protocol against actual provider commands;
old incomplete facial images are retained, not replaced with synthetic images.

Final harness suites pass 23 Python and 184 JavaScript/evidence tests, plus those
two visual capture cases. All owned disposable schemas were verified absent.
At that stage `main` and `origin/main` both pointed to TWC-04; TWC-05 remained
local until its remaining acceptance was completed below.

**Status before the separately authorized retests:** TWC-05 remained incomplete. All eight/$40 reservations are retained, and the
prior repair ledger remains unchanged. No further provider attempt is permitted
by this authorization. Coordination task completion/correction and Live Multimodal
commands need retests; final matched timing and provider visual confirmation
also remain open. HP-01 is the completed command/pose provider gate. Physical observations are
NOT RUN. Completed milestones TWC-01–04 are pushed; TWC-05 work is local pending
the remaining acceptance and a funding decision.

## Separately authorized two-attempt retest envelope

On November 19 the owner answered both pending questions, explicitly approving
the replacement **$10 total for two retests**. It supersedes the earlier $5
question; it is not a $15 authorization. `commanding-retests-plan.json` and
`target/commanding-retests-budget.json` reserve $5 each for one HP-02 and one HP-03
attempt, including failures. The original eight-attempt/$40 and GLF ledgers stay
unchanged. Per-session, turn, request, token and watchdog limits are unchanged.
`--commanding-retests` selects this separate envelope; the default remains the
exhausted original. Twenty-five Python tests and four evidence tests pass,
including duplicate-mode rejection under the reservation lock and cross-ledger
rejection. Both planned sessions include three matched ordinary turns on the
current guidance; final visual captures use the locally verified GPU readback.

9. `8fc54b4719` — HP-02 PASS, $5 reserved. Nine backend requests, 44,437 tokens,
   one session with 113 provider-reported usage seconds and a 126.620-second
   journey. The complete native reply was reviewed. The acceptance question ended
   at browser 75,026.5 ms, before the visitor finished saying Start at 76,261.808 ms;
   backend START was logged at 13:14:20.226874700Z. It was not asked anew after
   activation. Subsequent speech retained the roles/count/condition and confirmed
   it was set and waiting for the new rock cue. Nonmatching paper spent zero;
   fresh rock produced one joke with PLAYBACK_CONFIRMED, duplicate rock produced
   no replay, return succeeded and one standalone question gesture rendered.
   Its screenshot was inspected: complete face, expected pose, 1,406 iris pixels,
   no GL error. No browser errors or competing speech route.
10. `d6470248a5` — HP-03 FAIL, $5 reserved. Eighteen backend requests (including
    decision inference), 18,353 tokens, one session with 120 provider-reported
    usage seconds and a 128.374-second journey. Capability correctly named rock,
    paper and scissors. Scissors and question each had one accepted canonical
    plan and correct rendered pose. Both images were inspected with complete
    faces (1,420/1,406 iris pixels, no GL errors). For the next USER turn, Walk
    over here, native speech correctly declined, but backend inference returned
    ACTION/OPEN_QUESTION, repeating the previous gesture at source position 22.
    That is a real classification failure; the command-receipt timeout was only
    its harness symptom. Scissors also incorrectly set silent:true without an
    explicit user request. No task, actual locomotion, browser error or competing
    speech route occurred. The full captured native replies were reviewed.

Both runs retain unchanged source manifests and `manual-review.json` in their
`target/gptlive-acceptance-*` directories. The matched first three ordinary turns
use identical PCM hashes and models. HP-04 PASS: Live Multimodal median/max
-402.600/-395.533 ms; Coordination -523.933/-302.000 ms. Differences
-121.333/+93.533 ms meet the unchanged +1,000/+2,000 ms limits. Negative values
denote native audio starting before visitor speech ended. This is three-turn
observational evidence, not a p95 or a general latency claim; causal independence
is covered separately by the blocked-backend test. Raw samples/comparison are in
`d6470248a5/matched-conversation-comparison.json`.

The HP-03 follow-up explicitly classifies each last user message anew, prohibits
substituting a supported or previous gesture for unavailable locomotion/contact,
and separates the user's silence flag from the non-speech backend output format.
Native guidance, output allowlists and task authority are unchanged. The harness
now fails immediately on a completed receipt with the wrong status and checks
the explicit silence flag. Four agent variants cover a supported gesture followed
by an interpreted UNSUPPORTED result, preserving the new receipt without movement
or replay. These deterministic tests verify the result handling; they do not prove
the provider will classify the next trial correctly.

Follow-up verification passed 48 Java unit cases, four local-MySQL integration
cases (all four agent variants), and both native/Continuous browser journeys in
`target/gptlive-acceptance-75c66cef09/`. The browser now explicitly follows the
question gesture with an interpreted unsupported request, verifies its new
persisted receipt, no new behaviour event, no competing speech, a still-active
session, and subsequent Continuous recovery. The first local invocation used a
nonexistent test-class name and exited before any test/provider work; it is
retained as `f6a3d904e9`. The corrected invocation passed without skips. The two
paid sources matched and were unchanged during capture; the follow-up changes
only shared backend guidance, its regression tests, and the harness checks.
Native instructions/rendering/task control are unchanged. Read-only MySQL checks
confirmed all four newly owned schemas absent; cleanup logs confirm owned apps
stopped. Both preserved ledger hashes still match. `git diff --check` passes.

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests 'DirectCommandIntegrationTest,DirectCommandsUnitTest,DirectCommandAgentsUnitTest,LiveVoiceAcknowledgementUnitTest' --browser --browser-mode enabled --browser-grep 'direct command mode parity'
```

After preparing this follow-up, the owner explicitly authorized one additional
HP-03 attempt capped at $5. It uses `commanding-hp3-retest-plan.json`, the separate
`target/commanding-hp3-retest-budget.json`, and `--commanding-hp3-retest`. All prior
ledgers stay unchanged. Twenty-seven Python tests pass, including the new mode,
cap, exhausted-attempt and cross-authorization checks. The new allowance is one
attempt including failure; it cannot fund a second Coordination session.

**Status before the final HP-03 follow-up:** TWC-05 was incomplete. HP-01, HP-02 and matched HP-04 passed;
HP-03 needs verification after the interpretation follow-up. Ten attempts/$50
are reserved across the original $40 and replacement $10 envelopes, including
failures; these are ceilings, not provider invoices. Both original ledger hashes
were verified unchanged after the two retests. No additional paid attempt is
authorized. TWC-01–04 are pushed; TWC-05 changes remain local pending acceptance.
Physical microphone/speaker and robot experience remain NOT RUN. No deployment
was initiated.

## Final HP-03 follow-up and acceptance

11. `bb678c8d83` — HP-03 PASS, $5 reserved. Twenty backend requests, 21,582
    tokens, one session with 94 provider-reported usage seconds and a
    116.007-second overall journey. The entire native transcript was reviewed.
    Identity/activities/planning match the authored Valerian persona and available
    capabilities. The capability answer named rock, paper and scissors. Scissors
    and question each produced one accepted canonical plan and the correct visible
    pose, both with silent:false. Native action acknowledgement was absent, which
    is permitted. Walking produced UNSUPPORTED, null kind/value and no new movement;
    native speech correctly declined. No task mutation, competing synthesis route
    or browser errors occurred. Scissors and question screenshots were inspected
    with complete faces (1,420/1,406 iris pixels, no GL errors). Final source hashes
    were unchanged during the run.

Correlated records:

| Request | USER event ID | Canonical action event ID / result |
| --- | --- | --- |
| Capability | `2c7f02dd-0fe0-45f0-9001-b54bce9c1b90` | CAPABILITY; no action |
| Scissors | `e2eaea06-eaf6-4846-beb6-12a30acfe71a` | `721e7668-c346-42b9-b527-d3640393bc71` |
| Question | `e7ea0657-0037-49d9-82b5-389d583db0ff` | `3ce5c80e-9c9f-431e-ae90-0546643e337e` |
| Unsupported walking | `87322c64-79b2-4cae-aa50-ccb52a967e29` | UNSUPPORTED; no action |

The final matched ordinary comparison also passes: Live Multimodal median/max
-685.933/-250.400 ms against Coordination -523.933/-302.000 ms, differences
+162.000/-51.600 ms within the unchanged +1,000/+2,000 ms limits. The complete
raw samples and comparison are in `bb678c8d83/matched-conversation-comparison.json`.
Three turns per mode establish only this observed comparison; negative values
mean overlapping native/visitor speech. Small repeated prefixes remain in native
captions and are a fluency limitation, not a claim of perfectly polished speech.

Acceptance combines the source-traced HP-01, HP-02 and final HP-03 runs, plus the
matched HP-04 comparison and final local regressions. These are separate recorded
implementation snapshots, not a single all-green rerun of every journey on the
final tree. The last change refined shared command interpretation; HP-03 exercises
that correction, while task control, native guidance and rendering remain unchanged
from their passing evidence. The four-variant database and browser regressions
verify the integrated local paths. The original failures remain visible above.

```powershell
python -m unittest discover -s tests/gptlive -p 'test_*.py'
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests none --paid-core commanding-hp3 --commanding-hp3-retest
```

Final artifacts: `target/twc05-hp3-followup-budget-tests.log` (27 tests),
`target/twc05-hp3-followup-paid.log`, and `target/gptlive-acceptance-bb678c8d83/`
with source/plan/provider/journey/manual-review records and screenshots. Read-only
MySQL/process checks confirmed its schema and owned JVM/browser processes absent.
The original repair, original commanding and two-retest ledger hashes are unchanged.

**Final status:** TWC-01–05 are complete. All required provider outcomes passed
alongside the deterministic, persistence and reviewed browser/visual checks.
Eleven/$55 reservations across the three commanding authorizations include failed
attempts; they are ceilings, not invoices. No further paid run is authorized.
Physical operator checks remain NOT RUN and no Heroku deployment was initiated.

## Promotion regression follow-up — 2026-11-19

The owner subsequently authorized merging main into the HRI experiment and agents,
then deploying agents to Heroku. The experiment merge check exposed an existing
main regression: optional facial-correction guidance made Coordination's Live
instructions 16,069 bytes, exceeding the unchanged 16,000-byte limit. Comparing
compiled main and experiment instructions confirmed the same baseline content.
The direct-command voice guidance is now 83 bytes shorter while retaining its
capabilities, single-action consent, silence, pending-turn and acknowledgement
rules. The existing failing correction-budget test now passes, alongside all
71 selected command/voice cases. No budget increase or provider retest was used.
Evidence: `target/twc-deployment/main-budget-fix.log`; branch integration and
deployment verification are recorded on their respective target branches.
