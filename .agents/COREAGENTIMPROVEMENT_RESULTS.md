# Core-agent improvement evidence

## Current status

CA-01 complete and pushed as 55738757. CA-02 complete and pushed as d30196ff.
CA-03 complete and pushed as 4f11b1ae; CA-04 complete and pushed as 35fcfc21.
CA-05 complete and pushed as 677dd15a; CA-06 as 6a48fc33; CA-07 as b6060f86.
CA-08 combined acceptance is complete; the series is accepted for main integration.
Baseline: main 19d2d51c, clean feature/gptlive fast-forwarded on 2026-10-26.
Implemented on feature/gptlive for main. No agents deployment-catalog or standalone
Valerian runtime changes are included in this series.

## Shared core knowledge and Valerian persona — 2026-10-27

The eight conversational core agents now share a packaged
`agentdefs/core/reference.json`, following Gala's validated sources/sections/facts
pattern. It carries the supplied IWI/SIRA knowledge and Theresa Schmiedel's dated
leadership briefing, Marc Styger's lab-code responsibility, Jasmin Grassmugg's
operator/show-design role and a compact Alexandre de Spindler profile from the
owner's `markdowns/persons/ALEXANDREDESPINDLER.md`. His direction role is informal,
not a newly asserted formal Director title. The reference omits SML, Reto Steiner
and all Gala/event knowledge. Source descriptions, URLs and the local document
reference are auditable JSON metadata and never rendered into model context.
These are owner-supplied facts, not newly verified institutional information.

`ValerianPersona` separates real SIRA Lab authorship from the requested fictional
peacekeeper character, confidence/luck, dependence on Laureline's resourcefulness
and mild grumpiness about an unsuccessful search for her. It explicitly keeps this
occasional and self-directed: no repeated lament, visitor blame, new search task,
real-mission claim or change to task/game/consent/silence rules. Lab equipment facts
are not embodiment claims. Healthcare keeps its care identity and earlier humour;
Talk to Me and brief explicit closing prompts remain unchanged.

All active core contexts receive this shared block once, including Live and
applicable Text/Continuous. Previous duplicate lab exposition was consolidated
into the resource; Generic's saved-prompt recognition baseline remains unchanged.
No state transitions, schema, client contracts, sensing cadence or extra model/
database/network calls are introduced. Saved prompt text is not migrated; use new
instances for consistent behaviour. The earlier humour work is included.

Verified 74 focused unit/contract checks with zero failures, errors or skips:
packaged reference coverage and provenance validation, shared context once in every
active core activity, complete Live instruction budgets, core/healthcare contracts,
Generic/task/game regressions and Talk to Me verbatim output. Evidence:
`target/core-persona-reference-tests.log`; the final persona wording was rechecked
with the reference/catalog tests in `target/core-persona-reference-final.log`.
`git diff --check` passes. No provider, database or browser trial was run for this
prompt/resource change; factual answers and the intended tone still need a live
listening check. The user authorized publication through `feature/gptlive`, `main`
and the `agents` deployment branch on 2026-10-27; merged deployment verification
is recorded separately on that branch.

## Restrained humour follow-up — 2026-10-27

Most main personas already allowed warm micro-humour; Generic lacked explicit
guidance and others did not consistently include Gala's recurring self-irony.
One small `ConversationalHumour` prompt fragment now reaches all fourteen
conversational definitions through their existing persona composition. It uses
the main catalog's digital-agent identity, with short varied asides rather than
every-turn jokes. Facts, tasks, agreed wording, silence and permission to speak
take priority. Existing healthcare limits, explicit closing prompts and Talk to
Me's exact-text utility remain. No state, sensing, gesture, transport or database
behaviour changes; no extra inference. Persisted prompt text is not rewritten;
create new instances for consistent coverage.

Generic's saved `VOICE`/`CONFIGURE` recognition baseline and fingerprint remain
byte-for-byte unchanged; only new definition construction adds the tone fragment.
The explicit legacy policy upgrader therefore treats those new tone variants as
custom and preserves them, rather than downgrading their prompts to the baseline.

