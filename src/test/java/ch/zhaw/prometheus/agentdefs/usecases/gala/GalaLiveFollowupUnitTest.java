package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.google.gson.*;
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReception;
import ch.zhaw.prometheus.application.live.*;
import ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.*;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

/** Synthetic reproductions, not recordings. Known-failure characterizations are labelled explicitly. */
class GalaLiveFollowupUnitTest {
    private final Agent agent = new GalaDinnerReception().createAgent();
    private final Storage memory = ((GalaDinnerPolicy) agent.getCurrentState().ownPolicy()).storage();
    private final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    private final PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway)
            .withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID()));
    private final LiveContextProjection projection = new LiveContextProjection(new PromptMessageAssembler(), new LiveVoicePolicyAdapter());

    private void say(String operation, String utterance) {
        put(memory, COUNT, "1"); put(memory, SEEN, Instant.now().toString());
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer(operation, ""));
        agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, utterance), runtime);
    }

    private void hand() {
        agent.acknowledge(Event.observation(Event.TYPE_HAND_SIGN, "sensor",
                "{\"sign\":\"paper\",\"confidence\":0.99,\"captureId\":\"" + text(memory, CAPTURE_ID, "") + "\",\"ts\":\"" + Instant.now() + "\"}"), runtime);
    }

    @Test void exportGameExitAndWaitingFixturesFromActualContexts() throws Exception {
        put(memory, COUNT, "1"); put(memory, SEEN, Instant.now().toString());
        var initial = projection.project(agent); var delivery = new LiveContextDelivery(initial); delivery.ready();
        var gson = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(Instant.class,
                (JsonSerializer<Instant>) (value, type, context) -> new JsonPrimitive(value.toString())).create();
        var fixture = JsonParser.parseString(Files.readString(Path.of("tests/gptlive/gala_game_exit_scenario.json"))).getAsJsonObject();
        fixture.addProperty("instructions", initial.instructions()); fixture.add("input", initial.startupInput());
        say("START_GUESS", "Personenraten bitte"); export(fixture, "guessStart", delivery, gson);
        say("CHECK_EXIT", "Was macht das IWI?"); export(fixture, "confirmation", delivery, gson);
        say("KEEP_GAME", "Nein"); export(fixture, "continueGame", delivery, gson);
        say("WAIT", "Lebt die Person noch?");
        say("CHECK_EXIT", "Wann beginnt das Essen?"); export(fixture, "confirmationAgain", delivery, gson);
        say("EXIT_GAME", "Ja"); export(fixture, "exitGame", delivery, gson);
        say("START_RPS", "Schere Stein Papier bitte"); export(fixture, "rpsStart", delivery, gson);
        hand(); export(fixture, "rpsEnd", delivery, gson);
        Files.writeString(Path.of("target/gala-game-exit-fixture.json"), gson.toJson(fixture));
    }

    @Test void exportCurrentGameExitClassifierCases() throws Exception {
        var rows = new JsonArray();
        for (var entry : List.of(
                new String[]{"START_GUESS", "no", "Arbeitet die Person an der ZHAW?", "WAIT"},
                new String[]{"START_GUESS", "no", "Welche Schuhgrösse hat die Person?", "WAIT"},
                new String[]{"START_GUESS", "no", "Was macht das Institut für Wirtschaftsinformatik?", "CHECK_EXIT"},
                new String[]{"START_RPS", "no", "Wann beginnt das Essen heute Abend?", "CHECK_EXIT"},
                new String[]{"START_GUESS", "yes", "Bitte beende die Runde und beantworte meine andere Frage.", "EXIT_GAME"},
                new String[]{"START_RPS", "yes", "Ich möchte lieber noch weiterspielen.", "KEEP_GAME"},
                new String[]{"START_GUESS", "yes", "Okay", "WAIT"},
                new String[]{"START_GUESS", "no", "Ich möchte mit dem Spiel aufhören.", "RETURN"})) {
            GalaDinnerMemory.clear(memory); agent.reset();
            say(entry[0], entry[0].equals("START_GUESS") ? "Personenraten bitte" : "Schere Stein Papier bitte");
            if (entry[1].equals("yes")) say("CHECK_EXIT", "Was macht das IWI?");
            clearInvocations(gateway); say(entry[3], entry[2]);
            var capture = org.mockito.ArgumentCaptor.forClass(ch.zhaw.prometheus.spi.InferenceRequest.class);
            verify(gateway).infer(capture.capture());
            var row = new JsonObject(); row.addProperty("expected", entry[3]);
            var messages = new JsonArray();
            for (var message : capture.getValue().messages()) {
                var value = new JsonObject(); value.addProperty("role", message.getRole()); value.addProperty("content", message.getContent()); messages.add(value);
            }
            row.add("messages", messages); rows.add(row);
        }
        Files.writeString(Path.of("target/gala-game-exit-control-cases.json"), new Gson().toJson(rows));
    }

    @Test void dropoutBlocksHandResultsButKeepsRoundAndSpokenGiveUpAvailable() {
        say("START_RPS", "Schere Stein Papier bitte");
        String sign = text(memory, SIGN, ""), round = text(memory, GalaDinnerRounds.ID, "");
        agent.acknowledge(Event.observation(Event.TYPE_SOCIAL_GROUPING, "sensor",
                "{\"humanCount\":0,\"ts\":\"" + Instant.now().plusMillis(1) + "\"}"), runtime);
        clearInvocations(gateway); hand();
        assertEquals("RPS", mode(memory)); assertEquals(sign, text(memory, SIGN, ""));
        assertEquals(round, text(memory, GalaDinnerRounds.ID, "")); assertNull(agent.generate(runtime)); verifyNoInteractions(gateway);
        say("RETURN", "Stopp"); say("START_GUESS", "Personenraten bitte");
        String person = text(memory, PERSON, "");
        agent.acknowledge(Event.observation(Event.TYPE_SOCIAL_GROUPING, "sensor",
                "{\"humanCount\":0,\"ts\":\"" + Instant.now().plusMillis(1) + "\"}"), runtime);
        clearInvocations(gateway);
        agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Ich gebe auf"), runtime);
        assertEquals("RECEPTION", mode(memory)); assertTrue(recentOutcome(memory, Instant.now()).contains(person));
        verifyNoInteractions(gateway); assertNull(agent.generate(runtime));
    }

    @Test void slowPositiveCommitCannotDestroyAnEncounterThatTheBrowserAlreadyResumed() {
        say("START_GUESS", "Personenraten bitte"); String person = text(memory, PERSON, "");
        Instant observed = Instant.now().minusSeconds(1); put(memory, SEEN, observed.minusMillis(1).toString());
        agent.acknowledge(Event.observation(Event.TYPE_SOCIAL_GROUPING, "sensor",
                "{\"humanCount\":0,\"ts\":\"" + observed + "\"}"), runtime);
        put(memory, ABSENT_SINCE, Instant.now().minusSeconds(3).toString());
        clearInvocations(gateway); agent.tick(runtime);
        assertEquals("GUESS", mode(memory)); assertEquals(person, text(memory, PERSON, ""));
        agent.acknowledge(Event.observation(Event.TYPE_SOCIAL_GROUPING, "sensor",
                "{\"humanCount\":1,\"ts\":\"" + observed.plusMillis(1) + "\"}"), runtime);
        assertEquals("GUESS", mode(memory)); assertFalse(memory.containsKey(ABSENT_SINCE)); verifyNoInteractions(gateway);
    }

    @Test void neutralAcknowledgementsCannotExitGamesEvenWithADestructiveClassifier() {
        for (String start : List.of("START_GUESS", "START_RPS")) {
            say("RETURN", "Stop");
            say(start, "Ich möchte dieses Spiel spielen");
            String mode = mode(memory), person = text(memory, PERSON, ""), sign = text(memory, SIGN, "");
            String round = text(memory, GalaDinnerRounds.ID, ""), began = text(memory, STARTED, "");
            String occasion = text(memory, CONTINUATION_ID, ""), expiry = text(memory, CONTINUATION_EXPIRES, "");
            clearInvocations(gateway);
            for (String text : List.of("Ja, gerne.", "Okay!", "Alles klar", "Danke", "Sure", "Thank you")) {
                say("RETURN", text);
                assertEquals(mode, mode(memory));
                assertEquals(person, text(memory, PERSON, "")); assertEquals(sign, text(memory, SIGN, ""));
                assertEquals(round, text(memory, GalaDinnerRounds.ID, "")); assertEquals(began, text(memory, STARTED, ""));
                assertEquals(occasion, text(memory, CONTINUATION_ID, "")); assertEquals(expiry, text(memory, CONTINUATION_EXPIRES, ""));
                assertNull(agent.generate(runtime));
            }
            verify(gateway, never()).infer(any());
        }
    }

    @Test void acknowledgementPrefixesAndRealChoicesKeepTheirControlMeaning() {
        for (String text : List.of("Ja", "Nein", "Okay, ich gebe auf", "Ja, aber hör auf", "Danke, jetzt etwas anderes"))
            assertFalse(GalaDinnerInput.acknowledgement(text), text);
        say("START_GUESS", "Ja, gerne."); // Reception acceptance still uses context/classification.
        assertEquals("GUESS", mode(memory));
        say("WAIT", "Ist die Person eine Frau?");
        String target = text(memory, PERSON, "");
        say("GIVE_UP", "Okay, ich gebe auf.");
        assertEquals("RECEPTION", mode(memory));
        assertTrue(recentOutcome(memory, Instant.now()).contains(target));
        say("START_RPS", "Schere Stein Papier bitte");
        say("RETURN", "Danke, jetzt erzähl mir vom Gala Dinner");
        assertEquals("RECEPTION", mode(memory));
    }

    @Test void controlTraceContainsLabelsAndEventIdentityWithoutReplacingTheWaitingCue() {
        say("START_GUESS", "Personenraten bitte");
        var sink = mock(ch.zhaw.prometheus.logging.ActivityTrace.Sink.class);
        try (var scope = ch.zhaw.prometheus.logging.ActivityTrace.attach(sink)) {
            say("RETURN", "Ja, gerne");
        }
        verify(sink).stage("gala_control/GUESS/LOCAL/WAIT/GUESS/acknowledgement");
        verify(sink).event(any());
        verify(sink, never()).cue(any(), any());
    }

    @Test void explicitGiveUpWithoutDisclosureEndsLocallyWhileHypotheticalsStayInTheGame() {
        say("START_GUESS", "Personenraten bitte");
        clearInvocations(gateway);
        say("GIVE_UP", "Ich gebe auf, aber verrate den Namen nicht.");
        assertEquals("RECEPTION", mode(memory));
        assertFalse(memory.containsKey(PERSON)); assertFalse(memory.containsKey(OUTCOME));
        verify(gateway, never()).infer(any());
        for (String text : List.of("Wenn ich gebe auf, aber verrate den Namen nicht", "Ich gebe nicht auf",
                "Ich gebe auf keinen Fall auf", "Er sagt: \"Ich gebe auf, aber verrate den Namen nicht\""))
            assertFalse(GalaDinnerInput.giveUpWithoutReveal(text), text);
    }

    @Test void onlyNeutralCompletedRoundRepliesOptOutOfAmbiguousAdmission() {
        var policy = new GalaDinnerPolicy(memory);
        assertFalse(policy.liveNonMutatingAcknowledgement("Okay"));
        say("START_RPS", "Schere Stein Papier bitte"); hand();
        assertEquals("RECEPTION", mode(memory));
        assertTrue(policy.liveNonMutatingAcknowledgement("Okay!"));
        for (String input : List.of("Ja", "Nein", "Ja, gerne", "Okay, Personenraten bitte"))
            assertFalse(policy.liveNonMutatingAcknowledgement(input), input);
        clearInvocations(gateway);
        String occasion = text(memory, CONTINUATION_ID, "");
        say("START_GUESS", "Okay");
        verify(gateway, never()).infer(any()); assertEquals("RECEPTION", mode(memory));
        assertEquals(occasion, text(memory, CONTINUATION_ID, ""));
        put(memory, CONTINUATION_EXPIRES, Instant.now().minusSeconds(1).toString());
        put(memory, OUTCOME_EXPIRES, Instant.now().minusSeconds(1).toString());
        assertFalse(policy.liveNonMutatingAcknowledgement("Okay"));
        put(memory, MODE, "TASK");
        assertFalse(policy.liveNonMutatingAcknowledgement("Okay"));
        assertFalse(new GalaDinnerTaskPolicy(memory).liveNonMutatingAcknowledgement("Okay"));
    }

    @Test void exportCurrentControlParaphrasesForProviderComparison() throws Exception {
        var rows = new JsonArray();
        for (var entry : List.of(
                new String[]{"Alles klar, danke.", "WAIT"}, new String[]{"Ja, das klingt gut so.", "WAIT"},
                new String[]{"Nein, ich überlege noch.", "WAIT"}, new String[]{"Warte, ich denke kurz nach.", "WAIT"},
                new String[]{"Ich möchte weiter raten.", "WAIT"}, new String[]{"Lebt die Person noch?", "WAIT"},
                new String[]{"Was passiert, wenn ich aufgebe?", "WAIT"}, new String[]{"Okay, ich gebe auf.", "GIVE_UP"},
                new String[]{"Ja, aber hör bitte auf mit dem Spiel.", "RETURN"},
                new String[]{"Ich will aufhören; den Namen möchte ich nicht wissen.", "RETURN"},
                new String[]{"Erzähl mir stattdessen vom Gala Dinner.", "RETURN"},
                new String[]{"Sei bitte still.", "QUIET"})) {
            say("RETURN", "Stop"); say("START_GUESS", "Personenraten bitte");
            clearInvocations(gateway);
            say(entry[1], entry[0]);
            var capture = org.mockito.ArgumentCaptor.forClass(ch.zhaw.prometheus.spi.InferenceRequest.class);
            verify(gateway).infer(capture.capture());
            var row = new JsonObject(); row.addProperty("question", entry[0]); row.addProperty("expected", entry[1]);
            var messages = new JsonArray();
            for (var message : capture.getValue().messages()) {
                var value = new JsonObject(); value.addProperty("role", message.getRole()); value.addProperty("content", message.getContent());
                messages.add(value);
            }
            row.add("messages", messages); rows.add(row);
        }
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/gala-control-followup-cases.json"), new Gson().toJson(rows));
    }

    @Test void firstAndRepeatedRpsRequestsRemainUnconfirmedUntilInferenceReturns() {
        for (String request : List.of("Schere Stein Papier bitte", "Wollen wir das nochmal spielen?")) {
            put(memory, COUNT, "1"); put(memory, SEEN, Instant.now().toString());
            when(gateway.infer(any())).thenAnswer(call -> {
                assertEquals("RECEPTION", mode(memory));
                assertFalse(memory.containsKey(SIGN));
                assertTrue(projection.project(agent).activity().facts().contains("NOT_CONFIRMED"));
                var pending = projection.project(agent).activity().continuation();
                assertTrue(pending == null || !pending.facts().contains("round started"));
                return GalaDinnerReceptionUnitTest.answer("START_RPS", "");
            });
            agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, request), runtime);
            assertEquals("RPS", mode(memory));
            assertTrue(projection.project(agent).activity().continuation().facts().contains("round started"));
            hand(); assertEquals("RECEPTION", mode(memory));
        }
        verify(gateway, times(2)).infer(any());
    }

    @Test void pausedNonbinaryQuestionStaysOneTurnAndDoesNotAlterTheSelectedPerson() {
        say("START_GUESS", "Personenraten bitte");
        String person = text(memory, PERSON, ""), occasion = text(memory, CONTINUATION_ID, "");
        var capture = new LiveTranscriptSegmenter(UUID.randomUUID(), 2000);
        capture.fragment(fragment("first", 100, "Ist die Person männlich"));
        for (long at = 120; at <= 1300; at += 20) capture.audio(Speaker.USER, 480, false, at);
        assertTrue(capture.poll(1300).isEmpty());
        capture.fragment(fragment("last", 1320, " oder weiblich?"));
        for (long at = 1340; at <= 3320; at += 20) capture.audio(Speaker.USER, 480, false, at);
        var completed = capture.poll(3320);
        assertEquals(1, completed.size()); assertEquals(Closure.COMPLETE, completed.getFirst().closure());
        say("WAIT", completed.getFirst().text());
        assertEquals(person, text(memory, PERSON, ""));
        assertEquals(occasion, text(memory, CONTINUATION_ID, ""));
        // This establishes backend preservation, not whether Live waits or clarifies the question.
    }

    @Test void repeatedRevealedNameNeedsNoClassificationOrRecoveryAndPreservesOutcomeExpiry() {
        say("START_GUESS", "Personenraten bitte");
        say("WAIT", "Ich gebe auf");
        String outcome = text(memory, OUTCOME, "");
        String expiry = text(memory, OUTCOME_EXPIRES, ""), identity = text(memory, OUTCOME_ID, "");
        assertFalse(outcome.isBlank()); assertFalse(memory.containsKey(CONTINUATION_ID));
        clearInvocations(gateway);
        for (String name : List.of("Albert Einstein", "War es Einstein?", "Es war Albert Einstein.")) {
            say("GIVE_UP", name); // Would be invalid if inference were unnecessarily called.
            assertEquals("RECEPTION", mode(memory)); assertFalse(memory.containsKey(PERSON));
            assertEquals(outcome, text(memory, OUTCOME, ""));
            assertEquals(expiry, text(memory, OUTCOME_EXPIRES, ""));
            assertEquals(identity, text(memory, OUTCOME_ID, ""));
            assertFalse(memory.containsKey(CONTINUATION_ID));
        }
        verifyNoInteractions(gateway);
    }

    @Test void followUpClassifierSeesCurrentOperationsAndOnlyFreshCompletedOutcome() {
        say("START_GUESS", "Personenraten bitte"); say("WAIT", "Ich gebe auf");
        clearInvocations(gateway);
        say("CHAT", "Hat diese Person in der Schweiz gelebt?");
        var request = org.mockito.ArgumentCaptor.forClass(ch.zhaw.prometheus.spi.InferenceRequest.class);
        verify(gateway).infer(request.capture());
        var messages = request.getValue().messages();
        String control = messages.getFirst().getContent();
        assertTrue(control.contains("\"operation\":\"" + String.join("|", GalaDinnerPrompts.operations("RECEPTION")) + "\""));
        assertFalse(GalaDinnerPrompts.operations("RECEPTION").contains("GIVE_UP"));
        assertTrue(messages.stream().anyMatch(m -> m.getContent().startsWith("Recent completed round")
                && m.getContent().contains("Albert Einstein")));
        assertFalse(memory.containsKey(CONTINUATION_ID));
        put(memory, OUTCOME_EXPIRES, Instant.now().minusSeconds(1).toString());
        clearInvocations(gateway); say("CHAT", "Albert Einstein");
        verify(gateway).infer(request.capture());
        assertTrue(request.getValue().messages().stream().noneMatch(m -> m.getContent().startsWith("Recent completed round")));
        assertFalse(memory.containsKey(CONTINUATION_ID));
    }

    @Test void invalidModeOperationCannotChangeAnActiveTargetAndRealFailureStillHasRecovery() {
        say("START_GUESS", "Personenraten bitte");
        say("START_RPS", "Wechseln wir das Spiel");
        assertEquals("GUESS", mode(memory)); assertEquals("Albert Einstein", text(memory, PERSON, ""));
        assertFalse(memory.containsKey(SIGN));
        assertTrue(text(memory, CONTINUATION_FACTS, "").contains("failed"));
        say("WAIT", "Stop");
        say("GIVE_UP", "Starte Schere Stein Papier");
        assertEquals("RECEPTION", mode(memory)); assertFalse(memory.containsKey(SIGN));
        assertTrue(text(memory, CONTINUATION_FACTS, "").contains("failed"));
    }

    @Test void isolatedActivitySpikesNoLongerMergeThreeSeparateQuestions() {
        var capture = new LiveTranscriptSegmenter(UUID.randomUUID(), 2000);
        var filter = new LivePcmActivity.OnsetFilter();
        var completed = new java.util.ArrayList<Segment>();
        var questions = List.of("Ist die Person männlich?", "Hat die Person echt gelebt?", "Hat sie in Europa gelebt?");
        for (int question = 0; question < questions.size(); question++) {
            long start = 100 + question * 3000;
            capture.fragment(fragment("question-" + question, start, questions.get(question)));
            for (long offset = 20; offset <= 2980; offset += 20) {
                // A single 20-ms activity indication each second interrupts measured quiet.
                byte[] pcm = new byte[960];
                int level = offset % 1000 == 0 ? 600 : 0;
                for (int i = 0; i < pcm.length; i += 2) { pcm[i] = (byte) level; pcm[i + 1] = (byte) (level >> 8); }
                var activity = filter.measure(pcm, start + offset);
                capture.audio(Speaker.USER, activity.samples(), activity.trailingQuietSamples(), activity.voiced(), start + offset);
                completed.addAll(capture.poll(start + offset, filter.pending()));
            }
        }
        assertEquals(3, completed.size());
        assertEquals(questions, completed.stream().map(Segment::text).toList());
        assertTrue(completed.stream().allMatch(s -> s.closure() == Closure.COMPLETE && s.fragments().size() == 1));
    }

    @Test void exportActualGameContextsForRepeatableProviderTrials() throws Exception {
        put(memory, COUNT, "1"); put(memory, SEEN, Instant.now().toString());
        var initial = projection.project(agent);
        var delivery = new LiveContextDelivery(initial); delivery.ready();
        var gson = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(Instant.class,
                (JsonSerializer<Instant>) (value, type, context) -> new JsonPrimitive(value.toString())).create();
        var fixture = new JsonObject();
        fixture.addProperty("instructions", initial.instructions()); fixture.add("input", initial.startupInput());
        say("START_RPS", "Schere Stein Papier bitte"); export(fixture, "rpsStart", delivery, gson);
        hand(); export(fixture, "rpsEnd", delivery, gson);
        say("START_RPS", "Wollen wir das nochmal spielen?"); export(fixture, "rpsReplay", delivery, gson);
        hand(); export(fixture, "rpsReplayEnd", delivery, gson);
        say("START_GUESS", "Personenraten bitte"); export(fixture, "guessStart", delivery, gson);
        assertEquals("Albert Einstein", text(memory, PERSON, ""));
        say("WAIT", "Ich gebe auf"); export(fixture, "guessEnd", delivery, gson);
        assertNull(projection.project(agent).activity().continuation());
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/gala-followup-fixture.json"), gson.toJson(fixture));
    }

    private void export(JsonObject fixture, String name, LiveContextDelivery delivery, Gson gson) {
        var batch = delivery.plan(projection.project(agent), List.of());
        assertFalse(batch.commands().isEmpty());
        fixture.add(name, gson.toJsonTree(batch.commands())); delivery.acknowledged(batch);
    }

    private static Fragment fragment(String id, long at, String text) {
        return new Fragment(id, Speaker.USER, at, at + 20, at, at, text);
    }

    @Test void contextualFollowUpsCannotStartOrExitGamesAndPreferLatestSelectedFaceEvidence() {
        var now = Instant.now().minusMillis(100);
        sensedFace("neutral", .99, now.minusSeconds(2));
        say("CHAT", "Wie wirkt mein Gesichtsausdruck?");
        sensedFace("happy", .99, now);
        clearInvocations(gateway);
        say("START_RPS", "Und jetzt?");
        assertEquals("RECEPTION", mode(memory)); assertNull(agent.generate(runtime)); verifyNoInteractions(gateway);
        var selected = projection.project(agent);
        var face = selected.items().stream().filter(i -> Event.TYPE_FACE_EMOTION.equals(i.type())).findFirst().orElseThrow();
        assertTrue(face.text().contains("happy")); assertEquals(now.plusSeconds(15), face.expiresAt());
        assertTrue(selected.instructions().contains("Current follow-up topic overrides sensors"));
        assertTrue(selected.instructions().contains("not older averages"));
        for (String start : List.of("START_GUESS", "START_RPS")) {
            say("RETURN", "Stop"); say(start, "Dieses Spiel bitte");
            String before = mode(memory), target = text(memory, PERSON, ""), sign = text(memory, SIGN, "");
            clearInvocations(gateway); say("RETURN", "Und nun?");
            assertEquals(before, mode(memory)); assertEquals(target, text(memory, PERSON, ""));
            assertEquals(sign, text(memory, SIGN, "")); verifyNoInteractions(gateway);
        }
        for (String input : List.of("Und jetzt Personenraten", "And now stop", "Und jetzt gib auf"))
            assertFalse(GalaDinnerInput.contextualFollowUp(input));
    }

    @Test void faceFollowUpDoesNotRenewEvidenceOverrideCorrectionOrCreateAStickyTopic() {
        Instant observed = Instant.now().minusSeconds(16);
        sensedFace("happy", .99, observed);
        say("CHAT", "Wie wirkt mein Gesichtsausdruck?"); say("START_RPS", "Und jetzt?");
        var selected = projection.project(agent);
        var face = selected.items().stream().filter(i -> Event.TYPE_FACE_EMOTION.equals(i.type())).findFirst().orElseThrow();
        assertEquals("expired", face.freshness()); assertFalse(face.text().contains("happy"));
        assertEquals(observed.plusSeconds(15), face.expiresAt());
        say("CORRECT_FACE", "Ich bin eigentlich angespannt"); say("CHAT", "Wann beginnt das Essen?");
        say("START_GUESS", "Und jetzt?"); assertEquals("RECEPTION", mode(memory));
        assertTrue(new GalaDinnerPolicy(memory).liveActivityContext().facts().contains("Visitor corrected"));
        assertFalse(memory.toMap().keySet().stream().anyMatch(k -> k.contains("topic")));
        assertFalse(memory.containsKey(CONTINUATION_ID));
    }

    @Test void exportActualPerceptionFollowUpContexts() throws Exception {
        put(memory, COUNT, "1"); put(memory, SEEN, Instant.now().toString());
        var initial = projection.project(agent); var delivery = new LiveContextDelivery(initial); delivery.ready();
        var gson = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(Instant.class,
                (JsonSerializer<Instant>) (value, type, context) -> new JsonPrimitive(value.toString())).create();
        var fixture = new JsonObject(); fixture.addProperty("instructions", initial.instructions()); fixture.add("input", initial.startupInput());
        sensedFace("neutral", .99, Instant.now().minusSeconds(1)); export(fixture, "faceNeutral", delivery, gson);
        say("CHAT", "Wie wirkt mein Gesichtsausdruck?");
        sensedFace("happy", .99, Instant.now()); say("CHAT", "Und jetzt?"); export(fixture, "faceHappy", delivery, gson);
        say("CORRECT_FACE", "Ich bin eigentlich angespannt."); export(fixture, "faceCorrection", delivery, gson);
        var expired = new LiveContextProjection(new PromptMessageAssembler(), new LiveVoicePolicyAdapter(),
                java.time.Clock.fixed(Instant.now().plusSeconds(16), java.time.ZoneOffset.UTC));
        var batch = delivery.plan(expired.project(agent), List.of());
        fixture.add("faceExpired", gson.toJsonTree(batch.commands())); delivery.acknowledged(batch);
        sensedFace("sad", .2, Instant.now()); export(fixture, "faceUncertain", delivery, gson);
        JsonParser.parseString(Files.readString(Path.of("tests/gptlive/gala_perception_scenario.json")))
                .getAsJsonObject().entrySet().forEach(entry -> fixture.add(entry.getKey(), entry.getValue()));
        Files.writeString(Path.of("target/gala-perception-fixture.json"), gson.toJson(fixture));
    }

    private void sensedFace(String emotion, double confidence, Instant observed) {
        agent.acknowledge(Event.observation(Event.TYPE_FACE_EMOTION, "sensor", "{\"emotion\":\"" + emotion
                + "\",\"confidence\":" + confidence + ",\"ts\":\"" + observed + "\"}"), runtime);
    }

    @Test void faceTopicIsBoundedByUserIntentTimeAndEncounterNotSensorUpdates() {
        Instant start = Instant.parse("2026-10-22T08:00:00Z");
        GalaDinnerDialogue.user(memory, "Wie wirkt mein Gesichtsausdruck?", start);
        assertEquals(start.plusSeconds(30), GalaDinnerDialogue.refreshAt(memory, start));
        assertTrue(GalaDinnerDialogue.facts(memory, start.plusSeconds(29)).contains("topic: facial expression"));
        GalaDinnerDialogue.user(memory, "Und jetzt?", start.plusSeconds(29));
        assertEquals(start, time(memory, GalaDinnerDialogue.FACE_TOPIC_AT));
        assertTrue(GalaDinnerDialogue.facts(memory, start.plusSeconds(30)).contains("NOT facial expression"));
        assertNull(GalaDinnerDialogue.refreshAt(memory, start.plusSeconds(30)));
        GalaDinnerDialogue.user(memory, "How does my face look?", start);
        GalaDinnerDialogue.user(memory, "Wann beginnt das Essen?", start.plusSeconds(1));
        assertFalse(memory.containsKey(GalaDinnerDialogue.FACE_TOPIC_AT));
        GalaDinnerDialogue.user(memory, "Wie ist meine Mimik?", start);
        GalaDinnerDialogue.user(memory, "Personenraten bitte", start.plusSeconds(1));
        assertFalse(memory.containsKey(GalaDinnerDialogue.FACE_TOPIC_AT));
        GalaDinnerDialogue.user(memory, "Wie ist mein Gesicht?", start); endEncounter(memory, 0);
        assertFalse(memory.containsKey(GalaDinnerDialogue.FACE_TOPIC_AT));
        GalaDinnerDialogue.user(memory, "Wie nutzt man Facebook?", start);
        assertFalse(memory.containsKey(GalaDinnerDialogue.FACE_TOPIC_AT));
        Instant current = Instant.now(); GalaDinnerDialogue.user(memory, "Wie ist mein Gesicht?", current);
        assertEquals(current.plusSeconds(30), new GalaDinnerPolicy(memory).liveActivityContext().refreshAt());
        verifyNoInteractions(gateway);
    }

    @Test void exportIntegratedFollowUpJourneyForProviderRepetitions() throws Exception {
        put(memory, COUNT, "1"); put(memory, SEEN, Instant.now().toString());
        var initial = projection.project(agent); var delivery = new LiveContextDelivery(initial); delivery.ready();
        var gson = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(Instant.class,
                (JsonSerializer<Instant>) (value, type, context) -> new JsonPrimitive(value.toString())).create();
        var fixture = JsonParser.parseString(Files.readString(Path.of("tests/gptlive/gala_followup_scenario.json"))).getAsJsonObject();
        fixture.addProperty("instructions", initial.instructions()); fixture.add("input", initial.startupInput());
        say("START_RPS", "Schere Stein Papier bitte"); export(fixture, "rpsStart", delivery, gson);
        hand(); export(fixture, "rpsEnd", delivery, gson);
        say("CHAT", "Okay"); say("START_GUESS", "Personenraten bitte"); export(fixture, "guessStart", delivery, gson);
        String target = text(memory, PERSON, ""); fixture.addProperty("expectedPerson", target);
        say("RETURN", "Ja, gerne");
        Instant observed = Instant.now().minusSeconds(1); put(memory, SEEN, observed.minusMillis(1).toString());
        agent.acknowledge(Event.observation(Event.TYPE_SOCIAL_GROUPING, "sensor",
                "{\"humanCount\":0,\"ts\":\"" + observed + "\"}"), runtime);
        export(fixture, "absence", delivery, gson);
        agent.acknowledge(Event.observation(Event.TYPE_SOCIAL_GROUPING, "sensor",
                "{\"humanCount\":1,\"ts\":\"" + observed.plusMillis(1) + "\"}"), runtime);
        export(fixture, "resumed", delivery, gson);
        assertEquals(target, text(memory, PERSON, "")); assertFalse(memory.containsKey(ABSENT_SINCE));
        say("WAIT", "Lebt die Person noch?"); say("GIVE_UP", "Ich gebe auf"); export(fixture, "guessEnd", delivery, gson);
        fixture.getAsJsonObject("clips").addProperty("name", "War es " + target + "?");
        say("CHAT", "War es " + target + "?");
        sensedFace("neutral", .99, Instant.now()); export(fixture, "faceNeutral", delivery, gson);
        say("CHAT", "Wie wirkt mein Gesichtsausdruck?");
        sensedFace("happy", .99, Instant.now()); say("CHAT", "Und jetzt?"); export(fixture, "faceHappy", delivery, gson);
        say("CHAT", "Wann beginnt das Essen?"); export(fixture, "topicChanged", delivery, gson);
        var expired = new LiveContextProjection(new PromptMessageAssembler(), new LiveVoicePolicyAdapter(),
                java.time.Clock.fixed(Instant.now().plusSeconds(16), java.time.ZoneOffset.UTC));
        var batch = delivery.plan(expired.project(agent), List.of()); fixture.add("faceExpired", gson.toJsonTree(batch.commands()));
        Files.writeString(Path.of("target/gala-integrated-fixture.json"), gson.toJson(fixture));
    }
}
