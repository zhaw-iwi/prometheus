# Study application implementation

Read PLAN.md for sequence and RESULTS.md for actual evidence. This file describes
the evolving software contract; it is not a participant protocol or collection approval.

## Current boundary

OS-01–09 implement all three executable text scenarios, resumable sessions,
private forms, durable turns, research evidence, assessments and operations.
Human collection stays closed pending qualification and institutional arrangements.
Study procedural stages own one agent after preparation; Coordination retains
agreement and task authority.

Additive Hibernate-managed tables are introduced: `study_attempt` (hashed
credential, assignment, consent, procedural stage, predecessor and agent binding),
`study_form` (unique attempt/form, material version, private answers and time), and
`study_turn` (unique attempt/request, input, pending/terminal status and canonical
event/result correlation). `study_record` contains append-only metadata, procedural
changes, provisioning/turn events, acceptance snapshots and client evidence.
Use the repository's normal schema setup on a separate study database. No data
backfill or modification of baseline saved agents is required.

## Access and HTTP foundation

Operator endpoints use the existing `X-Prometheus-Admin-Token` and reject access
if `prometheus.admin.token` is blank. Participant requests use a separate
`X-Prometheus-Study-Token`, a random 256-bit bearer credential stored only as SHA-256
in the database. Issue it once; do not put it in query strings or application logs.
Study views expose neither this credential nor the underlying agent ID/access code.
No demo access code is created. Generic workbench routes cannot read or mutate a
study-owned agent, and generic lists exclude it. Ordinary agents retain their routes.

| Method/path | Body and result |
| --- | --- |
| `POST /admin/study/attempts` | `{ "mode":"SYNTHETIC", "scenario":"guess", "predecessor":null }`; returns `session` and one-time `token`. Explicit replacement links a new identity and closes the predecessor |
| `GET /admin/study/attempts/{id}` | Procedural metadata only |
| `POST /admin/study/attempts/{id}/revoke` | Closes further participant mutation |
| `GET /api/study/session` | Resumable metadata for the supplied credential; never reallocates or reprovisions |
| `POST /api/study/consent` | `{ "version":"synthetic-test-v1" }` for synthetic fixtures only |
| `POST /api/study/forms/pre` | Object mapping every manifest pre-item ID to an answer; `rating7` values are strings `1`–`7` |
| `POST /api/study/forms/intention` | `{ "intention":"..." }`, captured before the private-choice prompt |
| `POST /api/study/forms/private` | `{ "private":"..." }`; atomically stores this private record, creates the agent and advances to COORDINATION |
| `POST /api/study/withdraw` | Closes mutation, retains a withdrawal record pending the selected retention policy |

Private answers never enter agent storage, history, initial prompts or response
views. Form fields are bounded to 2000 characters. Unchanged repeated form
submissions are idempotent; conflicting replacements return 409. Invalid inputs
return 400, absent/invalid credentials 401, wrong stage/closed attempts 409, and
hidden agents 404. Consent version and time are durable. Concurrent preparation
submissions serialize on the attempt row; provisioning and ownership commit or
roll back together, including failure after the agent save.

Modes are `SYNTHETIC`, `PILOT` and `MAIN`. Synthetic is explicitly for operator-owned
software fixtures and permits testing the procedure before scenario execution is
ready. It must not be used for recruitment. Human modes require a ready scenario,
`prometheus.study.collection-enabled=true` and a nonblank reviewed
`prometheus.study.information-version` and reviewed information text; defaults keep collection closed. Ethics,
information text, retention and withdrawal policy remain owner prerequisites.
Allocation uses persisted balanced random blocks through the operator batch flow.
Only synthetic fixtures may select a task manually; replacements preserve the task.

## Durable text submission

Use one application process with `prometheus.runtime.tick.enabled=false` for this
study procedure. All task activity currently enters through participant requests;
autonomous scheduled evaluation is outside the tested study configuration.

