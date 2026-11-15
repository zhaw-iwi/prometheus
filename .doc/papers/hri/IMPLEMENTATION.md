# Study application implementation

Read PLAN.md for sequence and RESULTS.md for actual evidence. This file describes
the evolving software contract; it is not a participant protocol or collection approval.

## Current boundary

OS-01–04 provide materials, a study definition, durable attempts, private forms,
recoverable text turns and research recording/export. The landing page is closed.
Scenario execution and assessments are later milestones. Study attempts have independent procedural stages and own
one agent after preparation. Agent task state remains owned by Coordination.

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
`prometheus.study.information-version`; defaults keep collection closed. Ethics,
information text, retention and withdrawal policy remain owner prerequisites.
Allocation is currently operator-selected and stable; balanced random allocation
and the complete operator workflow belong to OS-09.

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
work and records the reason without claiming task success. The assessment screen
is a placeholder until OS-09. Finish differs from an agreed task Stop; withdrawal
closes the attempt. Human information and collection remain gated pending OS-09
and the owner's study arrangements. Compact study status wording preserves the
shared control rules, including unsupported proposals; baseline wording is unchanged.

## Verification commands

`python tests/gptlive/run_acceptance.py --study` runs the study foundations against
an owned disposable local MySQL schema, including two independently started
application contexts to verify persistence after restart. `--browser` adds the
selected study browser suite. Paid checks remain explicit through
`--paid-core coordination-text`. Never point fixture cleanup at a shared database.
