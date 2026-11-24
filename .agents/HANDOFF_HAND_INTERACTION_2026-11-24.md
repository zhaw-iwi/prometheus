# Hand interaction continuation — 2026-11-24

HI-01 through HI-11 are complete. HI-12 remains open. The historical implementation
checkpoint was `82d12bc7`. The latest findings and reservations are in
[`live-continuation.json`](checkpoints/hand-interaction-2026-11-24/live-continuation.json)
and the final section of [the results](HAND_INTERACTION_RESULTS.md).

## Start here

Read `CODEX.md`, `CONTEXT.MD`, and `../PROJECT.md` through Current milestone state,
stopping before Historical milestones checklist. Check the branch/worktree and
pull `main` without discarding local work. Then read:

- `PLAN_HAND_INTERACTION.md`, especially HI-12 acceptance revision 2.
- `HAND_INTERACTION_RESULTS.md`, the **Third-extension final sweep and checkpoint**
  section and the subsequent **Current-turn Live investigation**.
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

## Current result and next diagnostic

Attempt 89 established that Live had begun the incorrect post-task reply before
the exact-turn result was sent. With phase-specific guidance, 92 answered the
same imperative correctly while the backend was still working. Attempt 91
clarified the ambiguous sign and issued one right-hand ring on the next turn.
Attempt 90 exercised genuine incomplete consent, one inactive-proposal repair,
and fresh explicit acceptance. The original 92 traces place “started” before
the backend created the arming occasion and the second acknowledgement after
the commentary send. In 93, preparation was reviewed in the past tense and
arming was confirmed once. An unsupported motion claim during consent repair
failed the full journey. Attempt 94 passed L-03, including a deliberate repair,
fresh explicit consent, a rejected near miss and a single digital ring.
Attempt 95 left the tense of one preparation duty unclear. Attempt 96 separated
it from the remaining duties and confirmed arming once, but introduced an
unrequested task in the opening reply. Live also claimed digital display
between receipt of the rendering observation by the application and delivery
of that fact to Live. Inspect those two boundaries offline before a new paid
selection. Full L-02 remains open. Historical failures retain their verdicts.
The [results](HAND_INTERACTION_RESULTS.md) and portable
[run evidence](checkpoints/hand-interaction-2026-11-24/live-continuation.json)
preserve the individual verdicts and the review, repair and arming timelines.
Allocation 10 has used eight $5 reservations, reaching 96 attempts/$226
cumulatively. Two more ($10) fit the owner's cap.

## Findings carried into this continuation

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

Historical verification: 363 Java unit tests (28 suites), 48 local-MySQL tests,
six Playwright cases, 45 Python tests, 16 Node harness tests, nine schema examples
and packaging. Final paired L-09 native onset comparison passes. Images and full
received-audio ASR were reviewed; physical microphone/speaker/robot trials were
not run. Only docs and the reservation-ledger minimum changed after the paid
sweep; the latter was reproduced failing, repaired and covered by all 45 Python
tests. These results are historical evidence, not tests run on the new device.

## Carry the spending history across devices

Allocation 10 permits up to $50 of new reservations from attempt 89, including the
backend and GPT-Live. Each selection reserves $5. The enforced maximum is 98 entries
and $236 cumulative. The former allocation's unused balance is included in this
envelope. Obtain fresh owner authorization before increasing it. Invoices are unavailable.

Restore historical reservations before any paid selection. The helper verifies the
original canonical checksum and the complete tracked continuation prefix. It only
creates a missing ledger. A shorter or conflicting local ledger fails closed.
Historical artifact paths identify the originating run and are retained verbatim.

```powershell
python -B -c "import sys; sys.path.insert(0,'tests/gptlive'); from hand_paid import restore_checkpoint; d=restore_checkpoint(); print(len(d['attempts']), d['reservedUsd'])"
```

Use one device at a time and commit new reservations and findings before changing
devices. Both the original 88-entry ledger and the subsequent reservations are
needed to restore a missing local ledger.

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
is local. That pair reserves $10. Check the current ledger first.

After each paid run, retain the ledger and all failures, run local audio review,
inspect full captions, terms, rendered output and receipts, and write a separate
semantic verdict. Report remaining uncertainty and keep HI-12 open until all
required outcomes pass. Commit/push authorized progress to `main`; no deployment.
