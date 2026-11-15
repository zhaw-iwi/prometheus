# Study application implementation

Read PLAN.md for sequence and RESULTS.md for actual evidence. This file describes
the evolving software contract; it is not a participant protocol or collection approval.

## Current boundary

OS-01/02 provide materials, a study definition, durable attempts and private forms.
The landing page is closed. Turn submission, execution, assessments and exports
are later milestones. Study attempts have independent procedural stages and own
one agent after preparation. Agent task state remains owned by Coordination.

Two additive Hibernate-managed tables are introduced: `study_attempt` (hashed
credential, assignment, consent, procedural stage, predecessor and agent binding)
and `study_form` (unique attempt/form, material version, private answers and time).
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

## Verification

`python tests/gptlive/run_acceptance.py --study` runs the study foundations against
an owned disposable local MySQL schema, including two independently started
application contexts to verify persistence after restart. `--browser` adds the
selected study browser suite. Paid checks remain explicit through
`--paid-core coordination-text`. Never point fixture cleanup at a shared database.
