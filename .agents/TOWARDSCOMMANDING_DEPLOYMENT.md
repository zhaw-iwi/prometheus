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

## Deployed release

Merge **`223ad2a9`** was pushed to agents. [Deployment run 44546877327](https://github.com/zhaw-iwi/prometheus/actions/runs/44546877327)
succeeded for that exact commit. Heroku **valerian v171** succeeded at
2026-11-19 14:44:58 UTC (15:44:58 CET), container `b244c7b6f70d`.

Readiness verification passed:

- Heroku CLI reports web.1 up, maintenance disabled and the release succeeded.
  `/actuator/health` reports UP.
- URL, username and password from the ignored Valerian properties match Heroku's
  datasource configuration. A fresh read-only transaction confirms connectivity
  and `event.history_position`. The local verifier initially lacked the RDS trust
  root; loading the [official Amazon RDS CA bundle](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html)
  enabled certificate/hostname-verified TLS. No certificate checks were disabled
  and no application/database configuration was changed.
- All four changed client assets and all nine standard cockpit/robot assets match
  the deployed commit (ten distinct assets, accounting for overlap).
- The read-only deployment browser verifier loaded the actual cockpit, rendered
  a local synthetic scissors/question plan, switched Robot/Details and preserved
  speech/latest-event display. Zero page errors and zero attempted writes. Its
  screenshot was inspected; the robot's face and requested pose are complete.
- Post-release logs include successful Spring startup. The inspected 976-line
  window contains no application ERROR lines, database connection failures or
  Heroku router error codes. All owned local integration schemas were verified absent.

Evidence: `target/twc-deployment/{workflow,workflow-jobs,heroku-app-after,heroku-releases-after,release-verification,startup-log-summary}.json`
and `target/twc-deployment/browser/{verification.json,deployed-robot.png}`.
This release record is committed separately with `[skip ci]`; the deployed runtime
remains `223ad2a9` / v171. No paid provider sessions were used for this promotion,
and no production agent interactions or database rows were created by verification.
Physical microphone/speaker checks remain separate. Reload the cockpit to fetch
the updated client before trying the commands and instance labels.
