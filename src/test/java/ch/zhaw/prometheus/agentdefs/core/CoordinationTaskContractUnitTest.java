package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.coordination.*;
import ch.zhaw.prometheus.model.task.TaskMemory;
import ch.zhaw.prometheus.model.task.TaskResponse;
import ch.zhaw.prometheus.model.event.SpeechProvenance;

class CoordinationTaskContractUnitTest {
    @Test void nativeProposalNeedsNoGeneratedNarrationAndEverySpeechIntentRemainsBounded() {
        var f = new CoordinationAgentUnitTest(); var proposal = f.sensor(true);
        var update = JsonParser.parseString(f.update("PROPOSE", proposal, "")).getAsJsonObject();
        update.add("reply", new JsonObject());
        when(f.gateway.infer(any())).thenReturn(update.toString());
        f.say("Propose this recognition task, but do not start", f.live);
        assertEquals(proposal, f.storage().get(CoordinationMemory.DRAFT));
        assertEquals("PREPARATION", CoordinationMemory.stage(f.storage()));
        assertTrue(f.storage().get("core.task.response").getAsJsonObject().get("supportOnly").getAsBoolean());
        var request = org.mockito.ArgumentCaptor.forClass(ch.zhaw.prometheus.spi.InferenceRequest.class);
        verify(f.gateway).infer(request.capture());
        String prompt = request.getValue().messages().stream().map(m -> m.getContent()).collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(prompt.contains("For EVERY operation, reply.speech has at most 350 characters"));
        assertTrue(prompt.contains("For PROPOSE and REPLACE, omit reply.speech"));
        when(f.gateway.infer(any())).thenReturn(f.update("PROPOSE", f.dialogue(), "x".repeat(351)));
        f.say("Propose another activity", f.live);
        assertEquals(proposal, f.storage().get(CoordinationMemory.DRAFT), "Oversized generated wording cannot replace the draft");
        assertFalse(f.storage().containsKey(CoordinationMemory.AGREEMENT));
    }
    @Test void openCountClarificationRefinesTheProposalWithoutReplacingOrStartingIt() {
        var f = new CoordinationAgentUnitTest();
        var partial = fixtures.behaviour.HandSequenceFixture.agreement(fixtures.behaviour.HandSequenceFixture.counts("right", 1, 2, 3));
        partial.getAsJsonArray("questions").add("Is Ready the only acknowledgement?");
        f.propose(partial, f.text);
        var resolved = partial.deepCopy(); resolved.add("questions", new JsonArray());
        resolved.addProperty("success", "The human judges one Ready response after the accepted full sequence.");
        clearInvocations(f.gateway);
        f.turn("PROPOSE", resolved, "Only say Ready once. I judge your response. Keep the counts and preparation.", f.text);
        var request = org.mockito.ArgumentCaptor.forClass(ch.zhaw.prometheus.spi.InferenceRequest.class);
        verify(f.gateway).infer(request.capture());
        String policy = request.getValue().messages().stream().map(m -> m.getContent()).collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(policy.contains("An INCOMPLETE draft instead uses PROPOSE"));
        assertFalse(policy.contains("copy all unchanged fields VERBATIM from the current draft/accepted agreement"));
        String current = request.getValue().messages().getLast().getContent();
        var data = JsonParser.parseString(current.substring(current.lastIndexOf('\n') + 1)).getAsJsonObject();
        assertEquals(partial.get("questions"), data.get("unansweredProposalQuestions"));
        assertFalse(data.has("protectedRevisionTerms"));
        assertFalse(current.contains("copy protectedRevisionTerms exactly"), "A partial proposal must not receive the conflicting complete-revision instruction");
        assertEquals(resolved, f.storage().get(CoordinationMemory.DRAFT));
        assertTrue(CoordinationTaskContract.currentTurn(f.storage(), "Make the response shorter")
                .contains("copy protectedRevisionTerms exactly"), "Completed proposal terms remain protected");
        assertFalse(f.storage().containsKey(CoordinationMemory.AGREEMENT));
        assertEquals("PREPARATION", CoordinationMemory.stage(f.storage()));
        assertEquals("0", TaskMemory.text(f.storage(), TaskMemory.ACTIONS, "0"));
        f.start(f.text);
        assertFalse(f.storage().containsKey(CoordinationMemory.AGREEMENT));
        assertEquals(resolved, f.storage().get(CoordinationMemory.DRAFT));
    }
    @Test void feedbackContextSelectsTheDeliveredContributionInsteadOfLaterHelpOrUnheardDrafts() {
        var f = new CoordinationAgentUnitTest(); f.propose(f.dialogue(), f.live); f.start(f.live);
        String prefix = "Earlier preface. ", question = "What has keys, space but no room, and enter but no way inside?";
        f.agent.recordExternalSpeech(prefix + question, new SpeechProvenance(SpeechProvenance.Origin.NATIVE,
                f.owner.sessionId(), f.owner.epoch(), "question", SpeechProvenance.Association.CONFIRMED, java.util.List.of(), true, true));
        f.agent.recordExternalSpeech("The task is active.", new SpeechProvenance(SpeechProvenance.Origin.NATIVE,
                f.owner.sessionId(), f.owner.epoch(), "help", SpeechProvenance.Association.NONE, java.util.List.of(), true, true));
        f.agent.recordExternalSpeech("A different unheard question?", new SpeechProvenance(SpeechProvenance.Origin.NATIVE,
                f.owner.sessionId(), f.owner.epoch(), "unheard", SpeechProvenance.Association.NONE, java.util.List.of(), true, false));
        var response = f.storage().get(TaskResponse.KEY).getAsJsonObject().deepCopy();
        response.addProperty("status", "CAPTURED"); response.addProperty("segment", "question");
        response.addProperty("speechOffset", prefix.length()); response.addProperty("played", true);
        f.storage().put(TaskResponse.KEY, response);
        String prompt = CoordinationTaskContract.currentTurn(f.storage(), "My answer is a piano", f.agent.getEventHistory(), f.owner);
        var data = JsonParser.parseString(prompt.substring(prompt.lastIndexOf('\n') + 1)).getAsJsonObject();
        assertEquals(question, data.get("previousHeardContribution").getAsString());
        assertTrue(prompt.contains("ALL its defining clues"));
        assertEquals(response, f.storage().get(TaskResponse.KEY));

        response.addProperty("segment", "unheard"); f.storage().put(TaskResponse.KEY, response);
        assertFalse(CoordinationTaskContract.currentTurn(f.storage(), "My answer is a piano", f.agent.getEventHistory(), f.owner)
                .contains("previousHeardContribution"));
        response.addProperty("segment", "question"); response.addProperty("revision", -1); f.storage().put(TaskResponse.KEY, response);
        assertFalse(CoordinationTaskContract.currentTurn(f.storage(), "My answer is a piano", f.agent.getEventHistory(), f.owner)
                .contains("previousHeardContribution"));
    }
    @Test void currentTurnAndRemainingBudgetAreExplicitEvenWhenNativeAnswerPrecedesItsUserHistoryEntry() {
        var f = new CoordinationAgentUnitTest(); var proposal = f.dialogue(); proposal.addProperty("dialogueLimit", 2);
        f.propose(proposal, f.text); f.start(f.text);
        f.agent.recordExternalSpeech("I see scissor.", new ch.zhaw.prometheus.model.event.SpeechProvenance(
                ch.zhaw.prometheus.model.event.SpeechProvenance.Origin.NATIVE, f.owner.sessionId(), f.owner.epoch(), "early-answer",
                ch.zhaw.prometheus.model.event.SpeechProvenance.Association.NONE, java.util.List.of(), true));
        f.agent.getEventHistory().appendEvent(ch.zhaw.prometheus.model.event.Event.observation(
                ch.zhaw.prometheus.model.event.Event.TYPE_USER_UTTERANCE, "user", "What hand sign do you see now?"));
        clearInvocations(f.gateway);
        f.turn("STEP", null, "My answer is a piano", f.text);
        var request = org.mockito.ArgumentCaptor.forClass(ch.zhaw.prometheus.spi.InferenceRequest.class);
        verify(f.gateway).infer(request.capture());
        var messages = request.getValue().messages();
        assertTrue(messages.stream().anyMatch(m -> m.getContent().equals("I see scissor.")), "Retain native history");
        assertTrue(messages.stream().anyMatch(m -> m.getContent().equals("What hand sign do you see now?")), "Retain earlier user history");
        String current = messages.getLast().getContent();
        var data = JsonParser.parseString(current.substring(current.lastIndexOf('\n') + 1)).getAsJsonObject();
        assertEquals("My answer is a piano", data.get("currentUserTurn").getAsString());
        assertEquals(1, data.get("remainingDialogueContributions").getAsInt());
        assertEquals(2, f.storage().get(TaskMemory.ACTIONS).getAsInt());
    }
    @Test void sensorAnswerLoopIsRejectedWithoutSavingOrStartingIt() {
        var f = new CoordinationAgentUnitTest(); var proposal = f.sensor(false);
        proposal.getAsJsonObject("reactions").getAsJsonArray("rules").get(0).getAsJsonObject()
                .addProperty("action", "Ask a riddle and invite the person to answer");
        var response = f.turn("PROPOSE", proposal, "Propose a guessing activity using rock", f.text);
        assertEquals("CONVERSATION", CoordinationMemory.stage(f.storage()));
        assertFalse(f.storage().containsKey(CoordinationMemory.DRAFT));
        assertTrue(response.getPayload().contains("bounded dialogue alternative"));
        assertFalse(f.storage().containsKey(TaskMemory.ACTIONS));
    }