Verified 72 focused unit/contract checks across corrected runs: catalog coverage
in every non-final activity for Live and applicable Text/Continuous, complete
Live instruction budgets, core/healthcare persona contracts, Generic/task/game
regressions and Talk to Me verbatim output. Initial checks exposed an overbroad
ban on the word "joke" in Generic's no-preset-task assertion, closing-state
exceptions, and catalog-test outer-state/whitespace handling. Those tests now
check the intended boundaries while retaining their prior safeguards. All 66
other checks passed in `target/core-humour-tests-verified.log`; the six catalog
checks pass in `target/core-humour-catalog.log`. `git diff --check` passes.
No paid provider, database or browser trial was needed for this prompt-only edit;
actual humour frequency and delivery have not been assessed with a live listener.

## CA-08 — combined acceptance and handoff

Combined review found a mode-switch bug: a completed RPS round retained its Live
flag, suppressing a later Text result even after Live ownership ended. Result
generation now lets the current runtime owner suppress speech; both Text fallback
and Live silence are covered by the same unit journey. The real scoped RPS smoke
now acknowledges its captured countdown and submits a fresh correlated sign.
The test provider supplies facts-based task speech and semantic capture association;
scripted media acknowledges playback explicitly, while the CA-05 paid journey
exercises the actual observer. These are test-only fixture adaptations.

Initial full Java run `140fcbb18a` found two test failures: a speech-only suppressed
Live result returns no event (rather than a plan with null speech), and the old
voice-default test used new Generic as a legacy baseline. Assertions now reflect
the empty event and construct the unchanged legacy task factory explicitly.
Final full run `5da01feb53`: **658 tests, zero failures/errors/skips**, on disposable
local MySQL; owned schema/account and app removed. Node checks: **68 Live plus
50 transcription/speech**, all pass (`target/ca08-node.log`,
`target/ca08-legacy-node.log`). All three Python runner-isolation checks pass.

Initial broad browser run `87d58c92ae`: 60 pass, three fail. RPS and Generic
fixtures missed activity facts packed with state updates or split into context
parts; the scripted provider now assembles those facts before acknowledging them.
The mobile timing-export test emitted synthetic SSE before history hydration had
created its EventSource; its helper now waits for that boundary instead of
dereferencing an absent source. Production transport/playback code is unchanged.
Final targeted run `454f9bc361` passes the scoped Java smoke, all six selected
Live-enabled browser cases and both applicable Live-disabled cases (two Live-only
cases correctly skipped). Thus all three initial failures have passing targeted
verification; the other 60 broad cases retain their passing evidence. Reviewed
screenshots cover reception, held activation, the RPS result and mobile timing
export. No layout defects were observed in those views. Owned local resources
were removed after both runs. `git diff --check` passes.

Paid happy-path acceptance is retained from the relevant milestones, including
Generic/Social Space tasks, Live Multimodal, both sensing demos, two RPS rounds,
both core guessing roles and healthcare guessing. All failures and reasons for
targeted reruns are recorded below; successful runs do not erase them. CA-08 changes
no provider wording. CA-07's optional encounter opening was checked with scripted
provider/browser boundaries; its physical greeting quality remains an operator
check. Repeated paid trials solely to obtain another pass are unnecessary.
Waiting remarks, duplicate questions and
premature wording remain prompt-only limitations. No claim is made to have fixed
physical audio cut-offs, camera accuracy or actual robot execution.

[CORE_AGENTS.md](../tests/gptlive/CORE_AGENTS.md) provides a concise physical
operator script, fresh-instance/schema guidance and client integration handoff.
Standalone Valerian `feature/galadinnerupdate` at `398f410` was inspected read-only:
it already handles capture IDs, duplicate openings, scoped playback cues and
optional presence reception. No new client contract is required; no standalone
hardware acceptance or merge/deployment was performed by this milestone.

## CA-07 — optional Social Space reception

