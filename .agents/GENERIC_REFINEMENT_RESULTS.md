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
