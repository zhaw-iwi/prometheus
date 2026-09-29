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