New Social Space definitions opt in with `prometheus.core.social-space.reception`
(default false). The persisted profile and existing ExternalVoicePolicy hooks reuse
the cockpit's presence lifecycle and two-second audio/departure grace. Fresh,
source-ordered occupancy is required before a claim; prior dialogue/invitations/
sensory occasions are excluded from new Live context without deleting transcripts.
Weather retains its source expiry. Agreements, drafts and action counts survive.
Session-bound tasks pause; confirmed autonomous tasks retire the previous session's
caption gate and continue. Pending, uncertain or wrong-revision responses cannot
silently authorize continuation. Generic, saved profiles and Text/Continuous keep
their lifecycle. No new HTTP contract, schema or polling is added.

Verified: 25 focused unit/local-MySQL checks in `6dcc325d20`, covering scoped claims,
JPA reload, transcript retention, task pause and access revocation. An initial unit
fixture populated RUNNING storage while leaving the graph in configuration; it now
sets the matching execution state. Four final reception units pass, including a
stale response revision (`target/ca07-final-unit.log`). All 13 existing client
reception tests pass (`target/ca07-node.log`). Browser `8a1301113d` passes the real
scoped Social Space journey and existing dropout regression: no-person waiting,
arrival, brief absence with audio intact, departure closure, new session/history
boundary and explicit Stop. Live-disabled variant is correctly skipped. Screenshot
of the new encounter was visually inspected; durable old conversation remains
visible in the recording while excluded from provider startup context.

The fixture app alone enables the new mode; ordinary/paid apps retain the default.
All owned local test resources were removed. Physical camera/robot/audio behavior
is not established by synthetic browser media and still needs the final operator check.

## CA-06 — persisted guessing roles and secrets

Core role negotiation remains playful; healthcare keeps its care-center tone,
engagement and closing protocol. New graphs persist a role/phase/round record.
The agent selects from five familiar curated cards; the human's unspoken item is
never invented or persisted. Give-up reveals without closing the conversation;
fresh rounds replace the record. Control inference uses at most 30 current-round
events while durable transcripts remain intact. Unknown details and ambiguous
questions have different wording guidance. Live owns wording; Text/Continuous
resolve the same card through the existing storage-backed PromptPolicy formatter.

Common harmless replies/give-up use local checks. A pure local exit guard prevents
binary answers from closing active games; the allowlisted guard participates in
existing combined model-check preparation rather than breaking batching. Other
substantive game turns add one compact operation classification. Inference-budget
tests explicitly account for that call. Wrong names cannot be marked solved even
if control inference says SOLVED; selected role/secret cannot be replaced by model
output. No sensor-driven inference or new database polling is added.

Initial units caught fixture restubbing/canonical-plan mistakes, the guard-batching
regression, and incorrect syntax for the existing storage placeholder. These were
fixed before provider acceptance. `b4b444785b` failed only the unresolved-placeholder
check and made no provider calls. `c864bd6d1c` passes 20 Java/local persistence/
prompt/batching checks, but its paid journey answered “No, not blue” for an
unspecified colour; it is a behavioural failure. Guidance now explicitly states
that unknown is not false, and removes conflicting unconditional yes/no wording.
The current-round context edit initially failed compilation (`95157a6ded`, no paid
calls); it now uses the repository's EventSelector.of factory. Subsequent provider
and final regression evidence follows below. All runner-owned apps/schemas/accounts
are removed on exit; no Heroku database is used.

`c95669d897` passes 14 focused Java/local checks and the full user-guesses paid
journey: known nonliving answer, explicit unspecified colour, comparative
clarification, teacup reveal, fresh dog round and correct guess. Final-source
unit/persistence/prompt/batching checks pass (21 in `ac1f95f3a6`, six in
`c0da5ef681`). The agent-guesses run `ac1f95f3a6` completed its guess and persisted
a fresh second human-secret round, but the harness missed the valid new-round
question “Is your new item alive?”; its assertion now accepts that wording.
Healthcare run `c0da5ef681` similarly stopped on a narrow assertion: “I haven't
pinned down any color yet” correctly expressed uncertainty. The harness now
accepts that paraphrase and separately rejects a claimed positive/negative colour.
These unsuccessful runs are retained; full reruns remain required before acceptance.
Three acceptance-runner boundary tests pass. Unchanged KEEP records avoid redundant
storage writes. Live can still add waiting remarks or repeat an invitation before
updated facts arrive; prompt guidance is not an audio gate.

