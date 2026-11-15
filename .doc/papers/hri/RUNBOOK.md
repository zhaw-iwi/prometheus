# Study operator and pilot handoff

Read PAPER_BRIEF.md and STUDY.md for the research purpose; INSTRUMENTS.md defines
assessment/coding. This file covers operating the implementation. Human collection
is still closed. Software tests use invented SYNTHETIC records only.

## Build and configuration

Use the branch and exact commit reported in RESULTS.md. Run the integrated
acceptance command in ACCEPTANCE.md. Its `release/` JAR excludes local configuration
and test fixtures; retain its SHA-256 and manifest with the protocol version.
Do not distribute the ordinary development JAR, which may contain local properties.
After acceptance, an isolated release can also be built and launched as follows
(the private configuration path is an example to replace, not a supplied file):

```powershell
.\mvnw.cmd -Pstudy-release "-Dprometheus.build.directory=target/hri-release" -DskipTests clean package
java -jar target/hri-release/prometheus-0.0.1-SNAPSHOT.jar --spring.config.additional-location=file:C:/private/prometheus-study/application.properties
```

The explicit build directory keeps this package separate from the editor's
`target/classes`. The private file must contain the settings below, including
provider configuration; none is embedded in the release JAR.

Run one Java process against a dedicated MySQL schema. Supply settings through
environment variables or a private external properties file; do not copy secrets
into this document, Git, invitation URLs, or screenshots. Required configuration:

| Setting | Study operating value |
| --- | --- |
| `spring.datasource.url/username/password` | Dedicated deployment schema/account; separately backed up |
| `spring.datasource.hikari.maximum-pool-size` | 10, as tested |
| `prometheus.runtime.tick.enabled` | false |
| `prometheus.live.enabled` | false |
| `prometheus.study.max-active` | 4 for the measured local workload; recheck deployment quotas |
| `prometheus.study.presence-seconds` | 120 |
| `prometheus.study.max-turns` | 50 new ingress requests per attempt |
| `prometheus.study.interaction-minutes` | 45 after agent provisioning |
| `prometheus.admin.token` | Dedicated secret; enter only into operator UI |
| `prometheus.study.source-revision/source-dirty` | Actual deployed commit / false for frozen build |
| `prometheus.study.collection-enabled` | false until the human release gates below |
| `prometheus.study.information-version/information-text` | Institutionally reviewed information, contact, withdrawal and retention terms |
| `openai.*` and optional purpose routes | Exact reviewed model/effort from PROVIDER_MATRIX/RESULTS; 30-second timeout, 2,048 completion-token cap |

The observed bounded A/B/C live scripts used 5-8 calls and about 29,500-31,900
tokens each. Four such sessions suggest roughly 20-32 calls and 118,000-128,000
tokens per wave; two waves/hour suggest 40-64 calls and 236,000-256,000 tokens/hour.
These are planning estimates from short synthetic scripts, not maximum costs.
Bursts and longer human histories require additional provider allowance.

Keep provider/account RPM, TPM, spending limits and hosting DB allowance in a
restricted deployment record. OS-11 gives bounded call/token evidence, not an
account quota or a maximum human-session cost. Configure alerts/spend limits before
opening a wave. Changing models or prompt routes requires affected semantic trials. Longer human
histories or faster intake can exceed the scripted query estimate; inspect pilot
query/turn volume and lower intake further before main collection when needed.

Expose the participant and operator pages over HTTPS on the same origin. Preserve
custom credential headers and SSE streaming through the proxy; disable buffering
and test reconnect. Restrict operator access, configuration and backups. Do not run
another application process or edit study ownership directly in the DB: admission
leases and the ownership index assume one process. Keep invitations private;
participant credentials are removed from the URL and held in tab session storage.

## Schema and upgrade

The repository uses Hibernate schema generation, with no migration framework.
For a new empty dedicated schema, initialize with `spring.jpa.hibernate.ddl-auto=update`,
inspect startup/health, then use `validate` for the frozen deployment. Back up and
rehearse any upgrade on a restored copy before changing a populated study schema.
No destructive migration or historical-data conversion has been authorized.

Study tables are `study_attempt`, `study_form`, `study_turn`, `study_record`,
`study_allocation`, and `study_batch`, alongside the existing agent runtime tables.
Attempt ownership is unique; token hashes are unique; form kinds, turn request IDs
and recorder keys are unique within attempts. OS-12 adds nullable
`study_attempt.interaction_started_at` (microsecond timestamp), populated when a
fresh agent is provisioned. Old synthetic fixtures can have null start times;
the ingress-count bound still applies. Create fresh invitations for the pilot.

Do not manually modify frozen metadata, snapshots, forms, receipts or material
versions. Each invitation freezes its information/materials. A changed protocol
requires a new material version and new invitations, with affected verification.
Database backups are the recovery source; JSON exports support analysis/audit and
are not a complete runtime restore format.

## Before each wave

1. Check DB health, provider availability, quota/headroom, disk space, HTTPS/SSE
   and the deployed revision/configuration. Use synthetic mode for the smoke.
