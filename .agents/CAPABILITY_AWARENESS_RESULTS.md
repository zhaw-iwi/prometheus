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


## CA-03 / 199

The shared description is appended to LiveVoicePolicyAdapter guidance and counted
inside the existing 16000-byte instruction budget. It is independent of sensory
items, TTL and history eviction. Existing revisions/delivery propagate changes;
unchanged snapshots require no additional append. New sessions project the saved
profile again. Live eligibility remains a separate requirement.

Passed 38 focused Java cases: AgentCapabilityLiveContextUnitTest,
LiveContextProjectionUnitTest, LiveContextDeliveryUnitTest,
ScopedLiveSessionServiceUnitTest, LiveMultimodalUnitTest and
AgentDefinitionRegistryUnitTest. This includes all reachable core conversational
policies and the combined instruction budget. No provider or database access.


## CA-04 / 200

Added three real scoped-controller/MySQL smoke cases for creation and metadata,
reload, generation, reset, Live startup, cross-scope rejection, legacy saved JSON
and non-core opt-out. Existing real-app browser smoke now checks the actual
ordinary and Live provider context, including reconnection and Live-disabled TTS.

Verification on main:
- Focused `CapabilityAwarenessSmokeIntegrationTest`: 3 passed on disposable MySQL.
- `python tests/gptlive/run_acceptance.py --java-tests all --browser` with local
  admin overrides: full Java regression passed 463 cases in 111 classes, none
  skipped. Its browser phase exposed missing CDN UI assets (32 passed / 13 failed).
- Traces identified `ERR_INTERNET_DISCONNECTED` for Bootstrap. Added pinned dev-only
  Bootstrap 5.3.3 / Bootstrap Icons 1.11.3 and a shared Playwright routing fixture.
  CSS/JS integrity hashes match the production HTML; production assets are unchanged.
- `python tests/gptlive/run_acceptance.py --java-tests none --browser` after that
  test-only fix passed all 45 enabled cases and the disabled text/TTS case. The
  separate Live-only case is intentionally skipped with the feature disabled.
- Existing Node suites: Live 17, transcription 29, speech 21; all 67 passed.
- JavaScript syntax and `git diff --check` passed. Desktop Live and mobile
  classroom screenshots inspected; controls/layouts render correctly. Optional
  external fonts use browser fallbacks in offline execution.

Local artifact directories: `target/gptlive-acceptance-4febf7c107` (focused Java),
`target/gptlive-acceptance-7b9b093169` (full Java/initial browser diagnosis), and
`target/gptlive-acceptance-4e04fc8e9c` (successful browser acceptance). The runner
stopped each owned application and removed each disposable schema/account.

A read-only deployment database inventory before rollout found 24 saved agents,
none with capability awareness enabled. Heroku valerian baseline was release v99,
web.1 up, and HTTP health 200/UP. No deployment data was modified.

Synthetic providers establish transport/context correctness, not actual model
answer quality, physical audio performance or guaranteed task completion.