Healthcare `0240d32cfd` then exposed a real comparative error: answering
"Is it bigger?" as though the visitor had asked "Is it big?". The shared guidance
now requires a comparison reference. `9668035fee` passes five units but the paid
run received only "Blue" for the synthetic "Is it blue?" and produced no answer;
this is not a behavioural pass or evidence that the full question was understood.
The next bounded probe uses a fuller colour question and retains all raw input
captions. No production audio settings were changed to accommodate the test.

`26c0ed36e9` also fails the colour step, but investigation found that its generated
TTS fixture actually omitted the final question. A separate gpt-transcribe check
of the cached PCM confirms only "My next question is about color" was present
(2.6 seconds). `target/ca06-audio-validation.json` retains that diagnostic.
The replacement is one complete colour question. Independent transcription of it,
the comparison, reveal/replay and opposite-role clips confirms their intended
content (`target/ca06-audio-validation-followup.json`). The earlier short "Is it
blue?" PCM was complete, so its partial Live recognition remains unexplained.

`43d06c9044` passes six focused Java/local persistence checks and the complete
healthcare paid journey with the verified input: "I have not specified a color",
"Bigger than what?", teacup reveal, fresh dog round and confirmed correct guess.
It also demonstrates the retained limitation: "One second" before the reveal and
more than one readiness phrase. These are recorded, not treated as proof of
perfect prompt adherence. The backend phase/secret/role checks all pass.

Opposite-role `59b3c0a8e6` guessed dog and persisted SOLVED without a secret, but
the assertion required another mention of dog/correct. Actual speech was "Nice!
Digitally wagging my tail here ... Well played." Confirmation now requires spoken
output, rejects a contradiction, and checks the authoritative SOLVED phase rather
than requiring the visitor's answer to be repeated. Final rerun follows.

`1326421150` retained the correct AGENT_GUESSES role and asked whether the animal
lived indoors with people, a valid follow-up. The harness incorrectly demanded an
immediate dog guess from a clue alone. Its visitor now explicitly requests a guess
after supplying the clue; the replacement PCM was independently transcribed and
verified (`target/ca06-audio-validation-guess-request.json`). No production prompt
was changed to force immediate guessing. The opening caption also ends at "First
overly precise"; caption/audio completeness remains the separate cutoff follow-up.

`f8cd10cb01` exposes a real shared-prompt ambiguity: in AGENT_GUESSES, Live applied
the user-guesses fact-card uncertainty rule to the human's clue ("I haven't
specified a sound"). The rules now explicitly scope card-based answers and
clarification to USER_GUESSES. AGENT_GUESSES may make tentative guesses from human
clues without a backend card; only confirmation needs SOLVED. This fixes the
role conflict instead of forcing a particular guessed item.

`6e93666b76` passes 21 unit/local-persistence/prompt/inference-budget/guard batching
checks and the automatic journey assertions, but caption review rejects the paid
run: after the persisted fresh AGENT_GUESSES round, Live says it is still on the
previous item. It is NOT accepted as a behavioural pass. Current facts now state
explicitly that START confirms a new round and the human selects a new secret;
absence of a backend card is intentional. The runner now rejects that misleading
old-item claim. All owned test processes, schemas and accounts were removed.

`287e5169dc` passes five units and the new-round assertions, with correct fresh
human-secret wording. Review found that the harness advanced after a waiting
remark ("I'm checking for the official okay first") at the solved turn. It now
waits for a substantive acknowledgment/closing invitation before replay, accepting
natural paraphrases such as "Nice", "Well played" and "lucky". This run is retained
as incomplete speech acceptance, not a complete happy-path pass.

