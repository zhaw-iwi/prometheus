package ch.zhaw.prometheus.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.commons.decisions.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.*;

class BehaviourPreviewUnitTest {
    private final PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), new NoOpLanguageModelGateway());
    private final Event event = Event.observation(Event.TYPE_USER_UTTERANCE, "user", "hello");

    @Test void nestedPreviewUsesTheSameComposedPolicyAndSelectedEventsAsGeneration() {
        State inner = new State("inner", new PromptPolicy("inner instructions", null, null),
                List.of(new Transition(new StaticDecision("finish?"), new Final("done"))));
        State outer = new OuterState("outer instructions", "outer", List.of(), inner);
        Agent agent = new Agent("fixture", "", outer);
        agent.getEventHistory().appendEvent(event.withStatePath("outer", "inner"));
        InferenceRequest preview = BehaviourPreview.prepare(outer, event, runtime);
        assertNotNull(preview); assertEquals(InferencePurpose.BEHAVIOUR, preview.purpose());
        assertTrue(preview.messages().toString().contains("outer instructions inner instructions"));
        assertTrue(preview.messages().toString().contains("hello"));
        var captured = new ArrayList<InferenceRequest>();
        agent.generate(new PolicyRuntime(new PromptMessageAssembler(), new NoOpLanguageModelGateway() {
            @Override public String infer(InferenceRequest request) { captured.add(request); return "{\"speech\":\"hello\"}"; }
        }));
        assertEquals(preview.messages().toString(), captured.getFirst().messages().toString());
    }

    @Test void sensoryUnconditionalCustomStateAndCustomGuardPathsDoNotSpeculate() {
        var finish = new Final("done");
        var guarded = new State("guarded", new PromptPolicy("prompt", null, null), List.of(new Transition(new StaticDecision("finish?"), finish)));
        new Agent("fixture", "", guarded).getEventHistory().appendEvent(event.withStatePath("guarded"));
        assertNull(BehaviourPreview.prepare(guarded, Event.observation(Event.TYPE_FACE_EMOTION, "user", "{}"), runtime));
        var customAssembler = new PromptMessageAssembler(List.of(), List.of(events -> { fail("must not speculate through custom augmenter"); return List.of(); }));
        assertNull(BehaviourPreview.prepare(guarded, event, new PolicyRuntime(customAssembler, new NoOpLanguageModelGateway())));
        var unconditional = new State("u", new PromptPolicy("prompt", null, null), List.of(new Transition(finish)));
        assertNull(BehaviourPreview.prepare(unconditional, event, runtime));
        var custom = new State("c", new PromptPolicy("prompt", null, null), List.of()) {};
        assertNull(BehaviourPreview.prepare(custom, event, runtime));
        var decision = new StaticDecision("finish?") { @Override public boolean decide(EventHistory events, PolicyRuntime runtime) { fail("must not run custom decision"); return true; } };
        var customGuard = new State("g", new PromptPolicy("prompt", null, null), List.of(new Transition(decision, finish)));
        assertNull(BehaviourPreview.prepare(customGuard, event, runtime));
    }
}