`POST /api/study/turns` takes `{ "requestId":"<UUID>", "expectedTurn":0,
"text":"..." }` with the study header. Preparation must be complete. The
`expectedTurn` cursor comes from the session or conversation view. Text is limited
to 2000 characters. Concurrent different requests and stale cursors return 409.
The same ID and identical content return the same durable receipt without another
inference; changing either text or expected cursor under that ID returns 409.

`GET /api/study/turns/{requestId}` returns only the credential's own receipt.
`GET /api/study/conversation` returns its turn inputs/receipts and deterministic
saved draft, accepted terms, phase and contribution count. Neither returns forms.
Receipt fields include `status`, canonical `inputEventId` and `responseEventId`,
`responseType`, canonical JSON-string `responsePayload`, `committedTurn`, times
and a bounded diagnostic `reason`. Preserve IDs when reconciling delivery.

- PENDING means received and claimed, not accepted as an agent mutation.
- COMMITTED means the agent mutation and receipt committed atomically. It does not
  establish task success, instruction fidelity, display, or participant comprehension.
- UNCERTAIN means interrupted work or an elapsed 120-second deadline, with no
  automatic provider retry. The session blocks further turns pending reconciliation.
- REJECTED includes explicit cancellation or closure fencing pending work.

Provider waits hold no database transaction. A short commit transaction checks
current attempt/receipt authority, saves the agent and canonical result, and moves
the cursor together. Monitor publication is deferred until commit. Failure before
commit rolls all those changes back; failure after commit can be recovered through
lookup. A crash can leave PENDING until its deadline; elapsed work becomes UNCERTAIN
on lookup. None of this guarantees exactly-once provider billing.

`POST /api/study/turns/{requestId}/cancel` explicitly fences unfinished work and
advances the cursor without replaying it. Refresh the conversation before composing
a new request. It is a submission-recovery action, distinct from negotiating Pause
or Stop with the Coordination agent. Withdrawal/revocation also fence late commits.

## Research records and export

The recorder runs in the same transaction as each relevant mutation. A recording
failure rolls back the agent change and leaves an explicit uncertain request.
Initial and subsequent acceptances have independent, immutable JSON snapshots,
ordinal, canonical acceptance event, run, revision and preparation evidence.
Every committed turn records its events, current contract/control facts and new
journal entries; retention does not depend on the runtime's 12-entry journal or
eight-outcome buffer. Snapshots preserve accepted terms, not an LLM paraphrase.

Creation captures the exact material manifest, English persona, definition key,
configured model routes/effort/token limits/timeouts and source revision/dirty flag.
Set `prometheus.study.source-revision` and `prometheus.study.source-dirty` when
packaging; the isolated runner records Git HEAD and whether its checkout is dirty.
Unknown configuration is flagged, not invented. Route settings describe the
configured system; they are not a claim about every actual provider call or its
token usage. No keys or provider endpoint URLs enter this metadata.

| Method/path | Purpose |
| --- | --- |
| `GET /api/study/events` | Credential-scoped canonical interaction events, ordered by history position; no private forms or researcher rubrics |
| `POST /api/study/display` | `{ "eventId":"<canonical response UUID>", "clientTime":"<ISO UTC>" }`; one client rendering report per response, duplicate-safe; unknown/foreign responses rejected |
| `POST /api/study/client-events` | `{ "id":"<UUID>", "type":"DISCONNECT|RECONNECT|HELP|ERROR", "clientTime":"<ISO UTC>", "code":"<short diagnostic code>" }`; bounded, idempotent client evidence |
| `GET /admin/study/attempts/{id}/export` | Operator-only `hri-record-v1` JSON dataset, with metadata, records, immutable agreements, all request outcomes and tabular private-answer rows |

Export reports separate `recordingComplete`, `configurationComplete`,
`procedureComplete`, and `displayComplete` flags, explicit issues and record
counts. Recording completeness compares canonical history, committed requests,
acceptances and private-form records. Missing records are not reconstructed from
the latest agreement. Configuration completeness means the settings were recorded,
not that draft materials or a dirty source tree are approved/frozen for research.
Pending/uncertain work, absent display evidence and incomplete procedures remain
visible. Client times are untrusted browser reports, distinct from server times;
display reports establish neither reading nor comprehension. Exports contain no
participant bearer credential or its hash. They do contain private research
answers and must remain in authorized research storage, outside Git.

