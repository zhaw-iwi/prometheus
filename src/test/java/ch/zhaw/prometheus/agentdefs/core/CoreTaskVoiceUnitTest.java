package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class CoreTaskVoiceUnitTest {
    static final String SPEC = """
            {"goal":"On rock show paper silently","maxActions":2,"firstAction":"ON_CUE","sessionBound":true,
            "rules":[{"eventType":"obs.hand.sign","field":"sign","operator":"eq","value":"rock",
            "action":"Show paper silently","effect":"ACT","minConfidence":0.8,"samples":1,"cooldownSeconds":3}]}
            """;
    final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    final Agent agent = new GenericMultimodalBehaviour().createAgent();
    final ExternalSpeech owner = new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
    final PolicyRuntime text = new PolicyRuntime(new PromptMessageAssembler(), gateway), live = text.withExternalSpeech(owner);
    CoreTaskPolicy policy() { return (CoreTaskPolicy) agent.getCurrentState().ownPolicy(); }
    Storage storage() { return policy().storage(); }
    Event say(String value, PolicyRuntime runtime) { return agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", value), runtime); }
    String update(String op, String spec) { return "{\"operation\":\""+op+"\",\"task\":"+spec+",\"reply\":{\"speech\":\"Backend authored words\"}}"; }
    Event hear(String words, boolean complete, Instant start) {
        var event = agent.recordExternalSpeech(words, new SpeechProvenance(SpeechProvenance.Origin.NATIVE,
                owner.sessionId(), owner.epoch(), UUID.randomUUID().toString(), SpeechProvenance.Association.NONE, List.of(), complete));
        org.springframework.test.util.ReflectionTestUtils.setField(event, "createdDate", Instant.now());
        policy().liveSpeechCaptured(event, owner, start, Instant.now(), List.of()); return event;
    }
    void propose() {
        when(gateway.infer(any())).thenReturn(update("PROPOSE", SPEC)); say("Please propose that task, do not start yet", live);
        storage().put(CoreTaskVoice.INVITATION, new JsonPrimitive(Instant.EPOCH.toString()));
    }
    void arm() {
        when(gateway.infer(any())).thenReturn(update("ACTIVATE", SPEC)); say("Start this fully specified task now", live);
        assertFalse(TaskCueStatus.of(agent, Instant.now()).responseCaptured());
        var responseId = policy().liveActivityContext().continuation().id();
        clearInvocations(gateway); say(", please", live); verifyNoInteractions(gateway);
        assertEquals(responseId, policy().liveActivityContext().continuation().id(), "A polite tail must preserve the pending confirmation");
        var created = Instant.parse(storage().get(TaskResponse.KEY).getAsJsonObject().get("created").getAsString());
        var event = hear("The task is armed and waiting for your next rock sign.", true, created.plusMillis(1));
        var check = TaskResponse.claim(storage(), owner, event, created.plusMillis(1), created.plusMillis(2)).orElseThrow();
        assertTrue(TaskResponse.finish(storage(), owner, check, true, created.plusMillis(3)));
        assertTrue(TaskCueStatus.of(agent, Instant.now()).responseCaptured());
        storage().put(TaskMemory.AFTER, new JsonPrimitive(Instant.EPOCH.toString()));
    }
    Event sign() { return agent.acknowledge(Event.observation(Event.TYPE_HAND_SIGN, "sensor",
            "{\"sign\":\"rock\",\"confidence\":1,\"ts\":\""+Instant.now()+"\"}"), live); }
    @Test void proposalAndActivationExposeFactsWithoutBackendSpeechAndTextRetainsSpeech() {
        propose(); assertEquals("CONFIGURATION", TaskMemory.phase(storage()));
        assertTrue(policy().liveActivityContext().facts().contains("NOT ACTIVE"));
        assertFalse(agent.getEventHistory().toList().stream().anyMatch(ConversationProjection::isIntent));
        arm(); assertEquals(0, storage().get(TaskMemory.ACTIONS).getAsInt());
        assertTrue(policy().liveActivityContext().facts().contains("zero actions performed"));
        assertFalse(policy().voiceInstructions().contains("wait for its confirmed announcement"));
        assertTrue(policy().voiceInstructions().contains(ValerianCorePrompts.SIRA_NAME));
        assertDoesNotThrow(() -> new ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter().instructions(agent));
        when(gateway.infer(any())).thenReturn(update("KEEP", "null"));
        assertEquals("Backend authored words", BehaviourPlan.fromJson(say("What can you do?", text).getPayload()).getSpeech());
    }
    @Test void bareYesRequiresCurrentCompletedInvitationAndCannotActivateAnotherTask() {
        propose(); clearInvocations(gateway);
        say("yes", live); assertEquals("CONFIGURATION", TaskMemory.phase(storage())); verifyNoInteractions(gateway);
        Instant before = Instant.parse(storage().get(CoreTaskVoice.INVITATION).getAsString()).minusSeconds(1);
        hear("Shall I start the proposed task?", true, before);
        assertFalse(policy().liveResolvedControlReply("yes", agent.getEventHistory(), owner));
        hear("Shall I start the proposed task?", false, Instant.now());
        assertFalse(policy().liveResolvedControlReply("yes", agent.getEventHistory(), owner));
        hear("Want me to start it?", true, Instant.now());
        assertTrue(policy().liveResolvedControlReply("yes", agent.getEventHistory(), owner));
        hear("Want me to start that?", true, Instant.now());
        assertTrue(policy().liveResolvedControlReply("yes", agent.getEventHistory(), owner));
        hear("It ends after that. Activate it now?", true, Instant.now());
        assertTrue(policy().liveResolvedControlReply("yes", agent.getEventHistory(), owner));
        hear("Want me to activate that and start waiting now?", true, Instant.now());
        assertTrue(policy().liveResolvedControlReply("yes", agent.getEventHistory(), owner));
        hear("The proposal waits for a cue. If you like, we can activate it now so it waits for that cue.", true, Instant.now());
        assertTrue(policy().liveResolvedControlReply("yes", agent.getEventHistory(), owner));
        when(gateway.infer(any())).thenReturn(update("ACTIVATE", "null")); say("Yes", live);
        assertEquals("RUNNING", TaskMemory.phase(storage())); assertFalse(storage().containsKey(TaskMemory.DRAFT));
        String original = storage().get(TaskMemory.SPEC).toString(); clearInvocations(gateway);
        say("Okay", live); say("Thanks", live);
        assertEquals(original, storage().get(TaskMemory.SPEC).toString()); assertEquals("RUNNING", TaskMemory.phase(storage())); verifyNoInteractions(gateway);
    }
    @Test void completedQuestionOnlyAdmitsInterpretationAndCannotAuthorizeDifferentOperations() {
        propose(); var draft = storage().get(TaskMemory.DRAFT).deepCopy();
        for (String question : List.of("Would you like coffee?", "We could talk about coffee.", "Do you want to change the plan before I start it?",
                "Want me to start this when you say so?")) {
            hear(question, true, Instant.now());
            assertTrue(policy().liveResolvedControlReply("yes", agent.getEventHistory(), owner));
            doAnswer(call -> {
                var request = (ch.zhaw.prometheus.spi.InferenceRequest) call.getArgument(0);
                assertTrue(request.messages().stream().anyMatch(message -> message.getContent().contains(question)
                        && message.getContent().contains("NOT consent")));
                return update("KEEP", "null");
            }).when(gateway).infer(any());
            say("Yes", live); assertEquals("CONFIGURATION", TaskMemory.phase(storage()));
            assertEquals(draft, storage().get(TaskMemory.DRAFT)); assertFalse(storage().containsKey(TaskMemory.SPEC));
        }
        hear("Start this plan?", true, Instant.now());
        doReturn(update("ACTIVATE", SPEC.replace("maxActions\":2", "maxActions\":5"))).when(gateway).infer(any());
        say("yes", live); assertEquals("CONFIGURATION", TaskMemory.phase(storage()));
        assertEquals(draft, storage().get(TaskMemory.DRAFT));
        hear("Want me to start this when you say so?", true, Instant.now());
        doReturn(update("ACTIVATE", "null")).when(gateway).infer(any());
        say("yes", live); assertEquals("CONFIGURATION", TaskMemory.phase(storage()));
        assertEquals(draft, storage().get(TaskMemory.DRAFT));
        hear("Shall I start the proposed task?", true, Instant.now());
        assertFalse(policy().liveResolvedControlReply("yes", agent.getEventHistory(), new ExternalSpeech(UUID.randomUUID(), owner.epoch())));
    }
    @Test void invitationQuestionMustStartAfterProposalEvenWhenItsPreambleStartedEarlier() {
        propose(); Instant boundary = Instant.parse(storage().get(CoreTaskVoice.INVITATION).getAsString());
        var event = hear("Here is the plan. Activate it now?", true, boundary.minusSeconds(2));
        assertFalse(storage().containsKey(CoreTaskVoice.SPOKEN_PROMPT));
        assertTrue(CoreTaskVoice.capturedPrompt(storage(), event, owner, boundary.minusSeconds(2), List.of(
                new LiveSpeechPart("Here is the plan. ", boundary.minusSeconds(2)),
                new LiveSpeechPart("Activate it now?", boundary.plusMillis(1)))));
        storage().remove(CoreTaskVoice.SPOKEN_PROMPT);
        assertFalse(CoreTaskVoice.capturedPrompt(storage(), event, owner, boundary.minusSeconds(2), List.of(
                new LiveSpeechPart("Here is the plan. Activate", boundary.minusSeconds(1)),
                new LiveSpeechPart(" it now?", boundary.plusMillis(1)))));
    }
    @Test void repeatedSilentActionsDoNotWaitForSpeechAndCompletionRetainsTheAgreement() {
        arm(); when(gateway.infer(any())).thenReturn("{\"motion\":{\"handSign\":\"paper\"}}");
        var first = BehaviourPlan.fromJson(sign().getPayload()); assertNull(first.getSpeech()); assertNotNull(first.getMotion());
        assertEquals(1, storage().get(TaskMemory.ACTIONS).getAsInt()); assertFalse(storage().containsKey(TaskResponse.KEY));
        assertNull(policy().liveActivityContext().continuation());
        storage().put(TaskMemory.AFTER, new JsonPrimitive(Instant.EPOCH.toString())); sign();
        assertEquals("COMPLETED", TaskMemory.phase(storage())); assertTrue(storage().containsKey(TaskMemory.SPEC));
        assertTrue(policy().liveActivityContext().facts().contains("final nonverbal action"));
        agent.reset(); assertFalse(storage().containsKey(CoreTaskVoice.RESPONSE)); assertFalse(storage().containsKey(TaskMemory.SPEC));
    }
    @Test void spokenActionsRequireConfirmedCaptureAndFailuresPreserveRecoverableState() {
        arm(); when(gateway.infer(any())).thenReturn("{\"speech\":\"Tell one short joke\"}"); sign();
        assertEquals("PENDING", TaskResponse.status(storage())); clearInvocations(gateway);
        storage().put(TaskMemory.AFTER, new JsonPrimitive(Instant.EPOCH.toString())); assertNull(sign()); verifyNoInteractions(gateway);
        say("pause", live); assertEquals("PAUSED", TaskMemory.phase(storage()));
        say("resume", live); assertEquals("RUNNING", TaskMemory.phase(storage()));
        when(gateway.infer(any())).thenThrow(new IllegalStateException("private provider error")); say("Change the task", live);
        assertTrue(storage().containsKey(TaskMemory.SPEC)); assertFalse(policy().liveActivityContext().facts().contains("private provider"));
        say("stop", live); assertEquals("COMPLETED", TaskMemory.phase(storage()));
    }
    @Test void voiceContextRetainsExecutableRulesWithinByteBudgetAndExpiredOccasionsStayQuiet() {
        arm(); var spec = JsonParser.parseString(SPEC).getAsJsonObject(); spec.addProperty("goal", "🙂".repeat(190));
        storage().put(TaskMemory.SPEC, spec); assertTrue(CoreTaskVoice.bytes(policy().liveActivityContext().facts()) <= 4000);
        storage().get(CoreTaskVoice.RESPONSE).getAsJsonObject().addProperty("expires", Instant.EPOCH.toString());
        assertNull(policy().liveActivityContext().continuation());
        assertTrue(policy().liveActivityContext().facts().contains("obs.hand.sign.sign"));
    }
    @Test void explicitlyAutonomousTasksAndExplicitTextResumeDoNotWaitForANonexistentLiveCaption() {
        when(gateway.infer(any())).thenReturn(update("ACTIVATE", SPEC.replace("\"sessionBound\":true", "\"sessionBound\":false")));
        say("Run autonomously after our voice session", live);
        storage().put(TaskMemory.AFTER, new JsonPrimitive(Instant.EPOCH.toString()));
        when(gateway.infer(any())).thenReturn("{\"motion\":{\"handSign\":\"paper\"}}");
        assertNotNull(agent.acknowledge(Event.observation(Event.TYPE_HAND_SIGN, "sensor",
                "{\"sign\":\"rock\",\"confidence\":1,\"ts\":\""+Instant.now()+"\"}"), text));
        assertFalse(storage().containsKey(TaskResponse.KEY));
        say("pause", text); say("resume", text); assertEquals("RUNNING", TaskMemory.phase(storage()));
        assertFalse(TaskMemory.pauseForSession(storage(), "live_closed"));
        assertTrue(TaskMemory.pauseForResponse(storage(), "response_association_uncertain"));
        assertEquals("PAUSED", TaskMemory.phase(storage())); assertTrue(storage().containsKey(TaskMemory.SPEC));
    }
    @Test void explicitWaitCannotBeReinterpretedAsAnImmediateAction() {
        when(gateway.infer(any())).thenReturn(update("ACTIVATE", SPEC.replace("ON_CUE", "IMMEDIATE")));
        say("Wait for my sign before acting", live);
        assertEquals("CONFIGURATION", TaskMemory.phase(storage())); assertFalse(storage().containsKey(TaskMemory.SPEC));
        assertTrue(policy().liveActivityContext().facts().contains("could not be validated"));
        say("Do not wait for my sign; start the task now", live);
        assertEquals("RUNNING", TaskMemory.phase(storage()));
    }
    @Test void providerCompletionOnlyProposalCannotPromiseAnActionItCannotExecute() {
        when(gateway.infer(any())).thenReturn(update("PROPOSE", SPEC.replace("\"ACT\"", "\"COMPLETE\"")));
        say("Propose a task that waits for rock, shows paper and finishes", live);
        assertFalse(storage().containsKey(TaskMemory.DRAFT)); assertEquals("CONFIGURATION", TaskMemory.phase(storage()));
    }
}
