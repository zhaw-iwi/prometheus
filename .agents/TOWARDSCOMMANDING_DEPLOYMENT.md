# Commanding promotion — agents and Heroku

The owner authorized main → `experiment/hri-2027-coordination`, then main →
`agents` with Heroku redeployment on 2026-11-19. Experiment integration is pushed
as `d7d3b303`; its branch-local integration record retains the study evidence.
This agents merge integrates main through `84f90dc9`, including the completed
roadmap and the shared optional-correction instruction-budget fix.

The deployment-specific catalog, personas, embodiment selection, Gala paths,
configuration and agents-triggered workflow remain intact. Conflict resolution
combines persona-resolved embodiment instructions with validated direct commands,
and retains the Gala and new commanding runner selections. An automatic merge
missed the capability-profile argument when rebinding a persona/embodiment;
compilation caught it before publication. Both bindings now retain the profile,
and the existing composition-order test covers the new direct-persona route too.

Merged-source verification:

- 703 selected Java cases passed, including local-MySQL command persistence,
  Coordination, Live, catalog, persona, Aisha and Gala regression coverage.
  No selected failures, errors or skips.
- 28 Python runner/budget/watchdog cases, 111 Live JavaScript cases and 15
  embodiment JavaScript cases passed.
- Five real-app/database browser checks passed: both native/Continuous command
  journeys, queued/in-flight obsolete proposal withdrawal, and instance-label
  persistence/selection. Owned applications, disposable schemas and accounts were
  cleaned. Log: `target/twc-deployment/agents-acceptance-final.log`.
- The initial compilation failure is retained in
  `target/gptlive-acceptance-6c9a2acefe/`; the corrected run is
  `target/gptlive-acceptance-9b93de3948/`.

Predeployment checks used the local Heroku CLI. Valerian v170 was healthy with
web.1 up and maintenance disabled. Credentials were read in memory from ignored
`src/main/resources/application-valerian.properties`; the configured datasource
matches Heroku. A read-only MySQL transaction confirmed connectivity, the four
core runtime tables and `event.history_position`. No production rows were changed.
Private CLI/database evidence stays under ignored `target/twc-deployment/`.

The release will be verified after the agents push using workflow status, Heroku
release/dyno state, health, exact changed asset hashes, read-only cockpit rendering
and a fresh read-only DB connection. No paid provider sessions are authorized or
used for this promotion. Physical microphone/speaker checks remain separate.