## Participant interface and procedural finish

`/hri-study/` accepts the invitation in a password field or a URL fragment
`#token=...`, immediately removes the fragment from browser history, and keeps
the credential in session storage. Unsent text, form drafts and pending request
identity survive refresh in that tab. Operator credentials never enter this UI.
`GET /api/study/page` exposes only the current stage's materials and fields;
private prompts are not returned before their stage. All content renders as text.

The conversation combines canonical HTTP results and a fetch-based SSE reader
using the study credential header. `GET /api/study/stream` supports
`Last-Event-ID`; an immediate comment flushes headers even with nothing to replay.
Stream messages trigger canonical reconciliation, never speculative rendering.
Display receipts follow rendering on a visible page. The interface requests no
microphone/camera and has no Speech/Live client. Task and agreement panels collapse
on mobile; Help, Finish and Withdraw remain reachable during pending inference.

Unknown transport results retain the request ID for lookup or deliberate retry.
Explicit cancellation before receipt creates a rejection tombstone, fencing a
delayed POST with that ID. Receipt times use microsecond precision so first replies
and database replays agree. No uncertain model request is retried automatically.

`POST /api/study/finish` takes `{ "reason":"ACTIVITY_ENDED" }`; other reasons are
`NO_AGREEMENT`, `TECHNICAL`, `TIMEOUT`. It durably enters ASSESSMENT, fences pending
work and records the reason without claiming task success. Assessment, questionnaire
and debrief follow. Finish differs from an agreed task Stop; withdrawal
closes the attempt. Human information and collection remain gated pending reviewed
materials and the owner's study arrangements. Compact study status wording preserves the
shared control rules, including unsupported proposals; baseline wording is unchanged.

## Guessing-game execution

The shared Coordination graph now has a definition-owned update-action factory
and small reply/execution hooks. `StudyCoordinationUpdateAction` implements the
study executor; baseline Coordination still rejects typed activities it cannot
execute. Existing eight-field agreements and saved core agents remain supported.
Fresh study agents use the new persisted action subtype; previously created study
agents retain their original graph and should not be used for this scenario.

An optional `agreement.activity` is validated by `GuessingContract`:

```json
{"kind":"GUESSING","secretOwner":"HUMAN","answers":["YES","NO","UNKNOWN"],"questionLimit":5,"guessesCount":true}
```

This bounded implementation supports a human-held secret, assistant questions and
guesses, YES/NO with optional UNKNOWN, and 1–20 questions including guesses.
Reverse-role games are not implemented by this executor. The secret is never
part of its contract or state. `dialogueLimit` is the question limit plus one
final acknowledgment; the executor owns the question counter and awaits the final
answer even after asking the last question. Typed game counters represent questions
and guesses, not control/support messages. The participant panel displays that
budget explicitly. Nongame synthetic foundation exercises still use ordinary
Coordination contributions.

Each pending question has an ID, kind and exact words. Answer processing accepts
bounded explicit yes/no/unknown expressions. Other text continues through shared
coordination/support, without authorizing an unchecked game STEP or COMPLETE.
The model proposes one QUESTION or GUESS; the backend validates shape and budget,
and passes all accepted terms plus the persisted answer record to generation.
Semantic quality of a yes/no question or guessed object still requires review.
An invalid/failed next move preserves the prior question and budget for explicit
recovery; no automatic model retry occurs.

Only YES to a pending GUESS establishes `HUMAN_CONFIRMED_GUESS`, explicitly a
participant report. `QUESTION_LIMIT_REACHED` and early `CANCELLED` are separate.
Pause/resume preserve the pending question. A new accepted limit is a total,
including already asked questions; revision suspends execution and needs renewed
acceptance. It retains that question and progress rather than asking another one
on START. A limit-only change cannot silently alter answer choices, human role or
success commitments. A fresh game after a finished one starts fresh state; reset
clears game state. Full game state accompanies each immutable TURN record.

