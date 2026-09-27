package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.*;

class LiveMultimodalUnitTest {
    private static final String PLAN = """
            {"nonVerbal":{"gesture":"ACKNOWLEDGE","facialExpression":{"type":"attentive","intensity":0.4},
            "gaze":{"direction":"toward_user","focus":"person"},"motion":{"stillness":0.8,"energy":0.2}},
            "motion":{"handSign":"paper"},"display":{"text":"Ready"}}
            """;
    final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    final PromptMessageAssembler assembler = new PromptMessageAssembler();
    final PolicyRuntime runtime = new PolicyRuntime(assembler, gateway);

    @Test void creationIsQuietAndProfileIncludesEveryExistingObservationAndModality() {
        var created = new LiveMultimodal().createInstance(new AgentCreationContext(assembler, gateway));
        assertNull(created.starterEvent()); assertTrue(created.agent().getEventHistory().isEmpty());
        verifyNoInteractions(gateway);
        var profile = created.agent().getInteractionProfile();
        assertEquals(Set.of(Event.TYPE_USER_UTTERANCE, Event.TYPE_FACE_EMOTION, Event.TYPE_HUMAN_PRESENCE,
                Event.TYPE_SOCIAL_GROUPING, Event.TYPE_SOCIAL_CONTEXT, Event.TYPE_SOCIAL_SITUATION_CHANGE,
                Event.TYPE_HAND_SIGN, Event.TYPE_WEATHER_CURRENT, Event.TYPE_WEATHER_FORECAST), Set.copyOf(profile.getSupportedObservations()));
        assertEquals(Set.of("speech", "nonVerbal.gesture", "nonVerbal.facialExpression", "nonVerbal.gaze",
                "nonVerbal.motion", "motion.handSign", "display"), Set.copyOf(profile.getSupportedBehaviourModalities()));
        var voice = new LiveVoicePolicyAdapter();
        assertTrue(voice.supports(created.agent()));
        assertTrue(voice.instructions(created.agent()).contains(LiveMultimodal.VOICE));
        assertFalse(voice.instructions(created.agent()).contains(LiveMultimodal.EMBODIMENT));
    }

    @Test void oneEmbodimentRequestNeverIncludesVoiceInstructionsWithOrWithoutLiveOwnership() {
        when(gateway.infer(any())).thenReturn(PLAN);
        Agent agent = new LiveMultimodal().createAgent();
        agent.getEventHistory().appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Show paper and a caption"));
        for (PolicyRuntime active : List.of(runtime, runtime.withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID())))) {
            clearInvocations(gateway);
            BehaviourPlan plan = BehaviourPlan.fromJson(agent.generate(active).getPayload());
            assertNull(plan.getSpeech()); assertNotNull(plan.getNonVerbal());
            assertEquals("paper", plan.getMotion().getAsJsonObject().get("handSign").getAsString());
            assertEquals("Ready", plan.getDisplay().getAsJsonObject().get("text").getAsString());
            var request = ArgumentCaptor.forClass(InferenceRequest.class);
            verify(gateway, times(1)).infer(request.capture());
            assertEquals(InferencePurpose.BEHAVIOUR, request.getValue().purpose());
            String prompt = request.getValue().messages().stream().map(PromptMessage::getContent).reduce("", String::concat);
            assertTrue(prompt.contains(LiveMultimodal.EMBODIMENT)); assertTrue(prompt.contains("Show paper and a caption"));
            assertFalse(prompt.contains(LiveMultimodal.VOICE));
            assertFalse(prompt.contains("Put the exact user-facing spoken response"));
        }
    }

    @Test void unexpectedSpeechIsRejectedBeforePublication() {
        when(gateway.infer(any())).thenReturn("{\"speech\":\"unwanted\",\"nonVerbal\":{\"gesture\":\"NONE\"}}");
        Agent agent = new LiveMultimodal().createAgent();
        assertThrows(IllegalStateException.class, () -> agent.generate(runtime));
        assertTrue(agent.getEventHistory().isEmpty());
    }

    @Test void rawSensorFramesAndTicksDoNotGenerateButSocialChangesAndFinalEntryRemainNonverbal() {
        when(gateway.infer(any())).thenReturn(PLAN);
        when(gateway.decide(any())).thenReturn(true);
        Agent agent = new LiveMultimodal().createAgent();
        for (String type : List.of(Event.TYPE_FACE_EMOTION, Event.TYPE_HUMAN_PRESENCE, Event.TYPE_SOCIAL_GROUPING,
                Event.TYPE_SOCIAL_CONTEXT, Event.TYPE_HAND_SIGN, Event.TYPE_WEATHER_CURRENT, Event.TYPE_WEATHER_FORECAST)) {
            assertNull(agent.acknowledge(Event.observation(type, "sensor", "{}"), runtime));
        }
        assertNull(agent.tick(runtime)); verify(gateway, never()).infer(any());
        verify(gateway, never()).decide(any());
        Event social = agent.acknowledge(Event.observation(Event.TYPE_SOCIAL_SITUATION_CHANGE, "system", "{}"), runtime);
        assertNull(BehaviourPlan.fromJson(social.getPayload()).getSpeech());
        verify(gateway, times(1)).infer(any());
        Event ended = agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "End the whole demo"), runtime);
        assertFalse(agent.isActive()); assertNull(BehaviourPlan.fromJson(ended.getPayload()).getSpeech());
        assertTrue(new LiveVoicePolicyAdapter().instructions(agent).contains("interaction is complete"));
    }

    @Test void nestedEmbodimentPoliciesKeepVoiceAndBehaviourInstructionsSeparate() {
        var inner = new EmbodimentPolicy("INNER_VOICE", "INNER_BODY");
        var outer = new EmbodimentPolicy("OUTER_VOICE", "OUTER_BODY");
        var combined = inner.withOuterPolicy(outer);
        assertEquals("OUTER_VOICE\nINNER_VOICE", combined.describe());
        when(gateway.infer(any())).thenReturn(PLAN);
        combined.onRespond(null, new ch.zhaw.prometheus.model.event.EventHistory(), assembler, gateway);
        verify(gateway).infer(argThat(request -> {
            String text = request.messages().stream().map(PromptMessage::getContent).reduce("", String::concat);
            return text.contains("OUTER_BODY") && text.contains("INNER_BODY") && !text.contains("_VOICE");
        }));
        assertThrows(IllegalArgumentException.class, () -> inner.withOuterPolicy(new PromptPolicy()));
    }
}
