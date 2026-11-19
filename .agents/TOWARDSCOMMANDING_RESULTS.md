# Towards commanding: implementation and verification

Started: 2026-11-19. Plan: [PLAN_TOWARDSCOMMANDING.md](PLAN_TOWARDSCOMMANDING.md).
The owner authorizes sequential implementation with a commit and push after each
completed milestone. No new deployment is authorized. The owner explicitly
authorized an additional $40 across eight attempts, failures included, on
November 19. The completed GPT-Live repair ledger is unchanged.

| Milestone | Unit | Local DB | Browser functional | Visual review | Provider | Physical | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TWC-01 | PASS: 332 affected unit cases | PASS: 102-case Java acceptance run | PASS: seven cases, then two strengthened native-ingress reruns | PASS: both new screenshots inspected | NOT RUN | NOT RUN | Software complete |
| TWC-02 | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | Not started |
| TWC-03 | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | Not started |
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
