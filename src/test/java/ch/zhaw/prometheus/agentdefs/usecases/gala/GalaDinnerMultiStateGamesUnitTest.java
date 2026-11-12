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

    @ParameterizedTest @ValueSource(strings = {"gala", "swiss-de", "swiss-en"})
    void readyLeadInAndSlowCountdownSurviveYesWithoutRestartingOrMovingFreshness(String variant) {
        var selected = switch (variant) {
            case "swiss-de" -> new ch.zhaw.prometheus.agentdefs.usecases.SwissRoboticsDayReceptionMultiStateGames();
            case "swiss-en" -> new ch.zhaw.prometheus.agentdefs.usecases.SwissRoboticsDayReceptionMultiStateGamesEnglish();
            default -> definition;
        };
        var playing = selected.createAgent();
        var storage = ((GalaDinnerPolicy) playing.getCurrentState().ownPolicy()).storage();
        put(storage, COUNT, "1"); put(storage, SEEN, Instant.now().toString());
        put(storage, RPS_PREPARED, "true");
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_RPS", ""));
        playing.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER,
                variant.equals("swiss-en") ? "Let's play Rock, Paper, Scissors" : "Schere Stein Papier bitte"), runtime);
        var policy = (GalaDinnerPolicy) playing.getCurrentState().ownPolicy();
        String intro = text(storage, GalaDinnerRps.INTRO, ""), round = text(storage, GalaDinnerRounds.ID, "");
        String lastInput = text(storage, LAST_INPUT, "");
        Instant speechStarted = Instant.now();
        for (String guidance : List.of(text(storage, CONTINUATION_FACTS, ""), policy.liveActivityContext().facts())) {
            assertTrue(guidance.contains("Say \"Mach dich bereit.\" once"));
            assertTrue(guidance.contains("say \"Get ready.\" once"));
            assertTrue(guidance.contains("roughly one second per word"));
            assertTrue(guidance.contains("Continue automatically"));
        }
        clearInvocations(gateway);
        for (String yes : List.of("Ja!", "Yes", "Yeah", "Yep!", "Ja, gerne", "Okay")) {
            assertTrue(policy.liveNonMutatingAcknowledgement(yes));
            playing.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, yes), runtime);
            assertEquals(intro, text(storage, GalaDinnerRps.INTRO, ""));
            assertEquals(round, text(storage, GalaDinnerRounds.ID, ""));
            assertEquals(lastInput, text(storage, LAST_INPUT, ""));
            assertFalse(storage.containsKey(CAPTURE_ID));
        }
        var owner = runtime.externalSpeech();
        var speech = playing.recordExternalSpeech(variant.equals("swiss-en") ? "Get ready. Rock … Paper … Scissors." : "Mach dich bereit. Schere … Stein … Papier.",
                new ch.zhaw.prometheus.model.event.SpeechProvenance(
                        ch.zhaw.prometheus.model.event.SpeechProvenance.Origin.NATIVE, owner.sessionId(), owner.epoch(),
                        "slow-countdown", ch.zhaw.prometheus.model.event.SpeechProvenance.Association.NONE, List.of(), true));
        assertTrue(policy.liveSpeechCaptured(speech, owner, speechStarted, Instant.now()));
        assertTrue(policy.liveActivityContext().playbackCue().allowAcknowledgements());
        playing.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "Ja"), runtime);
        assertEquals("slow-countdown", policy.liveActivityContext().playbackCue().segmentId());
        var playback = new Event(GalaDinnerPolicy.COUNTDOWN_PLAYED, "system", Event.KIND_SYSTEM, intro);
        assertNotNull(playing.acknowledge(playback, runtime));
        assertNull(playing.acknowledge(playback, runtime));
        assertFalse(policy.liveNonMutatingAcknowledgement("Ja, stopp"));
        assertFalse(policy.liveNonMutatingAcknowledgement("Yeah, stop"));
        assertFalse(policy.liveNonMutatingAcknowledgement("Yep, wait"));
        assertFalse(policy.liveNonMutatingAcknowledgement("Ja, wann ist Mittagessen?"));
        put(storage, EXIT_REQUEST, "Andere Frage");
        assertFalse(policy.liveNonMutatingAcknowledgement("Ja"));
        verifyNoInteractions(gateway);
    }

    private void assertReceptionWithoutTask() {
        assertEquals("Gala reception", agent.getCurrentState().getName());
        assertEquals("RECEPTION", mode(memory));
        assertTrue(memory.toMap().keySet().stream().noneMatch(key -> key.startsWith("task.")));
        assertInstanceOf(GalaDinnerGamesPolicy.class, agent.getCurrentState().ownPolicy());
    }
    @ParameterizedTest @ValueSource(strings = {"Wunderbar", "Das klingt prima", "That sounds lovely"})
    void contextualPositiveAcknowledgementPreservesPendingCountdownAndBindsExactInput(String positive) {
        var policy = startCountdown();
        var owner = runtime.externalSpeech();
        var cue = policy.liveActivityContext().playbackCue();
        String lastInput = text(memory, LAST_INPUT, "");
        Instant received = Instant.now();
        var prefix = agent.recordExternalSpeech("Schere. Stein.", new ch.zhaw.prometheus.model.event.SpeechProvenance(
                ch.zhaw.prometheus.model.event.SpeechProvenance.Origin.NATIVE, owner.sessionId(), owner.epoch(),
                "prefix", ch.zhaw.prometheus.model.event.SpeechProvenance.Association.NONE, List.of(), true));
        assertFalse(policy.liveSpeechCaptured(prefix, owner, received, Instant.now(), List.of(
                new ch.zhaw.prometheus.model.policy.LiveSpeechPart("Schere. Stein.", received, "prefix-receipt"))));
        var pending = memory.get(GalaDinnerCountdown.PENDING).deepCopy();
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("COUNTDOWN_ACK", ""));
        assertFalse(policy.liveNonMutatingAcknowledgement(positive));
        say(positive);
        var input = agent.getEventHistory().toList().getLast();
        assertEquals(Event.TYPE_USER_UTTERANCE, input.getType());
        assertTrue(policy.liveNonMutatingAcknowledgement(input, owner));
        assertFalse(policy.liveNonMutatingAcknowledgement(input, new ExternalSpeech(UUID.randomUUID(), owner.epoch())));
        assertFalse(policy.liveNonMutatingAcknowledgement(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, positive), owner));
        assertEquals(cue, policy.liveActivityContext().playbackCue());
        assertEquals(lastInput, text(memory, LAST_INPUT, ""));
        assertEquals(pending, memory.get(GalaDinnerCountdown.PENDING));
        assertFalse(memory.containsKey(CAPTURE_ID));
        verify(gateway, times(1)).infer(any());
        var request = ArgumentCaptor.forClass(InferenceRequest.class);
        verify(gateway).infer(request.capture());
        assertTrue(request.getValue().messages().stream().anyMatch(message -> message.getContent().contains("COUNTDOWN_ACK")));
        // The last word arrived before the positive input was admitted, as in the retained trial.
        var last = agent.recordExternalSpeech("Papier.", new ch.zhaw.prometheus.model.event.SpeechProvenance(
                ch.zhaw.prometheus.model.event.SpeechProvenance.Origin.NATIVE, owner.sessionId(), owner.epoch(),
                "last", ch.zhaw.prometheus.model.event.SpeechProvenance.Association.NONE, List.of(), true));
        assertTrue(policy.liveSpeechCaptured(last, owner, received, Instant.now(), List.of(
                new ch.zhaw.prometheus.model.policy.LiveSpeechPart("Papier.", received.plusNanos(1), "last-receipt"))));
        assertEquals(List.of("prefix", "last"), policy.liveActivityContext().playbackCue().segmentIds());
        put(memory, GalaDinnerRps.DEADLINE, Instant.now().minusSeconds(1).toString());
        assertFalse(policy.liveNonMutatingAcknowledgement(input, owner));
    }

    @ParameterizedTest @ValueSource(strings = {"Wunderbar, aber warte", "Yeah, stop", "Great, wait", "Wunderbar?", "Nicht wunderbar"})
    void positiveWordsCannotAuthorizeMixedRequestsEvenWhenTheModelSelectsAcknowledgement(String mixed) {
        var policy = startCountdown();
        put(memory, LAST_INPUT, Instant.now().minusSeconds(2).toString());
        String before = text(memory, LAST_INPUT, "");
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("COUNTDOWN_ACK", ""));
        say(mixed);
        assertFalse(policy.liveNonMutatingAcknowledgement(agent.getEventHistory().toList().stream()
                .filter(event -> Event.TYPE_USER_UTTERANCE.equals(event.getType())).toList().getLast(), runtime.externalSpeech()));
        assertNotEquals(before, text(memory, LAST_INPUT, ""));
        assertFalse(memory.containsKey(GalaDinnerRps.ACK_INPUT));
        assertFalse(memory.containsKey(CAPTURE_ID));
    }

    @ParameterizedTest @ValueSource(strings = {"WAIT", "invalid-json"})
    void uncertainOrFailedClassificationDoesNotPreservePlayback(String result) {
        var policy = startCountdown();
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("COUNTDOWN_ACK", ""));
        say("Wunderbar");
        var prior = agent.getEventHistory().toList().getLast();
        assertTrue(policy.liveNonMutatingAcknowledgement(prior, runtime.externalSpeech()));
        put(memory, LAST_INPUT, Instant.now().minusSeconds(2).toString());
        String before = text(memory, LAST_INPUT, "");
        when(gateway.infer(any())).thenReturn(result.equals("WAIT") ? GalaDinnerReceptionUnitTest.answer("WAIT", "") : result);
        say("Das sieht interessant aus");
        assertNotEquals(before, text(memory, LAST_INPUT, ""));
        assertFalse(memory.containsKey(GalaDinnerRps.ACK_INPUT));
        assertFalse(policy.liveNonMutatingAcknowledgement(prior, runtime.externalSpeech()));
        assertFalse(policy.liveNonMutatingAcknowledgement(agent.getEventHistory().toList().stream()
                .filter(event -> Event.TYPE_USER_UTTERANCE.equals(event.getType())).toList().getLast(), runtime.externalSpeech()));
    }

    @Test void contextualAcknowledgementIsRejectedOutsideAnActiveRoundOrAfterItsDeadline() {
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("COUNTDOWN_ACK", ""));
        say("Wunderbar");
        assertEquals("RECEPTION", mode(memory));
        assertFalse(memory.containsKey(GalaDinnerRps.ACK_INPUT));
        var policy = startCountdown();
        when(gateway.infer(any())).thenAnswer(call -> {
            put(memory, GalaDinnerRps.DEADLINE, Instant.now().minusSeconds(1).toString());
            return GalaDinnerReceptionUnitTest.answer("COUNTDOWN_ACK", "");
        });
        say("Wunderbar");
        assertFalse(memory.containsKey(GalaDinnerRps.ACK_INPUT));
        assertFalse(policy.liveNonMutatingAcknowledgement(agent.getEventHistory().toList().getLast(), runtime.externalSpeech()));
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
