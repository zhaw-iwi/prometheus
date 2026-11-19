# Towards commanding: implementation and verification

Started: 2026-11-19. Plan: [PLAN_TOWARDSCOMMANDING.md](PLAN_TOWARDSCOMMANDING.md).
The owner authorizes sequential implementation with a commit and push after each
completed milestone. No new deployment is authorized. The owner explicitly
authorized an additional $40 across eight attempts, failures included, on
November 19. The completed GPT-Live repair ledger is unchanged.

| Milestone | Unit | Local DB | Browser functional | Visual review | Provider | Physical | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TWC-01 | PASS: 332 affected unit cases | PASS: 102-case Java acceptance run | PASS: seven cases, then two strengthened native-ingress reruns | PASS: both new screenshots inspected | NOT RUN | NOT RUN | Software complete |
| TWC-02 | PASS: 508 affected + 47 focused cases (overlap); 16 Python | PASS: four variants, reload/reset/silent rejection | PASS: two real-app Text journeys | PASS: rock/question screenshots inspected | NOT RUN | NOT RUN | Software complete |
| TWC-03 | PASS: 218 Java, final 40-case subset; 175 JavaScript | PASS: seven Java cases | PASS: two mode journeys + two independence cases | PASS: stable light/dark/narrow poses inspected | NOT RUN | NOT RUN | Software complete |
| TWC-04 | NOT RUN | NOT RUN | NOT RUN | NOT RUN | N/A: labels need no provider | N/A | Not started |
| TWC-05 | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | Not started |

## TWC-01 — Current task announcements

Implementation uses the existing persisted speaking-occasion identity, which is
derived from current task revision/state and scoped by the Live session epoch.
Coordination marks control/proposal/support narration as current-only; committed
contributions retain their delivery contract. The bridge scopes associated state
facts and speaking instructions together and rechecks before each send and after
each acknowledgement wait. Unrelated context revisions do not invalidate them.

Already-sent obsolete instructions receive a single quiet withdrawal, with an
instruction to correct only wording that conflicts with the latest state. The
adapter currently sends acknowledged context appends; this implementation does
not invent a response-specific provider cancellation operation or buffer native
audio. Provider compliance and physical speech remain separate acceptance items.
Content-free supersession traces retain old/new occasion identities for diagnosis.

The first focused test run exposed two regressions during implementation:

- Additional voice guidance exceeded the existing instruction budget. Replaced
  redundant wording with shorter withdrawal guidance; kept the budget unchanged.
- Making every cleared occasion urgent changed optional ambient refresh cadence.
  Restricted the urgent invalidation to current-only task narration; existing
  ambient scheduling retains its behavior.

The nine new deterministic bridge cases passed, including control changes during
an acknowledgement wait, already-sent narration, unrelated context updates and
a committed contribution entering FINISHING. All 332 affected unit cases passed
with no failures/errors/skips. The local-MySQL runner passed 102 selected Java
cases, including those shared unit anchors, then seven browser cases. Counts
overlap; they are not 434 distinct Java tests.
The persisted smoke passes creation/proposal/acceptance/reload, zero-action
activation, unchanged agreement, old-proposal suppression, stop and no reconnect
replay. New browser cases use actual MySQL/runtime/SSE and synthetic provider ACK
holds/audio to distinguish queued from in-flight instructions. The two cases
were subsequently strengthened to use native transcript ingress for the user
proposal and acceptance and to inspect the recorded conversation; both passed.
Their successful full-page screenshots were inspected. Native playback remained
unmuted/unpaused, there were no competing synthesis or fabricated playback-cue
requests, and zero actions were spent at acceptance. Provider wording/physical
audibility are not established by this synthetic transport.

A final compatibility check preserves older pending responses without a purpose
tag. The 32 targeted voice/narration unit cases passed after this refinement.
The final diagnostic refinement also records the superseding occasion ID; it
was included in the 332-case unit run. No schema/graph/history migration.

Commands and artifacts so far:

```powershell
.\mvnw.cmd -q '-Dtest=LiveContextDeliveryUnitTest,LiveTaskNarrationUnitTest,CoordinationVoiceUnitTest,AgentCapabilityLiveContextUnitTest' test
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests 'LiveTaskNarrationUnitTest,LiveContextDeliveryUnitTest,CoordinationVoiceUnitTest,CoordinationAgentIntegrationTest,CoordinationDeliveryIntegrationTest,AgentCapabilityLiveContextUnitTest,LiveSmokeConfigurationTest' --browser --browser-mode enabled --browser-grep 'native Coordination'
.\mvnw.cmd -q '-Dtest=Live*UnitTest,Coordination*UnitTest,CoreTaskVoiceUnitTest,Sensory*UnitTest,AgentCapability*UnitTest' test
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests none --browser --browser-mode enabled --browser-grep 'withdraws obsolete'
.\mvnw.cmd -q '-Dtest=CoordinationVoiceUnitTest,LiveTaskNarrationUnitTest' test
```

Ignored artifacts: `target/twc01-unit.log`, `target/twc01-regression.log`,
`target/twc01-acceptance.log`, `target/twc01-native-ingress.log`,
`target/twc01-final-unit.log`, `target/gptlive-acceptance-dd4e845363/` and
`target/gptlive-acceptance-f037455cb6/`. Both owned applications were stopped and
both disposable schemas/accounts removed. New screenshots are in the latter
run's `browser-true/` case directories.
No paid provider calls have been made for this roadmap.

## TWC-02 ? Shared direct requests