    @Test void legacySensorAnswerAttemptGetsSpecificRecoveryWithoutSpendingBudget() {
        var f = new CoordinationAgentUnitTest(); f.propose(f.sensor(false), f.text); f.start(f.text);
        var agreement = f.storage().get(CoordinationMemory.AGREEMENT).deepCopy();
        var response = f.turn("STEP", null, "My answer is a candle", f.text);
        assertTrue(response.getPayload().contains("output-only sensor reaction"));
        assertEquals(agreement, f.storage().get(CoordinationMemory.AGREEMENT));
        assertEquals(0, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        assertEquals("EXECUTION", CoordinationMemory.stage(f.storage()));
    }

    @Test void proposalExposesFiniteBudgetAndQuestionFeedbackNeedsEnoughContributions() {
        var f = new CoordinationAgentUnitTest(); var proposal = f.dialogue();
        proposal.addProperty("goal", "Try a short riddle together");
        proposal.addProperty("humanRole", "Answer the question"); proposal.addProperty("agentRole", "Ask a riddle and give feedback");
        proposal.addProperty("dialogueLimit", 1);
        var feedbackOnly = proposal.deepCopy(); feedbackOnly.addProperty("agentRole", "Give feedback on the supplied answer");
        assertFalse(CoordinationTaskContract.insufficientDialogueBudget(CoordinationAgreement.parse(feedbackOnly)));
        var rejected = f.turn("PROPOSE", proposal, "Propose a question and feedback exchange", f.text);
        assertTrue(rejected.getPayload().contains("proposed limits"));
        assertFalse(f.storage().containsKey(CoordinationMemory.DRAFT));
        proposal.addProperty("dialogueLimit", 2);
        var saved = f.turn("PROPOSE", proposal, "Propose the supported exchange with two contributions", f.text);
        assertTrue(saved.getPayload().contains("Limit: 2 agent contributions"));
        doReturn(f.update("START", null, "What becomes shorter while it works?")).when(f.gateway).infer(any());
        var question = f.say("Start", f.text);
        assertFalse(question.getPayload().contains("candle"));
        // Scripted contributions protect runtime control; generated quality remains a provider gate.
        assertEquals("EXECUTION", CoordinationMemory.stage(f.storage()));
        doReturn(f.update("STEP", null, "A candle fits that riddle.")).when(f.gateway).infer(any());
        var feedback = f.say("My answer is a candle", f.text);
        assertTrue(feedback.getPayload().contains("candle"));
        assertEquals(2, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        assertEquals("REFLECTION", CoordinationMemory.stage(f.storage()));
        assertEquals("LIMIT_REACHED", f.storage().get(CoordinationMemory.OUTCOME).getAsString());
        assertFalse(question.getPayload().isBlank()); assertFalse(feedback.getPayload().isBlank());
    }

    @Test void explicitLiteralAndOutputOnlyReactionRemainSupportedAndReplacementRequiresConsent() {
        var f = new CoordinationAgentUnitTest(); var sensor = f.sensor(false);
        sensor.getAsJsonObject("reactions").getAsJsonArray("rules").get(0).getAsJsonObject()
                .addProperty("action", "Say exactly: Please wait for an answer.");
        f.propose(sensor, f.text); assertEquals("READY", CoordinationMemory.stage(f.storage())); f.start(f.text);
        f.turn("PROPOSE", f.dialogue(), "Make it interactive", f.text);
        assertFalse(f.storage().containsKey(CoordinationMemory.DRAFT));
        f.turn("REPLACE", f.dialogue(), "Explicitly replace this with a dialogue task", f.text);
        assertEquals("READY", CoordinationMemory.stage(f.storage()));
        assertFalse(ch.zhaw.prometheus.model.task.TaskMemory.active(f.storage()));
        assertEquals(0, f.storage().get(TaskMemory.ACTIONS).getAsInt());
    }
}
