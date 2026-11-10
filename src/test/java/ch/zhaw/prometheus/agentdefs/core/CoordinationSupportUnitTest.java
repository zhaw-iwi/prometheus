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
import ch.zhaw.prometheus.model.task.*;

class CoordinationSupportUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"CONVERSATION", "COORDINATION", "PREPARATION", "READY", "EXECUTION", "PAUSED", "FINISHING", "UNCERTAIN", "REFLECTION"})
    void helpPreservesAuthorityInEveryPhase(String phase) {
        var f = new CoordinationAgentUnitTest();
        if (phase.equals("COORDINATION")) {
            var proposal = f.dialogue(); proposal.getAsJsonArray("questions").add("Which audience?"); f.propose(proposal, f.text);
        } else if (phase.equals("PREPARATION")) f.propose(f.sensor(true), f.text);
        else if (!phase.equals("CONVERSATION")) {
            f.propose(f.sensor(false), f.text);
            if (!phase.equals("READY")) {
                f.start(f.text);
                if (!phase.equals("EXECUTION")) f.storage().put(TaskMemory.PHASE, new JsonPrimitive(phase.equals("REFLECTION") ? "COMPLETED" : phase));
            }
        }
        assertEquals(phase, CoordinationMemory.stage(f.storage()));
        var protectedKeys = List.of(CoordinationMemory.AGREEMENT, CoordinationMemory.DRAFT, CoordinationMemory.RUN,
                CoordinationMemory.EVIDENCE, TaskMemory.ACTIONS, TaskMemory.REVISION, TaskMemory.PHASE);
        var before = new JsonObject();
        protectedKeys.forEach(key -> { if (f.storage().containsKey(key)) before.add(key, f.storage().get(key).deepCopy()); });
        clearInvocations(f.gateway); var response = f.say("I don't understand", f.text);
        assertTrue(response.getPayload().contains(phase)); verifyNoInteractions(f.gateway);
        protectedKeys.forEach(key -> { if (before.has(key)) assertEquals(before.get(key), f.storage().get(key)); else assertFalse(f.storage().containsKey(key)); });
        assertEquals(phase, CoordinationMemory.stage(f.storage()));
    }

    @Test void liveHelpCannotReplacePendingTaskIdentityOrExtendItsDeadline() {
        var f = new CoordinationAgentUnitTest(); f.propose(f.sensor(false), f.live); f.start(f.live);
        var expected = f.storage().get(TaskResponse.KEY).deepCopy();
        var permission = f.storage().get(CoreTaskVoice.RESPONSE).deepCopy();
        var context = f.policy().liveActivityContext();
        clearInvocations(f.gateway); f.say("This is not working", f.live);
        assertEquals(expected, f.storage().get(TaskResponse.KEY));
        assertEquals(permission, f.storage().get(CoreTaskVoice.RESPONSE));
        var helped = f.policy().liveActivityContext();
        assertEquals(context.continuation(), helped.continuation());
        assertTrue(helped.facts().contains("user requested help"));
        assertEquals("EXECUTION", CoordinationMemory.stage(f.storage())); verifyNoInteractions(f.gateway);
        assertEquals(context.facts(), CoordinationSupport.withHelp(f.storage(), context, Instant.now().plusSeconds(31)).facts());
        f.policy().beginLiveConversation(f.agent.getEventHistory());
        assertFalse(f.storage().containsKey(CoordinationSupport.LIVE_HELP));
    }

    @Test void helpListsOnlyOutstandingPreparationAndCreatesNoTaskPermissionInLive() {
        var f = new CoordinationAgentUnitTest(); f.propose(f.sensor(true), f.text);
        var evidence = new JsonObject(); evidence.add("0", new JsonObject());
        f.storage().put(CoordinationMemory.EVIDENCE, evidence);
        var words = CoordinationSupport.explain(f.storage(), Instant.now());
        assertTrue(words.contains("paper")); assertFalse(words.contains("rock"));
        f.say("Help me", f.live);
        assertFalse(f.storage().containsKey(TaskResponse.KEY));
        assertNotNull(f.policy().liveActivityContext().continuation());
        assertTrue(f.policy().liveActivityContext().facts().contains("Remaining recognition"));
        assertEquals("PREPARATION", CoordinationMemory.stage(f.storage()));
    }

    @Test void unknownPhrasingMayUseExistingInferenceButCannotSpoofAControlTransition() {
        var f = new CoordinationAgentUnitTest(); f.propose(f.sensor(false), f.text);
        var response = f.turn("SUPPORT", null, "My expectations differ from this behaviour", f.text);
        assertEquals("READY", CoordinationMemory.stage(f.storage()));
        assertTrue(response.getPayload().contains("no further demonstration"));
    }

    @Test void errorFallbackExplainsKnownFailureAndNegatedHelpIsNotADirectRequest() {
        var f = new CoordinationAgentUnitTest(); f.propose(f.sensor(false), f.text);
        doReturn("invalid private provider text").when(f.gateway).infer(any());
        var response = f.say("Begin our saved activity", f.text);
        assertTrue(response.getPayload().contains("generated update"));
        assertTrue(response.getPayload().contains("no further demonstration"));
        assertFalse(response.getPayload().contains("private"));
        assertFalse(CoordinationSupport.requested("I do not need help"));
        assertTrue(CoordinationSupport.requested("Ich verstehe das nicht"));
    }
}
