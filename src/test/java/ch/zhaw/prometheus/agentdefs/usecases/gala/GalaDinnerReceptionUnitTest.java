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

    @Test void rpsCommitsHiddenSignRejectsBadFramesAndReturnsToReceptionWithCalculatedResult() {
        when(gateway.infer(any())).thenReturn(answer("START_RPS", ""));
        var countdown = BehaviourPlan.fromJson(say("Spielen wir Schere Stein Papier").getPayload());
        assertEquals("Gala rock-scissor-paper", agent.getCurrentState().getName());
        assertEquals("rock", text(memory, SIGN, ""));
        assertNull(countdown.getMotion());
        assertFalse(countdown.getDisplay().getAsJsonObject().has("agentSign"));
        Instant began = time(memory, STARTED);
        assertNull(hand("paper", began.minusSeconds(1), 1));
        assertNull(hand("paper", began.plusMillis(1), 0.2));
        assertNull(hand("lizard", began.plusMillis(1), 1));
        assertNull(agent.acknowledge(Event.observation(Event.TYPE_HAND_SIGN, "sensor", "{}"), runtime));
        assertEquals("RPS", mode(memory));
        var result = BehaviourPlan.fromJson(hand("paper", Instant.now(), 0.95).getPayload());
        assertTrue(result.getSpeech().contains("Sie gewinnen"));
        assertEquals("rock", result.getMotion().getAsJsonObject().get("handSign").getAsString());
        assertEquals("Gala reception", agent.getCurrentState().getName());
        assertFalse(memory.containsKey(SIGN));
        assertNull(hand("paper", Instant.now(), 1));
        assertNull(agent.generate(runtime));
        verify(gateway, times(1)).infer(any());
    }

    @Test void guessingKeepsTargetAnswersOnlyYesNoAndCompletesOnDirectName() {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""),
                answer("YES", "Ja, und hier ist ein unerlaubter Hinweis."),
                answer("NO", "Nein, ich bin Albert Einstein."),
                answer("CLARIFY", "Albert Einstein."));
        assertFalse(speech(say("Ich möchte eine Person erraten")).contains("Einstein"));
        assertEquals("Albert Einstein", text(memory, PERSON, ""));
        assertEquals("Ja.", speech(say("Bist du Physiker?")));
        assertEquals("Nein.", speech(say("Bist du Musiker?")));
        assertFalse(speech(say("Welche Schuhgrösse hast du?")).contains("Einstein"));
        assertEquals("Albert Einstein", text(memory, PERSON, ""));
        assertTrue(speech(say("Bist du Albert Einstein?")).startsWith("Ja, genau: Albert Einstein!"));
        assertEquals("Gala reception", agent.getCurrentState().getName());
        assertFalse(memory.containsKey(PERSON));
        verify(gateway, times(4)).infer(any());
        assertFalse(GalaDinnerPeople.guessed(GalaDinnerPeople.PEOPLE.getFirst(), "Du bist nicht Albert Einstein"));
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""));
        say("Noch ein Ratespiel");
        assertEquals("Marie Curie", text(memory, PERSON, ""));
    }

    @Test void offTopicQuestionReturnsHomeAndAnswersTheSameUtterance() {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""),
                answer("RETURN", "Laut Programm gibt es den Hauptgang um 20:50."));
        say("Lass uns raten");
        assertNull(speech(say("Wann kommt der Hauptgang?")));
        assertEquals("Gala reception", agent.getCurrentState().getName());
        assertFalse(memory.containsKey(PERSON));
    }

    @ParameterizedTest @ValueSource(strings = {"Ich gebe auf.", "I give up!"})
    void explicitGiveUpRevealsStoredPersonOnceWithoutInference(String utterance) {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""));
        say("Personenraten bitte");
        clearInvocations(gateway);
        assertEquals("Es war Albert Einstein. Die Runde ist beendet; ich bin wieder für Ihre Fragen da.", speech(say(utterance)));
        assertEquals("RECEPTION", mode(memory));
        for (String key : List.of(PERSON, STARTED, REPLY)) assertFalse(memory.containsKey(key), key);
        assertNull(agent.generate(runtime));
        verifyNoInteractions(gateway);
    }

    @Test void inferredRevealUsesBackendTargetAndRequiresAnActiveGuessingRound() {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""), answer("GIVE_UP", ""));
        say("Personenraten bitte");
        assertTrue(speech(say("Ich gebe auf, kannst du mir sagen, wenn du herausgedenkt hast")).startsWith("Es war Albert Einstein."));
        assertEquals("RECEPTION", mode(memory));
        assertFalse(memory.containsKey(PERSON));
        assertNull(agent.generate(runtime));
        when(gateway.infer(any())).thenReturn(answer("GIVE_UP", ""));
        assertTrue(speech(say("Verrate die Person")).contains("nicht geklappt"));
        assertFalse(memory.containsKey(PERSON));
    }

    @Test void unknownFactsAndIncompleteQuestionsDifferWithoutLeakingOrChangingTheTarget() {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""), answer("UNKNOWN", ""),
                answer("CLARIFY", ""), answer("RULES", ""));
        say("Personenraten bitte");
        assertTrue(speech(say("Hatte die Person Schuhgrösse 42?")).startsWith("Dazu habe ich keine gesicherte Information."));
        assertTrue(speech(say("Lebt die")).contains("noch einmal vollständig"));
        assertTrue(speech(say("Was passiert, wenn ich aufgebe?")).contains("Wenn Sie aufgeben"));
        assertEquals("GUESS", mode(memory));
        assertEquals("Albert Einstein", text(memory, PERSON, ""));
        assertFalse(new LiveVoicePolicyAdapter().instructions(agent).contains("Albert Einstein"));
        var capture = ArgumentCaptor.forClass(InferenceRequest.class);
        verify(gateway, times(4)).infer(capture.capture());
        String privatePrompt = capture.getAllValues().get(1).messages().getFirst().getContent();
        for (String fact : List.of("not alive", "Switzerland", "co-invented a refrigerator"))
            assertTrue(privatePrompt.contains(fact), fact);
        assertTrue(privatePrompt.contains("Use UNKNOWN"));
        assertFalse(privatePrompt.contains("https://"));
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
        assertEquals("Was möchten Sie gern tun?", speech(say("Können wir")));
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
            assertEquals("Personenraten oder Schere, Stein, Papier?", speech(say(request)), request);
            assertEquals("RECEPTION", mode(memory));
            assertFalse(memory.containsKey(SIGN)); assertFalse(memory.containsKey(PERSON));
            assertFalse(memory.containsKey(ROUND)); assertFalse(memory.containsKey(STARTED));
        }
        verifyNoInteractions(gateway);
        // Other phrasings can use the same choice operation through normal language interpretation.
        when(gateway.infer(any())).thenReturn(answer("CHOOSE_GAME", ""));
        assertEquals("Personenraten oder Schere, Stein, Papier?", speech(say("Ich hätte Lust auf ein Spiel.")));
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""));
        assertTrue(speech(say("Wie wäre es mit einem Ratespiel?")).contains("Ja-Nein-Fragen"));
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
        assertEquals("Ich habe Stein, Sie haben Schere. Diese Runde geht an mich!", result.getSpeech());
        assertEquals("RECEPTION", mode(memory));
        assertNull(hand("rock", Instant.now(), .735));
        assertNull(agent.generate(runtime));
        verifyNoInteractions(gateway);
    }

    @Test void invalidProviderOutputOrFailureCannotStartGamesOrKeepAnActivityStuck() {
        for (String invalid : List.of("not json", answer("YES", "Ja"), answer("START_RPS", "").replace("NONE", "dance"),
                answer("START_GUESS", "").replace("\"operation\":\"START_GUESS\"", "\"operation\":42"))) {
            when(gateway.infer(any())).thenReturn(invalid);
            assertTrue(speech(say("Hallo")).contains("nicht geklappt"));
            assertEquals("Gala reception", agent.getCurrentState().getName());
            assertFalse(memory.containsKey(PERSON));
            assertFalse(memory.containsKey(SIGN));
        }
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", ""));
        say("Raten");
        when(gateway.infer(any())).thenThrow(new IllegalStateException("provider unavailable"));
        assertNotNull(say("Bist du ein Mann?"));
        assertEquals("Gala reception", agent.getCurrentState().getName());
        assertFalse(memory.containsKey(PERSON));
    }

    @Test void presenceIsSilentAndDepartureEndsTheGameImmediatelyWithoutTicks() {
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

    @Test void newLiveEncounterExcludesPreviousDialogueAndPrivateFacts() {
        when(gateway.infer(any())).thenReturn(answer("START_GUESS", "")); say("Geheimname möchte raten");
        assertFalse(new LiveVoicePolicyAdapter().instructions(agent).contains("Albert Einstein"));
        group(0, Instant.now()); group(1, Instant.now().plusMillis(1));
        var policy = (GalaDinnerPolicy) agent.getCurrentState().ownPolicy();
        policy.beginLiveConversation(agent.getEventHistory());
        var projection = new LiveContextProjection(new PromptMessageAssembler(), new LiveVoicePolicyAdapter()).project(agent);
        assertFalse(projection.startupInput().toString().contains("Geheimname"));
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
        return agent.acknowledge(Event.observation(Event.TYPE_HAND_SIGN, "sensor",
                "{\"sign\":\"" + sign + "\",\"confidence\":" + confidence + ",\"ts\":\"" + time + "\"}"), runtime);
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
    private static String speech(Event event) { return BehaviourPlan.fromJson(event.getPayload()).getSpeech(); }
}
