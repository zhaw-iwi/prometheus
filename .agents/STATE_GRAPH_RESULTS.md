# State graph viewer evidence

## GV-01 (2026-11-04, complete)

Added a scoped read-only graph description and active-state IDs in state/monitor
snapshots. IDs retain duplicate labels, cycles and parallel/shared transitions.
Nested regions explicitly represent membership and initial states, including
shared members. Transition priority is list order, conditions are conjunctive,
and empty conditions are unconditional. Inspection never evaluates these rules.
Only safe operation types/labels are exposed; unknown operation classes use their
type name rather than inferred meaning or serialized policy content.

Local verification uses the existing disposable runner and owned MySQL instance
on loopback port 60878; no configured application or production schema is used.

`f9b8a49f89` passed all 15 tests in StateGraphUnitTest, StateGraphIntegrationTest,
AgentNestedOuterStateRoutingUnitTest, StateTransitionUnitTest and
QueryVolumeIntegrationTest. The existing synthetic Coordination minute remains
1,993 questions (quota headroom still false). The loaded monitor snapshot adds
zero SQL statements. No failures/errors/skips; disposable resources were removed.
Command: `python tests/gptlive/run_acceptance.py --database-properties
target/coordination-mysql/admin.properties --java-tests
StateGraphUnitTest,StateGraphIntegrationTest,AgentNestedOuterStateRoutingUnitTest,StateTransitionUnitTest,QueryVolumeIntegrationTest`.
Whitespace checks passed. UI integration follows in GV-02/03.
