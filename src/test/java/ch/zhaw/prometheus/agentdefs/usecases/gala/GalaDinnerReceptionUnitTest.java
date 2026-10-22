package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.UUID;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.application.live.LiveContextProjection;
import org.mockito.ArgumentCaptor;
import com.google.gson.JsonObject;
import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReception;
import ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.AgentEmbodiment;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.spi.InferenceRequest;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class GalaDinnerReceptionUnitTest {
    private final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    private final PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway).withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID()));
    private final GalaDinnerReception definition = new GalaDinnerReception();
    private final Agent agent = definition.createAgent();
    private final Storage memory = ((GalaDinnerPolicy) agent.getCurrentState().ownPolicy()).storage();

    @BeforeEach void occupied() { put(memory, COUNT, "1"); put(memory, SEEN, Instant.now().toString()); }

    @Test void metadataGreetingAndIdentityWorkForBothEmbodimentsWithoutInference() {
        assertEquals("usecases.gala_dinner_reception", definition.key());
        assertEquals(List.of("usecases"), definition.packagePath());
        for (var embodiment : AgentEmbodiment.values()) {
            var created = definition.createInstance(new AgentCreationContext(new PromptMessageAssembler(), gateway, embodiment));
            assertEquals("GIGI", created.agent().getPersonaName());
            assertEquals("de", created.agent().getLanguageCode());
            assertEquals(embodiment, created.agent().getEmbodiment());
            assertNull(created.starterEvent());
            assertTrue(created.agent().getInteractionProfile().isCapabilityAwareness());
            assertTrue(created.agent().getInteractionProfile().isExternalRealtimeSpeech());
            assertTrue(created.agent().getInteractionProfile().isPresenceControlledLive());
            assertTrue(ch.zhaw.prometheus.model.interaction.AgentInteractionProfile.fromJson(
                    created.agent().getInteractionProfile().toJson()).withCapabilityAwareness(false)
                    .withExternalRealtimeSpeech(true).isPresenceControlledLive());
            assertTrue(new LiveVoicePolicyAdapter().supports(created.agent()));
            assertTrue(created.agent().getInteractionProfile().getSupportedObservations().contains(Event.TYPE_HAND_SIGN));
            assertNull(created.agent().generate(runtime));
        }
        verifyNoInteractions(gateway);
    }

    @Test void informationUsesEventReferenceAndCanonicalMultimodalReplyOnce() {
        when(gateway.infer(any())).thenReturn(answer("CHAT", "Laut Programm beginnt das Dessert um 22:20."));
        Event reply = say("Wann gibt es Dessert?");
        assertEquals("Gala reception", agent.getCurrentState().getName());
        var plan = BehaviourPlan.fromJson(reply.getPayload());
        assertNull(plan.getSpeech());
        assertEquals("NONE", plan.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        assertNull(plan.getMotion());
        assertNull(agent.generate(runtime));
        var capture = ArgumentCaptor.forClass(InferenceRequest.class);
        verify(gateway).infer(capture.capture());
        String instructions = new LiveVoicePolicyAdapter().instructions(agent);
        assertFalse(capture.getValue().messages().getFirst().getContent().contains("22:20"));
        for (String required : List.of("djee-djee", "Unitree G1", "23:30", "19:30", "24. Oktober 2026",
                "Stadthausstrasse 119", "German by default", "Respect the first refusal", "Keine Angaben zu Sitzplatzzuweisungen"))
            assertTrue(instructions.contains(required), required);
    }

    @Test void decorativeHistoryGuidesTheExistingRequestAndExplicitGestureCanRepeat() {
        when(gateway.infer(any())).thenReturn(answer("CHAT", "").replace("\"NONE\"", "\"EXPLAIN\""));
        var first = BehaviourPlan.fromJson(say("Was macht das Labor?").getPayload());
        var second = BehaviourPlan.fromJson(say("Und das Institut?").getPayload());
        var requested = BehaviourPlan.fromJson(say("Please show that gesture again").getPayload());
        assertEquals("EXPLAIN", first.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        assertEquals("NONE", second.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        assertEquals("EXPLAIN", requested.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        var requests = ArgumentCaptor.forClass(InferenceRequest.class);
        verify(gateway, times(3)).infer(requests.capture());
        String prompt = requests.getAllValues().get(1).messages().getFirst().getContent();
        assertTrue(prompt.contains(ch.zhaw.prometheus.agentdefs.core.ValerianCorePrompts.DECORATIVE_GESTURES));
        assertTrue(prompt.contains("Recent published gesture requests"));
        assertTrue(prompt.contains("\"label\":\"EXPLAIN\""));
        assertNull(agent.generate(runtime));
        assertEquals(2, memory.get(GalaDinnerGestures.HISTORY).getAsJsonArray().size());
    }

    @Test void rpsShowsCommittedSignImmediatelyRejectsBadFramesAndPublishesOneCalculatedResult() {
        when(gateway.infer(any())).thenReturn(answer("START_RPS", ""));
        var countdown = BehaviourPlan.fromJson(say("Spielen wir Schere Stein Papier").getPayload());
        assertEquals("Gala rock-scissor-paper", agent.getCurrentState().getName());
        assertEquals("rock", text(memory, SIGN, ""));
        assertEquals("rock", countdown.getMotion().getAsJsonObject().get("handSign").getAsString());
        assertTrue(text(memory, CONTINUATION_FACTS, "").contains("Do not name your sign before the result"));
        assertFalse(text(memory, CONTINUATION_FACTS, "").contains("Robot sign committed: rock"));
        assertFalse(new GalaDinnerPolicy(memory).liveActivityContext().facts().contains("rock"));
        assertFalse(countdown.getDisplay().getAsJsonObject().has("agentSign"));
        assertEquals(text(memory, CAPTURE_ID, ""), countdown.getDisplay().getAsJsonObject().get("captureId").getAsString());
        Instant began = time(memory, STARTED);
        assertNull(hand("paper", began.minusSeconds(1), 1));
        assertNull(hand("paper", began.plusMillis(1), 0.2));
        assertNull(hand("lizard", began.plusMillis(1), 1));
        assertNull(agent.acknowledge(Event.observation(Event.TYPE_HAND_SIGN, "sensor", "{}"), runtime));
        assertEquals("RPS", mode(memory));
        var result = BehaviourPlan.fromJson(hand("paper", Instant.now(), 0.95).getPayload());
        assertNull(result.getSpeech());
        assertTrue(text(memory, CONTINUATION_FACTS, "").contains("Robot: rock; visitor: paper"));
        assertTrue(text(memory, CONTINUATION_FACTS, "").contains("winner: user"));
        assertNull(result.getMotion(), "The result must not repeat the already requested gesture");
        assertEquals("rock", result.getDisplay().getAsJsonObject().get("agentSign").getAsString());
        assertEquals("Gala reception", agent.getCurrentState().getName());
        assertFalse(memory.containsKey(SIGN));
        assertNull(hand("paper", Instant.now(), 1));
        assertNull(agent.generate(runtime));
        verify(gateway, times(1)).infer(any());
    }

    @Test void successiveCapturesRejectMissingOrOldIdentifiersWithoutChangingChoiceAndCompleteExactlyOnce() {
        when(gateway.infer(any())).thenReturn(answer("START_RPS", ""));
        String previous = "previous-encounter";
        for (int round = 0; round < 2; round++) {
            var start = BehaviourPlan.fromJson(say("Schere Stein Papier bitte").getPayload());
            String capture = start.getDisplay().getAsJsonObject().get("captureId").getAsString();
            String chosen = text(memory, SIGN, "");
            assertNotEquals(previous, capture);
            assertNull(hand("paper", Instant.now().plusSeconds(1), .99, previous));
            assertNull(hand("paper", Instant.now(), .99, null));
            assertEquals(chosen, text(memory, SIGN, "")); assertEquals("RPS", mode(memory));
            assertFalse(text(memory, CONTINUATION_FACTS, "").contains("Robot sign committed: " + chosen));
            assertFalse(new GalaDinnerPolicy(memory).liveActivityContext().facts().contains(chosen));
            var result = BehaviourPlan.fromJson(hand("paper", Instant.now(), .99, capture).getPayload());
            assertEquals(capture, result.getDisplay().getAsJsonObject().get("captureId").getAsString());
            assertFalse(memory.containsKey(CAPTURE_ID));
            String occasion = text(memory, CONTINUATION_ID, "");
            assertNull(hand("paper", Instant.now(), .99, capture));
            assertEquals(occasion, text(memory, CONTINUATION_ID, ""));
            previous = capture;
        }
        verify(gateway, times(2)).infer(any());
    }

    @Test void anAlreadyPersistedLegacyRoundKeepsItsTimestampBoundaryUntilItEnds() {
        when(gateway.infer(any())).thenReturn(answer("START_RPS", ""));
        say("Schere Stein Papier bitte"); remove(memory, CAPTURE_ID);
        assertNull(hand("paper", time(memory, STARTED).minusMillis(1), .99, null));
        assertNotNull(hand("paper", Instant.now(), .99, null));
        say("Schere Stein Papier bitte");
        assertNull(hand("paper", Instant.now(), .99, null));
        assertEquals("RPS", mode(memory));
    }

    @Test void handFeedbackUsesActualReadinessAndNeverReplaysAResolvedRound() {
        var sink = mock(ch.zhaw.prometheus.logging.ActivityTrace.Sink.class);
        try (var ignored = ch.zhaw.prometheus.logging.ActivityTrace.attach(sink)) {
            assertNull(hand("paper", Instant.now(), .99));
            verify(sink).cue(eq("rps_not_started"), any(), eq(Event.TYPE_HAND_SIGN));
            when(gateway.infer(any())).thenReturn(answer("START_RPS", ""));
            var start = BehaviourPlan.fromJson(say("Schere Stein Papier bitte").getPayload());
            assertEquals(.7, start.getDisplay().getAsJsonObject().get("minimumConfidence").getAsDouble());
            verify(sink).cue(eq("rps_waiting_for_sign"), any(), eq(Event.TYPE_HAND_SIGN));
            Instant at = time(memory, STARTED).plusMillis(10);
            assertNull(hand("paper", at, .65));
            verify(sink).cue(eq("rps_low_confidence"), any(), eq(Event.TYPE_HAND_SIGN));
            assertNotNull(hand("paper", at.plusMillis(1), .7));
            verify(sink).cue(eq("cue_matched"), any(), eq(Event.TYPE_HAND_SIGN));
            verify(sink).cue(eq("rps_round_completed"), any(), eq(Event.TYPE_HAND_SIGN));
            assertNull(hand("paper", at.plusMillis(2), .99));
            assertNull(agent.generate(runtime));
            assertEquals("Gala reception", agent.getCurrentState().getName());
            verify(gateway, times(1)).infer(any());
            say("Schere Stein Papier bitte");
            assertEquals("Gala rock-scissor-paper", agent.getCurrentState().getName());
            say("Stopp");
            verify(sink).cue(eq("rps_cancelled"), any());
            assertNull(hand("paper", Instant.now(), .99));
        }
    }

    @Test void guessingPublishesTheCardForLiveAndBackendOnlyControlsTheRound() {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""), answer("WAIT", ""));
        assertNull(speech(say("Ich möchte eine Person erraten")));
        var projection = new LiveContextProjection(new PromptMessageAssembler(), new LiveVoicePolicyAdapter());
        var context = projection.project(agent);
        assertTrue(context.activity().facts().contains("Albert Einstein"));
        assertTrue(context.activity().facts().contains("not alive"));
        assertTrue(context.startupInput().toString().contains("co-invented a refrigerator"));
        assertFalse(context.instructions().contains("Albert Einstein"));
        var occasion = context.activity().continuation().id();
        for (String question : List.of("Bist du Physiker?", "Bist du Musiker?", "Welche Schuhgrösse hast du?")) {
            assertNull(say(question));
            assertEquals("Albert Einstein", text(memory, PERSON, ""));
            assertEquals(occasion, projection.project(agent).activity().continuation().id());
        }
        var requests = ArgumentCaptor.forClass(InferenceRequest.class);
        verify(gateway, times(4)).infer(requests.capture());
        assertTrue(requests.getAllValues().stream().noneMatch(r -> r.messages().getFirst().getContent().contains("Fact card:")));
        assertNull(speech(say("Bist du Albert Einstein?")));
        assertEquals("RECEPTION", mode(memory));
        assertNull(projection.project(agent).activity().continuation());
        assertTrue(projection.project(agent).activity().facts().contains("correct_guess; round ended; permitted reveal: Albert Einstein"));
        assertFalse(GalaDinnerPeople.guessed(GalaDinnerPeople.PEOPLE.getFirst(), "Du bist nicht Albert Einstein"));
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""));
        say("Noch ein Ratespiel");
        assertEquals("Marie Curie", text(memory, PERSON, ""));
    }

    @Test void offTopicQuestionKeepsTargetUntilConfirmationThenAnswersTheOriginalRequestWithoutReveal() {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""),
                answer("CHECK_EXIT", ""));
        say("Lass uns raten");
        assertNull(speech(say("Wann kommt der Hauptgang?")));
        assertEquals("GUESS", mode(memory));
        assertEquals("Albert Einstein", text(memory, PERSON, ""));
        assertEquals("Wann kommt der Hauptgang?", text(memory, EXIT_REQUEST, ""));
        assertTrue(new GalaDinnerPolicy(memory).liveActivityContext().facts().contains("EXIT CONFIRMATION PENDING"));
        assertFalse(GalaDinnerInput.nonMutatingAcknowledgement(memory, "Ja gerne", Instant.now()));
        clearInvocations(gateway);
        assertNull(speech(say("Ja gerne")));
        assertEquals("Gala reception", agent.getCurrentState().getName());
        assertFalse(memory.containsKey(PERSON));
        assertFalse(memory.containsKey(EXIT_REQUEST));
        assertFalse(memory.containsKey(OUTCOME));
        assertTrue(text(memory, CONTINUATION_FACTS, "").contains("Wann kommt der Hauptgang?"));
        verifyNoInteractions(gateway);
    }

    @Test void decliningExitKeepsRpsChoiceAndRequiresANewObservationAfterResumption() {
        when(gateway.infer(any())).thenReturn(answer("START_RPS", ""), answer("CHECK_EXIT", ""));
        say("Schere Stein Papier bitte");
        String sign = text(memory, SIGN, ""), round = text(memory, ROUND, "");
        String oldCapture = text(memory, CAPTURE_ID, "");
        say("Was ist das IWI?");
        Instant during = time(memory, STARTED);
        assertNull(hand("paper", during.plusNanos(1), .99));
        assertEquals("RPS", mode(memory));
        clearInvocations(gateway);
        var resumed = BehaviourPlan.fromJson(say("Nein danke").getPayload());
        assertTrue(resumed.getDisplay().getAsJsonObject().get("awaitingUserSign").getAsBoolean());
        assertEquals(sign, text(memory, SIGN, "")); assertEquals(round, text(memory, ROUND, ""));
        assertFalse(memory.containsKey(EXIT_REQUEST));
        assertNotEquals(oldCapture, text(memory, CAPTURE_ID, ""));
        assertNull(hand("paper", Instant.now(), .99, oldCapture));
        assertNull(hand("paper", during, .99));
        var result = BehaviourPlan.fromJson(hand("paper", time(memory, STARTED).plusMillis(1), .99).getPayload());
        assertEquals("user", result.getDisplay().getAsJsonObject().get("winner").getAsString());
        assertNull(result.getMotion()); verifyNoInteractions(gateway);
    }

    @Test void ambiguousConfirmationPreservesGameAndExplicitStopOrCloseStillClearsIt() {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""), answer("CHECK_EXIT", ""), answer("WAIT", ""));
        say("Personenraten bitte"); say("Was macht das IWI?"); say("Okay");
        assertEquals("GUESS", mode(memory)); assertTrue(memory.containsKey(EXIT_REQUEST));
        say("Stopp");
        assertFalse(memory.containsKey(EXIT_REQUEST)); assertFalse(memory.containsKey(PERSON));
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""), answer("CHECK_EXIT", ""));
        say("Personenraten bitte"); say("Wann gibt es Essen?");
        agent.acknowledge(new Event(GalaDinnerPolicy.LIVE_ENDED, "system", Event.KIND_SYSTEM, "stop"), runtime);
        assertFalse(memory.containsKey(EXIT_REQUEST)); assertEquals("RECEPTION", mode(memory));
    }

    @Test void freeFormExitDecisionsRequireAPendingConfirmationAndDeclineRetainsGuessingTarget() {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""), answer("EXIT_GAME", ""));
        say("Raten bitte"); say("Lebt die Person?");
        assertEquals("GUESS", mode(memory)); assertFalse(memory.containsKey(EXIT_REQUEST));
        when(gateway.infer(any())).thenReturn(answer("CHECK_EXIT", ""), answer("KEEP_GAME", ""));
        say("Erzähl mir vom IWI"); say("Ich würde lieber weiter raten");
        assertFalse(memory.containsKey(EXIT_REQUEST)); assertEquals("Albert Einstein", text(memory, PERSON, ""));
    }

    @Test void explicitReferenceQuestionCannotBeMisclassifiedAsGameUncertaintyOrImmediateExit() {
        for (String mistaken : List.of("WAIT", "RETURN")) {
            when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""), answer(mistaken, ""));
            say("Personenraten bitte");
            String person = text(memory, PERSON, ""); say("Was macht das Institut für Wirtschaftsinformatik?");
            assertEquals("GUESS", mode(memory)); assertEquals(person, text(memory, PERSON, ""));
            assertTrue(memory.containsKey(EXIT_REQUEST)); say("Stopp");
        }
        assertFalse(GalaDinnerInput.explicitReferenceQuestion("Arbeitet die Person am IWI?"));
        assertFalse(GalaDinnerInput.explicitReferenceQuestion("Was macht das IWI? Nein, weiterspielen."));
    }

    @ParameterizedTest @ValueSource(strings = {"EXIT_GAME", "RETURN"})
    void inferredConsentRetainsTheOriginalQuestionRegardlessOfExitLabel(String operation) {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""), answer("CHECK_EXIT", ""), answer(operation, ""));
        say("Personenraten bitte"); say("Was macht das IWI?");
        say("Beende bitte die Runde und beantworte meine Frage");
        assertEquals("RECEPTION", mode(memory)); assertFalse(memory.containsKey(PERSON));
        assertFalse(memory.containsKey(EXIT_REQUEST)); assertFalse(memory.containsKey(OUTCOME));
        assertTrue(text(memory, CONTINUATION_FACTS, "").contains("Was macht das IWI?"));
    }

    @ParameterizedTest @ValueSource(strings = {"Ich gebe auf.", "I give up!"})
    void explicitGiveUpClearsTheRoundWithoutDuplicatingLivesReveal(String utterance) {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""));
        say("Personenraten bitte");
        clearInvocations(gateway);
        assertNull(speech(say(utterance)));
        assertFalse(memory.containsKey(CONTINUATION_ID));
        assertEquals("RECEPTION", mode(memory));
        for (String key : List.of(PERSON, STARTED, REPLY)) assertFalse(memory.containsKey(key), key);
        assertNull(agent.generate(runtime));
        assertTrue(text(memory, OUTCOME, "").contains("Albert Einstein"));
        verifyNoInteractions(gateway);
    }

    @Test void inferredGiveUpEndsOnlyAnActiveGuessingRound() {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""), answer("GIVE_UP", ""));
        say("Personenraten bitte");
        assertNull(speech(say("Ich gebe auf, kannst du mir sagen, wenn du herausgedenkt hast")));
        assertEquals("RECEPTION", mode(memory));
        assertFalse(memory.containsKey(PERSON));
        assertNull(agent.generate(runtime));
        when(gateway.infer(any())).thenReturn(answer("GIVE_UP", ""));
        assertNull(speech(say("Verrate die Person")));
        assertTrue(text(memory, CONTINUATION_FACTS, "").contains("failed"));
        assertFalse(memory.containsKey(PERSON));
    }

    @Test void uncertainQuestionsAndRulesRemainLiveOwnedWithoutChangingOrRepeatingTheRound() {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""), answer("WAIT", ""));
        say("Personenraten bitte");
        String start = text(memory, CONTINUATION_ID, "");
        for (String question : List.of("Hatte die Person Schuhgrösse 42?", "Lebt die", "Was passiert, wenn ich aufgebe?")) {
            assertNull(say(question));
            assertEquals("GUESS", mode(memory));
            assertEquals("Albert Einstein", text(memory, PERSON, ""));
            assertEquals(start, text(memory, CONTINUATION_ID, ""));
        }
        var instructions = new LiveVoicePolicyAdapter().instructions(agent);
        assertTrue(instructions.contains("Missing facts are unknown, not false"));
        assertTrue(instructions.contains("Hypothetical/negated give-up, stop, silence or forbidden disclosure never reveal"));
        assertTrue(instructions.contains("Fresh camera estimates support tentative expression/apparent-mood"));
        assertFalse(instructions.contains("emotion diagnosis"));
    }

    @Test void negatedGiveUpDoesNotUseLocalRevealAndQuietNeverDisclosesTheName() {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""), answer("WAIT", ""));
        say("Personenraten bitte");
        assertNull(say("Ich gebe auf keinen Fall auf"));
        assertEquals("GUESS", mode(memory));
        verify(gateway, times(2)).infer(any());
        clearInvocations(gateway);
        assertNull(say("Bitte sei still"));
        assertEquals("RECEPTION", mode(memory));
        assertFalse(memory.containsKey(PERSON));
        verifyNoInteractions(gateway);
    }

    @Test void stopAndQuietExitEveryGameWithoutProviderAndQuietSurvivesSensorUpdates() {
        for (String operation : List.of("START_RPS", "START_GUESS")) {
            when(gateway.infer(any())).thenReturn(answer(operation, ""));
            say(operation.equals("START_RPS") ? "Schere Stein Papier bitte" : "Personenraten bitte");
            clearInvocations(gateway);
            assertNotNull(say("Stopp!"));
            assertEquals("Gala reception", agent.getCurrentState().getName());
            assertFalse(memory.containsKey(PERSON));
            assertFalse(memory.containsKey(SIGN));
            verifyNoInteractions(gateway);
        }
        assertNull(say("Bitte sei still"));
        assertNull(group(1, Instant.now()));
        assertNull(agent.tick(runtime));
        when(gateway.infer(any())).thenReturn(answer("CHAT", "Grüezi!"));
        assertNull(speech(say("Hallo")));
        assertFalse(memory.containsKey(QUIET));
    }

    @Test void incompleteReceptionRequestCanClarifyWithoutStartingAnActivityOrReportingFailure() {
        when(gateway.infer(any())).thenReturn(answer("CLARIFY", ""));
        assertNull(say("Können wir"));
        assertEquals("RECEPTION", mode(memory));
        assertFalse(memory.containsKey(PERSON)); assertFalse(memory.containsKey(SIGN));
        assertNull(agent.generate(runtime));
        verify(gateway, times(1)).infer(any());
    }

    @Test void unspecifiedPlayAsksForChoiceEvenWhenTheModelWouldStartThePreviouslyMentionedGame() {
        agent.getEventHistory().appendEvent(Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, "assistant",
                "{\"speech\":\"Ich kann kleine Spiele anbieten, etwa Schere, Stein, Papier.\"}"));
        when(gateway.infer(any())).thenReturn(answer("START_RPS", ""));
        for (String request : List.of("Können wir etwas spielen?", "Die Können wir etwas spielen",
                "Okay. Können wir etwas [laugh] spielen?", "Lass uns ein Spiel spielen!",
                "Spielen wir?", "Was können wir spielen?", "Can we play something?")) {
            assertNull(say(request), request);
            assertEquals("RECEPTION", mode(memory));
            assertFalse(memory.containsKey(SIGN)); assertFalse(memory.containsKey(PERSON));
            assertFalse(memory.containsKey(ROUND)); assertFalse(memory.containsKey(STARTED));
        }
        verifyNoInteractions(gateway);
        // Other phrasings can use the same choice operation through normal language interpretation.
        when(gateway.infer(any())).thenReturn(answer("CHOOSE_GAME", ""));
        assertNull(say("Ich hätte Lust auf ein Spiel."));
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""));
        assertNull(speech(say("Wie wäre es mit einem Ratespiel?")));
        assertTrue(text(memory, CONTINUATION_FACTS, "").contains("Guessing round started"));
        assertEquals("GUESS", mode(memory));
        assertEquals("Albert Einstein", text(memory, PERSON, ""));
    }

    @ParameterizedTest @ValueSource(strings = {"START_RPS", "START_GUESS"})
    void noiseAndNeutralWaitingPreserveTheGameAndExplicitStopStillWorks(String start) {
        when(gateway.infer(any())).thenReturn(answer(start, ""));
        say(start.equals("START_RPS") ? "Schere Stein Papier bitte" : "Personenraten bitte");
        var before = memory.toMap();
        clearInvocations(gateway);
        when(gateway.infer(any())).thenReturn(answer("QUIET", "")); // Reproduce the harmful model choice.
        for (String sound : List.of("[laugh]", "[laughter] [noise]", "[coughing]", "[lacht]", "Ha ha!")) {
            assertNull(say(sound));
            assertEquals(before, memory.toMap(), "Noise must not clear game facts or renew the idle timer");
            assertEquals(sound, agent.getEventHistory().toList().getLast().getPayload());
        }
        verifyNoInteractions(gateway);
        when(gateway.infer(any())).thenReturn(answer("WAIT", ""));
        assertNull(say("Einen Moment bitte"));
        assertEquals(before.get(MODE), memory.get(MODE));
        assertEquals(before.get(STARTED), memory.get(STARTED));
        assertEquals(before.get(SIGN), memory.toMap().get(SIGN));
        assertEquals(before.get(PERSON), memory.toMap().get(PERSON));
        clearInvocations(gateway);
        assertNotNull(say("[laugh] Stopp!"));
        assertEquals("RECEPTION", mode(memory));
        verifyNoInteractions(gateway);
        assertNull(say("Bitte sei still [laugh]"));
        var quiet = memory.toMap();
        assertNull(say("[laugh]"));
        assertEquals(quiet, memory.toMap(), "Noise cannot undo an explicit silence request");
    }

    @Test void laughterFollowedByFreshHandProducesExactlyOneResultWithoutInference() {
        when(gateway.infer(any())).thenReturn(answer("START_RPS", ""));
        say("Spielen wir Schere Stein Papier");
        when(gateway.infer(any())).thenReturn(answer("QUIET", ""));
        clearInvocations(gateway);
        assertNull(say("[laugh]"));
        var result = BehaviourPlan.fromJson(hand("scissor", Instant.now(), .731).getPayload());
        assertEquals("agent", result.getDisplay().getAsJsonObject().get("winner").getAsString());
        assertNull(result.getSpeech());
        assertTrue(text(memory, CONTINUATION_FACTS, "").contains("winner: agent"));
        assertEquals("RECEPTION", mode(memory));
        assertNull(hand("rock", Instant.now(), .735));
        assertNull(agent.generate(runtime));
        verifyNoInteractions(gateway);
    }

    @Test void invalidProviderControlCannotStartGamesAndInferenceFailurePreservesTheValidRound() {
        for (String invalid : List.of("not json", answer("YES", "Ja"),
                answer("START_GUESS", "").replace("\"operation\":\"START_GUESS\"", "\"operation\":42"))) {
            when(gateway.infer(any())).thenReturn(invalid);
            assertNull(speech(say("Hallo")));
            assertTrue(text(memory, CONTINUATION_FACTS, "").contains("failed"));
            assertEquals("Gala reception", agent.getCurrentState().getName());
            assertFalse(memory.containsKey(PERSON));
            assertFalse(memory.containsKey(SIGN));
        }
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""));
        say("Raten");
        when(gateway.infer(any())).thenThrow(new IllegalStateException("provider unavailable"));
        assertNotNull(say("Bist du ein Mann?"));
        assertEquals("Gala guess the person", agent.getCurrentState().getName());
        assertEquals("Albert Einstein", text(memory, PERSON, ""));
        assertTrue(text(memory, CONTINUATION_FACTS, "").contains("Prior activity remains GUESS"));
        assertTrue(text(memory, CONTINUATION_FACTS, "").contains("No automatic start or retry is authorized"));
        assertTrue(text(memory, CONTINUATION_FACTS, "").contains("await a new explicit request and backend confirmation"));
        assertNull(agent.generate(runtime));
        say("Stop");
        assertEquals("RECEPTION", mode(memory));
        assertFalse(memory.containsKey(PERSON));
    }

    @Test void unsupportedDecorationDoesNotDestroyControlAndSignsBeforeConfirmationCannotResolveRps() {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""));
        say("Personenraten bitte");
        when(gateway.infer(any())).thenReturn(answer("WAIT", "").replace("NONE", "dance").replace("attentive", "puzzled"));
        assertNull(say("Bist du ein Mann?"));
        assertEquals("Albert Einstein", text(memory, PERSON, ""));
        say("Stop");
        var signDuringInference = new java.util.concurrent.atomic.AtomicReference<Instant>();
        when(gateway.infer(any())).thenAnswer(call -> {
            signDuringInference.set(Instant.now().minusMillis(1));
            return answer("START_RPS", "").replace("NONE", "dance").replace("toward_user", "ceiling");
        });
        var plan = BehaviourPlan.fromJson(say("Schere Stein Papier bitte").getPayload());
        assertEquals("NONE", plan.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        assertEquals("toward_user", plan.getNonVerbal().getAsJsonObject().getAsJsonObject("gaze").get("direction").getAsString());
        assertEquals("RPS", mode(memory));
        assertNull(hand("paper", signDuringInference.get(), .99));
        assertEquals("RPS", mode(memory));
    }

    @ParameterizedTest @ValueSource(strings = {"Ist es Albert Einstein?", "War es Albert Einstein?", "Ich tippe auf Einstein.", "Mein Tipp ist Albert Einstein", "Was it Einstein?"})
    void directNameGuessEndsOnceWithQuietBoundedOutcome(String guess) {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""));
        say("Personenraten bitte"); clearInvocations(gateway);
        assertNull(speech(say(guess))); assertEquals("RECEPTION", mode(memory));
        assertTrue(text(memory, OUTCOME, "").contains("correct_guess"));
        String identity = text(memory, OUTCOME_ID, ""); assertFalse(identity.isBlank());
        assertNull(agent.generate(runtime)); assertEquals(identity, text(memory, OUTCOME_ID, ""));
        var projection = new LiveContextProjection(new PromptMessageAssembler(), new LiveVoicePolicyAdapter());
        assertTrue(projection.project(agent).activity().facts().contains("Albert Einstein"));
        put(memory, OUTCOME_EXPIRES, Instant.now().minusSeconds(1).toString());
        assertFalse(projection.project(agent).activity().facts().contains("Einstein"));
        verifyNoInteractions(gateway);
    }

    @Test void quotesNegationHypotheticalsAndWrongNamesCannotEndGuessingOrExposeAnOutcome() {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""));
        say("Personenraten bitte");
        when(gateway.infer(any())).thenReturn(answer("GIVE_UP", ""));
        for (String speech : List.of("War es Marie Curie?", "Es ist nicht Albert Einstein", "Du sagtest: Albert Einstein",
                "Was passiert, wenn ich aufgebe?", "Ich gebe auf keinen Fall auf", "Ich gebe auf, wenn du willst",
                "Ich gebe auf, aber nenne nicht den Namen", "\"Ich gebe auf\"")) {
            assertFalse(GalaDinnerPeople.guessed(GalaDinnerPeople.PEOPLE.getFirst(), speech));
            assertNull(say(speech)); assertEquals("GUESS", mode(memory)); assertFalse(memory.containsKey(OUTCOME));
        }
        assertTrue(GalaDinnerInput.revealRequest("Verrate mir bitte die Person."));
        assertTrue(GalaDinnerInput.revealRequest("Ich gebe auf, sag mir die Person."));
        assertTrue(GalaDinnerInput.revealRequest("Ich gebe auf, ich weiss es nicht."));
        assertFalse(GalaDinnerInput.revealRequest("Ich gebe auf. Nein, doch nicht."));
    }

    @Test void presenceIsSilentAndOnlyConfirmedDepartureEndsTheGame() {
        remove(memory, SEEN); remove(memory, COUNT);
        Instant baseline = Instant.now().minusSeconds(2);
        assertNull(group(1, baseline));
        assertNull(group(1, baseline.plusMillis(1)));
        assertNull(group(2, baseline.plusMillis(2)));
        assertNull(group(0, baseline.minusSeconds(1)));
        verifyNoInteractions(gateway);
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""));
        say("Raten wir");
        assertNull(speech(group(0, baseline.plusMillis(3))));
        assertEquals("GUESS", mode(memory)); assertTrue(memory.containsKey(PERSON));
        put(memory, ABSENT_SINCE, Instant.now().minusMillis(DEPARTURE_GRACE_MS).toString());
        assertNull(agent.acknowledge(new Event(GalaDinnerPolicy.LIVE_ENDED, "system", Event.KIND_SYSTEM, "departure"), runtime));
        assertEquals("RECEPTION", mode(memory)); assertFalse(memory.containsKey(PERSON));
        clearInvocations(gateway);
        assertNull(say("Bist du Physiker?")); verifyNoInteractions(gateway);
        assertNull(group(1, baseline.plusMillis(4)));
    }

    @Test void idleTimeoutReturnsSilently() {
        when(gateway.infer(any())).thenReturn(answer("START_RPS", "")); say("Schere Stein Papier bitte");
        put(memory, LAST_INPUT, Instant.now().minusSeconds(91).toString());
        assertNull(agent.tick(runtime)); assertEquals("Gala reception", agent.getCurrentState().getName());
        assertFalse(memory.containsKey(SIGN));
    }

    @Test void cancelledInferenceCannotPublishOrStartAGame() {
        var continuing = new java.util.concurrent.atomic.AtomicBoolean(true);
        when(gateway.infer(any())).thenAnswer(call -> { continuing.set(false); return answer("START_RPS", ""); });
        assertNull(agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Schere Stein Papier bitte"),
                runtime.withTaskContinuation(continuing::get)));
        assertEquals("RECEPTION", mode(memory)); assertFalse(memory.containsKey(SIGN));
    }

    @Test void faceChangesProduceOnlyEmbodimentAndStableRefreshesStaySilent() {
        Instant baseline = Instant.now().minusSeconds(1);
        assertNull(face("happy", 0.5, baseline));
        var plan = BehaviourPlan.fromJson(face("happy", 0.95, baseline.plusMillis(1)).getPayload());
        assertNull(plan.getSpeech()); assertEquals("gentleSmile", plan.getNonVerbal().getAsJsonObject()
                .getAsJsonObject("facialExpression").get("type").getAsString());
        assertNull(face("happy", 0.95, baseline.plusMillis(2)));
        assertNull(face("sad", 0.95, baseline.plusMillis(3)));
        put(memory, FACE_REACTED, Instant.now().minusSeconds(6).toString());
        assertNotNull(face("sad", 0.95, baseline.plusMillis(4)));
        assertNull(face("happy", 0.95, baseline.minusSeconds(20))); verifyNoInteractions(gateway);
        String context = new LiveContextProjection(new PromptMessageAssembler(), new LiveVoicePolicyAdapter())
                .project(agent).startupInput().toString();
        assertTrue(context.contains("Nonverbal summary"));
    }
    @Test void visitorCorrectionKeepsTheActivityAndSuppressesUnsolicitedInterpretations() {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", "")); say("Personenraten bitte");
        when(gateway.infer(any())).thenReturn(answer("CORRECT_FACE", ""));
        assertNull(say("Ich bin eigentlich angespannt."));
        assertEquals("GUESS", mode(memory));
        assertEquals("true", text(memory, GalaDinnerSensing.CORRECTED, ""));
        assertTrue(new GalaDinnerPolicy(memory).liveActivityContext().facts().contains("Visitor corrected"));
        say("Stopp"); assertEquals("true", text(memory, GalaDinnerSensing.CORRECTED, ""));
        group(0, Instant.now().plusMillis(1)); assertTrue(memory.containsKey(GalaDinnerSensing.CORRECTED));
        put(memory, ABSENT_SINCE, Instant.now().minusMillis(DEPARTURE_GRACE_MS).toString());
        agent.acknowledge(new Event(GalaDinnerPolicy.LIVE_ENDED, "system", Event.KIND_SYSTEM, "departure"), runtime);
        assertFalse(memory.containsKey(GalaDinnerSensing.CORRECTED));
    }

    @Test void newLiveEncounterExcludesPreviousDialogueAndPrivateFacts() {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", "")); say("Geheimname möchte raten");
        assertFalse(new LiveVoicePolicyAdapter().instructions(agent).contains("Albert Einstein"));
        group(0, Instant.now());
        agent.acknowledge(new Event(GalaDinnerPolicy.LIVE_ENDED, "system", Event.KIND_SYSTEM, "departure"), runtime);
        group(1, Instant.now().plusMillis(1));
        var policy = (GalaDinnerPolicy) agent.getCurrentState().ownPolicy();
        policy.beginLiveConversation(agent.getEventHistory());
        var projection = new LiveContextProjection(new PromptMessageAssembler(), new LiveVoicePolicyAdapter()).project(agent);
        assertFalse(projection.startupInput().toString().contains("Geheimname"));
        assertFalse(projection.startupInput().toString().contains("Einstein"));
        assertNull(projection.activity().continuation());
        assertTrue(projection.startupInput().toString().contains("greet the visible visitors once"));
        put(memory, COUNT, "0");
        assertThrows(IllegalArgumentException.class, () -> policy.beginLiveConversation(agent.getEventHistory()));
    }

    private Event face(String emotion, double confidence, Instant time) {
        return agent.acknowledge(Event.observation(Event.TYPE_FACE_EMOTION, "sensor",
                "{\"emotion\":\"" + emotion + "\",\"confidence\":" + confidence
                + ",\"valence\":0.8,\"ts\":\"" + time + "\"}"), runtime);
    }

    @Test void freshUserTurnAfterTimeoutUsesReceptionAndDoesNotCarryPreviousGuestDialogue() {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""));
        say("Mein Name ist Geheimname, ich will raten");
        put(memory, LAST_INPUT, Instant.now().minusSeconds(91).toString());
        when(gateway.infer(any())).thenReturn(answer("CHAT", "Grüezi!"));
        clearInvocations(gateway);
        say("Hallo");
        var capture = ArgumentCaptor.forClass(InferenceRequest.class);
        verify(gateway).infer(capture.capture());
        assertFalse(capture.getValue().messages().stream().anyMatch(message -> message.getContent().contains("Geheimname")));
        assertEquals("Gala reception", agent.getCurrentState().getName());
    }

    @Test void resetClearsOwnedMemoryAndPreservesOtherStorageAndPersona() {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""));
        say("Raten");
        put(memory, "unrelated", "keep");
        agent.reset();
        assertEquals("Gala reception", agent.getCurrentState().getName());
        assertEquals("GIGI", agent.getPersonaName());
        assertEquals("keep", text(memory, "unrelated", ""));
        assertTrue(memory.toMap().keySet().stream().noneMatch(key -> key.startsWith(PREFIX)));
    }

    private Event say(String speech) {
        return agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, speech), runtime);
    }
    private Event hand(String sign, Instant time, double confidence) {
        return hand(sign, time, confidence, text(memory, CAPTURE_ID, ""));
    }
    private Event hand(String sign, Instant time, double confidence, String captureId) {
        var payload = new JsonObject(); payload.addProperty("sign", sign); payload.addProperty("confidence", confidence);
        payload.addProperty("ts", time.toString());
        if (captureId != null) payload.addProperty("captureId", captureId);
        return agent.acknowledge(Event.observation(Event.TYPE_HAND_SIGN, "sensor",
                payload.toString()), runtime);
    }
    private Event group(int count, Instant time) {
        return agent.acknowledge(Event.observation(Event.TYPE_SOCIAL_GROUPING, "sensor",
                "{\"humanCount\":" + count + ",\"ts\":\"" + time + "\"}"), runtime);
    }
    static String answer(String operation, String speech) {
        var answer = new JsonObject();
        answer.addProperty("operation", operation);
        answer.addProperty("gesture", "NONE");
        answer.addProperty("expression", "attentive");
        answer.addProperty("gaze", "toward_user");
        return answer.toString();
    }
    private static String speech(Event event) { return event == null ? null : BehaviourPlan.fromJson(event.getPayload()).getSpeech(); }
}