`GET /api/study/activity` returns current revision, prediction requirement/saved
status, canonical game state as a JSON string, and applicable revision/probe cards.
`POST /api/study/predictions` takes `{ "revision":"<current revision>",
"prediction":"..." }`. It saves an immutable private form tied to the proposal
revision, records its server time and permits identical retries. Changed or stale
answers are rejected. Predictions and private intentions/secrets never enter agent
history or inference. Starting a typed game requires this pre-feedback record;
ordinary clarifications/revisions remain possible. A natural-language acceptance
that bypasses the early command check is fenced at commit, with an explicit
uncertain receipt requiring cancellation/reconciliation; no game output commits.
The initial and revised prediction are exported separately.

The UI adds prediction, question progress and revision/probe cards, with mobile
collapsing panels. Assessment instruments are specified in INSTRUMENTS.md. Scenario readiness flags
remain false until the complete study procedure is qualified; implementation of
the guessing executor alone does not authorize human collection.

## Contextual check-in execution

`ContextCheckInContract` adds `activity.kind:CONTEXT_CHECKIN`: one exact question,
`yesReply` and `noReply` (each at most 200 characters), `startCount:1`,
`postponeAt:2`, `departure:END` and `resume:EXPLICIT_WHEN_ONE`. It has no independent
sensor reactions/preparation checks and uses two task contributions. The study
profile accepts controlled `obs.human.presence` through a definition-owned scene
transition; ordinary Coordination profiles and saved graphs remain unchanged.

Acceptance arms the encounter without asking its question. A fresh one-person
scene asks it; two or more people or unknown evidence postpone it without repeating
private content. Returning to one person needs explicit Resume. Fresh absence ends
an encounter already begun; return alone never restarts it. Stale absence becomes
unknown, not confirmed departure. The controlled scene persists until changed,
as disclosed in the UI; this is not a real camera's privacy guarantee. YES/NO
responses deliver the exact accepted words and record fictional self-report,
not adherence verification. Revision preserves pending question identity/progress,
requires a prediction and renewed acceptance, then a fresh scene and Resume.

`POST /api/study/scene` accepts `{requestId,expectedScene,humanCount,observedAt,
probeStep?}`. Counts are 0–3 or null (unknown). New commands require a timestamp
within 15 seconds (two seconds future skew); identical accepted retries retain
their durable result. Scene claims advance a persisted scene epoch before waiting
for agent serialization, fence in-flight old-scene inference, and commit through
the existing event/receipt/recorder boundary. Normal text is blocked until the
current scene has been applied or a new scene resolves uncertainty. No provider
inference is needed for context execution. Scene requests, application and receipts
retain source, request identity, client timestamp and server times.

The versioned planned probe is user-paced **1 → 2 → 1**. Its optional `probeStep`
must match the saved next step and expected task state; progress advances only on
commit, survives reload and is idempotent. Manual scene buttons permit additional
exploration. Neither sequence timing nor experimental compliance is inferred from
the button alone; preserve its actual times and completion/missingness in analysis.

Participant `/events` projections withhold unreported old-scene responses. During
a pending scene change, the browser also delays new assistant rendering. A display
receipt has optional `disposition:DISPLAY|WITHHELD`; a late DISPLAY is separately
recorded as LATE_DISPLAY and cannot authorize old content on refresh. Canonical
operator/receipt records retain original generation. Already displayed text cannot
be recalled. Export distinguishes display completion from delivery accounting,
withheld notices and ambiguous late reports. Only the scoped event projection is
display authority; raw receipt/SSE content must not be rendered directly.

`/activity` includes context state, current/applied scene, scene epoch and planned
probe progress. Full context/scene state is included in research TURN records.
New persistence fields are additive; fresh study agents are required for the
definition-owned scene transitions. All human readiness flags remain false.

