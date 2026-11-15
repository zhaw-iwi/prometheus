# Bounded provider matrix: OS-11

Declared before the first OS-11 trial, 15 November 2026, base `330d1e8f`.
Three synthetic participants, one per scenario; English text, local disposable
MySQL, production study APIs/recorder, controlled simulated observations.
Current configured model observed in OS-09: `gpt-5.6-luna`, effort `none`.
The new trial metadata must confirm actual route/model settings, not assume them.

Each row runs once initially. Keep every failure; retry only after identifying a
cause, documenting the correction and running affected offline checks. No repeated
sampling until a favorable answer appears. Each run caps provider calls at 20,
tokens at 80,000 (checked before each request), completion at 2,048 tokens,
request timeout at 30 seconds and script duration at seven minutes. The token cap
can be exceeded by the final in-flight call; report actual usage. No Live/audio.
The initial three-run matrix therefore has at most 60 provider calls. Retries
are separately declared finite runs, not part of the initial sample.

| Script | Missing requirement and clarification | Preserved/changed agreement | Probe and ending |
| --- | --- | --- | --- |
| `guessing_journey.mjs` | Secret owner stated; ask about missing game rules, then specify five questions, yes/no/unknown and guess counting | Paraphrased initial acceptance; revise only total limit to three and derived dialogue bound; roles, success, answer rules preserved | Pause/resume first question; retain it through revision; three No answers exhaust the budget without success |
| `context_journey.mjs` | Fictional check-in requested; clarify missing scene/response conditions, then specify them | Paraphrased initial acceptance; shorten only no-answer wording; other exact terms unchanged | Versioned one–two–one scene probe; private question withheld while postponed; revised acceptance still paused; fresh one-person scene and explicit Resume; No delivers exact response; departure/return never restart |
| `signs_journey.mjs` | Sign check-in requested with questions/mappings still missing; clarify then specify three questions and mappings | Paraphrased initial acceptance; swap only yes/no sign mappings, preserving questions and all other fields | Demonstrate before each acceptance; first answer, open-palm wait, Resume, revision retaining second question; fresh signs yield YES/YES/NO and finish |

All rows include immutable private predictions, durable same-ID replay, private
understanding/post questionnaires, debrief and COMPLETE receipt. API scripts submit
synthetic display reports; actual rendering is covered separately by Playwright.

```powershell
python tests/gptlive/run_acceptance.py --study --java-tests none --paid-core coordination-text --study-scenario guess
python tests/gptlive/run_acceptance.py --study --java-tests none --paid-core coordination-text --study-scenario context
python tests/gptlive/run_acceptance.py --study --java-tests none --paid-core coordination-text --study-scenario signs
```

Review the clarification for a meaningful missing question; initial/revised
snapshots against the script's stated intentions; actual responses against roles,
conditions, limits and pending question; and absence of private-form markers from
provider requests. Premature action, unrelated clarification, changed protected
terms, unsupported promises or false success fail semantic acceptance even if
HTTP/schema assertions pass. Record that review separately in RESULTS.md; raw
journey reports retain `semanticReview:PENDING`. Latencies/tokens are descriptive
observations, not measured human-session cost or proof of deployment capacity.

The first three runs completed technically, but each clarification carried
unrendered nonverbal metadata. The UI did not execute it. Before qualification,
the study interpretation boundary now projects valid replies to speech only and
rejects missing text. One bounded rerun per scenario is declared after the focused
study/shared regression; the clarification script also asserts speech-only output.

The second context trial offered a different postponement threshold as a possible
choice. Its accepted/executed contract remained correct, but that clarification
misrepresented a fixed capability. Guidance now distinguishes example wording
from fixed scene/departure/resumption rules. One final bounded matrix rerun is
declared after context/shared offline checks, because this guidance is assembled
into the shared study interpreter prompt. Earlier trials remain in RESULTS.md.
