# Commanding promotion — HRI experiment

The owner authorized merging main into `experiment/hri-2027-coordination` and
`agents`, followed by Heroku deployment of agents on 2026-11-19. This branch
integrates the completed roadmap at `506b01ab` plus the shared instruction-budget
follow-up `84f90dc9`. Study runtime, protocol, camera assumptions and collection
gates remain owned by the experiment branch. This merge does not open collection.

Three conflicts were resolved by retaining both the study and shared behavior:
current project records, the test provider's study/command/ACK controls, and the
acceptance runner's study/repair/commanding selections. Every run now uses an
isolated build directory. The owned-process watchdog retains the study capacity
deadline and the stricter commanding deadline; source manifests remain separate.

Verification:

- 34 Python runner/budget/watchdog cases and six study JavaScript cases pass.
- The first local-MySQL run selected 313 Java cases; 312 passed and one existing
  Coordination voice-budget check failed. Compiled main and experiment guidance
  were identical: optional facial-correction instructions exceeded 16,000 bytes
  by 69. The shared fix shortens command guidance by 83 bytes without changing
  its rules or raising the cap. All 23 voice cases then passed; all 313 selected
  cases therefore have passing evidence across those runs. Main's separate
  focused verification also passed 71 command/voice cases.
- All 15 study browser journeys passed on the corrected source, with no skipped
  selected cases. This covers the participant task flow, revision, persistence,
  recovery, operator records and failure handling.
- All five shared commanding browser checks passed: both native/Continuous mode
  journeys, queued and in-flight obsolete proposal withdrawal, and persisted
  same-type labels. Artifact: `target/gptlive-acceptance-fd45d18ee1/` and
  `target/twc-hri-merge-commanding.log`. The study assessment screenshot was also
  inspected. Owned disposable applications, schemas and accounts were cleaned.

Artifacts: `target/twc-hri-merge-{python,js,acceptance,final}.log`,
`target/gptlive-acceptance-15a99c3edb/` (retained initial failure),
`target/gptlive-acceptance-24348cddf5/` (voice and study browser follow-up).
This is focused integration evidence, not renewed full camera/capacity or human
study qualification. No paid provider session was started.
