package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.guessing.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.agentdefs.usecases.healthcare.SingleStateGuessingGameUserGuess;
import ch.zhaw.prometheus.spi.*;

class GuessingGameUnitTest {
    final Storage storage = new Storage();
    final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    final PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway);
    static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    static EventHistory history(String text) { var events = new EventHistory(); events.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", text)); return events; }
    @Test void rolesSecretsAndFreshRoundsAreExplicitAndResettable() {
        GuessingMemory.initialize(storage, GuessingMemory.Role.USER_GUESSES);
        assertFalse(GuessingMemory.record(storage).has("secret"));
        GuessingMemory.apply(storage, GuessingMemory.Operation.START, true, NOW);
        var first = GuessingMemory.record(storage); assertEquals("teacup", first.getAsJsonObject("secret").get("name").getAsString());
        GuessingMemory.apply(storage, GuessingMemory.Operation.RESTART, true, NOW); assertEquals(first.get("id"), GuessingMemory.record(storage).get("id"));
        GuessingMemory.apply(storage, GuessingMemory.Operation.REVEAL, true, NOW);
        assertEquals(first.get("secret"), GuessingMemory.record(storage).get("secret"));
        GuessingMemory.apply(storage, GuessingMemory.Operation.RESTART, true, NOW.plusSeconds(1));
        assertNotEquals(first.get("id"), GuessingMemory.record(storage).get("id"));
        assertEquals("dog", GuessingMemory.record(storage).getAsJsonObject("secret").get("name").getAsString());
        GuessingMemory.start(storage, GuessingMemory.Role.AGENT_GUESSES, true, NOW);
        assertTrue(GuessingMemory.facts(storage).contains("A NEW round is confirmed"));
        assertTrue(GuessingMemory.facts(storage).contains("HUMAN chooses"));
        GuessingMemory.apply(storage, GuessingMemory.Operation.REVEAL, true, NOW);
        assertFalse(GuessingMemory.record(storage).has("secret"), "Never invent the human's secret");
        GuessingMemory.reset(storage); assertEquals("INVITED", GuessingMemory.text(GuessingMemory.record(storage), "phase"));
    }
    @Test void harmlessAnswersCannotEndRoundAndInvalidControlCannotChangeSecret() {
        GuessingMemory.initialize(storage, GuessingMemory.Role.USER_GUESSES); GuessingMemory.start(storage, GuessingMemory.Role.USER_GUESSES, false, NOW);
        var first = GuessingMemory.record(storage).get("secret"); var exit = new GuessingExitDecision(storage);
        when(gateway.decide(any())).thenReturn(true);
        for (String text : List.of("yes", "no", "Okay", "I give up", "I give up, tell me")) assertFalse(exit.decide(history(text), runtime));
        assertTrue(exit.decide(history("Stop the conversation"), runtime));
        var action = new GuessingAction(storage, GuessingAction.Mode.UPDATE);
        clearInvocations(gateway); action.execute(history("Okay"), runtime); verify(gateway, never()).infer(any());
        var version = storage.writeVersion(GuessingMemory.KEY); action.execute(history("Okay"), runtime);
        assertEquals(version, storage.writeVersion(GuessingMemory.KEY), "Repeated acknowledgments add no redundant state write");
        when(gateway.infer(any())).thenReturn("{\"operation\":\"RESTART\"}"); action.execute(history("What colour is it?"), runtime);
        assertEquals(first, GuessingMemory.record(storage).get("secret"));
        when(gateway.infer(any())).thenReturn("{\"operation\":\"SOLVED\",\"secret\":\"cat\"}"); action.execute(history("Is it a cat?"), runtime);
        assertEquals("ACTIVE", GuessingMemory.text(GuessingMemory.record(storage), "phase"));
        when(gateway.infer(any())).thenReturn("{\"operation\":\"SOLVED\"}"); action.execute(history("Is it a cat?"), runtime);
        assertEquals("ACTIVE", GuessingMemory.text(GuessingMemory.record(storage), "phase"), "A model cannot declare an unrelated name correct");
        action.execute(history("I give up"), runtime); assertEquals("REVEALED", GuessingMemory.text(GuessingMemory.record(storage), "phase"));
        assertEquals(first, GuessingMemory.record(storage).get("secret"));
    }
    static Storage storage(Agent agent) { return agent.reachableStates().stream().map(State::ownPolicy).filter(GuessingPolicy.class::isInstance)
            .map(GuessingPolicy.class::cast).findFirst().orElseThrow().storage(); }
    static void controls(LanguageModelGateway gateway, boolean humanGuesses) {
        doAnswer(call -> {
            List<PromptMessage> m = call.getArgument(0); String prompt = m.getFirst().getContent();
            return humanGuesses ? prompt.contains("where the user guesses Valerian's secret item") : prompt.contains("where Valerian guesses the person's secret item");
        }).when(gateway).decide(any());
        when(gateway.infer(any())).thenReturn("{\"operation\":\"KEEP\"}");
    }
    @Test void coreBothRoleDirectionsAndHealthcareRetainLiveWordingAndInstructionBudget() {
        for (boolean human : List.of(true, false)) {
            controls(gateway, human); var agent = new RoleClarificationGuessingGame().createAgent();
            var live = runtime.withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch()));
            assertNull(agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", human ? "You choose, I guess" : "I choose, you guess"), live));
            var storage = storage(agent); assertEquals(human ? "USER_GUESSES" : "AGENT_GUESSES", GuessingMemory.text(GuessingMemory.record(storage), "role"));
            var adapter = new ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter(); assertDoesNotThrow(() -> adapter.instructions(agent));
            assertNull(agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "I give up"), live));
            assertEquals("REVEALED", GuessingMemory.text(GuessingMemory.record(storage), "phase"));
            assertEquals(human, GuessingMemory.record(storage).has("secret"));
            when(gateway.infer(any())).thenReturn("{\"operation\":\"RESTART\"}");
            agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "A new round please"), live);
            assertEquals(2, GuessingMemory.record(storage).get("number").getAsInt()); agent.reset();
            assertFalse(GuessingMemory.record(storage).has("secret"));
        }
        doReturn(false).when(gateway).decide(any()); when(gateway.infer(any())).thenReturn("{\"operation\":\"START\"}");
        var care = new SingleStateGuessingGameUserGuess().createAgent(); var live = runtime.withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), care.executionEpoch()));
        care.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Yes, let's play"), live);
        assertTrue(GuessingMemory.active(storage(care)));
        var instructions = new ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter().instructions(care);
        assertTrue(instructions.contains("not specified")); assertTrue(instructions.contains("clarification")); assertTrue(instructions.contains("Do not talk about dementia"));
    }
    @Test void textAndContinuousUseBackendWordingAndTheSamePersistedFacts() {
        controls(gateway, true); when(gateway.infer(any())).thenReturn("{\"speech\":\"I have chosen an item; ask me a yes or no question.\",\"nv\":{\"g\":\"NONE\"}}");
        var agent = new RoleClarificationGuessingGame().createAgent();
        var response = agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "You choose, I guess"), runtime);
        assertNotNull(response); assertTrue(response.getPayload().contains("chosen an item"));
        assertTrue(((GuessingPolicy) agent.reachableStates().stream().filter(s -> s.getName().endsWith("user guesses")).findFirst().orElseThrow().ownPolicy()).describe().contains("teacup"));
    }
    @Test void currentRoundControlDropsOldInvitationsButKeepsTheLatestTurn() {
        GuessingMemory.initialize(storage, GuessingMemory.Role.USER_GUESSES);
        var events = history("Old round guessed a cat"); events.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Is it alive?"));
        GuessingMemory.start(storage, GuessingMemory.Role.USER_GUESSES, false, Instant.now().plusSeconds(10));
        var selected = GuessingMemory.currentHistory(storage, events);
        assertEquals(1, selected.toList().size()); assertEquals("Is it alive?", selected.toList().getFirst().getPayload());
        assertEquals(2, events.toList().size(), "Durable conversation is never deleted");
    }
}