2. In `/hri-study/operator.html`, provision a small synthetic batch. Complete one
   assigned scenario, including prediction, acceptance, revision, probe and forms.
   Reload/reconnect; ensure the draft and receipt survive. Check JSON completeness
   and answer CSV. Do not call synthetic records participant results.
3. Provision PILOT or MAIN only after the relevant release gate. Balanced blocks
   of three allocate tasks; do not redraw an inconvenient assignment. Invitation
   credentials are returned once. Save/distribute them securely. Batch retries
   return identities, not secrets; a lost invitation needs a linked replacement.
4. Admit no more than the measured wave cap and at most two four-person waves
   (eight interaction starts) per hour under the measured quota envelope. For
   asynchronous participation, the operator must enforce this intake schedule;
   the automatic cap limits simultaneous presence, not starts per hour. Leave
   time between waves for export and recovery, and name the available operator/support contact. An issued
   invitation is not an admitted interaction; private preparation saves only once
   a slot is available. Keep the page open while retrying a full wave.

## During and after interaction

- Participants use typed dialogue and real camera-derived observations. No microphone,
  raw-image upload, real patient data or actual medication recommendation is involved.
  Scenario B requires a participant-arranged consenting adult helper; see CAMERA.md.
- Visible pages renew presence every 30 seconds. After 120 seconds hidden/offline,
  a slot may be reassigned. Returning pages reacquire; assignment/history stay saved.
  The UI explains a full wave and retains Finish/withdrawal. There is no queued
  background inference waiting for capacity.
- Pending/uncertain turns retain their request identity. Check the saved receipt;
  do not recreate or automatically resend an uncertain request under a new ID.
  Cancel/reconcile explicitly. Technical failure or no agreement still proceeds
  to understanding and post-interaction questions; preserve the ending reason.
- At 50 ingress requests or 45 minutes, further interaction is bounded. Use Finish
  and the timeout ending, then complete assessments. This does not claim success.
- Log assistance as TECHNICAL, PROCEDURAL or CONTENT. Content help can affect the
  research outcome and must remain visible in analysis. Keep unsuccessful attempts.
- Revoke lost/compromised invitations; replacement retains participant/task linkage.
  Do not overwrite an existing attempt or count replacements as independent people.
- Export JSON/CSV outside active waves. Check recording, procedure, configuration,
  display and unresolved-request indicators separately. Save the exact build,
  materials, operator assistance and failure records with the dataset.

Dataset `participants` counts issued pseudonymous identities, including invitations
that never consented. Research denominators must filter by mode/consent and declared
inclusion rules; report invited, consented, started, completed, withdrawn and failed
separately. Do not treat all provisioned identities as N or discard failed sessions.

## Recovery

Stop admission during maintenance. Restart the same build/schema/configuration;
ownership reloads from persisted attempts and leases begin empty. Returning active
pages reacquire up to the cap. Verify assignment, forms, accepted terms, pending
question, request replay and export before reopening a wave. A provider call cut
off by restart is uncertain; absence of a saved commit is not permission to rerun it.

If the canonical agent is missing, export retained evidence and preserve
`canonical_agent_missing`; do not fabricate continuity. Replace explicitly when
appropriate and record assistance. Restore from a verified backup only with an
operator-controlled recovery plan; no restore/deletion tool is implemented here.
Retention and withdrawal deletion procedures require institutional decisions.

## Human release and pilot checklist — all PENDING

The material manifest has all scenarios `ready:false`; `StudyMaterials.QUALIFIED`
is empty and collection defaults off. Opening human provision is a reviewed source
and configuration change, not a test command. Keep the distinction between
software qualification, pilot authorization and main-study readiness.

- [ ] Institutional/ethics arrangements; voluntary student participation and an
  alternative without academic disadvantage; consent/contact/retention/withdrawal.
- [ ] Review English task cards, clinical fiction, private preparation, probes,
  questions, scoring/coding rules, session limits and support procedure.
- [ ] Verify actual hosting and provider quotas and repeat deployed smoke/capacity
  checks within the deployment authorization. Freeze the tested release/config.
- [ ] Authorize and enable PILOT with reviewed information and versioned materials.
- [ ] Run an actual 4-6-person pilot across all three scenarios. Observe role
  comprehension, task wording, revision/probe timing, physical sign recognition, person detection and helper feasibility,
  questionnaire clarity, duration, accessibility, admission/reconnect and export.
- [ ] Review unsuccessful/uncertain cases and operator help, resolve pilot issues,
  version changed code/materials and repeat affected automated/live checks.
- [ ] Freeze analysis/inclusion/missingness and coding rules; train human coders.
- [ ] Separately approve MAIN invitations and the final 40-50-student schedule.

No automated test substitutes for these observed human and deployment gates.

## Camera condition

New instances default to `prometheus.study.sensing=camera` and v3 materials.
`simulation` is for legacy synthetic regression only; it creates v2 attempts.
Use HTTPS for participant camera access (localhost is sufficient in local tests).
Camera permission and model availability are visible requirements. Permit the
Valerian detector asset hosts documented in CAMERA.md. Do not expose raw recordings.
The prior capacity qualification is not a camera-workload qualification. Pilot/main
material flags and collection remain closed; this change is not recruitment approval.
