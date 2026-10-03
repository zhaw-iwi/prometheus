# Generic multimodal refinement results

Plan: [Generic multimodal refinement](PLAN_GENERIC_REFINEMENT.md).

## Milestone 208

Completed. Production guidance uses neutral schema descriptions and no preset
demonstration task. Existing user agreements/history are unchanged; saved prompt
upgrades are deferred to the tested migration in milestone 213.

Passed 15 focused Java tests (14 task unit tests and the scoped MySQL/Live reload
integration test). Runner: `target/gptlive-acceptance-b5b4c553d9/java.log`, exit 0;
disposable schema/account removed. No live provider interpretation claim follows
from the mocked fixtures.

## Milestone 209

Completed ACT/WAIT/COMPLETE rules, temporary sensor waiting, explicit PAUSE/RESUME
and preserved action counts. Empty-scene rules cannot require positive average
person confidence. Conflicting identical conditions are rejected. Legacy complete
booleans remain readable with unchanged semantics. Questions no longer restart
the cooldown; generation failure pauses for explicit resumption.

Passed 18 unit and 2 MySQL integration cases. The first runs caught test-fixture
storage access and access-code length mistakes, both corrected. Unit evidence:
`target/gptlive-acceptance-1d0a55b682/java.log`; final two integration cases:
`target/gptlive-acceptance-8736f579fe/java.log`, exit 0, cleanup confirmed.

## Milestone 210

Completed advisory task readiness on the existing monitor, one fast qualifying
facial sample per response window, and acceptance/lifecycle states in the existing
footer. Unmatched/weak/stale evidence does not bypass cadence. Social emission
and backend cue authority remain unchanged; no database polling was added.

Passed 25 Java cases including query budgets and real HTTP/SSE, and 50 Node
performance/Live cases. Four Playwright light/dark desktop/mobile cases passed;
mobile acceptance screenshots were visually inspected without overflow. The
integrated Generic smoke initially retained the old Ready label after reset;
corrected to Ready to configure. The final enabled smoke passed; the disabled
run selected this same enabled-only test and skipped it, so it is not disabled-path evidence
in `target/gptlive-acceptance-391b43bc7a/`. Java and visual evidence:
`target/gptlive-acceptance-c326b495db/`. Both disposable runs cleaned up.
Physical expression effort and audibility remain unverified.

## Milestone 211

Live-owned interactive tasks pause durably on shutdown, retain their agreement
and budget, and require explicit resumption. In-flight activation/action results
are fenced using existing in-memory ownership; no authorization polling was added.
Explicit autonomous agreements can opt out. Old-session cleanup cannot pause a
resumed new session. Local stop is distinct from provider disconnect/failure.

The captured-response boundary now requires the response content across completed
native segments. Acknowledgments, unrelated speech and incomplete segments do not
arm cues. Guidance requests faithful wording; ASR errors/substantial paraphrases
may leave this conservative boundary unconfirmed. Neither captions nor append ACKs
prove audible playback completion, consistent with the
[OpenAI Live contract](https://developers.openai.com/api/docs/guides/live-conversations).

Verification: 30 focused unit tests passed in
`target/gptlive-acceptance-31e1f384a4/java.log`. The new MySQL lifecycle fixture
initially failed because its manually created execution epoch was not persisted;
fixed to match real session claiming. All three MySQL cases passed in
`target/gptlive-acceptance-a00011a486/java.log`; disposable schema/account removed.

## Milestone 212

Completed content-free task phase/revision and cue-source correlation in the
existing activity journal. Browser exports retain at most 256 task readiness
changes, with separate browser/server clocks and dropped counts. The footer
explains unconfirmed response capture. Caught provider, configuration-validation
and behaviour-validation failures have distinct fixed outcome codes. Expected
delivery cancellation during local close does not become a processing failure.

Urgent announcements can supersede unsent sensory work after the current complete
observation. Partial acknowledgments retain undelivered old evidence until an
actual replacement/removal. Narration identity, freshness checks and normal
five-second read coalescing remain intact. The controlled test sends the result
as the second append, discards the obsolete backlog, then sends fresh evidence;
this demonstrates ordering, not a measured production latency improvement.

Passed 42 distinct Java cases including three query-budget integrations:
`target/gptlive-acceptance-35b281d486/java.log` (41) and the expanded nine-case
task lifecycle suite in `target/gptlive-acceptance-f721f4b7ea/java.log`.
Passed 51 Node performance/Live cases. Both disposable database runs cleaned up.
Integrated visual/browser regression and live rollout remain milestone 213.

## Milestone 213

Implemented an authenticated per-instance preview/apply upgrade for exact known
built-in prompt hashes. Application uses the existing turn serialization and a
transaction, rejects changed previews/open Live sessions, and is idempotent.
Custom policy text, task/draft JSON, action counts and history remain intact.
Incompatible active rules are paused; incompatible saved drafts receive a focused
rule-correction response. Two MySQL migration tests passed in
`target/gptlive-acceptance-a1b05ee4ec/java.log` after correcting a fixture method name.

Integrated replay revealed that the milestone-211 full-word boundary would reject
valid captured actions when Live shortened trailing waiting instructions. It now
also accepts a complete substantive two-sentence opening, tolerates function-word
ASR variation, and still rejects acknowledgments, incomplete captures and a
question alone. The two task-start action captures in the supplied October-3
history now associate successfully. This remains conservative lexical association,
not semantic or acoustic completion proof; substantial paraphrases may remain
unconfirmed. The real browser path caught a null persistence-ID edge in selector
projections, which is fixed. Cue/source IDs remain optional when unavailable.

Verification before rollout:
- Full Java suite passed in `target/gptlive-acceptance-3bfa6ba0cd/java.log`.
  Affected task suites were rerun after the replay corrections (27 cases) in
  `target/gptlive-acceptance-818328d78b/java.log`.
- 101 Node tests passed. The broad browser run passed 46 cases; four updated
  light/dark desktop/mobile footer cases passed in
  `target/gptlive-acceptance-3a3a7d8984/` (mobile image visually inspected).
- Final focused browser run `target/gptlive-acceptance-9885ecb071/` passed three
  enabled cases and the actual disabled-backend smoke; the enabled-only Generic
  case correctly skips in disabled mode. The Generic fixture now supplies its
  actual spoken response rather than an unrelated caption. It verifies durable
  pause after Stop Live and a handled provider failure with failed telemetry.
- Four synthetic real-provider configuration trials using the configured
  gpt-5.6-luna/none route passed: neutral capability discussion, presence waiting,
  a hand-sign task and facial reflection feedback. All outputs also passed the
  production task/reply validators. Evidence: `target/generic213-provider-trial.json`.
  These are text interpretation checks, not a microphone/speaker acceptance trial.

Windows acceptance cleanup exposed a launcher-child JVM leak. The runner now
stops its owned process tree before dropping the disposable schema; stale JVMs
from this task were stopped and the final run leaves no owned fixture JVM.
The original production agreement/history fingerprint is recorded privately in
`target/generic213-rollout/before-deploy.json`. Branch integration, Heroku release
checks and the scoped saved-instance upgrade follow this implementation commit.
