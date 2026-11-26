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
        assertNotNull(GalaRpsTestFixture.countdownPlayed(agent, runtime));
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

    @ParameterizedTest @ValueSource(strings = {"gala", "swiss-de", "swiss-en", "openi-de", "openi-en"})
    void countdownIgnoresEveryUtteranceAndReleasesOneMoveFromCompleteCapture(String variant) {
        var selected = switch (variant) {
            case "swiss-de" -> new ch.zhaw.prometheus.agentdefs.usecases.SwissRoboticsDayReceptionMultiStateGames();
            case "swiss-en" -> new ch.zhaw.prometheus.agentdefs.usecases.SwissRoboticsDayReceptionMultiStateGamesEnglish();
            case "openi-de" -> new ch.zhaw.prometheus.agentdefs.usecases.OpenIReceptionMultiStateGames();
            case "openi-en" -> new ch.zhaw.prometheus.agentdefs.usecases.OpenIReceptionMultiStateGamesEnglish();
            default -> definition;
        };
        var playing = selected.createAgent();
        var storage = ((GalaDinnerPolicy) playing.getCurrentState().ownPolicy()).storage();
        put(storage, COUNT, "1"); put(storage, SEEN, Instant.now().toString()); put(storage, RPS_PREPARED, "true");
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_RPS", ""));
        playing.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "Schere Stein Papier bitte"), runtime);
        var policy = (GalaDinnerPolicy) playing.getCurrentState().ownPolicy();
        var owner = runtime.externalSpeech();
        var cue = policy.liveActivityContext().playbackCue();
        String round = text(storage, GalaDinnerRounds.ID, ""), lastInput = text(storage, LAST_INPUT, "");
        assertTrue(cue.completeOnCapture()); assertFalse(cue.allowAcknowledgements());
        assertFalse(policy.liveContextualAcknowledgements());
        assertTrue(policy.liveActivityContext().facts().contains("No lead-in or readiness question"));
        Instant during = Instant.now();
        clearInvocations(gateway);
        for (String reply : List.of("Ja", "Wunderbar", "Ich bestätige", "Hast du's?", "Stopp", "Ja, stopp", "Wait", "[noise]")) {
            playing.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, reply), runtime);
            assertEquals(round, text(storage, GalaDinnerRounds.ID, ""));
            assertEquals(lastInput, text(storage, LAST_INPUT, ""));
            assertEquals(cue, policy.liveActivityContext().playbackCue());
            assertFalse(storage.containsKey(EXIT_REQUEST)); assertFalse(storage.containsKey(CAPTURE_ID));
        }
        var speech = playing.recordExternalSpeech(variant.endsWith("-en") ? "Rock … Paper … Scissors." : "Schere … Stein … Papier.",
                new ch.zhaw.prometheus.model.event.SpeechProvenance(
                        ch.zhaw.prometheus.model.event.SpeechProvenance.Origin.NATIVE, owner.sessionId(), owner.epoch(),
                        "countdown", ch.zhaw.prometheus.model.event.SpeechProvenance.Association.NONE, List.of(), true));
        assertTrue(policy.liveSpeechCaptured(speech, owner, during, Instant.now()));
        var completion = policy.liveSpeechCaptureCompletion(owner);
        assertNotNull(completion);
        var move = playing.acknowledge(completion, runtime);
        assertNotNull(move); assertNotNull(BehaviourPlan.fromJson(move.getPayload()).getMotion());
        assertEquals("WAITING", text(storage, GalaDinnerRps.PHASE, ""));
        assertNull(policy.liveActivityContext().playbackCue());
        assertNull(policy.liveSpeechCaptureCompletion(owner));
        assertNull(playing.acknowledge(completion, runtime));
        assertTrue(policy.liveIgnoresInput(owner, during), "Delayed countdown speech remains ignored");
        assertFalse(policy.liveIgnoresInput(owner, Instant.now()));
        playing.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "Stopp"), runtime);
        assertEquals("RECEPTION", mode(storage));
        verifyNoInteractions(gateway);
    }

    private void assertReceptionWithoutTask() {
        assertEquals("Gala reception", agent.getCurrentState().getName());
        assertEquals("RECEPTION", mode(memory));
        assertTrue(memory.toMap().keySet().stream().noneMatch(key -> key.startsWith("task.")));
        assertInstanceOf(GalaDinnerGamesPolicy.class, agent.getCurrentState().ownPolicy());
    }
    @Test void splitCountdownSurvivesIgnoredQuestionsAndFreshnessChanges() {
        var policy = startCountdown(); var owner = runtime.externalSpeech();
        Instant received = Instant.now();
        var prefix = agent.recordExternalSpeech("Schere. Stein.", provenance("prefix", true));
        assertFalse(policy.liveSpeechCaptured(prefix, owner, received, Instant.now(), List.of(
                new ch.zhaw.prometheus.model.policy.LiveSpeechPart("Schere. Stein.", received, "prefix-receipt"))));
        var pending = memory.get(GalaDinnerCountdown.PENDING).deepCopy();
        say("Hast du's?"); say("Ja, stopp");
        assertEquals(pending, memory.get(GalaDinnerCountdown.PENDING));
        // Reproduce the old race: a later input timestamp cannot erase this round's words.
        put(memory, LAST_INPUT, received.plusSeconds(2).toString());
        var last = agent.recordExternalSpeech("Papier. Jetzt zeig deine Hand.", provenance("last", true));
        assertTrue(policy.liveSpeechCaptured(last, owner, received, Instant.now(), List.of(
                new ch.zhaw.prometheus.model.policy.LiveSpeechPart("Papier. Jetzt zeig deine Hand.", received.plusNanos(1), "last-receipt"))));
        assertNotNull(agent.acknowledge(policy.liveSpeechCaptureCompletion(owner), runtime));
        assertEquals("WAITING", text(memory, GalaDinnerRps.PHASE, ""));
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest @ValueSource(strings = {"incomplete", "quoted", "wrong_order", "stale", "foreign", "expired"})
    void invalidCaptureNeverReleasesTheMoveAndTimeoutLeavesCountdown(String failure) {
        var policy = startCountdown(); var owner = runtime.externalSpeech();
        Instant started = Instant.now();
        String words = switch (failure) { case "quoted" -> "\"Schere, Stein, Papier\"";
            case "wrong_order" -> "Schere, Papier, Stein"; default -> "Schere, Stein, Papier"; };
        if (failure.equals("stale")) started = time(memory, GalaDinnerRps.INTRO_AT).minusNanos(1);
        if (failure.equals("expired")) put(memory, GalaDinnerRps.DEADLINE, Instant.now().minusSeconds(1).toString());
        var speech = agent.recordExternalSpeech(words, provenance("invalid", !failure.equals("incomplete")));
        assertFalse(policy.liveSpeechCaptured(speech, failure.equals("foreign")
                ? new ExternalSpeech(UUID.randomUUID(), owner.epoch()) : owner, started, Instant.now()));
        assertNull(policy.liveSpeechCaptureCompletion(owner)); assertFalse(memory.containsKey(CAPTURE_ID));
        put(memory, GalaDinnerRps.DEADLINE, Instant.now().minusSeconds(1).toString());
        agent.acknowledge(new Event(Event.TYPE_SYSTEM_TICK, "system", Event.KIND_SYSTEM, "tick"), runtime);
        assertReceptionWithoutTask();
        assertFalse(policy.liveIgnoresInput(owner, Instant.now()));
        verifyNoInteractions(gateway);
    }

    @Test void inputWindowsStayScopedAfterRoundCompletionAndResetClearsThem() {
        var policy = startCountdown(); var owner = runtime.externalSpeech();
        Instant during = Instant.now();
        assertNotNull(GalaRpsTestFixture.countdownPlayed(agent, runtime));
        assertNotNull(hand("paper"));
        assertEquals("RECEPTION", mode(memory));
        assertTrue(policy.liveIgnoresInput(owner, during));
        assertFalse(policy.liveIgnoresInput(new ExternalSpeech(UUID.randomUUID(), owner.epoch()), during));
        assertFalse(policy.liveIgnoresInput(new ExternalSpeech(owner.sessionId(), UUID.randomUUID()), during));
        assertFalse(policy.liveIgnoresInput(owner, during.plusSeconds(16)));
        agent.reset();
        assertFalse(policy.liveIgnoresInput(owner, during));
        verifyNoInteractions(gateway);
    }

    private ch.zhaw.prometheus.model.event.SpeechProvenance provenance(String segment, boolean complete) {
        var owner = runtime.externalSpeech();
        return new ch.zhaw.prometheus.model.event.SpeechProvenance(
                ch.zhaw.prometheus.model.event.SpeechProvenance.Origin.NATIVE, owner.sessionId(), owner.epoch(),
                segment, ch.zhaw.prometheus.model.event.SpeechProvenance.Association.NONE, List.of(), complete);
    }

    private GalaDinnerPolicy startCountdown() {
        put(memory, RPS_PREPARED, "true");
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_RPS", ""));
        say("Schere Stein Papier bitte");
        clearInvocations(gateway);
        return (GalaDinnerPolicy) agent.getCurrentState().ownPolicy();
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
