package ch.zhaw.prometheus.model.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import com.google.gson.*;
import ch.zhaw.prometheus.agentdefs.core.GenericMultimodalBehaviour;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class TaskLifecycleUnitTest {
    static final String SPEC = """
            {"goal":"Offer an agreed observation while a person is visible; wait during absence","maxActions":4,"rules":[
            {"eventType":"obs.human.presence","field":"humanCount","operator":"gt","value":0,
             "action":"Offer the next observation","effect":"ACT","minConfidence":0.8,"samples":1,"cooldownSeconds":3},
            {"eventType":"obs.human.presence","field":"humanCount","operator":"eq","value":0,
             "action":"Wait for a person to return","effect":"WAIT","minConfidence":0,"samples":1,"cooldownSeconds":3}]}
            """;
    final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    final PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway);
    final Agent agent = new GenericMultimodalBehaviour().createAgent();
    Storage storage() { return ((TaskPolicy) agent.getCurrentState().ownPolicy()).storage(); }
    Event say(String text) { return agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", text), runtime); }
    void activate() {
        when(gateway.infer(any())).thenReturn(GenericMultimodalTaskUnitTest.update("ACTIVATE", SPEC, "The first observation."));
        say("Start the agreed interaction now."); arm(); clearInvocations(gateway);
    }
    void arm() { storage().put(TaskMemory.AFTER, new JsonPrimitive(Instant.now().minusSeconds(1).toString())); }
    Event presence(int count, double confidence) {
        return agent.acknowledge(Event.observation(Event.TYPE_HUMAN_PRESENCE, "sensor",
                "{\"humanCount\":" + count + ",\"avgDetectionConfidence\":" + confidence + ",\"ts\":\"" + Instant.now() + "\"}"), runtime);
    }
    @Test void absenceWaitsWithoutInferenceAndReturnResumesWithoutResettingBudget() {
        activate(); assertNull(presence(0, 0));
        assertEquals("WAITING", TaskMemory.phase(storage()));
        assertEquals(1, storage().get(TaskMemory.ACTIONS).getAsInt());
        assertNull(presence(0, 0)); assertNull(presence(1, .2)); verifyNoInteractions(gateway);
        when(gateway.infer(any())).thenReturn("{\"speech\":\"Here is the next observation.\"}");
        assertNotNull(presence(1, .95));
        assertEquals("RUNNING", TaskMemory.phase(storage()));
        assertEquals(2, storage().get(TaskMemory.ACTIONS).getAsInt()); verify(gateway).infer(any());
    }
    @Test void pauseNeedsExplicitResumeAndCompletedTaskCannotResume() {
        activate(); say("Pause the task."); assertEquals("PAUSED", TaskMemory.phase(storage()));
        presence(1, .95); presence(0, 0); verifyNoInteractions(gateway);
        say("Resume."); assertEquals("RUNNING", TaskMemory.phase(storage()));
        assertEquals(1, storage().get(TaskMemory.ACTIONS).getAsInt()); verifyNoInteractions(gateway);
        say("Stop."); say("Resume."); assertEquals("COMPLETED", TaskMemory.phase(storage()));
        verifyNoInteractions(gateway);
    }
    @Test void invalidAbsenceAndConflictingRulesCannotBeNewAgreements() {
        String impossible = SPEC.replace("\"minConfidence\":0,", "\"minConfidence\":0.8,");
        assertDoesNotThrow(() -> TaskSpec.parse(JsonParser.parseString(impossible)), "Legacy agreements remain readable");
        assertThrows(IllegalArgumentException.class, () -> TaskSpec.parse(JsonParser.parseString(impossible)).executable());
        var task = JsonParser.parseString(SPEC).getAsJsonObject();
        task.getAsJsonArray("rules").add(task.getAsJsonArray("rules").get(0).deepCopy());
        assertThrows(IllegalArgumentException.class, () -> TaskSpec.parse(task).executable());
        when(gateway.infer(any())).thenReturn(GenericMultimodalTaskUnitTest.update("ACTIVATE", impossible, "Started."));
        say("Start."); assertEquals("CONFIGURATION", TaskMemory.phase(storage()));
        assertFalse(storage().containsKey(TaskMemory.SPEC));
    }
    @Test void legacyCompletionRetainsMeaningAndQuestionsDoNotRearmCooldown() {
        var legacy = TaskSpec.parse(JsonParser.parseString(GenericMultimodalTaskUnitTest.SPEC));
        assertEquals(TaskSpec.Effect.ACT, legacy.rules().getFirst().effect());
        assertEquals(TaskSpec.Effect.COMPLETE, legacy.rules().getLast().effect());
        assertEquals(legacy, TaskSpec.parse(legacy.json()));
        activate(); String after = storage().get(TaskMemory.AFTER).getAsString();
        when(gateway.infer(any())).thenReturn(GenericMultimodalTaskUnitTest.update("KEEP", "null", "I can receive supported cues."));
        say("What can you observe?"); assertEquals(after, storage().get(TaskMemory.AFTER).getAsString());
    }
    @Test void monitorReadinessUsesTheSameResponseBoundaryAndHasNoTaskContent() {
        activate(); var status = TaskCueStatus.of(agent, Instant.now());
        assertTrue(status.responseCaptured()); assertEquals(0, status.nextCueInMs());
        assertEquals("RUNNING", status.phase());
        var owner = new ExternalSpeech(java.util.UUID.randomUUID(), agent.executionEpoch());
        when(gateway.infer(any())).thenReturn(GenericMultimodalTaskUnitTest.update("KEEP", "null", "A confirmed response."));
        agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Explain the agreement."), runtime.withExternalSpeech(owner));
        assertFalse(TaskCueStatus.of(agent, Instant.now()).responseCaptured());
        agent.recordExternalSpeech("A confirmed response.", new ch.zhaw.prometheus.model.event.SpeechProvenance(
                ch.zhaw.prometheus.model.event.SpeechProvenance.Origin.NATIVE, owner.sessionId(), owner.epoch(), "segment",
                ch.zhaw.prometheus.model.event.SpeechProvenance.Association.NONE, java.util.List.of(), true));
        assertTrue(TaskCueStatus.of(agent, Instant.now()).responseCaptured());
        String encoded = new Gson().toJson(TaskCueStatus.of(agent, Instant.now()));
        assertFalse(encoded.contains("confirmed response")); assertFalse(encoded.contains("Offer an agreed"));
    }
    @Test void acknowledgmentsIncompleteAndUnrelatedCaptionsDoNotReleaseTheResponseBoundary() {
        var owner = new ExternalSpeech(java.util.UUID.randomUUID(), agent.executionEpoch());
        when(gateway.infer(any())).thenReturn(GenericMultimodalTaskUnitTest.update("ACTIVATE", SPEC, "The first observation is now ready for you."));
        agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Activate"), runtime.withExternalSpeech(owner));
        java.util.function.BiConsumer<String, Boolean> capture = (text, complete) -> agent.recordExternalSpeech(text,
                new ch.zhaw.prometheus.model.event.SpeechProvenance(ch.zhaw.prometheus.model.event.SpeechProvenance.Origin.NATIVE,
                        owner.sessionId(), owner.epoch(), java.util.UUID.randomUUID().toString(),
                        ch.zhaw.prometheus.model.event.SpeechProvenance.Association.NONE, java.util.List.of(), complete));
        capture.accept("Okay, activating.", true); capture.accept("We can discuss something else.", true);
        capture.accept("The first observation is now ready for you.", false);
        assertFalse(TaskCueStatus.of(agent, Instant.now()).responseCaptured());
        capture.accept("The first observation", true);
        assertFalse(TaskCueStatus.of(agent, Instant.now()).responseCaptured());
        capture.accept("is now ready for you.", true);
        assertTrue(TaskCueStatus.of(agent, Instant.now()).responseCaptured());
    }
    @Test void lateActivationAndLateActionAreDiscardedWhenLiveEnds() {
        var allowed = new java.util.concurrent.atomic.AtomicBoolean(true);
        var owner = new ExternalSpeech(java.util.UUID.randomUUID(), agent.executionEpoch());
        var live = runtime.withExternalSpeech(owner).withTaskContinuation(allowed::get);
        storage().put(TaskMemory.DRAFT, TaskSpec.parse(JsonParser.parseString(SPEC)).json());
        when(gateway.infer(any())).thenAnswer(call -> {
            allowed.set(false); return GenericMultimodalTaskUnitTest.update("ACTIVATE", "null", "Started.");
        });
        assertNull(agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Activate"), live));
        assertEquals("CONFIGURATION", TaskMemory.phase(storage())); assertTrue(storage().containsKey(TaskMemory.DRAFT));
        assertFalse(storage().containsKey(TaskMemory.SPEC));
        activate(); allowed.set(true);
        when(gateway.infer(any())).thenAnswer(call -> { allowed.set(false); return "{\"speech\":\"A late action.\"}"; });
        assertNull(agent.acknowledge(Event.observation(Event.TYPE_HUMAN_PRESENCE, "sensor",
                "{\"humanCount\":1,\"avgDetectionConfidence\":0.95,\"ts\":\"" + Instant.now() + "\"}"), live));
        assertEquals("PAUSED", TaskMemory.phase(storage())); assertEquals(1, storage().get(TaskMemory.ACTIONS).getAsInt());
        assertTrue(storage().containsKey(TaskMemory.SPEC));
    }
    @Test void autonomousContinuationRequiresExplicitOptOutAndSurvivesSessionPause() {
        activate(); assertTrue(TaskSpec.parse(storage().get(TaskMemory.SPEC)).sessionBound());
        var independent = storage().get(TaskMemory.SPEC).deepCopy().getAsJsonObject(); independent.addProperty("sessionBound", false);
        storage().put(TaskMemory.SPEC, independent);
        assertFalse(TaskMemory.pauseForSession(storage(), "live_session_ended"));
        assertEquals("RUNNING", TaskMemory.phase(storage()));
    }
    @Test void caughtProviderFailureKeepsTheDraftAndHasAnExplicitContentFreeFailureOutcome() {
        var proposed = TaskSpec.parse(JsonParser.parseString(SPEC)).json(); storage().put(TaskMemory.DRAFT, proposed);
        when(gateway.infer(any())).thenThrow(new IllegalStateException("private transport message"));
        var activity = new ch.zhaw.prometheus.application.AgentActivityService(mock(ch.zhaw.prometheus.logging.AgentMonitorBroadcaster.class));
        try {
            assertNotNull(activity.call(agent.getId(), "acknowledge", true, () -> say("Activate")));
            assertEquals(proposed, storage().get(TaskMemory.DRAFT)); assertFalse(storage().containsKey(TaskMemory.SPEC));
            var outcome = activity.snapshot(agent.getId()).recent().getLast();
            assertEquals("failed", outcome.outcome()); assertEquals("task_provider_failed", outcome.details().get("reason"));
            assertFalse(new Gson().toJson(activity.snapshot(agent.getId())).contains("private"));
        } finally { activity.close(); }
    }
}
