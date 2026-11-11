package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReceptionMultiStateGames;
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReceptionRpsPreparation;
import ch.zhaw.prometheus.application.live.LiveContextProjection;
import ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.AgentEmbodiment;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.spi.InferenceRequest;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class GalaDinnerMultiStateGamesUnitTest {
    private final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    private final PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway)
            .withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID()));
    private final GalaDinnerReceptionMultiStateGames definition = new GalaDinnerReceptionMultiStateGames();
    private final Agent agent = definition.createAgent();
    private final Storage memory = ((GalaDinnerPolicy) agent.getCurrentState().ownPolicy()).storage();

    @BeforeEach void occupied() { put(memory, COUNT, "1"); put(memory, SEEN, Instant.now().toString()); }

    @Test void separateCloneKeepsTutorialGamesAndProfilesWithoutTaskStatesOrPrompts() {
        var original = new GalaDinnerReceptionRpsPreparation();
        assertNotEquals(original.key(), definition.key());
        assertEquals("Gigi - Gala with multi-state RPS and guessing game", definition.displayName());
        assertTrue(original.createAgent().listStates().contains("Gala co-created task"));
        assertEquals(Set.of("Gala reception", "Gala routing", "Gala guess the person", "Gala rock-scissor-paper",
                        "Gala rock-scissor-paper preparation", "Gala rock-scissor-paper countdown"),
                agent.reachableStates().stream().map(ch.zhaw.prometheus.model.State::getName)
                        .collect(java.util.stream.Collectors.toSet()));
        for (var embodiment : AgentEmbodiment.values()) {
            var created = definition.createInstance(new AgentCreationContext(new PromptMessageAssembler(), gateway, embodiment)).agent();
            assertEquals(embodiment, created.getEmbodiment());
            assertEquals("GIGI", created.getPersonaName());
            assertEquals("de", created.getLanguageCode());
            assertEquals(original.createAgent().getInteractionProfile().toJson(), created.getInteractionProfile().toJson());
            var adapter = new LiveVoicePolicyAdapter();
            assertTrue(adapter.supports(created));
            for (var state : created.reachableStates()) {
                assertInstanceOf(GalaDinnerGamesPolicy.class, state.ownPolicy());
                ReflectionTestUtils.setField(created, "currentState", state);
                String instructions = adapter.instructions(created);
                assertTrue(instructions.contains("Offer conversation, information and games"));
                assertTrue(instructions.contains("visitor asks yes/no and guesses your backend-selected person"));
                assertTrue(instructions.contains("name your sign only with the confirmed result"));
                assertTrue(instructions.contains("Triple Crown"));
                for (String excluded : List.of("co-created", "Only TASK work", "ON_CUE", "IMMEDIATE",
                        "Task phase:", "gemeinsam vereinbarte kurze Sensorreaktionen"))
                    assertFalse(instructions.contains(excluded), excluded);
                assertFalse(state.ownPolicy().describe().contains("co-created"));
                assertTrue(instructions.getBytes(StandardCharsets.UTF_8).length <= 14400);
                assertEquals(2000, new LiveContextProjection(new PromptMessageAssembler(), adapter).project(created).userSilenceMs());
            }
        }
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"START_TASK", "PROPOSE", "ACTIVATE"})
    void taskOperationsAreRejectedBeforeMutationAndRemainUnavailableAfterReset(String operation) {
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer(operation, ""));
        say("Wenn ich Papier zeige, erzähle einen Witz. Einverstanden?");
        assertReceptionWithoutTask();
        var requests = ArgumentCaptor.forClass(InferenceRequest.class);
        verify(gateway).infer(requests.capture());
        String prompt = requests.getValue().messages().getFirst().getContent();
        assertTrue(prompt.contains("START_RPS|START_GUESS"));
        assertFalse(prompt.contains("START_TASK"));
        assertFalse(prompt.contains("custom interaction request"));
        assertFalse(text(memory, CONTINUATION_FACTS, "").contains("task action"));
        agent.reset();
        occupied();
        assertTrue(GalaDinnerRpsPreparation.enabled(memory));
        say("Lass uns vereinbaren, auf ein Handzeichen zu reagieren.");
        assertReceptionWithoutTask();
        assertFalse(((GalaDinnerPolicy) agent.getCurrentState().ownPolicy()).voiceInstructions().contains("co-created"));
        verify(gateway, times(2)).infer(any());
    }

    @Test void rpsTutorialCountdownResultReplayAndGuessingRetainTheirControl() {
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_RPS", ""));
        say("Schere Stein Papier bitte");
        assertEquals("Gala rock-scissor-paper preparation", agent.getCurrentState().getName());
        for (String sign : List.of("rock", "paper", "scissor")) hand(sign);
        assertEquals("Gala rock-scissor-paper countdown", agent.getCurrentState().getName());
        assertNull(hand("paper"), "No playing sign before countdown playback");
        assertNotNull(GalaRpsTestFixture.countdownPlayed(agent, runtime));
        assertEquals("Gala rock-scissor-paper", agent.getCurrentState().getName());
        var result = BehaviourPlan.fromJson(hand("paper").getPayload());
        assertEquals("user", result.getDisplay().getAsJsonObject().get("winner").getAsString());
        assertReceptionWithoutTask();
        say("Noch einmal bitte");
        assertEquals("Gala rock-scissor-paper countdown", agent.getCurrentState().getName());
        assertEquals("2", text(memory, GalaDinnerRounds.RPS_COUNT, ""));
        say("Stopp");
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_GUESS", ""));
        say("Personenraten bitte");
        assertEquals("Gala guess the person", agent.getCurrentState().getName());
        assertEquals("Albert Einstein", text(memory, PERSON, ""));
        say("Albert Einstein");
        assertReceptionWithoutTask();
        assertFalse(memory.containsKey(PERSON));
        assertTrue(new LiveVoicePolicyAdapter().instructions(agent).contains("Identity is SECRET"));
        verify(gateway, times(2)).infer(any());
    }

    private void assertReceptionWithoutTask() {
        assertEquals("Gala reception", agent.getCurrentState().getName());
        assertEquals("RECEPTION", mode(memory));
        assertTrue(memory.toMap().keySet().stream().noneMatch(key -> key.startsWith("task.")));
        assertInstanceOf(GalaDinnerGamesPolicy.class, agent.getCurrentState().ownPolicy());
    }
    private Event say(String speech) {
        return agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, speech), runtime);
    }
    private Event hand(String sign) {
        var payload = new JsonObject();
        payload.addProperty("sign", sign); payload.addProperty("confidence", .99);
        payload.addProperty("ts", Instant.now().toString());
        if (memory.containsKey(CAPTURE_ID)) payload.addProperty("captureId", text(memory, CAPTURE_ID, ""));
        return agent.acknowledge(Event.observation(Event.TYPE_HAND_SIGN, "sensor", payload.toString()), runtime);
    }
}