Final `74c19c0eec` passes the tightened AGENT_GUESSES paid journey: correct role,
"A dog?", substantive success acknowledgment, and "Think of a new item, please"
on replay, with a new persisted round and no stored human secret. Caption review
accepts these outcomes; a repeated initial question remains the documented
prompt-only duplication limitation. Final facts have five passing unit checks
(`287e5169dc`); the preceding 21-test local/prompt/batching suite also passes.
Together with `c95669d897` (core USER_GUESSES) and `43d06c9044` (healthcare), all
three paid happy paths have been exercised successfully. CA-06 is accepted.

## CA-05 — round identity and coordinated capture

New core RPS graphs retain Start/Reveal/Result/Final and their replay route while
persisting a fresh round ID, source-time capture boundary, owner/session/epoch,
preparation deadline and bounded results. Selection/result computation use existing
deterministic RPS rules. Old captures, low confidence, stale/future/out-of-order
observations and unstable camera signs cannot finish a round. Live starts capture
and requests its gesture only after the existing captured-countdown/playback cue;
Text/Continuous retain their speech/motion plan. Timeout/session closure aborts
without a winner, allowing a fresh attempt. No new continuous sensor traffic or
provider call is needed for sign acceptance or result calculation.

Verification so far: `d7cbf162bb` passes 37 Java/local-MySQL checks, including
two-round reloads at every boundary, legacy rule/prompt checks and reset. The
initial unit runs exposed a decision-mock mismatch, an overly broad no-interaction
assertion counting options reads, and an old sensor fixture expecting a reaction
to malformed evidence. Corrected fixtures preserve the stricter production rules.
All 17 hand-sensor/playback Node checks pass (`target/ca05-node.log`). Local browser
run `131da2ad3a` passes with Live enabled and disabled, including real manual-sign
capture, persisted result, monitor popup, screenshot and reset. Result screenshot
was visually inspected; no rendering issue found.

The initial paid run `d7cbf162bb` completed both rounds with correct results, but
caption review found an early duplicate countdown and invented sign before the
first backend selection. It is **not** accepted as a behavioural pass. The initial
state now uses explicit no-round facts and the same application voice policy as
the active round; the provider harness rejects repeated countdowns and announced
own signs. The follow-up evidence is recorded below when complete. Real-provider
speech remains probabilistic; these checks do not prove physical audibility,
robot gesture timing or camera exclusion of the robot's own hand.

Follow-up `b7d9616207` passes 11 unit/persistence checks and both paid rounds with
one countdown each and no invented sign, but its English results copy German
labels from the old diagnostic `reason`. Live facts now omit that redundant field;
durable results and Text/Continuous are unchanged. Final `01ad02d8a6` passes all
five round unit checks and the tightened paid journey: one countdown, no premature
own-sign announcement, correct English draw/win, two motion requests, new capture
identity and old-capture rejection. All owned local apps/databases were removed.
CA-05 is accepted. Prompt wording still cannot guarantee hard speech timing.

## CA-01 — baseline and acceptance harness

Added the tracked eight-milestone plan and paid `--paid-core live` runner. It uses
the real production app, scoped APIs, MySQL, control inference and Live WebRTC;
only visitor audio/sensing is synthetic. Default database properties now select
the local test configuration. Loopback enforcement, unique schema/account,
provider preflight, subprocess deadlines/tree cleanup and failure artifacts bound
the run. Ordinary offline acceptance remains available.

Actually verified (2026-10-26):

- 45 Java tests: GenericMultimodalTaskUnitTest, SocialSpaceAgentUnitTest,
  LiveMultimodalUnitTest, ValerianCorePromptContractTest,
  HealthcareUseCasePromptContractTest, GenericMultimodalTaskIntegrationTest,
  SocialSpaceAgentIntegrationTest, SocialSpaceLiveJourneySmokeIntegrationTest.
  Disposable MySQL artifact: `target/gptlive-acceptance-3de3428350`.
- Three Python boundary checks: reject remote database, missing model before paid
  calls, and mixing paid/mocked providers. Node syntax and git diff checks pass.
- Paid baseline passes: `target/gptlive-acceptance-38526dd0ae`. Actual configured
  control model `gpt-5.6-luna`, effort none; Live `gpt-live-1`, Willow; synthetic
  visitor `gpt-4o-mini-tts`, Alloy. SIRA answer, supplied one-person evidence,
  persisted paper hand sign, retained state and attached session all checked.
  Each invocation stopped its app and removed its owned database/account.

