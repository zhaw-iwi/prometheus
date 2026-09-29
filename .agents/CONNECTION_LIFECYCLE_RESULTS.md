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
