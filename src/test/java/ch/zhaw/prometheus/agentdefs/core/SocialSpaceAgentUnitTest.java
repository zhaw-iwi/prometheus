package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.SpeechProvenance;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.task.TaskMemory;
import ch.zhaw.prometheus.model.task.TaskPolicy;
import ch.zhaw.prometheus.spi.InferenceRequest;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class SocialSpaceAgentUnitTest {
    private final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    private final PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway);
    private final GenericMultimodalBehaviourAtSocialSpace definition = new GenericMultimodalBehaviourAtSocialSpace();
    // Preserve the saved-instance contract; new-instance admission is covered by CoreSensoryReactionUnitTest.
    private final Agent agent = legacyAgent();
    private Agent legacyAgent() {
        var value = definition.createAgent(); ((TaskPolicy) value.getCurrentState().ownPolicy()).storage()
                .remove(ch.zhaw.prometheus.model.behaviour.SensoryRemarks.ENABLED); return value;
    }
    private final Instant baseline = Instant.now().minusSeconds(10);
    private int sample;
    private static final String SPEC = """
            {"goal":"Respond to a requested facial cue","maxActions":3,"sessionBound":true,"rules":[
            {"eventType":"obs.emotion.face","field":"valence","operator":"gt","value":0.5,
             "action":"Say one encouraging sentence","effect":"ACT","minConfidence":0.5,"samples":1,"cooldownSeconds":3}]}
            """;

    @Test void creationKeepsValerianAndRetainsGenericCapabilitiesAndLiveSupport() {
        var expected = new GenericMultimodalBehaviour().createAgent().getInteractionProfile();
        assertEquals("core.generic_multimodal_behaviour_at_social_space", definition.key());
        assertEquals(List.of("core"), definition.packagePath());
        var created = definition.createInstance(new AgentCreationContext(new PromptMessageAssembler(), gateway));
        var actual = created.agent();
        assertEquals("Generic Multimodal Behaviour at Social Space", actual.getName());
        assertEquals("en", actual.getLanguageCode());
        assertEquals("I'm Valerian. How can I be useful?", BehaviourPlan.fromJson(created.starterEvent().getPayload()).getSpeech());
        assertEquals(expected.getSupportedObservations(), actual.getInteractionProfile().getSupportedObservations());
        assertEquals(expected.getSupportedBehaviourModalities(), actual.getInteractionProfile().getSupportedBehaviourModalities());
        assertTrue(actual.getInteractionProfile().isCapabilityAwareness());
        assertTrue(actual.getInteractionProfile().getProfileTags().contains("demo.valerian.core"));
        String preview = actual.getTotalPolicy(new PromptMessageAssembler()).getPromptMessages().getFirst().getContent();
        assertTrue(preview.contains("Your name is Valerian"));
        var adapter = new LiveVoicePolicyAdapter();
        assertTrue(adapter.supports(actual));
        String live = adapter.instructions(actual);
        assertTrue(live.contains("Your name is Valerian"));
        assertTrue(live.contains("including just \"Valerian\""));
        assertTrue(live.replaceAll("\\s+", " ").contains("do not call for another greeting"));
        assertTrue(live.contains("Live owns the wording of replies"));
        assertFalse(live.contains("deliver the backend's spoken response faithfully"));
        verifyNoInteractions(gateway);
    }

    @Test void nameAddressUsesNormalTaskConversationWithEnglishStyleAndUnchangedAuthorizationRules() {
        when(gateway.infer(any())).thenReturn(update("KEEP", "null", "Yes, how can I help?"));
        assertNotNull(say("Hey Valerian"));
        var request = ArgumentCaptor.forClass(InferenceRequest.class);
        verify(gateway).infer(request.capture());
        String instructions = request.getValue().messages().getFirst().getContent();
        for (String required : List.of("Your name is Valerian", "Speak English by default", "3-10 words", "at most one question",
                "microhumor", "Respect skepticism", "A proposal is not activation", "Do not ask for another confirmation when already authorized",
                "not active and awaits acceptance", "1..50 maxActions", "sessionBound", "Supported trigger fields")) {
            assertTrue(instructions.contains(required), required);
        }
        assertFalse(instructions.contains("Gigi"));
        assertTrue(request.getValue().messages().stream().anyMatch(message ->
                "user".equals(message.getRole()) && "Hey Valerian".equals(message.getContent())));
        assertEquals("CONFIGURATION", TaskMemory.phase(memory()));
    }

    @Test void onlyVisibleCountChangesReactAndNeitherStableFramesNorDerivedChangesRepeatThem() {
        when(gateway.infer(any())).thenReturn("{\"speech\":\"Hello there.\"}");
        assertNull(group(0));
        assertNotNull(group(1));
        for (String type : List.of(Event.TYPE_HUMAN_PRESENCE, Event.TYPE_SOCIAL_CONTEXT,
                Event.TYPE_SOCIAL_SITUATION_CHANGE, Event.TYPE_FACE_EMOTION)) {
            assertNull(agent.acknowledge(Event.observation(type, "sensor", "{}"), runtime));
        }
        assertNull(group(1));
        assertNull(group(1));
        assertNull(agent.tick(runtime));
        assertNull(agent.generate(runtime));
        verify(gateway, times(1)).infer(any());
        assertNotNull(group(2));
        assertNotNull(group(1));
        assertNotNull(group(0));
        assertNull(group(0));
        verify(gateway, times(4)).infer(any());
        assertEquals("CONFIGURATION", TaskMemory.phase(memory()));
        assertFalse(memory().containsKey(TaskMemory.ACTIONS));
    }

    @Test void initialOccupiedViewReactsButRegroupingAtTheSameCountDoesNot() {
        when(gateway.infer(any())).thenReturn("{\"speech\":\"Welcome, everyone.\"}");
        assertNotNull(group(5));
        assertNull(agent.acknowledge(grouping(5, baseline.plusSeconds(2), 2), runtime));
        // Every count change is noticed even if group count and largest group do not change.
        assertNotNull(agent.acknowledge(grouping(6, baseline.plusSeconds(3), 2), runtime));
        verify(gateway, times(2)).infer(any());
    }

    @Test void malformedStaleFutureDuplicateAndOutOfOrderSamplesCannotTrigger() {
        for (String payload : List.of("{}", "not json", "{\"humanCount\":-1}", "{\"humanCount\":1.5}", "{\"humanCount\":\"1\"}")) {
            var freshAgent = legacyAgent();
            String input = payload.endsWith("}") ? payload.substring(0, payload.length() - 1)
                    + (payload.equals("{}") ? "" : ",") + "\"ts\":\"" + Instant.now() + "\"}" : payload;
            assertNull(freshAgent.acknowledge(Event.observation(Event.TYPE_SOCIAL_GROUPING, "sensor", input), runtime));
        }
        assertNull(agent.acknowledge(grouping(0, baseline, 0), runtime));
        assertNull(agent.acknowledge(grouping(1, baseline.minusSeconds(30), 0), runtime));
        assertNull(agent.acknowledge(grouping(1, Instant.now().plusSeconds(30), 0), runtime));
        assertNull(agent.acknowledge(grouping(2, baseline, 0), runtime));
        assertNull(agent.acknowledge(grouping(3, baseline, 0), runtime));
        verifyNoInteractions(gateway);
    }

    @Test void ambientRemarksPreserveDraftAndExplicitTaskActivationPauseAndExecutionKeepGenericSemantics() {
        when(gateway.infer(any())).thenReturn(update("PROPOSE", SPEC, "A proposal, awaiting your acceptance."),
                "{\"speech\":\"Hello there.\"}", update("ACTIVATE", "null", "Let's begin."),
                "{\"speech\":\"You have got this.\"}");
        say("Let's plan an interaction.");
        var draft = memory().get(TaskMemory.DRAFT).deepCopy();
        assertNotNull(group(1));
        assertEquals(draft, memory().get(TaskMemory.DRAFT));
        assertFalse(memory().containsKey(TaskMemory.SPEC));
        say("Start it now.");
        assertEquals(draft, memory().get(TaskMemory.SPEC));
        assertEquals(1, memory().get(TaskMemory.ACTIONS).getAsInt());
        assertNull(group(2));
        say("Pause");
        assertEquals("PAUSED", TaskMemory.phase(memory()));
        assertNull(group(0));
        say("Resume");
        memory().put(TaskMemory.AFTER, new JsonPrimitive(Instant.EPOCH.toString()));
        assertNotNull(agent.acknowledge(Event.observation(Event.TYPE_FACE_EMOTION, "sensor",
                "{\"facePresent\":true,\"valence\":0.8,\"confidence\":0.9,\"ts\":\"" + Instant.now() + "\"}"), runtime));
        assertEquals(2, memory().get(TaskMemory.ACTIONS).getAsInt());
        say("Stop");
        assertEquals("COMPLETED", TaskMemory.phase(memory()));
        verify(gateway, times(4)).infer(any());
        agent.reset();
        assertEquals("CONFIGURATION", TaskMemory.phase(memory()));
        assertFalse(memory().containsKey(TaskMemory.SPEC));
        assertTrue(new LiveVoicePolicyAdapter().instructions(agent).contains("Your name is Valerian"));
    }

    @Test void rejectedOldAndMalformedFramesDoNotCreateAnotherArrivalOnTheNextStableSample() {
        when(gateway.infer(any())).thenReturn("{\"speech\":\"Hello there.\"}");
        assertNotNull(agent.acknowledge(grouping(1, baseline.plusSeconds(2), 0), runtime));
        assertNull(agent.acknowledge(grouping(0, baseline, 0), runtime));
        assertNull(agent.acknowledge(Event.observation(Event.TYPE_SOCIAL_GROUPING, "sensor", "{}"), runtime));
        assertNull(agent.acknowledge(grouping(0, Instant.now().plusSeconds(30), 0), runtime));
        assertNull(agent.acknowledge(grouping(1, baseline.plusSeconds(3), 0), runtime));
        verify(gateway, times(1)).infer(any());
    }

    @Test void failedOrInvalidSocialOutputStaysSilentAndDoesNotRetryOnContinuedVisibility() {
        for (String response : List.of("provider failure", "{\"operation\":\"ACTIVATE\"}", "{}")) {
            var freshAgent = legacyAgent();
            reset(gateway);
            if (response.equals("provider failure")) when(gateway.infer(any())).thenThrow(new IllegalStateException("offline"));
            else when(gateway.infer(any())).thenReturn(response);
            assertNull(freshAgent.acknowledge(grouping(1, baseline, 0), runtime));
            assertNull(freshAgent.acknowledge(grouping(1, baseline.plusSeconds(1), 0), runtime));
            verify(gateway, times(1)).infer(any());
            assertEquals("CONFIGURATION", TaskMemory.phase(((TaskPolicy) freshAgent.getCurrentState().ownPolicy()).storage()));
        }
    }

    @Test void liveSocialReplyUsesBackendIntentAndSessionCancellationDiscardsLateOutput() {
        var owner = new ExternalSpeech(java.util.UUID.randomUUID(), agent.executionEpoch());
        when(gateway.infer(any())).thenReturn("{\"speech\":\"Hello there.\"}");
        var event = agent.acknowledge(grouping(1, baseline, 0), runtime.withExternalSpeech(owner));
        assertEquals(SpeechProvenance.Origin.BACKEND_INTENT, event.speechProvenance().origin());
        assertEquals(owner.sessionId(), event.speechProvenance().sessionId());
        var continuing = new AtomicBoolean(true);
        when(gateway.infer(any())).thenAnswer(call -> { continuing.set(false); return "{\"speech\":\"Too late.\"}"; });
        assertNull(agent.acknowledge(grouping(2, baseline.plusSeconds(1), 0),
                runtime.withExternalSpeech(owner).withTaskContinuation(continuing::get)));
        assertFalse(memory().containsKey(TaskMemory.REPLY));
        verify(gateway, times(2)).infer(any());
    }

    private Storage memory() { return ((TaskPolicy) agent.getCurrentState().ownPolicy()).storage(); }
    private Event say(String speech) { return agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", speech), runtime); }
    private Event group(int count) { return agent.acknowledge(grouping(count, baseline.plusMillis(++sample * 100), 0), runtime); }
    private static Event grouping(int count, Instant time, int groups) {
        var json = new JsonObject();
        json.addProperty("humanCount", count);
        json.addProperty("groupCount", groups);
        json.addProperty("singletonCount", groups == 0 ? count : 0);
        json.addProperty("largestGroupSize", groups == 0 ? Math.min(1, count) : 3);
        json.addProperty("ts", time.toString());
        return Event.observation(Event.TYPE_SOCIAL_GROUPING, "sensor", json.toString());
    }
    private static String update(String operation, String spec, String speech) {
        var result = new JsonObject();
        result.addProperty("operation", operation);
        result.add("task", JsonParser.parseString(spec));
        result.add("reply", BehaviourPlan.speechOnly(speech).toJsonObject());
        return result.toString();
    }
}