All unsuccessful trials are retained, not counted as passes:

- `b8a32b383c`, `401d8771df`: new worktree lacked ignored openai.properties;
  backend route validation failed before control inference. Temporary content-free
  exception instrumentation identified this and was removed. Preflight now rejects
  missing model/key before billing. No production runtime fix was needed.
- `39453c92bc`: test incorrectly required speech for a gesture-only request. The
  existing agent policy requires silence and persisted the paper sign correctly.
  Corrected the test to check execution intent, then ran the successful trial.

Baseline limitation: synthetic speech occasionally splits into short durable user
segments and can cause repeated embodiment plans. Preserve this evidence for
CA-04/08; no capture timing changed. Physical audio/robot execution and full browser
visual regression are unverified at this milestone. No main/agents deployment.

## CA-02 — decorative gesture cadence

New Generic/Social Space and Live Multimodal instances share DecorativeGestures:
12-second spacing, 60-second same-label suppression, five published requests of
history, explicit request/agreed-action exemptions, and reset cleanup. Publication
only replaces the decorative label with NONE; other channels are retained.
Generic's configuration prompt fingerprint and legacy factory default remain.
Saved instances are not silently upgraded. EmbodimentPolicy adds one nullable
storage relationship; README records managed-schema requirements.

Verified so far:

- 43 Java checks (helper boundaries, explicit/task exemptions, existing core
  units and Generic/Social Live journeys) on disposable MySQL
  `target/gptlive-acceptance-1ba8d68547`. The new persistence case covers all three
  agents across reload, repeat suppression and explicit reset.
- Paid Live Multimodal journey passes with the cadence code:
  `target/gptlive-acceptance-4194b9bc66`. SIRA answer, current person count,
  requested paper sign, retained state and attached session verified.
- Shared reset/policy regression passes (`target/ca02-reset-regression.log`),
  covering oblivious history clearing, transitions, regulation reset, nested/outer
  routing and PromptPolicy. Physical movement remains unverified.

## CA-03 — meaningful sensory remarks

New Social Space instances opt into stable 2s counts, 4s conversation priority
and 15s remark cooldown. Face/social demos use 1s stability, 2s conversation
priority and 8s cooldown. Source order/15s freshness remains independent of receipt
refresh. A suppressed crossing is dropped, not queued. Rich social signatures
ignore tracker IDs and confidence jitter but retain confident movement/attention.
Explicit facial corrections/opt-outs suppress automatic interpretation until
resumed/reset; ordinary direct questions remain available.

Live uses expiring, interruptible PROACTIVE occasions and owns wording; these
sensor turns make no language-model call in the backend. Different/invalid newer
evidence or a user turn cancels a pending comment. Text/Continuous generate a
validated plan, discarded if its source expires or continuation is cancelled.
Existing saved Social Space and generic Multimodal Behaviour semantics are retained;
new opt-in policy classes/nullable storage association require a new instance.

- 41 Java/local MySQL checks pass in `target/gptlive-acceptance-9ebcf402e6`:
  helper rules, new agent graphs, existing saved-instance contracts, legacy
  canonical/compact Social Space journeys, Generic task regressions, gesture and
  sensory storage reload. Core sensory persistence covers all three definitions.
- Initial integrated run `b848ece2c7` failed due test fixtures mutating the read-only
  Agent storage projection and counting gateway configuration reads as inference.
  Corrected the fixtures/verification without loosening behavioural assertions.
- Eight focused helper/agent checks also cover conversation priority, source expiry
  and the quiet context after an occasion expires (`target/ca03-final-units.log`).
- Paid face `40e3471bf4`, social `dad7800a17` and Social Space `249b50d7f1`
  journeys pass: ordinary questions, direct current sensing, one stable-change
  comment, retained state and attached session. Face also persists a correction
  and withdraws its pending comment.