Existing task/embodiment inference now returns optional internal command metadata.
A shared validator gates the finite vocabulary on persisted profile modalities,
blocks overlapping task outputs, and emits ordinary canonical BehaviourPlans.
It never creates/starts/changes a task or spends its contribution budget. Silent
requests have no backend speech. A bounded handled-turn ledger uses the admitted
history position (the event UUID is assigned only when flushed), survives event
selection and database reload, and is cleared on reset. Distinct new turns can
repeat an action. Existing transport receipt deduplication remains authoritative
for native/Continuous retries; this is not a new general HTTP idempotency API.
Saved profile tags opt in without graph or schema migration. Owner loss after
inference prevents late plan publication. Silent decisions persist even when no
behaviour event is emitted; otherwise an unsupported request could be retried.

Validation: 508 affected unit cases passed; after the silent-persistence fix,
47 focused service/command cases passed (overlapping, not additive unique totals).
The Python harness passed 16 tests. A local-MySQL run passed ten Java cases
(four persisted agent variants plus six Coordination contract tests); the final
run repeated the four integration variants with silent rejection persistence.
Two final real-app browser journeys passed through Text, DB and SSE, checking
rock, question gesture, deliberate repeat, unsupported request, reload and no
animation replay. Successful rock/question WebGL screenshots were inspected.
No assistant output was injected into the UI; inference was synthetic. These
fixtures prove validation/routing/rendering, not model language understanding.
Native speech and paid model interpretation remain TWC-03/TWC-05 work.

Implementation failures retained: initial event UUID assumptions corrected to
history position; EventSelector wrapper and foreign-scope Optional contract fixed
in tests; Mockito restubbing fixed; final-current-turn prompt position restored.
The browser correctly exposed a lost silent-result persistence path, now fixed
and covered in the four-variant integration test. Its initial SSE readiness wait
was moved after the first output because an empty stream has no event to flush.

Artifacts (ignored): `target/twc02-regression-final.log`,
`target/twc02-service-regression.log`, `target/twc02-python.log`,
`target/gptlive-acceptance-89c4066634/` (Java pass, browser setup failure),
`target/gptlive-acceptance-1181077693/` (silent-result failure), and
`target/gptlive-acceptance-50512df0f0/` (final four Java/two browser pass).
Earlier failures remain in `target/twc02-*.log` and their runner directories.
Owned apps and disposable schemas/accounts were cleaned. No paid calls yet.
TWC-01 was committed/pushed as `e5a8b6e4`.

## TWC-03 ? Native voice and command alignment

Voice guidance now describes declared digital hand signs/gestures positively and
separates pending validation from execution. Scoped saved instances retain direct
command guidance even with general capability awareness disabled. Shared inference
still supplies backend command interpretation; no second ordinary inference or
Live speech synthesizer was introduced. Existing instruction limits are unchanged;
redundant guidance was shortened after budget tests caught overflow.

Command results are quiet deltas, bounded to 380 UTF-8 bytes before transport
framing. They name the persisted user event and are available only when that event
is the latest user turn, under 30 seconds old, and has a COMPLETE USER transcript
record in the current session/epoch. Stop/owner checks exclude old work. Startup
omits these facts; reset/new turns/expiry withdraw them. They never create a new
speaking occasion, request replay or claim client/physical execution. This keeps
ordinary native speech independent and avoids command follow-up announcements
that could outlive their request. Provider compliance remains a separate gate.

Verification so far: 218 affected Java unit cases passed; the final guidance
subset passed 40 cases. Seven local-MySQL Java cases passed (four command variants
and three transcript ingress tests), followed later by six result/fixture unit
anchors in the disposable runner. JavaScript passed 110 Live, 15 embodiment,
21 speech and 29 transcription cases (175 total). Both existing native
blocked-backend browser cases passed, including Stop fencing. The two new mode
journeys passed Continuous -> Live -> Continuous -> reconnect, with actual DB,
production ingress/SSE and WebGL pose assertions; the strengthened duplicate
packet replay and stable reduced-motion screenshot rerun is recorded below.

Failed attempts are retained. Initial prompt overflow was corrected without
raising limits. Existing mocked projection tests needed the unchanged overload
when no command facts exist. Repeated synthetic user captions exposed missing
PCM lead-in after idle gaps; the fixture now provides it before USER captions as
it already did for ASSISTANT captions. Runtime coverage-gap rejection is intact.
The duplicate-packet browser check was strengthened to decode fixture captions
and assert successful replay transport, rather than ignoring a 400 response.

Artifacts: `target/twc03-regression-final.log`, `target/twc03-final-guidance.log`,
`target/twc03-*-js.log`, `target/gptlive-acceptance-0302d54977/` (seven Java cases,
two independence cases and Live Multimodal journey pass; Coordination fixture
failure), `target/gptlive-acceptance-89ddcd5294/` (fixture-gap failures), and
`target/gptlive-acceptance-684d5004d6/` (six Java/two mode journeys pass before the
strengthened replay assertion). No paid calls. TWC-02 commit: `aac0cce7`.

Final strengthened rerun: both mode journeys passed in
`target/gptlive-acceptance-f758afc4a1/` / `target/twc03-modes-replay.log`.
The duplicate packet was actually accepted by the fixture transport and produced
no second action. Stable representative WebGL poses were captured with reduced
motion enabled; light scissors, dark question and narrow dark paper screenshots
were inspected alongside normalized render-state assertions. TWC-02 supplies
the rock screenshot. No layout overflow or browser errors. All owned applications
and disposable schemas/accounts were cleaned. Provider/physical acceptance pending.
