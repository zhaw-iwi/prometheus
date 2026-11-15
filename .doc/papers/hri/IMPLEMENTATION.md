# Study application implementation

Read PLAN.md for sequence and RESULTS.md for actual evidence. This file describes
the evolving software contract; it is not a participant protocol or collection approval.

## Current boundary

OS-01–03 provide materials, a study definition, durable attempts, private forms
and recoverable text turns. The landing page is closed. Scenario execution,
assessments and exports are later milestones. Study attempts have independent procedural stages and own
one agent after preparation. Agent task state remains owned by Coordination.

Additive Hibernate-managed tables are introduced: `study_attempt` (hashed
credential, assignment, consent, procedural stage, predecessor and agent binding),
`study_form` (unique attempt/form, material version, private answers and time), and
`study_turn` (unique attempt/request, input, pending/terminal status and canonical
event/result correlation).
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

## Verification commands

`python tests/gptlive/run_acceptance.py --study` runs the study foundations against
an owned disposable local MySQL schema, including two independently started
application contexts to verify persistence after restart. `--browser` adds the
selected study browser suite. Paid checks remain explicit through
`--paid-core coordination-text`. Never point fixture cleanup at a shared database.
