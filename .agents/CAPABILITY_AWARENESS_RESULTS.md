# Capability awareness evidence

See PLAN_CAPABILITY_AWARENESS.md for authorized scope, rollout and branch sequence.

## CA-01 / 197

Implemented explicit per-definition opt-in and additive persisted profile JSON;
all main core definitions opt in and non-core definitions default out. The bounded
description includes only declared channels, known meanings and operational limits.
Live compatibility stays independent. Delivery is added in CA-02 and CA-03.

Verification: `mvnw.cmd -q clean -Dtest=AgentCapabilityDescriptionUnitTest,AgentInteractionProfileUnitTest,AgentDefinitionRegistryUnitTest,TalkToMePolicyUnitTest test` passed 19 cases. No database/provider access.
