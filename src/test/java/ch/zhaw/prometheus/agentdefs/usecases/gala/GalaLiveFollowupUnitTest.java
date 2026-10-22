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
                "{\"sign\":\"paper\",\"confidence\":0.99,\"ts\":\"" + Instant.now() + "\"}"), runtime);
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
        assertTrue(messages.stream().anyMatch(m -> m.getContent().startsWith("Recent completed guessing round")
                && m.getContent().contains("Albert Einstein")));
        assertFalse(memory.containsKey(CONTINUATION_ID));
        put(memory, OUTCOME_EXPIRES, Instant.now().minusSeconds(1).toString());
        clearInvocations(gateway); say("CHAT", "Albert Einstein");
        verify(gateway).infer(request.capture());
        assertTrue(request.getValue().messages().stream().noneMatch(m -> m.getContent().startsWith("Recent completed guessing round")));
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
        assertEquals("Charlie Chaplin", text(memory, PERSON, ""));
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
}
