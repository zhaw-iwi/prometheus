package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ch.zhaw.prometheus.model.coordination.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.task.*;

class CoordinationPerceptionUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"CONVERSATION", "COORDINATION", "PREPARATION", "READY", "EXECUTION", "PAUSED", "FINISHING", "UNCERTAIN", "REFLECTION"})
    void freshHandQuestionsAreAnsweredWithoutInferenceOrTaskMutation(String phase) {
        var f = new CoordinationAgentUnitTest();
        if (List.of("COORDINATION", "PREPARATION", "READY").contains(phase)) {
            var draft = f.sensor(phase.equals("PREPARATION"));
            if (phase.equals("COORDINATION")) draft.getAsJsonArray("questions").add("Which greeting?");
            f.propose(draft, f.text);
        } else if (!phase.equals("CONVERSATION")) {
            f.propose(f.sensor(false), f.text); f.start(f.text);
            if (!phase.equals("EXECUTION")) f.storage().put(TaskMemory.PHASE, new JsonPrimitive(phase.equals("REFLECTION") ? "COMPLETED" : phase));
        }
        f.agent.getEventHistory().appendEvent(Event.observation(Event.TYPE_HAND_SIGN, "camera", "{\"sign\":\"scissor\",\"confidence\":0.868,\"ts\":\"" + Instant.now() + "\"}"));
        var actions = TaskMemory.text(f.storage(), TaskMemory.ACTIONS, "0");
        clearInvocations(f.gateway); var answer = f.say("Siehst du mein Handzeichen?", f.text);
        assertTrue(answer.getPayload().contains("scissors")); assertTrue(answer.getPayload().contains("87%"));
        assertEquals(phase, CoordinationMemory.stage(f.storage()));
        assertEquals(actions, TaskMemory.text(f.storage(), TaskMemory.ACTIONS, "0")); verifyNoInteractions(f.gateway);
    }

    @Test void missingExpiredFutureInvalidAndUncertainEvidenceDoNotBecomeCurrentEstimates() {
        var history = new EventHistory(); var now = Instant.parse("2026-11-10T12:00:00Z");
        assertTrue(CoordinationPerception.answer(history, "What hand sign do you see?", now).startsWith("No "));
        for (int age : List.of(15, -3)) {
            var h = new EventHistory(); h.appendEvent(Event.observation(Event.TYPE_HAND_SIGN, "camera",
                    "{\"sign\":\"rock\",\"confidence\":0.9,\"ts\":\"" + now.minusSeconds(age) + "\"}"));
            assertTrue(CoordinationPerception.answer(h, "What hand sign do you see?", now).contains("cannot describe it as current"));
        }
        history.appendEvent(Event.observation(Event.TYPE_FACE_EMOTION, "camera",
                "{\"emotion\":\"happy\",\"confidence\":0.9,\"facePresent\":false,\"ts\":\"" + now + "\"}"));
        assertTrue(CoordinationPerception.answer(history, "Was erkennst du aus meinem Gesichtsausdruck?", now).contains("too uncertain"));
        assertNull(CoordinationPerception.answer(history, "Can you tell a joke when I show a hand sign?", now));
        assertNull(CoordinationPerception.answer(history, "Which hand sign should we use?", now));
    }

    @Test void invalidCosmeticExpressionCannotDiscardAnOrdinaryAnswerButMotionRemainsStrict() {
        var f = new CoordinationAgentUnitTest(); f.propose(f.sensor(false), f.text);
        var raw = JsonParser.parseString(f.update("KEEP", null, "I can discuss the supplied observations.")).getAsJsonObject();
        raw.getAsJsonObject("reply").add("nonVerbal", JsonParser.parseString("{\"facialExpression\":{\"type\":\"invented\",\"intensity\":0.5}}"));
        doReturn(raw.toString()).when(f.gateway).infer(any());
        assertTrue(f.say("What are your capabilities?", f.text).getPayload().contains("discuss the supplied"));
        assertEquals("READY", CoordinationMemory.stage(f.storage()));
        raw.getAsJsonObject("reply").add("motion", JsonParser.parseString("{\"handSign\":\"unsafe\"}"));
        doReturn(raw.toString()).when(f.gateway).infer(any());
        assertTrue(f.say("Explain your capabilities", f.text).getPayload().contains("couldn"));
        assertEquals("READY", CoordinationMemory.stage(f.storage()));
    }

    @Test void validDialogueStartKeepsItsContributionWhenOnlyOptionalExpressionIsInvalid() {
        var f = new CoordinationAgentUnitTest(); f.propose(f.dialogue(), f.text);
        var raw = JsonParser.parseString(f.update("START", null, "Which audience should the invitation address?")).getAsJsonObject();
        raw.getAsJsonObject("reply").add("nonVerbal", JsonParser.parseString("{\"facialExpression\":{\"type\":\"invented\",\"intensity\":0.5}}"));
        doReturn(raw.toString()).when(f.gateway).infer(any());
        var response = f.say("Start the saved proposal now", f.text);
        assertTrue(response.getPayload().contains("Which audience"));
        assertEquals("EXECUTION", CoordinationMemory.stage(f.storage()));
        assertEquals(1, f.storage().get(TaskMemory.ACTIONS).getAsInt());
    }

    @Test void misclassifiedCapabilityQuestionCannotSpendAnActiveDialogueContribution() {
        var f = new CoordinationAgentUnitTest(); f.propose(f.dialogue(), f.text); f.start(f.text);
        var response = f.turn("STEP", null, "What can you do?", f.text);
        assertTrue(response.getPayload().contains("not permitted"));
        assertEquals("EXECUTION", CoordinationMemory.stage(f.storage()));
        assertEquals(1, f.storage().get(TaskMemory.ACTIONS).getAsInt());
    }

    @Test void livePerceptionCannotReplaceAPendingContribution() {
        var f = new CoordinationAgentUnitTest(); f.propose(f.sensor(false), f.live); f.start(f.live);
        var pending = f.storage().get(TaskResponse.KEY).deepCopy();
        f.agent.getEventHistory().appendEvent(Event.observation(Event.TYPE_HAND_SIGN, "camera", "{\"sign\":\"paper\",\"confidence\":0.8,\"ts\":\"" + Instant.now() + "\"}"));
        f.say("What hand sign do you see?", f.live);
        assertEquals(pending, f.storage().get(TaskResponse.KEY));
        assertTrue(f.policy().liveActivityContext().facts().contains("request-time") || f.policy().liveActivityContext().facts().contains("Request-time"));
        assertTrue(f.policy().liveActivityContext().facts().contains("paper"));
    }
}