- Social trial `42e873c98a` failed an overly narrow test wording check: Live said
  "a second pops in", correctly describing the supplied second visitor. Added
  that semantic alternative and reran; no production behaviour changed for it.

Conversation priority uses committed dialogue timestamps. A just-starting user
utterance can overlap an already admitted remark before its transcript arrives;
the face trial exhibited this at the first question. This is no hard audio gate.
No physical audio/movement or UI rendering change is claimed. All paid runs used
disposable local databases and cleaned up their app/schema/account.

## CA-04 — task facts and contextual short replies

New core instances opt into Live wording, confirmed task facts and identified
speaking occasions; ordinary KEEP answers remain quiet to avoid duplicate replies.
Text/Continuous and saved opt-out contracts retain backend speech. Bare assent
requires a current completed invitation and the unchanged proposal; harmless
acknowledgements preserve control without another model request. Spoken actions
use the existing bounded TaskResponse check; silent gesture actions never wait for
captions. Explicit autonomous tasks can proceed after voice ownership ends.

During invitation testing, found that EventHistory.select discarded source
createdDate. Preserving it in detached selections fixes invitation timing and
CA-03 conversation priority. The earlier CA-03 helper tests bypassed selection;
the added selection regression explicitly protects that missing path.

Initial tests exposed old-contract assertions that needed explicit legacy opt-out,
one test using EventSelector as a functional interface, and a wall-clock unit
fixture with a one-nanosecond future start. Corrected fixtures; retained artifacts.
Integrated attempt `0cc03a255a`: 50 checks passed, one clock-fixture error (no
provider requests). `601f1f2cf1` passed 60 checks with one obsolete new-instance
voice assertion; updated it to check Live ownership and absence of legacy narration.
`b6592ed9f3` passes all 61 selected Java/local-MySQL checks, including new persisted
Generic/Social Space invitation acceptance, legacy scripted journeys, autonomous
continuation, timestamped selection and reset.

Paid attempt `1dcabaca6f` failed: invitation recognition missed "Want me to start
it?", and a split request ending in "finishes" produced a replacement proposal
without ON_CUE timing. Added the invitation form, runtime firstAction guidance
outside the unchanged configuration prompt, preservation guidance for fragments,
and rejection of IMMEDIATE for an explicit waiting request. The trial also showed
unsolicited waiting acknowledgements despite silent-wait guidance; prompt-only
speech coordination remains a limitation, not a claimed fix. No global capture
interval or media interruption changed.

`5b489b3846` passed the new task checks; three lexical-boundary tests still assumed
the old voice contract and now explicitly exercise legacy opt-out. `5920679f60`
passes all 43 selected local checks, but its paid journey rejected the proposal.
An explicitly paid, single-request synthetic control probe reproduced the cause:
the model used COMPLETE for "show once and finish". COMPLETE performs no action,
so ON_CUE correctly rejected it. Added guidance to use ACT plus maxActions=1 and
a rejection regression; `target/ca04-control-probe.json` retains the failed raw
synthetic response. The fixture starts no app/database and requires paid opt-in.
Post-fix synthetic control probe passes (`target/ca04-control-probe-fixed.json`);
36 final focused tests pass (`target/ca04-final-units.log`).

