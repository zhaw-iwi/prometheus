package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.task.TaskMemory;
import ch.zhaw.prometheus.model.task.TaskResponse;
import ch.zhaw.prometheus.model.coordination.CoordinationSupport;

class CoordinationCaptureUnitTest {
    @ParameterizedTest @ValueSource(strings = {"A dry", "Tell me a", "Make it a", "Can you", "My answer is", "I think it is", "Die Antwort ist"})
    void unfinishedInputCannotReviseOrExecuteEvenIfProviderWouldCompleteIt(String fragment) {
        var f = new CoordinationAgentUnitTest(); f.propose(f.sensor(false), f.text); f.start(f.text);
        String revision = TaskMemory.text(f.storage(), TaskMemory.REVISION, "0");
        clearInvocations(f.gateway);
        var response = f.turn("PROPOSE", f.dialogue(), fragment, f.text);
        assertTrue(response.getPayload().contains("finish that request"));
        assertFalse(f.storage().containsKey(CoordinationMemory.DRAFT));
        assertEquals(revision, TaskMemory.text(f.storage(), TaskMemory.REVISION, "0"));
        verifyNoInteractions(f.gateway);
        f.say("Stop", f.text); assertEquals("REFLECTION", CoordinationMemory.stage(f.storage()));
    }
    @Test void completeButUnfinishedAnswerGetsALiveSupportOccasionWithoutSpending() {
        var f = new CoordinationAgentUnitTest(); f.propose(f.sensor(false), f.text); f.start(f.text);
        var revision = f.storage().get(TaskMemory.REVISION).deepCopy();
        clearInvocations(f.gateway); f.say("My answer is", f.live);
        assertEquals(revision, f.storage().get(TaskMemory.REVISION));
        assertEquals(0, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        assertFalse(f.storage().containsKey(TaskResponse.KEY));
        var response = f.storage().get(CoreTaskVoice.RESPONSE).getAsJsonObject();
        assertTrue(response.get("supportOnly").getAsBoolean());
        assertTrue(f.policy().liveActivityContext().facts().contains("finish that request"));
        assertNotNull(f.policy().liveActivityContext().continuation()); verifyNoInteractions(f.gateway);
    }
    @Test void unfinishedAnswerCannotReplaceAnInFlightResponseOrItsDeadline() {
        var f = new CoordinationAgentUnitTest(); f.propose(f.sensor(false), f.live); f.start(f.live);
        var pending = f.storage().get(TaskResponse.KEY).deepCopy();
        var permission = f.storage().get(CoreTaskVoice.RESPONSE).deepCopy();
        clearInvocations(f.gateway); f.say("My answer is", f.live);
        assertEquals(pending, f.storage().get(TaskResponse.KEY));
        assertEquals(permission, f.storage().get(CoreTaskVoice.RESPONSE));
        assertTrue(f.storage().containsKey(CoordinationSupport.LIVE_HELP)); verifyNoInteractions(f.gateway);
    }
}
