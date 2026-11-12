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
        f.storage().put(ch.zhaw.prometheus.model.policy.LiveSpeechRequest.ENABLED, new com.google.gson.JsonPrimitive(true));
        var revision = f.storage().get(TaskMemory.REVISION).deepCopy();
        var agreement = f.storage().get(CoordinationMemory.AGREEMENT).deepCopy();
        var phase = TaskMemory.phase(f.storage());
        clearInvocations(f.gateway); f.say("My answer is", f.live);
        assertEquals(revision, f.storage().get(TaskMemory.REVISION));
        assertEquals(agreement, f.storage().get(CoordinationMemory.AGREEMENT));
        assertEquals(phase, TaskMemory.phase(f.storage()));
        assertEquals(0, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        assertFalse(f.storage().containsKey(TaskResponse.KEY));
        var response = f.storage().get(CoreTaskVoice.RESPONSE).getAsJsonObject();
        assertTrue(response.get("supportOnly").getAsBoolean());
        var context = f.policy().liveActivityContext();
        assertFalse(context.facts().contains("finish that request"));
        assertTrue(context.continuation().selfContained());
        assertEquals("Speak these words verbatim: \"Please finish that request.\"", context.continuation().facts());
        assertEquals(response.get("id").getAsString(), context.continuation().id().toString());
        assertEquals(response.get("expires").getAsString(), context.continuation().expiresAt().toString());
        assertTrue(f.policy().liveSpeechRequest(f.owner).postCommitOnly(), "An early acknowledgement cannot consume the committed clarification");
        verifyNoInteractions(f.gateway);
    }
    @ParameterizedTest @ValueSource(strings = {"PENDING", "REJECTED"})
    void unfinishedAnswerCannotReplaceAnInFlightResponseOrItsDeadline(String status) {
        var f = new CoordinationAgentUnitTest(); f.propose(f.sensor(false), f.live); f.start(f.live);
        var response = f.storage().get(TaskResponse.KEY).getAsJsonObject(); response.addProperty("status", status); f.storage().put(TaskResponse.KEY, response);
        var pending = f.storage().get(TaskResponse.KEY).deepCopy();
        var permission = f.storage().get(CoreTaskVoice.RESPONSE).deepCopy();
        clearInvocations(f.gateway); f.say("My answer is", f.live);
        assertEquals(pending, f.storage().get(TaskResponse.KEY));
        assertEquals(permission, f.storage().get(CoreTaskVoice.RESPONSE));
        assertTrue(f.storage().containsKey(CoordinationSupport.LIVE_HELP)); verifyNoInteractions(f.gateway);
    }
}
