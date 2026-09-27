package ch.zhaw.prometheus.application.live;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.commons.decisions.LatestEventTypeDecision;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class LiveSpeechExecutionUnitTest {
    final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    final PromptMessageAssembler assembler = new PromptMessageAssembler();
    final ExternalSpeech owner = new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID());
    PolicyRuntime live() { return new PolicyRuntime(assembler, gateway).withExternalSpeech(owner); }
    PromptPolicy policy(boolean nonverbal) {
        var policy = new PromptPolicy("Speak briefly", "Say hello", null);
        if (nonverbal) policy.setNonVerbalPlanPrompt("Use an ACKNOWLEDGE gesture.");
        return policy;
    }
    @Test void ordinaryNativeSpeechAvoidsCompetingGenerationButPreservesNonSpeechComplement() {
        Agent speechOnly = new Agent("speech", "", new State("talk", policy(false), List.of()));
        assertNull(speechOnly.generate(live())); verifyNoInteractions(gateway);
        Agent multimodal = new Agent("multi", "", new State("talk", policy(true), List.of()));
        when(gateway.infer(any())).thenReturn("{\"nonVerbal\":{\"gesture\":\"ACKNOWLEDGE\"},\"display\":{\"mode\":\"ready\"}}");
        var event = multimodal.generate(live()); var plan = BehaviourPlan.fromJson(event.getPayload());
        assertNull(plan.getSpeech()); assertNotNull(plan.getNonVerbal()); assertNotNull(plan.getDisplay());
        verify(gateway).infer(argThat(request -> request.messages().getLast().getContent().contains("omit speech completely")));
        when(gateway.infer(any())).thenReturn("{\"speech\":\"competing\",\"nonVerbal\":{\"gesture\":\"NONE\"}}");
        int before = multimodal.getEventHistory().toList().size();
        assertThrows(IllegalStateException.class, () -> multimodal.generate(live()));
        assertEquals(before, multimodal.getEventHistory().toList().size());
    }
    @Test void entryNestedFinalSensorySelfLoopAndTickPlansRetainIntentSpeechAndActions() {
        when(gateway.infer(any())).thenReturn("{\"speech\":\"Planned announcement\",\"nonVerbal\":{\"gesture\":\"ACKNOWLEDGE\"}}");
        var count = new AtomicInteger();
        Action action = new Action(new NoOpPolicy()) {
            @Override public void execute(EventHistory events, PolicyRuntime runtime) { count.incrementAndGet(); }
        };
        State leaf = new State("leaf", policy(true), List.of());
        State end = new Final("finished", "Say goodbye");
        leaf.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_FACE_EMOTION), action, leaf));
        leaf.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_SYSTEM_TICK), action, end));
        Agent agent = new Agent("nested", "", new OuterState("Outer guidance", "outer", List.of(), leaf));
        Event start = agent.start(live()); assertTrue(ConversationProjection.isIntent(start));
        Event sensory = agent.acknowledge(Event.observation(Event.TYPE_FACE_EMOTION, Event.ACTOR_USER, "{}"), live());
        assertTrue(ConversationProjection.isIntent(sensory)); assertEquals(1, count.get());
        assertNotNull(BehaviourPlan.fromJson(sensory.getPayload()).getNonVerbal());
        Event finalEvent = agent.tick(live()); assertFalse(agent.isActive()); assertEquals(2, count.get());
        assertTrue(ConversationProjection.isIntent(finalEvent));
        assertEquals("Planned announcement", ConversationProjection.view(finalEvent).plannedSpeech());
        assertFalse(ConversationProjection.view(finalEvent).payload().contains("Planned announcement"));
        verify(gateway, times(3)).infer(any());
    }
    @Test void nativeRecordingNeverAcknowledgesAndConversationalProjectionHidesIntentOnly() {
        State state = new State("talk", policy(false), List.of());
        State next = new State("must not enter", policy(false), List.of());
        state.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN), next));
        Agent agent = new Agent("native", "", state);
        Event intent = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT, "{\"speech\":\"intent sentinel\"}").withStatePath("talk");
        intent.speechProvenance(SpeechProvenance.intent(owner)); agent.getEventHistory().appendEvent(intent);
        var provenance = new SpeechProvenance(SpeechProvenance.Origin.NATIVE, owner.sessionId(), owner.epoch(), "assistant-1",
                SpeechProvenance.Association.NONE, List.of(), true);
        var recorded = agent.recordExternalSpeech("Actual native words", provenance);
        assertSame(state, agent.getCurrentState()); verifyNoInteractions(gateway);
        assertEquals(provenance, recorded.speechProvenance());
        var messages = assembler.compose(state.getEventHistory(), "context");
        assertEquals(2, messages.size()); assertEquals("Actual native words", messages.getLast().getContent());
        assertFalse(state.getEventHistory().toString().contains("intent sentinel"));
        assertEquals("Actual native words", ConversationProjection.speech(recorded));
    }
    @Test void defaultRuntimeKeepsBackendSpeechAndFullPlanContract() {
        Agent agent = new Agent("ordinary", "", new State("talk", policy(true), List.of()));
        when(gateway.infer(any())).thenReturn("{\"speech\":\"Normal backend reply\",\"nonVerbal\":{\"gesture\":\"NONE\"}}");
        Event event = agent.generate(new PolicyRuntime(assembler, gateway));
        assertNull(event.speechProvenance()); assertEquals("Normal backend reply", ConversationProjection.speech(event));
        assertEquals(OutputProfile.FULL_PLAN, live().outputProfile());
        var speculation = mock(BehaviourSpeculation.class);
        assertNull(live().withBehaviourSpeculation(speculation).behaviourSpeculation());
    }
}
