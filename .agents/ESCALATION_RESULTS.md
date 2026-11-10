# Coordination escalation and classroom reliability evidence

## ES-06 — Faithful social conditions and reviewed alternatives (2026-11-10)

Coordination interpretation now receives the actual first-arrival, crowd-threshold
and grouping-change semantics. Recognised additional-person promises cannot be
saved or accepted as zero-to-present arrival; ambiguous immediate-start requests
cannot silently execute a substituted count condition. Instead, a specific visible
count alternative is proposed for review, without pretending to identify a person
or establish physical entry. Complete unsupported legacy drafts receive accurate
clarification guidance in help, controls and acceptance wording; repeating sensor
demonstrations is not presented as a fix for invalid terms. Partial proposals may
still ask clarification questions. No detector, raw event schema, saved agreement
or shared Generic/Social Space semantics were changed; no new relative/compound
condition engine was needed.

Verification:

- 64 Java cases passed across CoordinationSocialContractUnitTest (10),
  SocialSituationChangeDetectorUnitTest (8), CoordinationAcceptanceUnitTest (6),
  CoordinationRevisionUnitTest (11), CoordinationAgentUnitTest (16) and
  CoordinationSupportUnitTest (13). Cases include zero-to-one, one-to-two,
  two-to-three, zero-to-crowd precedence, departure, unchanged scene and
  grouping-only changes, plus atomic rejection and one bounded count response.
- `python target/run_coordination_mysql.py CoordinationAgentIntegrationTest,CoordinationDeliveryIntegrationTest`:
  final 19 cases passed, including persisted reviewed count terms, zero actions
  on arming, no action for unrelated/nonmatching evidence and one action after a
  matching fresh count. Initial smoke correctly stayed in Execution because the
  cue was before the existing arming cooldown. The fixture now waits for the
  recorded bounded cue window rather than bypassing it. Both attempts and cleanup
  are retained; final artifacts `target/gptlive-acceptance-ab45977de4/`, prior
  failed attempt `target/gptlive-acceptance-fd9e4a8669/`.
- One Playwright count-condition/sensed-count/status case passed; the updated
  singular-limit screenshot was inspected. `git diff --check` passed.
- Language guards cover recognised forms, not every possible paraphrase; real
  Live explanation/compilation quality remains ES-08. No provider or production
  writes, schema migration or graph rewrite.

## ES-05 — Supported task forms and visible limits (2026-11-10)

Runtime negotiation guidance describes the existing output-only sensor and
dialogue contracts. Deterministic guards reject recognised sensor promises to
invite/evaluate answers or start a riddle game, and a question-plus-feedback
dialogue without two contributions. Exact literal output and a one-contribution
feedback-only activity remain supported. Unsupported sensor STEP attempts now
explain the task-form mismatch and offer explicit alternatives without changing
terms or budget. This is a bounded language guard, not semantic proof for every
possible phrasing or a new game engine. New complete proposals disclose the
finite limit and early Stop option.

Dialogue guidance withholds the riddle solution, counts question/feedback
separately, avoids starting unfundable rounds and uses recent history to avoid
repetition. Cue and Live guidance prohibit output-only answer-loop promises.
Generated content quality still needs ES-08's actual provider checks. No mixed
cue-to-dialogue protocol was introduced; the roadmap's supported-alternative
default suffices. Saved agreements are not rewritten; unsupported saved drafts
must be explicitly replaced rather than silently converted. No schema migration.

Verification:

- 71 Java cases passed across CoordinationTaskContractUnitTest (4),
  CoordinationAgentUnitTest (16), CoordinationRevisionUnitTest (11),
  CoordinationVoiceUnitTest (9), CoordinationSupportUnitTest (13),
  CoordinationDeliveryUnitTest (12) and CoordinationAcceptanceUnitTest (6).
  A final 20-case contract/agent run also passed after narrowing the two-contribution
  requirement to question-plus-feedback rather than feedback-only activities.
- Initial regression caught a Live instruction-budget overflow; compacted the
  voice guidance and retained the existing 16,000-byte cap. It also found an older
  delivery test using an epoch-dated invitation; updated that synthetic invitation
  to be recent under ES-03's new expiry contract. Expired-invitation rejection
  remains covered separately; production expiry was not weakened.