## Negotiated sign-answer execution

`SignAnswerContract` adds a typed bounded question list:

```json
{"kind":"SIGN_ANSWERS","questions":["Would you like a short activity?"],"yesSign":"THUMBS_UP","noSign":"THUMBS_DOWN","waitSign":"OPEN_PALM","resume":"EXPLICIT_TEXT","demonstrate":true}
```

There are 1–8 distinct exact questions, at most 160 characters each. Either thumb
can mean YES, the other NO. Open palm waits without answering; explicit text Resume
continues the same question. The executor asks saved questions and acknowledges
answers deterministically, completing with `ANSWERS_RECORDED`; it infers no health
outcome. The derived contribution budget is question count plus one. Plain text
yes/no does not answer a task configured for sign answers.

With `demonstrate:true`, all three fresh signs must be demonstrated after the
proposal. Demonstrations populate ordinary preparation evidence, never answers,
and are copied into immutable acceptance snapshots. The shared readiness check
consults this typed preparation contract. A sign cannot accept a draft. Private
prediction and explicit conversation acceptance still precede the first question.

`POST /api/study/sign` takes `{requestId,expectedTurn,observationId,sign,mode,
revision,run,question,observedAt}`. `/activity.signScope` supplies the current scope:
DEMONSTRATE uses the proposal revision and null run/question; ANSWER uses the
accepted agreement revision, run UUID and pending question UUID. `/activity.signs`
contains saved progress. UUIDs/timestamps/mappings are validated before inference
and again by the executor. A fresh command has a 15-second age bound/two-second
future skew. Idempotent request retries return their durable receipt. A distinct
request cannot reuse an observation ID, even across preparation and execution.
The bounded observation ledger admits at most 256 accepted signs per instance;
it never silently evicts old identities to permit replay.

The study-only event type is `obs.study.sign`, actor `study-simulation`, with
`simulation:true` and `source:controlled_study`. It is declared only by the study
profile and produced by the scoped study API. The existing `obs.hand.sign`
rock/scissor/paper sensor and motion contracts remain unchanged. No camera or sign
recognition model is involved. UI clicks are fresh discrete observations; holding
a visual sign is not simulated. Pending signs use the ordinary durable turn path.

Changing an ongoing mapping suspends execution and requires renewed demonstrations
if agreed, a new prediction and explicit acceptance. Questions, wait/resume rules,
human role and success commitments are preserved. The pending question and already
recorded answers survive, but run/revision authority changes: old signs cannot be
reinterpreted. Other question-list changes require ending and agreeing a fresh
activity. The shared replacement-request guard has a study-only extension for
explicit “swap/reverse/change … signs/mappings” requests. Baseline guards are
unchanged. Full sign state accompanies each research TURN record.

## Verification commands

`python tests/gptlive/run_acceptance.py --study` runs the study foundations against
an owned disposable local MySQL schema, including two independently started
application contexts to verify persistence after restart. `--browser` adds the
selected study browser suite. Paid checks remain explicit through
`--paid-core coordination-text`. Never point fixture cleanup at a shared database.
Add `--study-scenario guess` for the bounded guessing/acceptance/revision/pause/
exhaustion/export trial. The foundation greeting smoke remains the default.
`--study-scenario context` selects the fictional check-in, privacy interruption,
revision, explicit resumption and exact-response trial.
`--study-scenario signs` selects demonstration, scoped answers, wait/resume,
opposite mapping revision and bounded completion.
Study runs compile into their own artifact directory through
`-Dprometheus.build.directory=...`, isolating acceptance from an editor's concurrent
incremental compiler. Ordinary Maven builds still use `target`. Reports, compiled
fixtures, browser screenshots and provider evidence belong to that one run.

## Assessment and operations (OS-09)

Default materials are `materials-v2.json` (`hri-text-v2-draft`). Metadata freezes
the entire material/information snapshot at invitation creation. Participant pages,
forms and cards use that snapshot on resume. Read [INSTRUMENTS.md](INSTRUMENTS.md)
for item sources, scoring, protocol timing and human coding rules.

