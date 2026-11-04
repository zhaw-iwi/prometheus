package ch.zhaw.prometheus.model;

import java.util.*;

/** Read-only, content-free description of a saved machine. Never evaluates its policies or guards. */
public record StateGraph(int schemaVersion, UUID initialStateId, List<Node> nodes, List<Edge> edges,
        List<Region> regions, List<UUID> activeStateIds) {
    public record Node(UUID id, String name, String kind, boolean startsOnEntry) {}
    public record Operation(String type, String label) {}
    public record Edge(String id, UUID sourceId, UUID targetId, int priority,
            List<Operation> conditions, List<Operation> actions) {}
    /** Region membership describes inner reachability, rather than assuming every state has one parent. */
    public record Region(UUID ownerId, UUID initialStateId, List<UUID> stateIds) {}

    public static StateGraph of(Agent agent) {
        var all = new LinkedHashMap<UUID, State>();
        var pending = new ArrayDeque<State>();
        pending.add(agent.initialState()); pending.add(agent.getCurrentState());
        while (!pending.isEmpty()) {
            var state = pending.removeFirst();
            if (all.putIfAbsent(requiredId(state), state) != null) continue;
            state.getTransitions().forEach(t -> pending.add(t.getSubsequentState()));
            if (state instanceof OuterState outer) {
                pending.add(outer.innerInitialState()); pending.add(outer.getInnerCurrent());
            }
        }
        var nodes = new ArrayList<Node>(); var edges = new ArrayList<Edge>(); var regions = new ArrayList<Region>();
        for (var state : all.values()) {
            UUID source = requiredId(state);
            nodes.add(new Node(source, state.getName(), state instanceof OuterState ? "compound"
                    : state instanceof Final ? "final" : "state", state.isStarting()));
            int order = 0;
            for (var transition : state.getTransitions()) {
                UUID transitionId = Objects.requireNonNull(transition.getId(), "Graph requires saved transitions");
                edges.add(new Edge(source + ":" + transitionId, source, requiredId(transition.getSubsequentState()), ++order,
                        transition.getDecisions().stream().map(d -> new Operation(d.getClass().getSimpleName(), d.inspectionLabel())).toList(),
                        transition.getActions().stream().map(a -> new Operation(a.getClass().getSimpleName(), a.inspectionLabel())).toList()));
            }
            if (state instanceof OuterState outer) {
                var members = new LinkedHashSet<UUID>();
                var inner = new ArrayDeque<State>(); inner.add(outer.innerInitialState()); inner.add(outer.getInnerCurrent());
                while (!inner.isEmpty()) {
                    var member = inner.removeFirst();
                    if (!members.add(requiredId(member))) continue;
                    member.getTransitions().forEach(t -> inner.add(t.getSubsequentState()));
                }
                regions.add(new Region(source, requiredId(outer.innerInitialState()), List.copyOf(members)));
            }
        }
        return new StateGraph(1, requiredId(agent.initialState()), List.copyOf(nodes), List.copyOf(edges),
                List.copyOf(regions), activeIds(agent.getCurrentState()));
    }

    /** Uses only the already-loaded active chain; does not traverse the graph for monitor updates. */
    public static List<UUID> activeIds(State current) {
        var ids = new ArrayList<UUID>(); var seen = Collections.newSetFromMap(new IdentityHashMap<State, Boolean>());
        while (current != null && seen.add(current)) {
            if (current.getId() != null) ids.add(current.getId());
            current = current instanceof OuterState outer ? outer.getInnerCurrent() : null;
        }
        return List.copyOf(ids);
    }

    private static UUID requiredId(State state) { return Objects.requireNonNull(state.getId(), "Graph requires saved states"); }
}
