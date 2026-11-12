# ES-08 speech delivery decision (2026-11-12)

Status: owner approved existing Speech for committed replies on 2026-11-12.
Implementation and the agreed ES-08 behavioural acceptance are complete. Three
real-provider journeys have separate semantic PASS reviews on the same source
configuration; final Java/Node/browser/preflight/package checks pass. Exact evidence,
audio-review limitations and retained failures are at the top of
`ESCALATION_RESULTS.md`. Physical trials and capacity remain separate.

The earlier native-output decision was prompted by the following evidence.
Run `307eb96b2c` exercised 772 timed reflected-output chunks with complete capture,
then withheld two ready reviews that omitted the human judgement role. Worse, its
initial preparation reply added an unsupported recognition claim that the semantic
verifier approved. Deterministic support now requires its committed words through
a local literal check; focused tests passed. This closes that false-approval path,
but does not establish native proposal-review fidelity. Exact evidence and the
latest integrated regression are in `ESCALATION_RESULTS.md`.

The owner selected “Use existing Speech for committed replies”. This authorizes
the alternative below while preserving Live input, task authority and deadlines.
The new output route uses the existing configured Speech model and Alloy voice;
the synthetic visitor remains Onyx. Paid journeys retain the prior input/model/
session caps and separately cap committed-output synthesis at 12 calls and 6,000
characters per journey. No deployment or commit is authorized.

## Retained native-output evidence and boundary

The earlier implementation kept native GPT-Live speech, buffered its original
audio, checks it against backend meaning, and permits one unheard correction
within the original 45-second verification / 60-second playback limits. It must
not weaken meaning checks, replay a released response, grant another contribution
or silently extend those limits to make acceptance pass.

The retained runs include complete proposal playback and task-question playback,
but no complete three-scenario acceptance. Examples on the latest implementation:

- `deaaab2cc2`: the full proposal played. The committed question was "What has many
  keys but cannot open a door?"; native speech omitted "many". The verifier rejected
  it; the reserved correction produced no usable replacement before expiry.
  The task became PAUSED/UNCERTAIN with one spent contribution. This pair also
  shows that semantic-verifier strictness remains a concern, not just generation.
- `3649b59a63`: the first question played, but help omitted the active-state answer
  in both drafts. Help rendering was subsequently simplified and made task-aware.
- `35b5b3a3fc`: preparation request/help played, but the complete acceptance review
  exceeded the native capture-duration bound. The task remained inactive with no
  contribution. Complete review content also needs a practical spoken length;
  changing output source alone does not remove this requirement.
- `72841d5726`: a complete review was captured, but an early acknowledgement
  consumed correction while the review was in flight; complete audio matching and
  the original window then failed. Proposal reviews now require post-commit speech.
- `e6379034d8`: the backend committed "A brief welcome to the visible group" and
  native speech reproduced it. Both drafts were rejected as an actual welcome.
  Cue guidance already asks for actual words rather than directions. This is a
  backend-content issue that changing synthesis alone cannot fix.
  Further request inspection found the cause: inherited Generic Live guidance
  simultaneously requested a description, "not a finished utterance". Coordination
  now overrides that guidance through the existing action specialization; Generic
  retains its original intent behavior. Verify this causal repair before choosing
  a broader output-path change.

See `ESCALATION_RESULTS.md` for all attempts, usage, actual checks and the final
scenario batch. Individual successes do not establish integrated acceptance.

## Approved delivery design

Retain Live input, the explicit Coordination state machine, saved agreements,
finite budgets, owner/epoch/revision fences and observed-playback completion.
Render **committed Coordination replies** through the existing
`SpeechSynthesisGateway` instead of asking native Live to reconstruct them.
Keep other agents on their current voice paths.

Implementation seams and required work:

1. Preserve the authorized spoken content alongside the existing response identity.
   Proposal/support/control wording and task contributions remain backend-owned;
   synthesis is not a new action or agreement interpreter.
2. Add a scoped synthesis/release path using `ExternalSpeechOwnership` and the
   existing speech gateway. Recheck request identity, expiry, interruption and
   release before and after provider work. Existing
   `ScopedBehaviourSpeechService.canonicalSpeech` deliberately rejects unheard
   native speech/intents: do not bypass that guard or relabel native receipts.
3. Adapt the production player to consume the authorized synthesized clip, with
   one persisted release and observed completion. The original Live audio remains
   inaudible for these replies. No fallback may play a rejected native draft.
4. Represent synthesized-output provenance explicitly. Only observed completion
   enters delivered conversation and proposal-consent evidence. Do not pretend
   synthesized output was captured native speech or remove delivery confirmation.
5. Test one normal response, supersession/interruption during synthesis, duplicate
   release, lost completion acknowledgement, deadline expiry and final Reflection.
   Include real MySQL reload and the production browser, then all three paid
   journeys with semantic review on the final configuration.

This changes the voice/latency/cost path and uses Alloy for committed replies.
It does not prove backend content correct: answer quality and semantic
interpretation still need their own checks. Before considering this alternative
accepted, evaluate the retained ambiguous riddle pair and description-versus-
greeting cases against the configured backend/verifier; establish concise complete
review wording that fits the delivery bounds. Physical microphone/camera/speaker
acceptance and deployment capacity remain separate.

## Rejected alternative: retaining native-only output

Retain the verified repairs and keep ES-08 unaccepted. A further native-only
investigation should first isolate generation versus verifier errors with a small,
labelled model/configuration comparison and explicit trial caps. Do not run the
same failed journeys repeatedly without a concrete causal change, or call one
successful turn evidence of completion.