After `/finish`, immutable `ASSESSMENT_REFERENCE` captures the accepted terms,
run/revision and outcome. Stages are ASSESSMENT -> POST -> DEBRIEF -> COMPLETE.
`forms/understanding` and `forms/post` require exact versioned keys; submitted
answers cannot change. Identical retries return the saved result. Optional ratings
support explicit SKIP; exploratory ratings also support NOT_APPLICABLE in the UI.
Understanding is open-reference with explicit unknown/not-applicable answers.
`POST /api/study/complete` saves the completion receipt. Completion means procedure
completion, not task success. JSON exports include transparent UMUX-LITE scores,
missingness and separately named exploratory ratings; free text needs human coding.

`/hri-study/operator.html` holds the admin credential only in page memory.
`POST /admin/study/batches` accepts `{requestId,mode,count}` (1-50), allocates
random balanced blocks of three, and returns invitation credentials once. Retrying
the same ID returns identities only; lost credentials require explicit replacements.
`GET /admin/study/attempts?mode=SYNTHETIC&page=0&size=50` lists progress and failures.
`POST /admin/study/attempts/{id}/assistance` accepts `{id,kind,note}` with TECHNICAL,
PROCEDURAL or CONTENT and an idempotent immutable note. Dataset JSON at
`GET /admin/study/export?mode=SYNTHETIC` and spreadsheet-safe answer CSV at
`GET /admin/study/assessments.csv?mode=SYNTHETIC` retain every attempt, replacement,
failure and missing answer. Dataset export is a bounded operator operation (1000
attempts maximum); perform large exports outside participant waves.

Additive schema: `study_allocation` persists shuffled block/index under a row lock;
`study_batch` persists provisioning request identity and attempt IDs (no raw secrets).
`study_attempt` adds participant identity, allocation metadata, frozen information
and a synthetic-only foundation flag. Replacement copies identity/task and does
not redraw. Each full session restricts proposals/acceptance to its assigned typed
executor. Explicit `foundation:true` is available only for synthetic generic-runtime
fixtures, recorded in metadata and excluded from scenario qualification. Private
forms supply no task parameters; the assigned executor kind is a capability limit.

Human provisioning additionally requires reviewed `prometheus.study.information-text`
and `information-version`, collection enablement and qualified materials. All remain
closed by default. No clinical/physical execution or human readiness is inferred.

## Integrated acceptance and ordinary-route cost

Run `python tests/gptlive/run_acceptance.py --study-acceptance`; see
[ACCEPTANCE.md](ACCEPTANCE.md) for selection, artifacts and package reproduction.
The study-release Maven profile excludes local application/OpenAI configuration.
The browser dataset rehearsal includes every failed/replacement attempt and the
intentional recorder-corruption fixture, reported explicitly as incomplete.

`StudyAgentRegistry` indexes owned agent UUIDs at application startup and registers
new study IDs before provisioning commits. Rollback removes a provisional ID.
Ordinary HTTP guards and list filtering use this positive ownership index without
an additional database query per request. This relies on the documented one-process
study deployment and provisioning through StudySessions; a second writer or direct
DB ownership edits are unsupported. Restart reloads ownership from persisted rows.

Export remains available if a canonical runtime agent has been lost: immutable
records/forms/snapshots are retained and `canonical_agent_missing` makes recording
completeness false. This is an audit/recovery path, not permission to resume that
attempt or silently recreate the agent.

## Provider qualification boundary

Study-generated interpretation replies are projected to speech only after ordinary
validation; missing/blank text is rejected. The model can generate decorative
modalities in its raw response, but they cannot enter the study's canonical output.
Typed executors also emit text only. Baseline Coordination retains its multimodal
behavior. The predeclared bounded matrix is in PROVIDER_MATRIX.md; independent
semantic reviews and all attempts belong in RESULTS.md.
