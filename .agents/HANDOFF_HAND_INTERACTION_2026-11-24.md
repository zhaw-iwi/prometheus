# Hand interaction continuation — 2026-11-24

Implementation checkpoint: **`82d12bc7` on `main`**, pushed to `origin/main`.
HI-01..11 are complete; **HI-12 remains incomplete**. This handoff and its JSON
checkpoint are committed separately so a fresh checkout can resume without this
conversation or the old machine's ignored files.

## Start here

Read `CODEX.md`, `CONTEXT.MD`, and `../PROJECT.md` through Current milestone state,
stopping before Historical milestones checklist. Check the branch/worktree and
pull `main` without discarding local work. Then read:

- `PLAN_HAND_INTERACTION.md`, especially HI-12 acceptance revision 2.
- `HAND_INTERACTION_RESULTS.md`, the **Third-extension final sweep and checkpoint**
  section near the end. Earlier passes do not supersede final failures.
- `checkpoints/hand-interaction-2026-11-24/evidence.json`: curated findings for
  attempts 67–88 and final-sweep native captions, local ASR text, admitted user
  turns, backend model outputs, proposals and task outcomes.

The repository was clean after the implementation push. Other local worktrees
were `agents` at `9f3bb418` and `feature/gptlive` at `86fc501a`; neither was changed.
The owner authorized implementation and commit/push to `main`, not deployment,
an `agents` merge, remote-database work or physical acceptance. No paid runs are
queued and no runner-owned apps remain active. The old machine's local MySQL was
left running because it predated this checkpoint.

## Architecture and fixes to preserve

Backend state owns agreements, preparation, consent, task budget and all
nonverbal behavior. Text/Continuous also use backend speech. GPT-Live owns
immediate independent native speech. Strict “silently do X” is intentionally
unsupported in Live; users can select Text/Continuous. Preserve this decision:
no speech approval gate, buffering, muting or replacement TTS to force a pass.
Keep capture completeness, semantic delivery, target expiry and no-replay rules.

`82d12bc7` repaired incomplete-draft versus protected-agreement guidance,
completed-task context for independent demonstrations, exact imperative consent,
the Live 350-character speech-intent prompt mismatch, and quiet framing on every
sensor chunk. The driver now waits for actual native playback and demonstrates
counts with distinct capture timestamps spanning the dwell. Production model
settings were not changed. Final isolated tests used GPT-6.2 Sol (low) and
GPT-Live-1.1.

Guide further work with the existing files and neighboring tests:

- `CoordinationAgent`, `CoordinationUpdateAction`, `CoordinationTaskContract`,
  `CoordinationAcceptance`, `CoordinationMemory` and `DirectCommands`; their
  Coordination/direct-command unit suites cover interpretation and authority.
- `CoordinationPolicy`, `CoreTaskVoice`, `LiveContextDelivery`,
  `LiveTranscriptSegmenter`, `LiveTranscriptCaptureService` and `TaskResponse`;
  voice/capture/context tests preserve bounded evidence and independent playback.
- `tests/gptlive/hand_journey.mjs`, `hand_scenarios.mjs`, `hand_audio_input.mjs`,
  `hand_paid.py`, the adjacent tests and `hand-paid-plan.json` own the acceptance
  driver and spending limits. `hand-cases.json` contains the spoken stimuli.

## Remaining work

Use the saved failure evidence for an offline reproduction before another paid
selection. Do not spend the remaining allowance on unchanged retries.

1. **Shape clarification (attempt 80 / L-03):** “Show me an okay sign” elicited a
   correct native clarification but backend ACTION/ok_ring. Prevent an ambiguous
   interpretation from authorizing output; retain explicit thumbs-up/ring choices.
2. **Native consistency (79/82/84):** correct backend target/pose handling can
   coexist with invented requirements or contradictory native speech. L-02 passed
   all backend/visual assertions, but Live asked the user to demonstrate an already
   fully specified immediate pose. Keep semantic failure distinct from render pass.
3. **Review capture (79/81/83):** repeated terms/backchannels and long native
   reviews fail complete capture. Existing guidance already requests at most 55
   words. Preserve complete roles/terms and bounded capture; do not merely widen
   limits or mark incomplete speech complete.
4. **Acceptance input (85/86):** the local stimulus says “I accept the current
   proposal”; provider input captions stop at “I accept the current” in both the
   browser and backend. No missing final fragment was available to recover.
   Check stimulus/PCM and provider capture separately; retain explicit consent.
5. Complete the affected paid journeys, especially L-06 person/item and stale
   target removal, only after relevant repairs. The final L-01..08 are failed or
   inconclusive; L-09 and M-01/M-02 pass. HI-12 cannot be marked complete yet.

Verification already run: 363 Java unit tests (28 suites), 48 local-MySQL tests,
six Playwright cases, 45 Python tests, 16 Node harness tests, nine schema examples
and packaging. Final paired L-09 native onset comparison passes. Images and full
received-audio ASR were reviewed; physical microphone/speaker/robot trials were
not run. Only docs and the reservation-ledger minimum changed after the paid
sweep; the latter was reproduced failing, repaired and covered by all 45 Python
tests. These results are historical evidence, not tests run on the new device.

## Carry the spending history across devices

The cumulative authorization is **$200**: $50 + $20 + $30 + $100. The immutable
checkpoint contains **88 attempts/$186 reserved**, including failures. **$14 is
unreserved**, but allocation 9 permits only **two more $5 attempts** (90/$196
maximum) without a new allocation. Actual invoice totals are unavailable.