Paid Generic `0541648618` passes: ordinary
conversation/perception, ON_CUE one-action proposal, current completed invitation,
short acceptance, semantically confirmed activation, fresh rock cue, persisted
paper motion, completed phase and harmless acknowledgment. Social Space
`af78a01d8c` passed state assertions, but transcript review found contradictory
"activation is still pending" wording and a late arrival greeting before completion.
Do not count that as full behavioural acceptance. "Yes, please" split into Yes
and a polite tail; KEEP then cleared the still-owed activation facts. Polite tails
now preserve pending confirmations without inference, as do ordinary KEEP updates.
Strengthened smoke assertions to reject pending/repeated-acceptance wording and
an obsolete greeting during the result. `66028ea493` passes the 10 focused
unit/persistence checks, but its paid run exposed a conservative invitation
recognition miss for "Want me to start that?". Added the demonstrative-pronoun
case and regression; no task was activated by the unrecognized invitation.
`a421ebdc9a` again passes the 10 local checks but its paid invitation was
"Want me to start this when you say so?". That conflates accepting/arming now
with future activation, and remains deliberately unrecognized. Clarified this
distinction in the proposal facts and voice guidance; added a rejection test.
`d1b6c72c83` passes nine focused checks. The paid provider used the unambiguous
but shortened "Activate it now?", exposing another conservative recognition miss.
Made the modal invitation prefix optional and added the exact completed-segment
regression; conditional future invitations and unrelated questions remain rejected.
`b4e03658d0` passes nine focused checks but reveals an invitation timing defect:
the full speech segment began before the final proposal fragment committed,
whereas its final "Start it?" question arrived afterward. The invitation check
now uses existing timestamped speech parts to require that the question itself
starts after the current proposal; older/overlapping questions remain rejected.
No capture interval or additional provider check is introduced. `4ca54483f0`
passes 11 unit/persistence checks; its paid invitation adds the explicit
"and start waiting now" clause. Added that bounded conjunct to recognition and
the regression cases, preserving rejection of future activation and changes to
the proposal. `fc96ea1719` again passes focused checks but misses a natural
"activate that waiting rule now" invitation. These accumulating lexical misses
showed that a wording whitelist conflicts with Live wording ownership.

The final approach records a current completed question, then reuses the existing
control inference to interpret assent. Deterministic validation permits only KEEP
or activation of the unchanged draft; it additionally rejects deferred wording
without explicit current activation. No extra invitation inference is added.
`target/ca04-contextual-units.log` exposed a Mockito restubbing fixture error;
the corrected fixture and all 38 selected unit regressions pass in
`target/ca04-contextual-final-units.log`. Synthetic paid probe
`target/ca04-confirmation-probe.json` correctly handled activation/unrelated/revision
but misclassified future activation. The added deterministic guard rejects that
same response; all four persisted outcomes now pass in
`target/ca04-confirmation-probe-guarded.json` (five bounded provider requests total,
including one proposal). This does not claim infallible model intent recognition.
`92917226ba` stopped before provider execution: the question unit fixture could
capture and propose in the same clock tick. It now pins the proposal boundary
explicitly; production strict ordering is unchanged. `d3ebd9cc27` passes all 12
selected unit/local-persistence checks and the full paid Social Space journey:
proposal, short acceptance of "activate that waiting rule now", captured substantive
activation, no ambient arrival remark during the task, one fresh rock/paper action,
completion and harmless acknowledgment. The recorded response association points
to "It's active now and waiting for that rock sign", not its preceding "Okay".
Three Python runner-boundary checks and JavaScript syntax also pass. Generic recheck
`e1675bc70d` failed before the task: Live invented "Service Innovation and Robotics"
as SIRA's expansion. The compact Generic policy omitted the correct definition
already present in the shared core prompt. It now includes that same shared name
and brief lab facts; the configuration prompt is untouched. Added instruction/budget
and negative provider-output checks. `cac7126402` passes core prompt/unit checks
and answers the lab question correctly, but offers activation in a statement
rather than a question. The completed-context admission now supports statements
as well as questions; meaning is still checked by ordinary task control, with
unchanged-draft/deferral validation. All 38 focused regressions pass in
`target/ca04-prompt-units.log`; six paid confirmation cases pass in
`target/ca04-confirmation-offers.json` (one proposal plus six control requests).
Final Generic Live recheck `da1e9c2233` passes: correct lab/perception answers,
proposal/lifecycle explanation, short acceptance, captured activation, one fresh
rock cue, paper motion, completion and harmless acknowledgment. Together with
Social Space `d3ebd9cc27`, the required task journeys pass. `cac7126402` also
passed the core prompt/unit checks, including the unchanged configuration prompt
and Live instruction budget. CA-04 is complete; CA-05 is next.
All owned apps/databases/accounts were
removed. No Heroku database was used. Live sometimes adds acknowledgments or
technical lifecycle wording despite guidance; physical speech/movement and hard
audio-turn coordination are not established by these tests.
