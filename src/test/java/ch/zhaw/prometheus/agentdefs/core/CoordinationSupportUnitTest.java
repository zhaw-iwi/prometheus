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
    @ValueSource(strings={"I understand. Do I need to demonstrate again", "Do I have to repeat the check again?"})
    void preparationQuestionUsesSavedReadinessWithoutInventingAnotherDemonstration(String words) {
        var f = new CoordinationAgentUnitTest(); f.propose(f.sensor(true), f.text);
        f.policy().beginLiveConversation(f.agent.getEventHistory());
        var completed = new JsonObject(); completed.add("0", new JsonObject()); completed.add("1", new JsonObject());
        f.storage().put(CoordinationMemory.EVIDENCE, completed);
        assertEquals("READY", CoordinationMemory.stage(f.storage()));
        var evidence = f.storage().get(CoordinationMemory.EVIDENCE).deepCopy();
        var draft = f.storage().get(CoordinationMemory.DRAFT).deepCopy();
        clearInvocations(f.gateway);
        f.say(words, f.live);
        String spoken = f.policy().liveCommittedSpeech(f.owner).text();
        assertTrue(spoken.contains("no further demonstration is needed"));
        assertTrue(spoken.contains("After acceptance, show a fresh matching cue"));
        assertEquals(evidence, f.storage().get(CoordinationMemory.EVIDENCE));
        assertEquals(draft, f.storage().get(CoordinationMemory.DRAFT));
        assertFalse(f.storage().containsKey(CoordinationMemory.AGREEMENT));
        assertFalse(f.storage().containsKey(TaskResponse.KEY));
        verifyNoInteractions(f.gateway);
    }

    @Test void shortenedPerceptionQuestionStillUsesSuppliedEvidenceWithoutInference() {
        var f = new CoordinationAgentUnitTest();
        f.sign("scissor", .87, Instant.now(), f.text); clearInvocations(f.gateway);
        f.policy().beginLiveConversation(f.agent.getEventHistory()); f.say("hand sign do you see now", f.live);
        String spoken = f.policy().liveCommittedSpeech(f.owner).text();
        assertTrue(spoken.contains("supplied hand observation reported scissors"));
        assertTrue(spoken.contains("not direct access to camera images"));
        verifyNoInteractions(f.gateway);
    }

    @ParameterizedTest
    @ValueSource(strings={"Please help me set up a dialogue task: draft an invitation with me. Propose it without starting.",
            "Can you help me propose a dialogue task?", "Hilf mir eine Dialogaufgabe zu planen."})
    void helpWithAConcreteProposalStillReachesTheTaskInterpreter(String request) {
        var f = new CoordinationAgentUnitTest();
        f.turn("PROPOSE", f.dialogue(), request, f.text);
        assertEquals("READY", CoordinationMemory.stage(f.storage()));
        verify(f.gateway).infer(any());
        assertFalse(TaskMemory.active(f.storage()));
    }
    @ParameterizedTest
    @ValueSource(strings={"Pause the task, I need help", "I don't understand. Please pause", "Please pause and help me"})
    void explicitPauseInAHelpRequestTakesPriorityWithoutInference(String words) {
        var f = new CoordinationAgentUnitTest(); f.propose(f.sensor(false), f.text); f.start(f.text);
        clearInvocations(f.gateway); f.say(words, f.text);
        assertEquals("PAUSED", CoordinationMemory.stage(f.storage()));
        assertEquals(0, f.storage().get(TaskMemory.ACTIONS).getAsInt()); verifyNoInteractions(f.gateway);
    }
    @Test void contradictoryControlsClarifyAndExplicitRestartStillUsesTheExistingInterpreter() {
        var f = new CoordinationAgentUnitTest(); f.propose(f.sensor(false), f.text); f.start(f.text);
        var run=f.storage().get(CoordinationMemory.RUN).deepCopy();
        f.say("Pause; resume; help me", f.text); assertEquals("EXECUTION", CoordinationMemory.stage(f.storage()));
        f.turn("SUPPORT",null,"Don't pause; I need help",f.text); assertEquals("EXECUTION",CoordinationMemory.stage(f.storage()));
        f.turn("RESTART",null,"Restart the same task; I need help",f.text);
        assertNotEquals(run,f.storage().get(CoordinationMemory.RUN)); assertEquals(0,f.storage().get(TaskMemory.ACTIONS).getAsInt());
    }
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
        clearInvocations(f.gateway); var response = f.say("I don't understand ", f.text);
        String expected = switch (phase) {
            case "CONVERSATION" -> "No task is active";
            case "COORDINATION" -> "unresolved terms";
            case "PREPARATION" -> "Before starting, demonstrate";
            case "READY" -> "no further demonstration is needed";
            case "EXECUTION" -> "The task is active";
            case "PAUSED" -> "progress is preserved";
            case "FINISHING" -> "awaiting speech association or playback";
            case "UNCERTAIN" -> "delivery is uncertain";
            default -> "The task has ended";
        };
        assertTrue(response.getPayload().contains(expected)); verifyNoInteractions(f.gateway);
        assertFalse(response.getPayload().contains("Current coordination state:"));
        protectedKeys.forEach(key -> { if (before.has(key)) assertEquals(before.get(key), f.storage().get(key)); else assertFalse(f.storage().containsKey(key)); });
        assertEquals(phase, CoordinationMemory.stage(f.storage()));
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void activeHelpMentionsIndependentReactionsOnlyForASensorTask(boolean sensor) {
        var f = new CoordinationAgentUnitTest(); f.propose(sensor ? f.sensor(false) : f.dialogue(), f.text); f.start(f.text);
        String words = CoordinationSupport.explain(f.storage(), Instant.now());
        assertTrue(words.startsWith("The task is active."));
        assertTrue(words.contains("Pause to pause it, or Stop to end it"));
        assertEquals(sensor, words.contains("independent sensor reactions"));
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
        assertTrue(words.contains("then accept the ready proposal"));
        f.say("Help me", f.live);
        assertFalse(f.storage().containsKey(TaskResponse.KEY));
        assertNotNull(f.policy().liveActivityContext().continuation());
        assertTrue(f.policy().liveActivityContext().continuation().selfContained());
        assertTrue(f.policy().liveActivityContext().continuation().facts().contains("Speak these words verbatim:"));
        assertTrue(f.policy().liveActivityContext().continuation().facts().contains(words));
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
