package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.coordination.*;
import ch.zhaw.prometheus.model.task.TaskMemory;

class CoordinationTaskContractUnitTest {
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
