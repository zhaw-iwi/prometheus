package ch.zhaw.prometheus.model.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.google.gson.*;
import ch.zhaw.prometheus.agentdefs.*;
import ch.zhaw.prometheus.agentdefs.core.*;
import ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.*;

class GenericMultimodalTaskUnitTest {
    private final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    private final PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway);
    private final Agent agent = new GenericMultimodalBehaviour().createAgent();
    static final String SPEC = """
            {"goal":"Tell jokes using facial feedback","maxActions":5,"rules":[
            {"eventType":"obs.emotion.face","field":"valence","operator":"lt","value":-0.25,
             "action":"Tell one new short joke","complete":false,"minConfidence":0.6,"samples":2,"cooldownSeconds":5},
            {"eventType":"obs.emotion.face","field":"valence","operator":"gt","value":0.25,
             "action":"Finish the joke task","complete":true,"minConfidence":0.6,"samples":2,"cooldownSeconds":5}]}
            """;
    static String update(String operation, String spec, String speech) {
        var json = new JsonObject(); json.addProperty("operation", operation); json.add("task", JsonParser.parseString(spec));
        json.add("reply", BehaviourPlan.speechOnly(speech).toJsonObject()); return json.toString();
    }
    private Storage memory() { return ((TaskPolicy) agent.getCurrentState().ownPolicy()).storage(); }
    private Event say(String value) { return agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", value), runtime); }
    private void activate() {
        when(gateway.infer(any())).thenReturn(update("ACTIVATE", SPEC, "First joke.")); say("Start the agreed joke game.");
    }
    private void arm(Instant after) { TaskMemory.put(memory(), TaskMemory.AFTER, after.toString()); }
    private Event face(Instant time, double valence, double confidence, PolicyRuntime active) {
        return agent.acknowledge(Event.observation(Event.TYPE_FACE_EMOTION, "sensor", "{\"valence\":" + valence
                + ",\"confidence\":" + confidence + ",\"facePresent\":true,\"ts\":\"" + time + "\"}"), active);
    }
    @Test void profileMatchesExistingAgentAndCreationIsAQuietProviderFreeWelcome() {
        var created = new GenericMultimodalBehaviour().createInstance(new AgentCreationContext(new PromptMessageAssembler(), gateway));
        var expected = new MultimodalBehaviour().createAgent().getInteractionProfile();
        assertEquals(expected.getSupportedObservations(), created.agent().getInteractionProfile().getSupportedObservations());
        assertEquals(expected.getSupportedBehaviourModalities(), created.agent().getInteractionProfile().getSupportedBehaviourModalities());
        assertTrue(created.agent().getInteractionProfile().isCapabilityAwareness());
        assertTrue(new LiveVoicePolicyAdapter().supports(created.agent()));
        assertTrue(new LiveVoicePolicyAdapter().instructions(created.agent()).contains("Conversational task configuration"));
        assertNotNull(created.starterEvent()); verifyNoInteractions(gateway);
    }
    @Test void discussionProposalActivationAndSensorOnlyCompletionUseExplicitStates() {
        when(gateway.infer(any())).thenReturn(update("KEEP", "null", "What would you like to achieve?"),
                update("PROPOSE", SPEC, "I propose one joke, then wait for your face. Shall we start?"),
                update("ACTIVATE", "null", "First joke."), "{\"speech\":\"Another joke.\"}", "{\"speech\":\"The joke task is complete.\"}");
        say("What can you do?"); assertEquals("CONFIGURATION", TaskMemory.phase(memory()));
        say("Let's plan jokes controlled by facial feedback."); assertTrue(memory().containsKey(TaskMemory.DRAFT));
        face(Instant.now(), -0.8, 0.9, runtime); verify(gateway, times(2)).infer(any());
        say("Go ahead."); assertEquals("Generic multimodal execution", agent.getCurrentState().getName());
        Instant base = Instant.now().minusSeconds(2); arm(base);
        assertNull(face(base.plusMillis(100), -0.8, 0.9, runtime));
        var reaction = face(base.plusMillis(200), -0.7, 0.9, runtime);
        assertEquals("Another joke.", BehaviourPlan.fromJson(reaction.getPayload()).getSpeech());
        assertNull(face(Instant.now(), -0.8, 0.9, runtime)); // Cooldown.
        base = Instant.now().minusSeconds(1); arm(base);
        assertNull(face(base.plusMillis(100), 0.8, 0.9, runtime));
        assertNotNull(face(base.plusMillis(200), 0.9, 0.9, runtime));
        assertEquals("Generic multimodal task completed", agent.getCurrentState().getName());
        assertNull(face(Instant.now(), -0.8, 0.9, runtime)); verify(gateway, times(5)).infer(any());
    }
    @Test void staleFutureLowConfidenceDuplicateAndNeutralObservationsCannotTriggerInference() {
        activate(); clearInvocations(gateway); Instant now = Instant.now(); arm(now.minusSeconds(100));
        assertNull(face(now.minusSeconds(30), -0.9, 1, runtime));
        assertNull(face(now.plusSeconds(30), -0.9, 1, runtime));
        assertNull(face(now.minusSeconds(2), -0.9, 0.2, runtime));
        assertNull(face(now.minusSeconds(1), 0, 1, runtime));
        assertNull(face(now, -0.9, 1, runtime));
        assertNull(face(now, -0.9, 1, runtime));
        assertNull(agent.tick(runtime)); verifyNoInteractions(gateway);
    }
    @Test void explicitStopNeedsNoProviderAndResetCannotResumeOldTask() {
        activate(); clearInvocations(gateway); say("Stop telling jokes.");
        assertEquals("COMPLETED", TaskMemory.phase(memory())); verifyNoInteractions(gateway);
        agent.reset(); assertEquals("CONFIGURATION", TaskMemory.phase(memory())); assertFalse(memory().containsKey(TaskMemory.SPEC));
        assertNull(agent.tick(runtime));
    }
    @Test void invalidConfigurationDoesNotPublishItsActivationClaimOrReplaceExistingTask() {
        activate(); String original = memory().get(TaskMemory.SPEC).toString();
        when(gateway.infer(any())).thenReturn(update("ACTIVATE", SPEC.replace("valence", "runCode"), "Activated the unsupported task."));
        var response = say("Please change the task.");
        assertEquals(original, memory().get(TaskMemory.SPEC).toString());
        assertFalse(response.getPayload().contains("Activated the unsupported"));
        assertTrue(BehaviourPlan.fromJson(response.getPayload()).getSpeech().contains("couldn't validate"));
    }
    @Test void currentPerceptionAnswersReceiveFreshCountsAndNoExpiredFacialClaims() {
        agent.acknowledge(Event.observation(Event.TYPE_HUMAN_PRESENCE, "sensor", "{\"humanCount\":1,\"ts\":\"" + Instant.now() + "\"}"), runtime);
        face(Instant.now().minusSeconds(60), -0.9, 1, runtime);
        when(gateway.infer(any())).thenReturn(update("KEEP", "null", "I detect one person in view.")); say("Am I on my own?");
        var request = ArgumentCaptor.forClass(InferenceRequest.class); verify(gateway).infer(request.capture());
        String prompt = request.getValue().messages().stream().map(PromptMessage::getContent).reduce("", String::concat);
        assertTrue(prompt.contains("humanCount")); assertTrue(prompt.contains("obs.emotion.face: unknown"));
        assertFalse(prompt.contains("\"valence\":-0.9"));
    }
    @Test void liveCueWaitsForCompletedNativeSpeechAndProducesAnIntentExactlyOnce() {
        var owner = new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch()); var live = runtime.withExternalSpeech(owner);
        when(gateway.infer(any())).thenReturn(update("ACTIVATE", SPEC, "First joke."));
        agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Start now."), live);
        clearInvocations(gateway); Instant base = Instant.now().minusSeconds(2); arm(base);
        face(base.plusMillis(100), -0.9, 1, live); face(base.plusMillis(200), -0.9, 1, live); verifyNoInteractions(gateway);
        agent.recordExternalSpeech("First joke.", new SpeechProvenance(SpeechProvenance.Origin.NATIVE, owner.sessionId(), owner.epoch(),
                "native-1", SpeechProvenance.Association.NONE, List.of(), true));
        when(gateway.infer(any())).thenReturn("{\"speech\":\"Second joke.\"}");
        assertNull(face(base.plusMillis(300), -0.9, 1, live));
        var result = face(base.plusMillis(400), -0.9, 1, live);
        assertTrue(ConversationProjection.isIntent(result));
        assertNull(agent.generate(live)); verify(gateway, times(1)).infer(any());
    }
    @Test void reconfigurationPausesOldRulesAndRejectsOversizedOrUnknownShapes() {
        activate(); when(gateway.infer(any())).thenReturn(update("PROPOSE", SPEC, "Here is the revised plan.")); say("Let's change the plan.");
        assertEquals("CONFIGURATION", TaskMemory.phase(memory())); clearInvocations(gateway);
        face(Instant.now(), -0.9, 1, runtime); verifyNoInteractions(gateway);
        assertThrows(IllegalArgumentException.class, () -> TaskSpec.parse(JsonParser.parseString(SPEC.replace("\"maxActions\":5", "\"maxActions\":500"))));
        assertThrows(IllegalArgumentException.class, () -> TaskMemory.plan(JsonParser.parseString("{\"display\":{}}")));
        assertThrows(IllegalArgumentException.class, () -> TaskMemory.plan(JsonParser.parseString("{\"motion\":{\"move\":true}}")));
    }
    @Test void actionLimitEndsTaskAndProviderFailurePausesWithoutSensorRetries() {
        when(gateway.infer(any())).thenReturn(update("ACTIVATE", SPEC.replace("\"maxActions\":5", "\"maxActions\":1"), "Only joke."),
                "{\"speech\":\"The agreed limit is reached.\"}");
        say("Start with a limit of one joke."); Instant base = Instant.now().minusSeconds(2); arm(base);
        face(base.plusMillis(100), -0.9, 1, runtime); face(base.plusMillis(200), -0.9, 1, runtime);
        assertEquals("COMPLETED", TaskMemory.phase(memory())); assertEquals(1, memory().get(TaskMemory.ACTIONS).getAsInt());
        activate(); base = Instant.now().minusSeconds(1); arm(base);
        when(gateway.infer(any())).thenThrow(new IllegalStateException("private provider message")); clearInvocations(gateway);
        face(base.plusMillis(100), -0.9, 1, runtime);
        Event failed = face(base.plusMillis(200), -0.9, 1, runtime);
        assertEquals("CONFIGURATION", TaskMemory.phase(memory())); assertFalse(failed.getPayload().contains("private provider"));
        face(Instant.now(), -0.9, 1, runtime); verify(gateway, times(1)).infer(any());
    }
    @Test void forecastRulesUseActualNestedPayloadAndOutputValidationBoundsPersistedJson() {
        String forecast = SPEC.replace("obs.emotion.face", "obs.weather.forecast").replace("\"valence\"", "\"days.0.temperature_max_c\"")
                .replace("\"minConfidence\":0.6", "\"minConfidence\":0").replace("\"samples\":2", "\"samples\":1");
        when(gateway.infer(any())).thenReturn(update("ACTIVATE", forecast, "Watching the forecast."), "{\"speech\":\"Threshold reached.\"}");
        say("Start the agreed weather task."); arm(Instant.EPOCH);
        assertNotNull(agent.acknowledge(Event.observation(Event.TYPE_WEATHER_FORECAST, "sensor",
                "{\"days\":[{\"temperature_max_c\":20}],\"ts\":\"" + Instant.now() + "\"}"), runtime));
        assertEquals("COMPLETED", TaskMemory.phase(memory()));
        assertThrows(IllegalArgumentException.class, () -> TaskMemory.plan(JsonParser.parseString("{\"speech\":\"" + "'".repeat(800) + "\"}")));
        assertThrows(IllegalArgumentException.class, () -> TaskMemory.plan(JsonParser.parseString("{\"nonVerbal\":{\"facialExpression\":{\"type\":\"gentleSmile\",\"intensity\":2}}}")));
    }
}
