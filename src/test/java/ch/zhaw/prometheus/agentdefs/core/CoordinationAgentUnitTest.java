package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.*;
import ch.zhaw.prometheus.model.coordination.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.spi.*;

class CoordinationAgentUnitTest {
    final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    final Agent agent = new CoordinationAgent().createAgent();
    final PolicyRuntime text = new PolicyRuntime(new PromptMessageAssembler(), gateway);
    final ExternalSpeech owner = new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
    final PolicyRuntime live = text.withExternalSpeech(owner);
    CoordinationPolicy policy() { return (CoordinationPolicy) agent.getCurrentState().ownPolicy(); }
    Storage storage() { return policy().storage(); }
    static JsonObject dialogue() {
        return JsonParser.parseString("""
                {"goal":"Draft a two-sentence lab invitation", "humanRole":"Supply audience, demonstration and time",
                "agentRole":"Edit only supplied or accepted details", "success":"The organiser accepts the invitation",
                "questions":[], "checks":[], "dialogueLimit":4, "reactions":null}
                """).getAsJsonObject();
    }
    static JsonObject sensor(boolean prepare) {
        var agreement = dialogue(); agreement.addProperty("goal", "Offer welcome ideas on rock; finish on paper");
        agreement.addProperty("humanRole", "Show agreed signs deliberately"); agreement.addProperty("agentRole", "Offer one short idea per accepted rock cue");
        agreement.addProperty("success", "The participant reports a useful idea");
        agreement.add("reactions", JsonParser.parseString("""
                {"goal":"Offer welcome ideas on rock; finish on paper","maxActions":3,"sessionBound":true,"firstAction":"ON_CUE",
                "rules":[{"eventType":"obs.hand.sign","field":"sign","operator":"eq","value":"rock","action":"Offer one welcome idea",
                "effect":"ACT","minConfidence":0.8,"samples":1,"cooldownSeconds":3},
                {"eventType":"obs.hand.sign","field":"sign","operator":"eq","value":"paper","action":"Finish","effect":"COMPLETE",
                "minConfidence":0.8,"samples":1,"cooldownSeconds":3}]}
                """));
        if (prepare) agreement.add("checks", JsonParser.parseString("""
                [{"eventType":"obs.hand.sign","field":"sign","operator":"eq","value":"rock","minConfidence":0.8},
                 {"eventType":"obs.hand.sign","field":"sign","operator":"eq","value":"paper","minConfidence":0.8}]
                """));
        return agreement;
    }
    static String update(String operation, JsonObject agreement, String words) {
        var result = new JsonObject(); result.addProperty("operation", operation);
        result.add("agreement", agreement == null ? JsonNull.INSTANCE : agreement);
        result.add("reply", BehaviourPlan.speechOnly(words).toJsonObject()); return result.toString();
    }
    Event say(String words, PolicyRuntime runtime) { return agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", words), runtime); }
    Event turn(String op, JsonObject agreement, String user, PolicyRuntime runtime) {
        when(gateway.infer(any())).thenReturn(update(op, agreement, op.equals("PROPOSE") ? "Shall we start this proposal now?" : "One agreed contribution."));
        return say(user, runtime);
    }
    Event sign(String value, double confidence, Instant at, PolicyRuntime runtime) {
        return agent.acknowledge(Event.observation(Event.TYPE_HAND_SIGN, "camera",
                "{\"sign\":\"" + value + "\",\"confidence\":" + confidence + ",\"ts\":\"" + at + "\"}"), runtime);
    }
    void openCueWindow() { storage().put(TaskMemory.AFTER, new JsonPrimitive(Instant.EPOCH.toString())); }
    void propose(JsonObject agreement, PolicyRuntime runtime) { turn("PROPOSE", agreement, "Please propose this task; do not start yet", runtime); }
    void start(PolicyRuntime runtime) { turn("START", null, "I accept the saved proposal. Start it now", runtime); }
    Event hear(String words, boolean complete, Instant start) {
        var event = agent.recordExternalSpeech(words, new SpeechProvenance(SpeechProvenance.Origin.NATIVE,
                owner.sessionId(), owner.epoch(), UUID.randomUUID().toString(), SpeechProvenance.Association.NONE, List.of(), complete));
        org.springframework.test.util.ReflectionTestUtils.setField(event, "createdDate", Instant.now());
        policy().liveSpeechCaptured(event, owner, start, Instant.now(), List.of()); return event;
    }

    @Test void creationRetainsCompletePersonaKnowledgeHumourModalitiesAndOptionalReception() {
        var created = new CoordinationAgent().createInstance(new AgentCreationContext(new PromptMessageAssembler(), gateway));
        assertTrue(BehaviourPlan.fromJson(created.starterEvent().getPayload()).getSpeech().contains("Valerian")); verifyNoInteractions(gateway);
        var original = new GenericMultimodalBehaviourAtSocialSpace().createAgent().getInteractionProfile();
        assertEquals(original.getSupportedObservations(), agent.getInteractionProfile().getSupportedObservations());
        assertEquals(original.getSupportedBehaviourModalities(), agent.getInteractionProfile().getSupportedBehaviourModalities());
        var adapter = new LiveVoicePolicyAdapter(); assertTrue(adapter.supports(agent));
        for (var state : agent.reachableStates()) {
            org.springframework.test.util.ReflectionTestUtils.setField(agent, "currentState", state);
            String instructions = adapter.instructions(agent);
            for (String fact : List.of("Laureline", "Theresa Schmiedel", "Marc Styger", "Jasmin Grassmugg", "Alexandre de Spindler", "Institute of Information Systems"))
                assertTrue(instructions.contains(fact), fact);
            assertTrue(instructions.contains(ValerianPersona.context().strip()));
            assertTrue(instructions.contains(ch.zhaw.prometheus.agentdefs.ConversationalHumour.GUIDANCE.strip()));
            assertEquals(1, instructions.split("CORE LAB REFERENCE", -1).length - 1);
            assertTrue(state.getTotalPolicy().contains(ValerianPersona.context().strip()));
        }
        var reception = new CoordinationAgent().createAgent(true);
        assertTrue(reception.getInteractionProfile().isPresenceControlledLive());
        assertEquals(2000, ((CoordinationPolicy) reception.getCurrentState().ownPolicy()).liveDepartureGraceMs());
    }

    @Test void partialDialogueAgreementPersistsThenRunsReflectsAndRevisesWithoutSensorRules() {
        var partial = dialogue(); partial.addProperty("humanRole", ""); partial.getAsJsonArray("questions").add("Who provides the visit details?");
        propose(partial, text); assertEquals("Coordination agreement", agent.getCurrentState().getName());
        assertEquals("COORDINATION", TaskCueStatus.of(agent, Instant.now()).phase());
        start(text); assertFalse(storage().containsKey(CoordinationMemory.AGREEMENT)); assertEquals(partial, storage().get(CoordinationMemory.DRAFT));
        propose(dialogue(), text); assertEquals("Coordination awaiting acceptance", agent.getCurrentState().getName());
        assertTrue(TaskCueStatus.of(agent, Instant.now()).draft());
        start(text); assertEquals("Coordination execution", agent.getCurrentState().getName()); assertFalse(storage().containsKey(TaskMemory.SPEC));
        turn("STEP", null, "Audience: students. Demonstration: the lab robot. Time: Friday at two", text);
        assertEquals(2, storage().get(TaskMemory.ACTIONS).getAsInt());
        turn("KEEP", null, "Who is Marc?", text); assertEquals(2, storage().get(TaskMemory.ACTIONS).getAsInt());
        turn("COMPLETE", null, "The invitation meets my requirements; the task is complete", text);
        assertEquals("Coordination reflection", agent.getCurrentState().getName());
        turn("REFLECT", null, "Clarifying the time was helpful", text); assertTrue(storage().get(CoordinationMemory.REVIEW).toString().contains("Clarifying the time"));
        var revision = dialogue(); revision.addProperty("agentRole", "Clarify ambiguous references before editing");
        propose(revision, text); assertEquals(dialogue(), storage().get(CoordinationMemory.AGREEMENT));
        assertEquals(revision, storage().get(CoordinationMemory.DRAFT));
    }

    @Test void preparationRequiresDistinctValidObservationsAndCannotBeBypassedByClaimsOrOldFrames() {
        propose(sensor(true), text);
        assertEquals("PREPARATION", TaskCueStatus.of(agent, Instant.now()).phase());
        Instant now = Instant.now(); storage().put(CoordinationMemory.PREPARED_AFTER, new JsonPrimitive(now.minusSeconds(5).toString()));
        start(text); assertEquals("PREPARATION", CoordinationMemory.stage(storage()));
        clearInvocations(gateway);
        sign("rock", 1, now.minusSeconds(30), text); sign("rock", .1, now.minusSeconds(1), text);
        sign("rock", 1, now.plusSeconds(30), text); assertEquals(0, CoordinationMemory.evidence(storage()).size());
        sign("rock", 1, now.minusSeconds(1), text); sign("rock", 1, now.minusSeconds(1), text);
        assertEquals(1, CoordinationMemory.evidence(storage()).size()); verifyNoInteractions(gateway);
        turn("START", null, "I have shown all the signs, start", text); assertFalse(storage().containsKey(CoordinationMemory.AGREEMENT));
        clearInvocations(gateway);
        assertNotNull(sign("paper", 1, now, text)); verifyNoInteractions(gateway);
        assertEquals("READY", CoordinationMemory.stage(storage())); assertFalse(storage().containsKey(TaskMemory.SPEC));
        start(text); assertEquals(0, storage().get(TaskMemory.ACTIONS).getAsInt());
        assertEquals(2, storage().get(CoordinationMemory.ACCEPTED_EVIDENCE).getAsJsonObject().size());
        assertEquals("EXECUTION", CoordinationMemory.stage(storage()));
        assertNull(sign("rock", 1, now.minusSeconds(1), text));
    }

    @Test void sensorExecutionRetainsPauseResumeCompletionAndRevisionIsolation() {
        propose(sensor(false), text); start(text); openCueWindow();
        when(gateway.infer(any())).thenReturn("{\"speech\":\"Welcome to the lab.\"}");
        assertNotNull(sign("rock", 1, Instant.now(), text)); assertEquals(1, storage().get(TaskMemory.ACTIONS).getAsInt());
        var requests = org.mockito.ArgumentCaptor.forClass(InferenceRequest.class);
        verify(gateway, atLeastOnce()).infer(requests.capture());
        assertTrue(requests.getAllValues().getLast().messages().stream().anyMatch(message ->
                message.getContent().contains("Show agreed signs deliberately") && message.getContent().contains("The participant reports a useful idea")));
        assertEquals(1, storage().get(CoordinationMemory.LAST_CUE).getAsJsonObject().get("rule").getAsInt());
        say("Pause the task", text); openCueWindow(); clearInvocations(gateway);
        assertNull(sign("rock", 1, Instant.now(), text)); verifyNoInteractions(gateway);
        say("Resume the task", text); openCueWindow();
        assertNotNull(sign("paper", 1, Instant.now(), text)); assertEquals("REFLECTION", CoordinationMemory.stage(storage()));
        assertEquals("COMPLETION_CUE", storage().get(CoordinationMemory.OUTCOME).getAsString());
        assertEquals(2, storage().get(CoordinationMemory.LAST_CUE).getAsJsonObject().get("rule").getAsInt());
        assertTrue(policy().liveActivityContext().facts().contains("Last accepted cue: rule 2"));
        clearInvocations(gateway); assertNull(sign("rock", 1, Instant.now(), text)); verifyNoInteractions(gateway);
        propose(sensor(false), text); start(text); openCueWindow();
        turn("REPLACE", dialogue(), "Replace this with a dialogue task", text); clearInvocations(gateway);
        assertNull(sign("rock", 1, Instant.now(), text)); verifyNoInteractions(gateway);
        turn("DISCARD", null, "Discard the replacement proposal", text);
        assertEquals("PAUSED", TaskMemory.phase(storage())); assertNotNull(storage().get(TaskMemory.SPEC));
        say("Resume the task", text); assertEquals("EXECUTION", CoordinationMemory.stage(storage()));
    }

    @Test void malformedAgreementAndReplyPreserveDraftAndItsPreparationEvidence() {
        propose(sensor(true), text); var draft = storage().get(CoordinationMemory.DRAFT).deepCopy();
        storage().put(CoordinationMemory.PREPARED_AFTER, new JsonPrimitive(Instant.now().minusSeconds(5).toString()));
        sign("rock", 1, Instant.now(), text); var evidence = storage().get(CoordinationMemory.EVIDENCE).deepCopy();
        var invalid = sensor(true); invalid.getAsJsonArray("checks").get(0).getAsJsonObject().addProperty("field", "executeCode");
        propose(invalid, text); assertEquals(draft, storage().get(CoordinationMemory.DRAFT)); assertEquals(evidence, storage().get(CoordinationMemory.EVIDENCE));
        when(gateway.infer(any())).thenReturn(update("PROPOSE", dialogue(), "Changed").replace("{\"speech\":\"Changed\"}", "{\"motion\":{\"move\":true}}"));
        say("Revise our agreement", text); assertEquals(draft, storage().get(CoordinationMemory.DRAFT));
    }

    @Test void actionBudgetEndsDialogueWithoutClaimingSuccessAndResetClearsCoordination() {
        var plan = dialogue(); plan.addProperty("dialogueLimit", 1); propose(plan, text); start(text);
        assertEquals("REFLECTION", CoordinationMemory.stage(storage())); assertEquals("LIMIT_REACHED", storage().get(CoordinationMemory.OUTCOME).getAsString());
        turn("STEP", null, "Do another task step", text); assertEquals(1, storage().get(TaskMemory.ACTIONS).getAsInt());
        turn("REPLACE", dialogue(), "Replace this with a larger dialogue task", text); turn("DISCARD", null, "Discard the new proposal", text);
        assertEquals("REFLECTION", CoordinationMemory.stage(storage()));
        say("Resume the task", text); assertEquals("REFLECTION", CoordinationMemory.stage(storage()));
        agent.reset(); assertEquals("CONVERSATION", CoordinationMemory.stage(storage()));
        assertFalse(storage().containsKey(CoordinationMemory.AGREEMENT)); assertFalse(storage().containsKey(CoordinationMemory.LOG));
    }

    @Test void liveDialogueAcceptanceUsesTheCurrentCompletedInvitationAndBackendOwnedSteps() {
        propose(dialogue(), live); clearInvocations(gateway);
        say("yes", live); verifyNoInteractions(gateway); assertFalse(TaskMemory.active(storage()));
        hear("Shall I start the proposal now?", false, Instant.now()); assertFalse(policy().liveResolvedControlReply("yes", agent.getEventHistory(), owner));
        hear("Shall I start the proposal now?", true, Instant.now()); assertTrue(policy().liveResolvedControlReply("yes", agent.getEventHistory(), owner));
        var reply = turn("START", null, "yes", live); assertTrue(TaskMemory.active(storage()));
        assertTrue(reply == null || BehaviourPlan.fromJson(reply.getPayload()).getSpeech() == null);
        assertNotNull(policy().liveActivityContext().continuation()); assertEquals("PENDING", TaskResponse.status(storage()));
        assertEquals(1, storage().get(TaskMemory.ACTIONS).getAsInt());
        assertTrue(TaskMemory.pauseForSession(storage(), "live_session_ended"));
        assertEquals("PAUSED", CoordinationMemory.stage(storage()));
    }

    @Test void liveSensorTaskRetainsResponseCaptureGateAndSilentActionsDoNotAwaitCaptions() {
        var agreement = sensor(false); agreement.getAsJsonObject("reactions").getAsJsonArray("rules").get(0).getAsJsonObject().addProperty("action", "Show paper silently");
        propose(agreement, live); start(live); openCueWindow(); clearInvocations(gateway);
        var originalResponse = policy().liveActivityContext().continuation().id();
        say("thanks", live); verifyNoInteractions(gateway);
        assertEquals(originalResponse, policy().liveActivityContext().continuation().id());
        turn("REFLECT", null, "Why are we waiting for a sign?", live);
        assertEquals(originalResponse, policy().liveActivityContext().continuation().id()); clearInvocations(gateway);
        sign("rock", 1, Instant.now(), live); verifyNoInteractions(gateway);
        var created = Instant.parse(storage().get(TaskResponse.KEY).getAsJsonObject().get("created").getAsString());
        var event = hear("The task is armed and waiting for your rock sign", true, created.plusMillis(1));
        var check = TaskResponse.claim(storage(), owner, event, created.plusMillis(1), created.plusMillis(2)).orElseThrow();
        assertTrue(TaskResponse.finish(storage(), owner, check, true, created.plusMillis(3)));
        when(gateway.infer(any())).thenReturn("{\"motion\":{\"handSign\":\"paper\"}}");
        assertNotNull(sign("rock", 1, Instant.now(), live)); assertEquals(1, storage().get(TaskMemory.ACTIONS).getAsInt());
        assertFalse(storage().containsKey(TaskResponse.KEY)); assertNull(policy().liveActivityContext().continuation());
    }

    @Test void cancelledInferenceAndNewLiveEncounterCannotAuthorizeObsoleteWork() {
        propose(sensor(true), text); var draft = storage().get(CoordinationMemory.DRAFT).deepCopy();
        var continuing = new AtomicBoolean(true);
        when(gateway.infer(any())).thenAnswer(call -> { continuing.set(false); return update("PROPOSE", dialogue(), "Changed"); });
        say("Change the proposal", text.withTaskContinuation(continuing::get)); assertEquals(draft, storage().get(CoordinationMemory.DRAFT));
        storage().put(CoordinationMemory.PREPARED_AFTER, new JsonPrimitive(Instant.now().minusSeconds(2).toString()));
        sign("rock", 1, Instant.now(), text); assertEquals(1, CoordinationMemory.evidence(storage()).size());
        policy().beginLiveConversation(agent.getEventHistory()); assertEquals(0, CoordinationMemory.evidence(storage()).size());
        assertEquals(draft, storage().get(CoordinationMemory.DRAFT));
    }

    @Test void inheritedSocialRemarksRemainStableBoundedAndQuietDuringTasks() {
        Instant now = Instant.now();
        for (Instant at : List.of(now.minusSeconds(3), now))
            assertNull(agent.acknowledge(Event.observation(Event.TYPE_SOCIAL_GROUPING, "camera", "{\"humanCount\":1,\"ts\":\"" + at + "\"}"), live));
        var comment = policy().liveActivityContext(); assertNotNull(comment.continuation()); assertTrue(comment.continuation().interruptible());
        verifyNoInteractions(gateway);
        assertNull(agent.acknowledge(Event.observation(Event.TYPE_SOCIAL_GROUPING, "camera", "{\"humanCount\":1,\"ts\":\"" + now.plusMillis(1) + "\"}"), live));
        assertEquals(comment.continuation().id(), policy().liveActivityContext().continuation().id());
        storage().get(SensoryRemarks.PENDING).getAsJsonObject().addProperty("expires", now.minusSeconds(1).toString());
        assertNull(policy().liveActivityContext().continuation());
        propose(dialogue(), text); start(text); clearInvocations(gateway);
        for (Instant at : List.of(now.plusMillis(2), now.plusMillis(3)))
            assertNull(agent.acknowledge(Event.observation(Event.TYPE_SOCIAL_GROUPING, "camera", "{\"humanCount\":2,\"ts\":\"" + at + "\"}"), live));
        verifyNoInteractions(gateway);
    }

    @Test void liveContinuationBindsTextDialogueWhileExplicitAutonomousSensorTasksKeepTheirLifecycle() {
        propose(dialogue(), text); start(text);
        turn("STEP", null, "Continue with the audience I supplied", live);
        assertEquals(owner.sessionId().toString(), storage().get(TaskMemory.SESSION).getAsString());
        assertTrue(TaskMemory.pauseForSession(storage(), "live_session_ended"));
        var autonomous = sensor(false); autonomous.getAsJsonObject("reactions").addProperty("sessionBound", false);
        turn("REPLACE", autonomous, "Replace this with an autonomous sensor task", text); start(text);
        assertFalse(TaskMemory.pauseForSession(storage(), "live_session_ended"));
        assertTrue(TaskMemory.active(storage()));
    }

    @Test void multilingualAgreementBoundsProtectLiveContextBeforeMutation() {
        var bounded = dialogue(); bounded.addProperty("goal", "界".repeat(200));
        bounded.addProperty("humanRole", "界".repeat(160)); bounded.addProperty("agentRole", "界".repeat(160));
        bounded.addProperty("success", "界".repeat(250));
        propose(bounded, live); assertEquals(bounded, storage().get(CoordinationMemory.DRAFT));
        assertDoesNotThrow(() -> policy().liveActivityContext());
        var oversized = bounded.deepCopy(); oversized.addProperty("goal", "界".repeat(300));
        oversized.addProperty("humanRole", "界".repeat(200)); oversized.addProperty("agentRole", "界".repeat(200));
        oversized.addProperty("success", "界".repeat(300));
        propose(oversized, live); assertEquals(bounded, storage().get(CoordinationMemory.DRAFT));
        assertDoesNotThrow(() -> policy().liveActivityContext());
    }
}