- `python target/run_coordination_mysql.py CoordinationAgentIntegrationTest,CoordinationDeliveryIntegrationTest`:
  18 cases passed. A scripted riddle round persists question, answer, feedback and
  LIMIT_REACHED as distinct facts, and an explicit sensor-to-dialogue replacement
  reloads without executing before acceptance. Generated question quality is not
  inferred from scripted output. Owned cleanup confirmed; artifacts:
  `target/gptlive-acceptance-0a882d246e/` and `target/es05-db.log`.
- One Playwright case verified visible limits, separate question/feedback and
  Reflection state; its proposal screenshot was visually inspected. Whitespace
  checks passed. No provider calls or production writes.

## ES-04 — Grounded questions and safe optional-expression fallback (2026-11-10)

Recognisable direct hand/face questions now read supplied source-timed observations
without inference, in all nine phases. Missing/stale/future/invalid evidence does
not become a current estimate; face absence and poor confidence remain uncertain.
Replies explicitly distinguish estimates from direct camera access or feelings.
Other phrasing uses the existing interpreter. Narrow ordinary capability questions
cannot grant an inferred task operation. Optional invalid expression styling may
be discarded for ordinary/support and START replies, without relaxing speech,
hand motion, agreement or control validation.

Live pending questions preserve task identity/deadlines and expose bounded
request-time perception facts as historical evidence, not proof of current input
or task completion. This adds no inference, polling, schema or saved-graph rewrite.
The original classroom failure's exact model result remains unknown; these are
independently verified failure boundaries, not a claim of reproducing its cause.

Verification:

- 53 focused Java cases passed across CoordinationPerceptionUnitTest (14),
  CoordinationAcceptanceUnitTest (6), CoordinationSupportUnitTest (13),
  CoordinationVoiceUnitTest (9) and CoordinationRevisionUnitTest (11). Coverage
  includes nine phases, source ages, invalid styling versus invalid motion,
  dialogue contribution preservation and pending Live identity. An initial local
  edit introduced a UTF-8 BOM into a new Java test; removed it before verification.
- `python target/run_coordination_mysql.py CoordinationAgentIntegrationTest,CoordinationDeliveryIntegrationTest`:
  17 cases passed, including real persisted fresh scissors recognition without
  inference and provider-failure recovery preserving the saved proposal after
  reload. Owned schema/account/server cleanup confirmed. Artifacts:
  `target/gptlive-acceptance-dc97ffe0a4/` and `target/es04-db.log`.
- One Playwright sensed-input/reply/unchanged-status case passed; its screenshot
  was inspected. No client rendering changed. `git diff --check` passed.
- No production writes or provider calls; actual Live wording remains ES-08.

## ES-03 — Natural acceptance and truthful preparation (2026-11-10)

Unchanged ON_CUE starts accept explicit English/German forms and the measured
minor phrase omission without model inference. Contextual short assent includes
Ready/Bereit but requires a completed invitation less than two minutes old;
matching native capture remains required in Live. Stale/missing invitation asks
for explicit acceptance instead. Negated, future, quoted and question-form START
updates are rejected even when the scripted interpreter proposes START. Repeated
starts preserve run and budget. Dialogue starts retain the existing inference
for the actual contribution, with runtime guidance against redundant confirmation;
provider behavioural acceptance remains ES-08.

Verification:

- 37 Java cases passed across CoordinationAcceptanceUnitTest,
  CoordinationSupportUnitTest, CoordinationVoiceUnitTest and
  CoordinationLifecycleUnitTest. Includes nine explicit-start phrasings and unsafe
  inferred START variants, missing/expired invitations and repeated starts.
  The initial contextual-assent unit fixture had no persisted event timestamp;
  corrected its synthetic completed invitation timestamp, not production logic.
- `python target/run_coordination_mysql.py CoordinationAgentIntegrationTest`:
  ten cases passed, including persisted preparation followed by Bereit after
  reload with zero inference calls, zero actions and retained accepted evidence.
  Owned schema/account/server cleanup confirmed. Artifacts:
  `target/gptlive-acceptance-5661b79ce6/` and `target/es03-db.log`.
- Existing desktop/narrow preparation/acceptance recovery browser cases passed
  again (two cases); no client rendering changed in this milestone.
- `git diff --check` passed. No provider or production writes; no schema or saved
  graph migration. Configurator guidance is appended at runtime for saved actions.

ES-02's final context-budget adjustment also passed an additional 22 focused
support/voice cases before its commit.

## ES-02 — Shared state-aware support (2026-11-10)

