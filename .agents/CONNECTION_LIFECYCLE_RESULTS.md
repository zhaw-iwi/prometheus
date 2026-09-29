# Classroom connection lifecycle

Authorized sequence: CL-01 release connections during SSE, CL-02 bound scoped
creation transactions, CL-03 classroom/browser acceptance and branch integration.
Implement on main; commit/push each completed milestone; merge main into
feature/gptlive and agents without rebasing or copying deployment definitions.

## CL-01 — HTTP streams release persistence resources (2026-09-29)

The shared application.yaml disables Open EntityManager in View even when an
existing ignored application.properties is present. Existing eager agent graph
loading provides stream snapshots and persisted event IDs outside request scope.
No stream paths, payloads, replay rules, or browser lifecycle were changed.

A real random-port HTTP/MySQL regression opens two streams for each of eight
agents with a ten-connection pool. It exercises a ninth creation/login, ordinary
acknowledge/generate, speech by persisted ID, both conversation/raw projections,
replay after disconnected generation, two connection cycles, and scope rejection.
It asserts zero borrowed connections and zero waiters while streams are idle.
It deliberately has no enclosing test transaction and does not mock SSE.

Before the fix, the regression failed opening another stream with HTTP 500 and
Hikari total=10/active=10/idle=0 after a shortened 1500 ms timeout. After the fix,
all 20 focused cases passed (SSE, scoped controllers, Live cockpit, native speech
persistence and transcript ingress). Artifacts: target/gptlive-acceptance-06947c5fa1
(red) and target/gptlive-acceptance-a7e8f1c3e8 (focused green).

Environment: disposable local MySQL 8.4.11 on loopback with generated restricted
accounts, Java 21, synthetic providers. Production configuration and data were
not used as a test target. The runner invokes the Unix Maven wrapper via bash,
so a checkout without its executable bit can still run acceptance.

Full Java regression: 439 cases passed, no failures/errors/skips.
Artifacts: target/gptlive-acceptance-e49a4f9fd3. Browser and deployment
acceptance belong to CL-03.

## CL-02 — Scoped creation commits after provider work (2026-09-29)

ScopedDemoService now validates access, generates the initial response without
opening a creation transaction, then revalidates code identity/enabled status and
type permission inside a short TransactionTemplate. Saving the agent and its
access association is atomic. Initial monitor publication now follows commit,
matching existing behaviour publication. Failed commits publish neither channel.

ScopedCreationConcurrencyIntegrationTest uses actual HTTP/MySQL with four pooled
connections. Eight blocked model calls all reach the provider with no active
transaction and no borrowed database connection; another login succeeds. Released
calls create eight visible agents and publish each initial update once. Separate
cases verify disabling a code, removing its type, replacing the code identity,
provider failure and association failure; rejection/rollback leaves no orphan or
premature publication.

Verification: 25 distinct focused cases passed across scoped creation, SSE,
scoped controllers, Live cockpit/persistence and Talk to Me. Artifacts:
target/gptlive-acceptance-61c47206cd and target/gptlive-acceptance-81d1758ef6.
No production operations were performed. This milestone changes scoped creation;
Live transcript ingress retains its existing atomic receipt/task transaction.
Provider cost already incurred before a revocation cannot be recovered.

## CL-03 — Classroom/browser acceptance and branch integration (2026-09-29)

The new valerian-classroom-smoke.spec.mjs exercises eight isolated browser
contexts joining in sequence while earlier groups remain connected. It uses real
HTTP, MySQL and native EventSource transports, with synthetic external providers.
All sixteen streams remain open during eight simultaneous turns. Checks cover
scoped persisted conversation, no HTTP 5xx, zero idle pool use/waiters with a pool
of ten, disconnect/reconnect, switching instances and reload. A metrics endpoint
exists only in the test fixture classpath. Desktop/mobile captures were inspected.

Fresh compilation of main passed all 445 Java cases without failures/errors/skips.
All 67 Live/transcription/speech Node cases passed. Java artifacts are in
target/gptlive-acceptance-bf986aaf11; generated classes from earlier branch work
were moved aside before this run. That first browser run passed 44 cases and
exposed an existing test race: the multilateral listener's optimistic Listening
label preceded creation of its fake data channel. The event-injection helper now
waits for an open channel. Test databases/accounts are local and disposable.

Final browser rerun: all 45 Live-enabled cases passed, plus the feature-disabled
real text/TTS smoke (one Live-only case is intentionally skipped when disabled).
Artifacts: target/gptlive-acceptance-220d24be3a. The classroom case passed in both
runs. The production Heroku setting SPRING_JPA_OPEN_IN_VIEW is absent, so it does
not override the new shared default. No production database was used for tests.

The agents merge b6180e0 preserves its embodiment-aware creation overload while
removing the old transaction annotation from that overload too. Fresh compilation
of that merged deployment tree passed all 579 Java cases, including the six
provider-wait/revocation/rollback cases through the actual deployment HTTP entry
point. All 67 Node and 46 browser cases passed there too, with the same single
intentional feature-disabled skip. The eight-context classroom flow and inspected
desktop/mobile captures passed with deployment personas/catalog intact. Artifacts:
target/gptlive-acceptance-4443351106.

Main commits f84327b, d43e8fe and a2c8fb6 contain CL-01, CL-02 and CL-03 respectively.
feature/gptlive fast-forwards from main; agents integrates through a normal merge.
This evidence-only follow-up goes through both branches as well. No rebase,
cherry-pick or reverse merge of deployment definitions is involved, preserving the
subsequent feature/gptlive to main to agents workflow. The final evidence merge
changes documentation only; the verified deployment implementation stays identical.

Remaining trial gates: paid-provider throughput/latency, physical audio and a
live classroom rehearsal. These checks establish connection lifecycle and client
contracts; they are not a production capacity benchmark. Existing Live ingress
receipt/task transactions remain outside this scoped-creation milestone.
