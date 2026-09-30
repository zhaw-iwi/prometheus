# Capability awareness evidence

See PLAN_CAPABILITY_AWARENESS.md for authorized scope, rollout and branch sequence.

## CA-01 / 197

Implemented explicit per-definition opt-in and additive persisted profile JSON;
all main core definitions opt in and non-core definitions default out. The bounded
description includes only declared channels, known meanings and operational limits.
Live compatibility stays independent. Delivery is added in CA-02 and CA-03.

Verification: `mvnw.cmd -q clean -Dtest=AgentCapabilityDescriptionUnitTest,AgentInteractionProfileUnitTest,AgentDefinitionRegistryUnitTest,TalkToMePolicyUnitTest test` passed 19 cases. No database/provider access.


## CA-02 / 198

Agent start/generate/acknowledge bind an immutable capability-aware assembler.
It delegates custom composition, preserves independent guard/speculation eligibility,
and supplies one reference message to behaviour composition, including empty or
selected histories. Condensed decision/action/summary contexts remain unchanged.
No initialization events or extra inference calls are introduced.

Passed 45 focused Java cases: AgentCapabilityAwarenessUnitTest,
PromptMessageAssemblerUnitTest, BehaviourPreviewUnitTest,
BehaviourSpeculationServiceUnitTest, TalkToMePolicyUnitTest, LiveMultimodalUnitTest,
CatalogInferenceCountUnitTest, GuardEvaluationUnitTest and
ParallelGuardEvaluationUnitTest. Initial tests exposed a guard batching regression
from the wrapper's class identity; explicit delegated eligibility fixed it and
catalog inference counts passed unchanged. A test fixture also needed its final
state attached before constructing the agent graph.

Local integration setup: installed missing PyMySQL and initialized a separate
loopback-only MySQL 8.0 server under target/capability-mysql on port 33316. This
avoids the deployment database configured in ignored local properties. The
existing acceptance runner still creates/removes a distinct schema and account
for each run. No developer or deployment schema is used for tests.
