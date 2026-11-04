package ch.zhaw.prometheus.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.model.commons.decisions.LatestEventTypeDecision;

class StateGraphUnitTest {
    static <T> T saved(T node) { ReflectionTestUtils.setField(node, "id", UUID.randomUUID()); return node; }
    static State state(String name) { return saved(new State(name, new NoOpPolicy(), List.of())); }
    static void edge(State from, State to) { from.addTransition(saved(new Transition(to))); }

    @Test void cyclesParallelTransitionsAndDuplicateNamesRetainIdentityAndPriorityWithoutExecution() {
        var a = state("Same"); var b = state("Same");
        var guard = mock(Decision.class); when(guard.inspectionLabel()).thenReturn("Condition summary");
        var action = mock(Action.class); when(action.inspectionLabel()).thenReturn("Action summary");
        a.addTransition(saved(new Transition(guard, action, b))); edge(a, b); edge(b, a); edge(b, b);
        var graph = StateGraph.of(new Agent("fixture", "", a));
        assertEquals(2, graph.nodes().size()); assertEquals(4, graph.edges().size());
        assertEquals(a.getId(), graph.initialStateId()); assertEquals(List.of(a.getId()), graph.activeStateIds());
        assertEquals(List.of(1,2,1,2), graph.edges().stream().map(StateGraph.Edge::priority).toList());
        assertEquals(4, graph.edges().stream().map(StateGraph.Edge::id).distinct().count());
        assertEquals("Condition summary", graph.edges().getFirst().conditions().getFirst().label());
        verify(guard).inspectionLabel(); verify(action).inspectionLabel(); verifyNoMoreInteractions(guard, action);
        assertTrue(graph.edges().get(1).conditions().isEmpty());
    }

    @Test void nestedAndSharedRegionsPreserveTheirInitialStatesAndActiveChain() {
        var leaf = state("Leaf"); var end = saved(new Final("End")); edge(leaf, end);
        var inner = saved(new OuterState("PRIVATE PROMPT", "Inner", List.of(), leaf));
        var outer = saved(new OuterState("PRIVATE PROMPT", "Outer", List.of(), inner));
        var shared = saved(new OuterState("PRIVATE PROMPT", "Shared", List.of(), leaf)); edge(outer, shared);
        var graph = StateGraph.of(new Agent("fixture", "", outer));
        assertEquals(5, graph.nodes().size()); assertEquals(3, graph.regions().size());
        assertEquals(List.of(outer.getId(),inner.getId(),leaf.getId()), graph.activeStateIds());
        assertEquals(2, graph.regions().stream().filter(r -> r.initialStateId().equals(leaf.getId())).count());
        assertEquals("final", graph.nodes().stream().filter(n -> n.id().equals(end.getId())).findFirst().orElseThrow().kind());
        assertFalse(new com.google.gson.Gson().toJson(graph).contains("PRIVATE PROMPT"));
    }

    @Test void sharedTransitionHasSeparateSourceEdgesAndTypedEventLabels() {
        var a = state("A"); var b = state("B");
        var transition = saved(new Transition(new LatestEventTypeDecision("obs.user_utterance"), b));
        a.addTransition(transition); b.addTransition(transition);
        var graph = StateGraph.of(new Agent("fixture", "", a));
        assertEquals(2, graph.edges().stream().map(StateGraph.Edge::id).distinct().count());
        assertEquals("Latest event is obs.user_utterance",graph.edges().getFirst().conditions().getFirst().label());
    }
}
