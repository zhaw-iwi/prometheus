# Reproducing study acceptance

Use the repository bootstrap, JDK 21+, Node/Playwright, Python with pymysql and
local MySQL administration as described in README. All data must be synthetic.
The runner reads the local administration config, creates its own random schema
and restricted account, binds the app to loopback, and removes those resources on
exit. It never runs against the configured application schema. Avoid parallel
capacity measurements or unrelated database workloads during capacity runs.

```powershell
python -m unittest discover -s tests/gptlive -p test_acceptance_runner.py
python tests/gptlive/run_acceptance.py --study-acceptance
```

The second command selects all ordinary Java tests (the separately enabled
classroom-capacity test is explicitly excluded), all JS regressions and the coding
rehearsal, all study browser cases, dataset analysis, and a production JAR smoke.
Selected Java/browser cases cannot be empty, skipped, retried or silently filtered.
It uses test providers with external inference disabled. Paid trials require a
separate explicit command; there is no automatic provider fallback.

The evidence directory is printed as `target/gptlive-acceptance-<id>/`:

| Artifact | Interpretation |
| --- | --- |
| `manifest.json` | Source revision/dirty flag, selected tests, source/material hashes and finite limits |
| `study-tests.json`, `TEST-*.xml`, `java.log` | Actual Java counts and individual reports |
| `javascript.log` | Client/coding tests and count |
| `study-browser.json`, `browser-false/` | Real HTTP/DB/SSE journeys, screenshots and failing traces |
| `browser-false/*/completed-export.json` | Three complete scenario records and two unsuccessful completed procedures |
| `browser-false/*/dataset.json` | All synthetic attempts, including deliberate recorder fault fixtures |
| `browser-false/analysis.json` | Counts/denominators, agreement/form checks and explicit incomplete/missing counts |
| `release/*.jar`, `package.json`, `app-package.log` | Configuration-free application, hash and DB/HTTP boot smoke |

The analysis checks immutable accepted snapshots, failed attempts and missingness.
The combined dataset intentionally includes the recorder-deletion and missing-runtime-agent fault fixtures and baseline agent cleanup;
report them as incomplete rather than filtering it or claiming every fixture passed
recording completeness. The five participant procedure exports must individually
be complete. Private answers in these artifacts are synthetic and operator-only.
Do not commit participant exports, credentials, logs or raw provider captures.

The `study-release` Maven profile excludes local application/OpenAI configuration
and test fixture classes. Supply database/provider/study settings externally when
running the JAR. Do not publish the ordinary development JAR with ignored local
properties inside it. The package smoke verifies DB health and the participant
route; the full execution journeys run against the same compiled production code
with test-only providers. Production deployment and human qualification are later
gates, not consequences of a successful package smoke.

For a focused correction, use `--study --java-tests ClassA,ClassB` and/or
`--browser --browser-grep <title>` as appropriate. Such a run is focused evidence,
not the complete acceptance command. Keep failed artifacts and document why a
rerun was warranted. A screenshot or HTTP status alone does not prove semantics.

## Sustained study capacity and production recovery

```powershell
# Diagnostic script rehearsal only, never capacity acceptance.
python tests/gptlive/run_acceptance.py --study-capacity 90
# Sustained reduced four-session candidate after the measured eight-session failure; run without other DB workloads.
python tests/gptlive/run_acceptance.py --study-capacity 1800 --study-wave 4
```

This selection cannot mix paid providers, browser tests or a partial Java suite.
It enables admission, runs actual HTTP/DB/SSE with a deterministic one-second
provider, completes all three procedures, tests the blocked extra invitation,
then restarts a configuration-free production JAR with `ddl-auto=validate`.
The restart checks the wave's completed exports and one additional pending contextual
question/receipt, followed by its technical ending and assessments.

Artifacts: `capacity.json` (requests, distributions, resources, query phases and
qualification), `capacity-export.json`, `local-quotas.json`, `capacity-package.json`
(JAR hash and exclusions), and `capacity-recovery.json`. `recovery-private.json`
contains synthetic invitation credentials only for this owned run; keep it ignored
and never copy it into a human dataset or published artifact. Numeric/semantic
limits and interpretation are in CAPACITY.md; actual results are in RESULTS.md.
