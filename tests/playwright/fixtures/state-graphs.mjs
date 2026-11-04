const node = (id, name = id, kind = "state") => ({
  id,
  name,
  kind,
  startsOnEntry: true,
});
const edge = (
  sourceId,
  targetId,
  priority = 1,
  label = "Latest event is obs.user_utterance",
) => ({
  id: `${sourceId}:${targetId}:${priority}`,
  sourceId,
  targetId,
  priority,
  conditions: label ? [{ type: "LatestEventTypeDecision", label }] : [],
  actions: [{ type: "ExampleAction", label: "ExampleAction" }],
});
export const small = {
  schemaVersion: 1,
  initialStateId: "conversation",
  activeStateIds: ["conversation"],
  regions: [],
  nodes: [
    node("conversation", "Conversation"),
    node("execute", "Execution"),
    node("finish", "Finished", "final"),
  ],
  edges: [
    edge("conversation", "execute"),
    edge("conversation", "conversation", 2),
    edge("execute", "finish", 1, null),
    edge("finish", "conversation"),
  ],
};
// Mirrors CoordinationAgent's nine phases, routing hub and 47 saved transitions.
const phases = [
  "conversation",
  "agreement",
  "preparation",
  "awaiting acceptance",
  "execution",
  "paused",
  "finishing",
  "uncertain",
  "reflection",
];
export const coordination = {
  schemaVersion: 1,
  initialStateId: phases[0],
  activeStateIds: ["execution"],
  regions: [],
  nodes: [
    ...phases.map((id) => node(id, `Coordination ${id}`)),
    { ...node("routing", "Coordination routing"), startsOnEntry: false },
  ],
  edges: phases
    .flatMap((id, i) => [
      edge("routing", id, i + 1, `Coordination stage is ${id}`),
      ...Array.from({ length: id === "execution" ? 5 : 4 }, (_, j) =>
        edge(id, "routing", j + 1),
      ),
    ])
    .concat(edge("routing", phases[0], 10, null)),
};
export const nested = {
  schemaVersion: 1,
  initialStateId: "outer",
  activeStateIds: ["outer", "inner", "leaf"],
  nodes: [
    node("outer", "Outer", "compound"),
    node("other", "Other owner", "compound"),
    node("inner", "Inner", "compound"),
    node("leaf", "Duplicate label"),
    node("shared", "Duplicate label"),
  ],
  edges: [
    edge("leaf", "leaf"),
    edge("inner", "shared"),
    edge("outer", "other"),
    edge("shared", "inner"),
  ],
  regions: [
    {
      ownerId: "outer",
      initialStateId: "inner",
      stateIds: ["inner", "shared"],
    },
    { ownerId: "inner", initialStateId: "leaf", stateIds: ["leaf"] },
    { ownerId: "other", initialStateId: "shared", stateIds: ["shared"] },
  ],
};