`checkpoints/hand-interaction-2026-11-24/ledger.json` preserves every entry from
the old `target/hand-interaction-budget.json`. Historical Windows artifact paths
are identifiers; they do not have to exist on the new device and must not be
rewritten. The current validator checks this snapshot without provider access.
The canonical JSON checksum in `evidence.json` is insensitive to Git line endings.

Restore only a missing local ledger. If one already exists, retain its newer
entries and verify the checkpoint prefix. This Python snippet runs from the repo
root and does not call the provider or reserve money:

```python
import hashlib, json, sys
from pathlib import Path
sys.path.insert(0, "tests/gptlive")
from hand_paid import history

checkpoint = Path(".agents/checkpoints/hand-interaction-2026-11-24")
saved = history(checkpoint / "ledger.json")
evidence = json.loads((checkpoint / "evidence.json").read_text(encoding="utf-8"))
canonical = json.dumps(saved, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
assert hashlib.sha256(canonical.encode()).hexdigest() == evidence["ledgerCanonicalSha256"]
local = Path("target/hand-interaction-budget.json")
if local.exists():
    current = history(local)
    assert current["attempts"][:88] == saved["attempts"], "Conflicting history: reconcile before spending"
else:
    local.parent.mkdir(parents=True, exist_ok=True)
    with local.open("x", encoding="utf-8") as stream:
        stream.write((checkpoint / "ledger.json").read_text(encoding="utf-8"))
current = history(local)
print(len(current["attempts"]), "attempts;", current["reservedUsd"], "USD reserved")
```

Use one device at a time for paid runs: local ledgers do not synchronize. Before
switching again, commit the updated budget checkpoint and findings, preserving
every prior entry. Never restore this 88-entry snapshot over a newer ledger or
treat the remaining allowance as a separate allocation on each device.

## Recreate local prerequisites

Use Java 21, the Maven wrapper, Node dependencies (`npm ci`), Playwright Chromium
(`npx playwright install chromium`), Python, `pymysql[rsa]` and the requirements
in `tests/hand-interaction/requirements.txt`. Supply your own ignored local
configuration; keys, passwords and database contents are not in this handoff.

- Explicitly point the acceptance runner at a **loopback-only MySQL** admin
  properties file. The old file was
  `target/hand-interaction-mysql/application-test.properties`, port 65440; neither
  that path nor port is required on the new device. The runner creates/removes
  only its disposable schema/account. Do not use the normal application datasource,
  which may point to a remote database.
- Paid configuration uses `OPENAI_API_KEY` before ignored `openai.key` properties.
  Set it privately on the new device; do not print or commit it. The old session
  used an inherited project key. Set `OPENAI_MODEL=gpt-6.2-sol` for the current
  priced plan; the runner sets Live/model-route/low-effort caps itself.
- `target/hand-interaction-audio` is also local. On Windows,
  `python -B tests/gptlive/prepare_hand_audio.py` uses local SAPI/en-US speech and
  `pywin32`, with **zero provider synthesis calls**. The checked-in
  `stimulus-manifest.json` records the old voice/text/hash evidence, not audio.
  The generator currently requires Windows. On another OS, transfer the approved
  local PCM/manifest privately from the old machine or implement and verify an
  equivalent local generator before paid tests; do not silently use paid TTS.
- `review_hand_audio.py` uses local faster-whisper/base.en. Its optional
  `--download-model` downloads public weights, not OpenAI transcription. Install
  dependencies locally when needed; no model cache is carried in Git.

The raw recordings, screenshots, full provider/debug logs and temporary database
remain under ignored `target/` on the old machine. The curated JSON supports
diagnosis on the new device, but cannot recreate the original audio or replace a
fresh complete acceptance run. The final sweep suffixes and reviewed image/audio
names are retained for retrieving original evidence if needed. Old ad-hoc
`target/hi12_*.py` helpers are not required; use the committed runner and review tool.

## Commands for resuming verification

Choose the smallest set relevant to the next change. Commands below are from the
repository root; use `./mvnw` on Unix or `.\mvnw.cmd` on Windows.

```text
python -B -m unittest discover -s tests/gptlive -p "test_*.py"
node --test tests/gptlive/test_hand_audio_input.mjs tests/gptlive/test_hand_scenarios.mjs
./mvnw -q "-Dtest=Coordination*UnitTest,DirectCommand*UnitTest,LiveContext*UnitTest,LiveDelegationUnitTest,LiveTranscript*UnitTest,AgentCapabilityLiveContextUnitTest,TaskResponseUnitTest" test
python -B tests/gptlive/run_acceptance.py --hand-interaction contracts
```

The last local DB/browser selection (replace the properties path with your own):

```text
python -B tests/gptlive/run_acceptance.py --database-properties target/hand-interaction-mysql/application-test.properties --java-tests CoordinationAgentIntegrationTest,DirectCommandIntegrationTest,LiveQueryBudgetIntegrationTest,LiveTranscriptIngressIntegrationTest --browser --browser-mode enabled --browser-grep "strict hand silence|native Coordination audio precedes blocked backend|native Coordination final contribution" --live-only
```

An explicitly selected paid journey, after ledger/audio/config preflight and an
offline repair, uses `--hand-interaction L-06 --paid-core hand-live` with the same
explicit database-properties argument. Do not mix it with offline Java/browser
options. L-09 requires a separate `--hand-interaction L-09 --hand-comparison
--paid-core hand-live` run first on the new device; its baseline pointer/recording
is local. That pair alone would consume both remaining reservation slots.

After each paid run, retain the ledger and all failures, run local audio review,
inspect full captions, terms, rendered output and receipts, and write a separate
semantic verdict. Report remaining uncertainty and keep HI-12 open until all
required outcomes pass. Commit/push authorized progress to `main`; no deployment.