All nine phases handle direct help/confusion requests through the shared action;
other phrasings can use SUPPORT in the existing interpretation request, including
saved configurators via runtime guidance. The reply is derived from current state,
remaining preparation and applicable rejection facts, not generated claims of
state changes. Help consumes no contribution and changes no task authority.
Explicit existing pause/resume/stop controls remain available.

Live support preserves pending response/occasion IDs and capture/playback deadlines.
During a pending contribution it adds bounded quiet explanation facts without a
new speaking permission; once no contribution is pending, it uses an ordinary
non-task user-response occasion. It is not an acoustic gate or a claim that the
pending response has finished. Reset, next user turn and new Live conversation
fence old help. Control-only background keeps explanation context bounded without
replacing the current task contribution's purpose. No new inference/polling or
schema/saved-graph migration is introduced.

Verification:

- Final focused Java run: 51 cases passed across CoordinationSupportUnitTest,
  CoordinationRecoveryUnitTest, CoordinationAcceptanceUnitTest,
  CoordinationDeliveryUnitTest, CoordinationVoiceUnitTest and
  CoordinationLifecycleUnitTest. Includes nine-phase authority preservation,
  partial preparation, pending Live identity/deadline preservation, expired help,
  session replacement and error fallback. One initial test incorrectly inspected
  the consumed reply-storage slot; corrected to inspect the actual response event.
- `python target/run_coordination_mysql.py CoordinationAgentIntegrationTest,CoordinationDeliveryIntegrationTest`:
  15 cases passed; help survives in history across reload, preserves terms/budget,
  and existing pause/resume and delivery paths pass. Owned schema/account/server
  cleanup confirmed; artifacts `target/gptlive-acceptance-29696e4bf9/`.
- `python tests/playwright/run_static.py tests/playwright/valerian-lifecycle.spec.mjs --grep 'Coordination recovery guidance'`:
  two cases passed, desktop/light and narrow/dark; wrapping and disconnect clearing
  checked. Both screenshots visually inspected. Initial screenshots exposed an
  open test-fixture Diagnostics drawer; the fixture now dismisses it before capture.
- Real OpenAI Live behavioural acceptance remains ES-08; synthetic browser/media
  and unit checks do not establish physical audibility or natural-language quality.

No production writes or provider calls. No additional task engine or state graph.

## ES-01 — Bounded recovery evidence and synthetic cases (2026-11-10)

Implementation on main adds one content-free `coordination.lastFailure` value to
ordinary Storage. It records safe rejection category/operation, source position,
time, phase, run/revision and action count, explicitly marking no committed task
mutation. Provider text, exception details and user wording are excluded. The
record is applicable for 30 minutes only while task identity/progress still match;
successful control updates clear it and reset removes it. Unknown/malformed legacy
records cannot be interpreted as current failure. A failed diagnostic write does
not replace the original recovery reply or claim successful persistence.

Six synthetic classroom-derived cases cover rejected activation after preparation,
perception questions, unsupported riddle-answer promises, extra-person semantics,
unaccepted cues and bounded replacement. They contain no student transcripts or
production IDs. The existing isolated acceptance runner validates the manifest
before database access and copies it into each run's artifacts. Baseline cases are
explicitly not claimed as behavioural acceptance; later milestones implement them.

Verification:

- `mvnw.cmd -q -Dtest=CoordinationRecoveryUnitTest,CoordinationAcceptanceUnitTest,CoordinationLifecycleUnitTest,CoordinationRevisionUnitTest test`: 28 cases passed, including the final rerun after adding action-count scoping.
- `python tests/gptlive/test_acceptance_runner.py`: eight cases passed.
- `python target/run_coordination_mysql.py CoordinationAgentIntegrationTest`:
  eight integration cases passed against the owned loopback MySQL and a fresh
  restricted schema/account. Rejection facts survived reload with unchanged
  proposal, then cleared on accepted progress. Cleanup confirmed. Artifacts:
  ignored `target/gptlive-acceptance-4da9f295d2/` and `target/es01-db.log`.
- `git diff --check`: passed. The default DB invocation was safely rejected for
  lacking a local administration URL; it made no database changes. A first Maven
  invocation was rejected by PowerShell's comma parsing and was rerun with its
  test selector quoted.

No provider call or
production write is part of ES-01. Existing saved graphs use the updated shared
action implementation; no fresh graph or schema change is required for diagnostics.
Conversational escalation remains ES-02 work.
