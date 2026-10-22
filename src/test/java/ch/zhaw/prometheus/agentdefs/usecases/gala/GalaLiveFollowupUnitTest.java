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

    @Test void baselineInvalidPostRevealClassificationPreservesOutcomeButCreatesFalseRecovery() {
        say("START_GUESS", "Personenraten bitte");
        say("WAIT", "Ich gebe auf");
        String outcome = text(memory, OUTCOME, "");
        assertFalse(outcome.isBlank()); assertFalse(memory.containsKey(CONTINUATION_ID));
        // Synthetic invalid operation; the real trial did not log the returned operation.
        say("GIVE_UP", "Albert Einstein");
        assertEquals("RECEPTION", mode(memory)); assertFalse(memory.containsKey(PERSON));
        assertEquals(outcome, text(memory, OUTCOME, ""));
        assertTrue(text(memory, CONTINUATION_FACTS, "").contains("failed"),
                "GI-15 must replace this known-failure characterization with ordinary follow-up acceptance");
    }

    @Test void baselineBriefActivitySpikesMergeThreeOtherwiseSeparateQuestions() {
        var capture = new LiveTranscriptSegmenter(UUID.randomUUID(), 2000);
        var questions = List.of("Ist die Person männlich?", "Hat die Person echt gelebt?", "Hat sie in Europa gelebt?");
        for (int question = 0; question < questions.size(); question++) {
            long start = 100 + question * 3000;
            capture.fragment(fragment("question-" + question, start, (question == 0 ? "" : " ") + questions.get(question)));
            for (long offset = 20; offset <= 2980; offset += 20) {
                // A single 20-ms activity indication each second interrupts measured quiet.
                capture.audio(Speaker.USER, 480, offset % 1000 == 0, start + offset);
                assertTrue(capture.poll(start + offset).isEmpty());
            }
        }
        for (long at = 9100; at <= 11100; at += 20) capture.audio(Speaker.USER, 480, false, at);
        var completed = capture.poll(11100);
        assertEquals(1, completed.size(), "GI-16 must distinguish noise from real speech before changing this baseline");
        assertEquals(Closure.COMPLETE, completed.getFirst().closure());
        assertEquals(String.join(" ", questions), completed.getFirst().text());
        assertEquals(3, completed.getFirst().fragments().size());
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
