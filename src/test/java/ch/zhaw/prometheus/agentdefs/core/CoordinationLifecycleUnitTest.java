package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.task.*;

class CoordinationLifecycleUnitTest {
    final CoordinationAgentUnitTest f = new CoordinationAgentUnitTest();
    @ParameterizedTest @ValueSource(strings = {"CONVERSATION", "COORDINATION", "PREPARATION", "READY", "EXECUTION", "PAUSED", "REFLECTION"})
    void returnIsHistoryPreservingAndRevokesEveryPhase(String phase) {
        if (!phase.equals("CONVERSATION")) {
            var proposal = phase.equals("PREPARATION") ? f.sensor(true) : f.dialogue();
            if (phase.equals("COORDINATION")) proposal.addProperty("goal", "");
            f.propose(proposal, f.text);
            if (java.util.Set.of("EXECUTION", "PAUSED", "REFLECTION").contains(phase)) f.start(f.text);
            if (phase.equals("PAUSED")) f.say("Pause", f.text);
            if (phase.equals("REFLECTION")) f.turn("COMPLETE", null, "We have finished", f.text);
        }
        assertEquals(phase, CoordinationMemory.stage(f.storage()));
        int history = f.agent.getEventHistory().toList().size();
        var response = f.say("Go back to the beginning state", f.text);
        assertTrue(response.getPayload().contains("now in Conversation"));
        assertEquals("Coordination conversation", f.agent.getCurrentState().getName());
        assertTrue(f.agent.getEventHistory().toList().size() > history);
        for (String key : java.util.List.of(TaskMemory.SPEC, TaskResponse.KEY, CoordinationMemory.DRAFT, TaskMemory.SESSION))
            assertFalse(f.storage().containsKey(key), key);
        f.say("Return to conversation", f.text);
        assertEquals("CONVERSATION", CoordinationMemory.stage(f.storage()));
        assertTrue(f.say("Where are we?", f.text).getPayload().contains("Current state: CONVERSATION"));
    }
    @Test void completedOutcomeSurvivesStopReturnAndAnExplicitFreshRun() {
        var agreement = f.dialogue(); agreement.addProperty("dialogueLimit", 1);
        f.propose(agreement, f.text); f.start(f.text);
        String run = f.storage().get(CoordinationMemory.RUN).getAsString();
        f.say("Stop", f.text); f.say("Stop", f.text);
        assertEquals("LIMIT_REACHED", f.storage().get(CoordinationMemory.OUTCOME).getAsString());
        f.say("Start all over again", f.text);
        assertEquals(run, f.storage().get(CoordinationMemory.RUN).getAsString());
        f.turn("RESTART", null, "Restart the same task", f.text);
        assertNotEquals(run, f.storage().get(CoordinationMemory.RUN).getAsString());
        assertEquals(1, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        f.say("Return to conversation", f.text);
        assertEquals(2, f.storage().get(CoordinationMemory.OUTCOMES).getAsJsonArray().size());
        assertEquals("LIMIT_REACHED", f.storage().get(CoordinationMemory.OUTCOME).getAsString());
    }
    @Test void restartCannotReusePreparationAndLateRevisionCannotCommit() {
        f.propose(f.sensor(false), f.text); f.start(f.text);
        var agreement = f.sensor(true); f.storage().put(CoordinationMemory.AGREEMENT, agreement);
        f.turn("RESTART", null, "Restart the same task", f.text);
        assertEquals("PREPARATION", CoordinationMemory.stage(f.storage()));
        assertFalse(TaskMemory.active(f.storage()));
        when(f.gateway.infer(any())).thenAnswer(call -> {
            f.storage().put(TaskMemory.REVISION, new JsonPrimitive(500));
            return f.update("RETURN_TO_CONVERSATION", null, "Returned");
        });
        f.say("Please take us back to general conversation", f.text);
        assertEquals("PREPARATION", CoordinationMemory.stage(f.storage()));
    }
}
