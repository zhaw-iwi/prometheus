package ch.zhaw.prometheus.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.core.*;
import ch.zhaw.prometheus.model.commons.decisions.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.interaction.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.*;

class AgentCapabilityAwarenessUnitTest {
    private final List<InferenceRequest> captured = new ArrayList<>();
    private final NoOpLanguageModelGateway gateway = new NoOpLanguageModelGateway() {
        @Override public String infer(InferenceRequest request) {
            captured.add(request);
            return "{\"speech\":\"Ready\",\"nonVerbal\":{\"gesture\":\"NONE\"}}";
        }
        @Override public boolean decide(List<PromptMessage> messages) {
            assertFalse(messages.toString().contains(AgentCapabilityDescription.MARKER));
            return false;
        }
    };
    private final PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway);

    @Test void definitionInitialGenerationNeedsOnlyOptInAndTalkToMeStillMakesNoInference() {
        var context = new AgentCreationContext(runtime.promptMessageAssembler(), gateway);
        new FacialExpressionSensitivity().createInstance(context);
        assertEquals(1, captured.size());
        assertCapability(captured.getFirst(), true);
        new TalkToMe().createInstance(context);
        assertEquals(1, captured.size());
    }

    @Test void nestedStateSelectionFinalEntryAndResetAllRetainCapabilitiesWithoutHistoryEvents() {
        State leaf = new State("inner", new PromptPolicy("Inner guidance", null, null), List.of());
        leaf.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE), new Final("done")));
        leaf.setEventSelectorSpec(EventSelectorSpec.type("never.selected"));
        Agent agent = aware(new Agent("fixture", "", new OuterState("Outer guidance", "outer", List.of(), leaf)));
        agent.start(runtime);
        agent.generate(runtime);
        // The leaf's selector must include the trigger for the transition, not the capability context.
        leaf.setEventSelectorSpec(EventSelectorSpec.type(Event.TYPE_USER_UTTERANCE));
        agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "finish"), runtime);
        assertFalse(agent.isActive());
        agent.generate(runtime);
        agent.reset();
        assertTrue(agent.getEventHistory().isEmpty());
        agent.start(runtime);
        assertEquals(5, captured.size());
        captured.forEach(request -> assertCapability(request, true));
        assertTrue(agent.getEventHistory().toList().stream().allMatch(e -> Event.KIND_RESPONSE.equals(e.getKind())));
    }

    @Test void speculationAndActualGenerationHaveIdenticalBoundContext() {
        State leaf = new State("inner", new PromptPolicy("Help", null, null),
                List.of(new Transition(new StaticDecision("finished?"), new Final("done"))));
        Agent agent = aware(new Agent("fixture", "", leaf));
        var previews = new ArrayList<InferenceRequest>();
        BehaviourSpeculation speculation = new BehaviourSpeculation() {
            public void prepare(State state, Event event, PolicyRuntime bound) {
                previews.add(BehaviourPreview.prepare(state, event, bound));
            }
            public void invalidate() {}
        };
        agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "What can you do?"),
                runtime.withBehaviourSpeculation(speculation));
        agent.generate(runtime);
        assertCapability(previews.getFirst(), true);
        assertEquals(previews.getFirst().messages().toString(), captured.getFirst().messages().toString());
    }

    @Test void embodimentAndExternalSpeechComplementIncludeContextWithoutCompetingSpeech() {
        var nonSpeechGateway = new NoOpLanguageModelGateway() {
            @Override public String infer(InferenceRequest request) {
                captured.add(request); return "{\"nonVerbal\":{\"gesture\":\"NONE\"}}";
            }
        };
        var bound = new PolicyRuntime(new PromptMessageAssembler(), nonSpeechGateway);
        Agent live = new LiveMultimodal().createAgent();
        assertFalse(live.generate(bound).getPayload().contains("\"speech\""));
        PromptPolicy policy = new PromptPolicy("Speak", null, null);
        policy.setNonVerbalPlanPrompt("Choose one gesture.");
        Agent ordinary = aware(new Agent("ordinary", "", new State("state", policy, List.of())));
        assertFalse(ordinary.generate(bound.withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), ordinary.executionEpoch())))
                .getPayload().contains("\"speech\""));
        assertEquals(2, captured.size());
        captured.forEach(request -> assertCapability(request, true));
    }

    @Test void sharedRuntimeNeverLeaksAcrossAgentsAndRebindingIsIdempotent() {
        Agent on = aware(new Agent("on", "", new State("state", new PromptPolicy("Help", null, null), List.of())));
        Agent off = new Agent("off", "", new State("state", new PromptPolicy("Help", null, null), List.of()));
        var reused = runtime.forCapabilities(on.getInteractionProfile());
        on.generate(reused);
        off.generate(reused);
        on.setInteractionProfile(on.getInteractionProfile().withCapabilityAwareness(false));
        on.generate(runtime);
        assertCapability(captured.get(0), true);
        assertCapability(captured.get(1), false);
        assertCapability(captured.get(2), false);
        assertFalse(runtime.promptMessageAssembler().compose(null, "plain").toString().contains(AgentCapabilityDescription.MARKER));
    }

    @Test void customAssemblerOverridesArePreservedWithoutEnablingSpeculation() {
        var custom = new PromptMessageAssembler(List.of(), List.of()) {
            @Override public List<PromptMessage> compose(EventHistory history, String prepend) {
                return List.of(PromptMessage.system("custom guidance"));
            }
        };
        var bound = custom.forCapabilities(AgentInteractionProfiles.speechOnly().withCapabilityAwareness(true));
        assertFalse(bound.supportsSpeculativeComposition());
        assertFalse(bound.supportsGuardComposition());
        assertEquals("custom guidance", bound.compose(null, "ignored").getFirst().getContent());
        assertEquals(2, bound.compose(null, "ignored").size());
    }

    private static Agent aware(Agent agent) {
        agent.setInteractionProfile(AgentInteractionProfiles.speechOnly().withCapabilityAwareness(true));
        return agent;
    }
    private static void assertCapability(InferenceRequest request, boolean expected) {
        assertNotNull(request);
        assertEquals(InferencePurpose.BEHAVIOUR, request.purpose());
        assertEquals(expected ? 1 : 0, request.messages().stream()
                .filter(m -> m.getRole().equals("system") && m.getContent().startsWith(AgentCapabilityDescription.MARKER)).count());
    }
}
