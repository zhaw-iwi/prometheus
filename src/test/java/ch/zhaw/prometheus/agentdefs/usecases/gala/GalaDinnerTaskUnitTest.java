package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReception;
import ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.SpeechProvenance;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.spi.InferenceRequest;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import fixtures.behaviour.TaskReplyFixture;

class GalaDinnerTaskUnitTest {
    private static final String SPEC = """
            {"goal":"Eine kurze Reaktion auf Papier zeigen","maxActions":2,"sessionBound":true,"rules":[
            {"eventType":"obs.hand.sign","field":"sign","operator":"eq","value":"paper",
             "action":"Sage einen kurzen Roboterwitz","effect":"ACT","minConfidence":0.8,"samples":1,"cooldownSeconds":3}]}
            """;
    private static final String OPENING = "Die Aufgabe ist aktiv. Ich warte auf Ihr Papierzeichen.";
    private final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    private final Agent agent = new GalaDinnerReception().createAgent();
    private final Storage memory = ((GalaDinnerPolicy) agent.getCurrentState().ownPolicy()).storage();
    private final ExternalSpeech owner = new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
    private final PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway).withExternalSpeech(owner);

    @BeforeEach void occupied() { put(memory, COUNT, "1"); put(memory, SEEN, Instant.now().toString()); }

    @Test void agreedTaskGestureIsNotSuppressedByRecentDecorativeHistory() {
        propose(SPEC.replace("Sage einen kurzen Roboterwitz", "Zeige die EXPLAIN Geste"));
        GalaDinnerGestures.publish(memory, GalaDinnerAction.plan(null, "EXPLAIN", "attentive", "toward_user"), false, Instant.now());
        when(gateway.infer(any())).thenReturn("""
                {"operation":"ACTIVATE","task":null,"reply":{"nonVerbal":{"gesture":"EXPLAIN"}}}
                """);
        var plan = BehaviourPlan.fromJson(say("Ja, jetzt aktivieren.").getPayload());
        assertEquals("EXPLAIN", plan.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        assertEquals(1, memory.get(TaskMemory.ACTIONS).getAsInt());
        assertNull(plan.getSpeech());
    }

    @Test void proposalActivationCaptureFreshCueAndCompletionUseTheGenericContract() {
        propose(SPEC);
        assertEquals("Gala co-created task", agent.getCurrentState().getName());
        assertInstanceOf(TaskPolicy.class, agent.getCurrentState().ownPolicy());
        assertTrue(TaskCueStatus.of(agent, Instant.now()).draft());
        assertFalse(memory.containsKey(TaskMemory.SPEC));
        clearInvocations(gateway);
        assertNull(hand("paper", .99, Instant.now())); verifyNoInteractions(gateway);
        String instructions = new LiveVoicePolicyAdapter().instructions(agent);
        assertTrue(new GalaDinnerPolicy(memory).liveActivityContext().facts().contains("Proposed goal: Eine kurze Reaktion"));
        assertTrue(instructions.contains("German"));
        assertTrue(instructions.contains("speak from confirmed agreement/response facts"));
        activate();
        assertEquals("RUNNING", TaskMemory.phase(memory));
        assertEquals(1, memory.get(TaskMemory.ACTIONS).getAsInt());
        assertEquals(owner.sessionId().toString(), TaskMemory.text(memory, TaskMemory.SESSION, ""));
        clearInvocations(gateway);
        assertNull(hand("paper", .99, Instant.now()));
        arm();
        assertNull(hand("paper", .99, Instant.now()), "An uncaptured announcement still blocks cues");
        capture(OPENING);
        assertTrue(TaskCueStatus.of(agent, Instant.now()).responseCaptured());
        assertNull(hand("paper", .99, Instant.now().minusSeconds(20)));
        assertNull(hand("paper", .1, Instant.now()));
        assertNull(hand("rock", .99, Instant.now()));
        verifyNoInteractions(gateway);
        when(gateway.infer(any())).thenReturn("{\"speech\":\"Mein Humor hat heute keine Ladepause.\"}");
        Event result = hand("paper", .99, Instant.now());
        assertNoSpeech(result);
        assertTrue(facts().contains("final allowed action"));
        assertTrue(facts().contains("Sage einen kurzen Roboterwitz"));
        assertHome();
        assertNull(hand("paper", .99, Instant.now()));
        verify(gateway).infer(any());
    }

    @Test void noiseKeepsAnActiveTaskAndItsActionBudgetWithoutInference() {
        propose(SPEC);
        activate();
        var before = memory.toMap();
        clearInvocations(gateway);
        assertNull(say("[laugh] [noise]"));
        assertEquals("Gala co-created task", agent.getCurrentState().getName());
        assertEquals(before, memory.toMap());
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void futureOneActionAgreementArmsWithoutPerformingAndCompletesOnItsFirstFreshCue(boolean compact) {
        String spec = SPEC.replace("\"maxActions\":2", "\"maxActions\":1,\"firstAction\":\"ON_CUE\"");
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_TASK", ""),
                TaskReplyFixture.update("PROPOSE", spec, "UNTRUSTED", compact));
        var proposed = say("Einen Witz erst bei Papier, genau einmal.");
        assertNoSpeech(proposed);
        assertTrue(facts().contains("ON_CUE"));
        assertTrue(facts().contains("\"maxActions\":1"));
        assertTrue(facts().contains("paper"));
        assertTrue(facts().contains("Sage einen kurzen Roboterwitz"));
        if (proposed != null) assertNull(BehaviourPlan.fromJson(proposed.getPayload()).getMotion());
        when(gateway.infer(any())).thenReturn(TaskReplyFixture.update("ACTIVATE", "null", "PREMATURE JOKE", compact));
        var armed = say("Ja, aktivieren"); assertNoSpeech(armed);
        assertFalse(facts().contains("PREMATURE"));
        assertTrue(facts().contains("Zero actions performed"));
        assertTrue(facts().indexOf("Do NOT tell the joke") < facts().indexOf("Agreed goal:"));
        if (armed != null) assertNull(BehaviourPlan.fromJson(armed.getPayload()).getMotion());
        assertEquals(0, memory.get(TaskMemory.ACTIONS).getAsInt());
        assertEquals("RUNNING", TaskMemory.phase(memory));
        clearInvocations(gateway); capture("Ich warte jetzt auf dein Papierzeichen."); arm();
        assertNull(hand("rock", .99, Instant.now())); verifyNoInteractions(gateway);
        when(gateway.infer(any())).thenReturn("{\"speech\":\"Mein kurzer Roboterwitz.\"}");
        assertNoSpeech(hand("paper", .99, Instant.now()));
        assertTrue(facts().contains("Roboterwitz"));
        assertHome(); assertNull(hand("paper", .99, Instant.now())); verify(gateway).infer(any());
    }

    @Test void orderedCrossSensorRequestClarifiesInsteadOfFlatteningOrReplacingTheProposal() {
        propose(SPEC);
        var draft = memory.get(TaskMemory.DRAFT).deepCopy();
        clearInvocations(gateway);
        for (String request : List.of("Zuerst Stein, dann fröhlich, dann stoppen.", "First happy, then paper, then stop.")) {
            var response = say(request);
            assertNoSpeech(response);
            assertTrue(facts().contains("ordered sequence"));
            assertEquals(draft, memory.get(TaskMemory.DRAFT));
            assertFalse(memory.containsKey(TaskMemory.SPEC));
        }
        assertFalse(GalaDinnerInput.orderedCueSequence("Bei Papier einen Witz erzählen, dann stoppen."));
        assertFalse(GalaDinnerInput.orderedCueSequence("Zuerst aktivieren, dann auf Papier reagieren."));
        verifyNoInteractions(gateway);
    }

    @Test void activationWithoutProposalAndInvalidOrAlteredPlansCannotPublishActivationClaims() {
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_TASK", ""),
                update("ACTIVATE", SPEC, "UNAUTHORIZED"));
        assertNoSpeech(say("Starte direkt")); assertFalse(facts().contains("UNAUTHORIZED"));
        assertFalse(memory.containsKey(TaskMemory.SPEC));
        for (String invalid : List.of(SPEC.replace("\"sessionBound\":true", "\"sessionBound\":false"),
                SPEC.replace("\"maxActions\":2", "\"maxActions\":6"),
                SPEC.replace("\"field\":\"sign\"", "\"field\":\"identity\""))) {
            when(gateway.infer(any())).thenReturn(update("PROPOSE", invalid, "UNAUTHORIZED"));
            assertNoSpeech(say("Ändere den Vorschlag")); assertFalse(facts().contains("UNAUTHORIZED"));
            assertFalse(memory.containsKey(TaskMemory.DRAFT));
        }
        when(gateway.infer(any())).thenReturn(update("PROPOSE", SPEC, "Vorschlag, noch nicht aktiv."));
        say("Bitte einen gültigen Vorschlag");
        var draft = memory.get(TaskMemory.DRAFT).deepCopy();
        when(gateway.infer(any())).thenReturn(update("ACTIVATE", SPEC.replace("\"maxActions\":2", "\"maxActions\":3"), "UNAUTHORIZED"));
        assertNoSpeech(say("Ja")); assertFalse(facts().contains("UNAUTHORIZED"));
        assertEquals(draft, memory.get(TaskMemory.DRAFT));
        assertFalse(memory.containsKey(TaskMemory.SPEC));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void oneActionAgreementEmitsItsInitialHandSignAndFinishesWithoutAnotherCue(boolean compact) {
        propose(SPEC.replace("\"maxActions\":2", "\"maxActions\":1")
                .replace("Sage einen kurzen Roboterwitz", "Sage Papier und zeige Papier"));
        clearInvocations(gateway);
        when(gateway.infer(any())).thenReturn(TaskReplyFixture.update("ACTIVATE", "null", "Papier!", compact));
        var result = BehaviourPlan.fromJson(say("Ja, jetzt aktivieren.").getPayload());
        assertEquals("paper", result.getMotion().getAsJsonObject().get("handSign").getAsString());
        assertNull(result.getSpeech());
        assertTrue(facts().contains("Sage Papier und zeige Papier"));
        assertTrue(facts().contains("final allowed action"));
        assertHome();
        assertNull(hand("paper", .99, Instant.now()));
        verify(gateway, times(1)).infer(any());
    }

    @ParameterizedTest @ValueSource(ints = {1, 3, 5})
    void proposalFactsPreserveValidatedTermsAndDiscardModelProse(int actions) {
        String spec = SPEC.replace("\"maxActions\":2", "\"maxActions\":" + actions)
                .replace("Eine kurze Reaktion auf Papier zeigen", actions == 3 ? "界".repeat(399) + "." : "界".repeat(400));
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_TASK", ""),
                TaskReplyFixture.update("PROPOSE", spec, "UNTRUSTED: Drei spätere Reaktionen, dann pausiert es.", true));
        var proposal = say("Bitte einen Vorschlag.");
        assertNoSpeech(proposal);
        String text = facts();
        assertFalse(text.contains("UNTRUSTED"));
        assertTrue(text.contains("not active"));
        assertTrue(text.contains("IMMEDIATE"));
        assertTrue(text.contains("\"maxActions\":" + actions));
        assertTrue(text.contains("\"sessionBound\":true"));
        assertTrue(text.contains("Sage einen kurzen Roboterwitz"));
        if (proposal != null) assertNull(BehaviourPlan.fromJson(proposal.getPayload()).getMotion());
        assertTrue(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 4000);
        assertEquals(JsonParser.parseString(spec), memory.get(TaskMemory.DRAFT));
        assertFalse(memory.containsKey(TaskMemory.SPEC));
        clearInvocations(gateway);
        when(gateway.infer(any())).thenReturn(update("KEEP", "null", "Eine Antwort auf Ihre Frage."));
        assertNoSpeech(say("Was bedeutet das?"));
        assertTrue(facts().contains("current question"));
        verify(gateway, times(1)).infer(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"departure", "presence", "close", "stop", "pause", "timeout", "reset"})
    void encounterEndClearsDraftAndActiveTask(String ending) {
        for (boolean active : List.of(false, true)) {
            occupied(); propose(SPEC);
            if (active) activate();
            put(memory, "unrelated", "keep");
            clearInvocations(gateway);
            switch (ending) {
                case "departure" -> agent.acknowledge(Event.observation(Event.TYPE_SOCIAL_GROUPING, "sensor",
                        "{\"humanCount\":0,\"ts\":\"" + Instant.now().plusMillis(1) + "\"}"), runtime);
                case "presence" -> agent.acknowledge(Event.observation(Event.TYPE_HUMAN_PRESENCE, "sensor",
                        "{\"humanCount\":0,\"avgDetectionConfidence\":0,\"ts\":\"" + Instant.now().plusMillis(1) + "\"}"), runtime);
                case "close" -> agent.acknowledge(new Event(GalaDinnerPolicy.LIVE_ENDED, "system", Event.KIND_SYSTEM, "{}"), runtime);
                case "stop" -> say("Stopp");
                case "pause" -> say("Pause");
                case "timeout" -> { put(memory, LAST_INPUT, Instant.now().minusSeconds(91).toString()); agent.tick(runtime); }
                case "reset" -> agent.reset();
                default -> fail();
            }
            if (List.of("departure", "presence").contains(ending)) {
                assertEquals("TASK", mode(memory));
                put(memory, ABSENT_SINCE, Instant.now().minusMillis(DEPARTURE_GRACE_MS).toString());
                agent.tick(runtime);
            }
            assertHome();
            assertEquals("keep", text(memory, "unrelated", ""));
            verifyNoInteractions(gateway);
        }
    }

    @Test void offTopicQuestionReturnsHomeWithoutCompetingSpeechOrASecondTaskInference() {
        propose(SPEC); activate();
        when(gateway.infer(any())).thenReturn("{\"operation\":\"STOP\",\"task\":null,\"reply\":{}}");
        clearInvocations(gateway);
        assertNull(say("Wann gibt es Dessert?"));
        assertHome();
        verify(gateway).infer(any());
    }

    @Test void taskFaceRulesReplaceAmbientReactionsAndWaitingIsSilent() {
        String faceTask = SPEC.replace("\"maxActions\":2", "\"maxActions\":4")
                .replace("obs.hand.sign", Event.TYPE_FACE_EMOTION).replace("\"field\":\"sign\"", "\"field\":\"emotion\"")
                .replace("\"paper\"", "\"happy\"");
        JsonObject task = JsonParser.parseString(faceTask).getAsJsonObject();
        JsonObject wait = task.getAsJsonArray("rules").get(0).getAsJsonObject().deepCopy();
        wait.addProperty("value", "neutral"); wait.addProperty("effect", "WAIT");
        task.getAsJsonArray("rules").add(wait);
        propose(task.toString()); activate(); capture(OPENING); arm(); clearInvocations(gateway);
        assertNull(face("sad"));
        assertNull(face("neutral"));
        assertEquals("WAITING", TaskMemory.phase(memory));
        assertNull(face("neutral")); verifyNoInteractions(gateway);
        when(gateway.infer(any())).thenReturn("{\"speech\":\"Ein kleiner Roboterwitz.\"}");
        Event response = face("happy");
        assertNoSpeech(response);
        assertTrue(facts().contains("Sage einen kurzen Roboterwitz"));
        assertEquals("RUNNING", TaskMemory.phase(memory));
        assertEquals(2, memory.get(TaskMemory.ACTIONS).getAsInt());
        assertFalse(memory.containsKey(FACE), "Ambient expression tracking must not override the task");
        assertNull(face("happy")); verify(gateway).infer(any());
    }

    @Test void lateActivationAndCueResultsAreDiscardedOnVoiceClosure() {
        propose(SPEC);
        var continuing = new AtomicBoolean(true);
        when(gateway.infer(any())).thenAnswer(call -> {
            continuing.set(false); return update("ACTIVATE", "null", OPENING);
        });
        assertNull(agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Ja"),
                runtime.withTaskContinuation(continuing::get)));
        assertHome();
        propose(SPEC); activate(); capture(OPENING); arm(); continuing.set(true);
        when(gateway.infer(any())).thenAnswer(call -> { continuing.set(false); return "{\"speech\":\"Zu spät\"}"; });
        assertNull(agent.acknowledge(Event.observation(Event.TYPE_HAND_SIGN, "sensor",
                "{\"sign\":\"paper\",\"confidence\":0.99,\"ts\":\"" + Instant.now() + "\"}"),
                runtime.withTaskContinuation(continuing::get)));
        assertHome();
    }

    @Test void recoveryFactsPreserveTheAgreementAndRequireANewResponseCheck() {
        propose(SPEC); activate(); capture(OPENING); arm();
        var spec = memory.get(TaskMemory.SPEC).deepCopy();
        clearInvocations(gateway);
        assertNoSpeech(say("Resume the task."));
        assertTrue(facts().contains("already active"));
        verifyNoInteractions(gateway);
        when(gateway.infer(any())).thenThrow(new IllegalStateException("provider unavailable"));
        assertNoSpeech(say("Wie war unsere Vereinbarung?"));
        assertTrue(facts().contains("could not be validated"));
        assertEquals(spec, memory.get(TaskMemory.SPEC));
        assertFalse(TaskCueStatus.of(agent, Instant.now()).responseCaptured());
        capture("Die bestehende Vereinbarung bleibt erhalten. Soll ich es noch einmal versuchen?");
        assertTrue(TaskCueStatus.of(agent, Instant.now()).responseCaptured());
        doReturn("{\"speech\":\"Ein letzter Witz.\"}").when(gateway).infer(any());
        assertNoSpeech(hand("paper", .99, Instant.now()));
        assertHome();
    }

    @Test void newTaskConfigurationExcludesPreviousVisitorsAndHasNoGenericPersona() {
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("CHAT", ""));
        say("Besucher-Geheimnis");
        put(memory, SESSION, Integer.toString(agent.getEventHistory().toList().size()));
        clearInvocations(gateway); propose(SPEC);
        var requests = ArgumentCaptor.forClass(InferenceRequest.class);
        verify(gateway, times(2)).infer(requests.capture());
        var configuration = requests.getAllValues().getLast();
        assertFalse(configuration.messages().stream().anyMatch(message -> message.getContent().contains("Besucher-Geheimnis")));
        assertTrue(configuration.messages().getFirst().getContent().contains("You are djee-djee"));
        assertFalse(configuration.messages().getFirst().getContent().contains("You are Valerian"));
        String instructions = configuration.messages().getFirst().getContent();
        for (String obsolete : List.of("IPA", "dʒ", "PAUSE|RESUME", "Interactive tasks pause",
                "require explicit resumption", "1..50 maxActions", "fully specified task now"))
            assertFalse(instructions.contains(obsolete), obsolete);
        for (String contract : List.of("KEEP|PROPOSE|ACTIVATE|STOP", "1..5 maxActions", "sessionBound: always true",
                "discards its plan", "firstAction", "ON_CUE", "IMMEDIATE", "reply.motion.handSign",
                "WOULD do are KEEP, not STOP", "acceptance performs nothing and uses zero actions",
                "On PROPOSE, use reply {}"))
            assertTrue(instructions.contains(contract), contract);
    }

    @Test void lostResponseEndsOnceWithoutReplayingTheActionAndKeepsOnlyTerminalFacts() {
        propose(SPEC); activate(); clearInvocations(gateway);
        var pending = memory.get(TaskResponse.KEY).getAsJsonObject().deepCopy();
        pending.addProperty("deadline", Instant.now().minusSeconds(1).toString());
        memory.put(TaskResponse.KEY, pending);
        assertFalse(TaskCueStatus.of(agent, Instant.now()).responseCaptured());
        assertNull(agent.acknowledge(new Event(GalaDinnerTaskPolicy.RESPONSE_UNCERTAIN, "system", Event.KIND_SYSTEM, "deadline"), runtime));
        assertHome();
        var recovery = new GalaDinnerPolicy(memory).liveActivityContext().continuation();
        assertEquals(LiveActivityContext.Occasion.PROACTIVE, recovery.occasion());
        assertTrue(facts().contains("physical action may already have happened"));
        assertFalse(facts().contains("Agreed executable terms"));
        assertNull(hand("paper", .99, Instant.now()));
        assertNull(agent.acknowledge(new Event(GalaDinnerTaskPolicy.RESPONSE_UNCERTAIN, "system", Event.KIND_SYSTEM, "late"), runtime));
        assertEquals(recovery.id(), new GalaDinnerPolicy(memory).liveActivityContext().continuation().id());
        verifyNoInteractions(gateway);
        say("Stopp");
        assertNull(new GalaDinnerPolicy(memory).liveActivityContext().continuation());
        assertFalse(memory.containsKey(TASK_FACTS));
    }

    @Test void clarificationInvalidatesThePreviousResponseAndStaleCheckCannotReleaseIt() {
        propose(SPEC); activate();
        var event = agent.recordExternalSpeech("Mein Roboterwitz ist fertig.", new SpeechProvenance(SpeechProvenance.Origin.NATIVE,
                owner.sessionId(), owner.epoch(), "old", SpeechProvenance.Association.NONE, List.of(), true));
        var started = Instant.parse(memory.get(TaskResponse.KEY).getAsJsonObject().get("created").getAsString()).plusNanos(1);
        var check = TaskResponse.claim(memory, owner, event, started, started.plusMillis(1)).orElseThrow();
        var spec = memory.get(TaskMemory.SPEC).deepCopy(); clearInvocations(gateway);
        assertNull(say("First happy, then paper, then stop."));
        assertEquals(spec, memory.get(TaskMemory.SPEC));
        assertEquals("PENDING", TaskResponse.status(memory));
        assertFalse(TaskResponse.finish(memory, owner, check, true, Instant.now()));
        assertNull(hand("paper", .99, Instant.now())); verifyNoInteractions(gateway);
    }

    @Test void excessiveUtf8TermsCannotReplaceAValidProposal() {
        propose(SPEC); var prior = memory.get(TaskMemory.DRAFT).deepCopy();
        String oversized = SPEC.replace("Eine kurze Reaktion auf Papier zeigen", "界".repeat(400))
                .replace("Sage einen kurzen Roboterwitz", "界".repeat(300));
        when(gateway.infer(any())).thenReturn(update("PROPOSE", oversized, "UNAUTHORIZED"));
        assertNoSpeech(say("Ändere den Vorschlag"));
        assertEquals(prior, memory.get(TaskMemory.DRAFT));
        assertFalse(facts().contains("UNAUTHORIZED"));
    }

    private void propose(String spec) {
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_TASK", ""),
                update("PROPOSE", spec, "Ich schlage eine kurze Reaktion vor. Soll ich sie aktivieren?"));
        say("Lass uns eine Aufgabe mit meinem Handzeichen gestalten.");
    }

    @Test void dropoutKeepsAgreementAndBudgetWithoutExecutingOrReplayingCues() {
        propose(SPEC); activate(); capture(OPENING); arm();
        var agreement = memory.get(TaskMemory.SPEC).deepCopy(); var actions = memory.get(TaskMemory.ACTIONS).deepCopy();
        Instant observed = Instant.now();
        agent.acknowledge(Event.observation(Event.TYPE_SOCIAL_GROUPING, "sensor",
                "{\"humanCount\":0,\"ts\":\"" + observed + "\"}"), runtime);
        clearInvocations(gateway); hand("paper", .99, observed.plusMillis(1)); face("happy");
        assertEquals(agreement, memory.get(TaskMemory.SPEC)); assertEquals(actions, memory.get(TaskMemory.ACTIONS));
        agent.acknowledge(Event.observation(Event.TYPE_SOCIAL_GROUPING, "sensor",
                "{\"humanCount\":1,\"ts\":\"" + observed.plusMillis(2) + "\"}"), runtime);
        assertEquals(actions, memory.get(TaskMemory.ACTIONS)); verifyNoInteractions(gateway);
        assertEquals("RUNNING", TaskMemory.phase(memory));
    }
    private void activate() { when(gateway.infer(any())).thenReturn(update("ACTIVATE", "null", OPENING)); say("Ja, bitte aktivieren."); }
    private void arm() { put(memory, TaskMemory.AFTER, Instant.now().minusSeconds(1).toString()); }
    private void capture(String text) {
        var speech = agent.recordExternalSpeech(text, new SpeechProvenance(SpeechProvenance.Origin.NATIVE,
                owner.sessionId(), owner.epoch(), UUID.randomUUID().toString(), SpeechProvenance.Association.NONE, List.of(), true));
        // Model association is mocked here; persistence/provider trials exercise the actual asynchronous check.
        Instant started = Instant.parse(memory.get(TaskResponse.KEY).getAsJsonObject().get("created").getAsString()).plusNanos(1);
        Instant captured = Instant.now(); if (captured.isBefore(started)) captured = started;
        var check = TaskResponse.claim(memory, owner, speech, started, captured).orElseThrow();
        assertTrue(TaskResponse.finish(memory, owner, check, true, Instant.now()));
    }
    private void assertHome() {
        assertEquals("Gala reception", agent.getCurrentState().getName());
        assertTrue(memory.toMap().keySet().stream().noneMatch(key -> key.startsWith("task.")));
    }
    private Event say(String text) { return agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", text), runtime); }
    private Event hand(String sign, double confidence, Instant time) {
        return agent.acknowledge(Event.observation(Event.TYPE_HAND_SIGN, "sensor",
                "{\"sign\":\"" + sign + "\",\"confidence\":" + confidence + ",\"ts\":\"" + time + "\"}"), runtime);
    }
    private Event face(String emotion) {
        return agent.acknowledge(Event.observation(Event.TYPE_FACE_EMOTION, "sensor",
                "{\"emotion\":\"" + emotion + "\",\"confidence\":0.99,\"ts\":\"" + Instant.now() + "\"}"), runtime);
    }
    private String facts() { return new GalaDinnerPolicy(memory).liveActivityContext().facts(); }
    private static void assertNoSpeech(Event event) {
        if (event != null) assertNull(BehaviourPlan.fromJson(event.getPayload()).getSpeech());
    }
    private static String update(String operation, String spec, String speech) {
        return "{\"operation\":\"" + operation + "\",\"task\":" + spec + ",\"reply\":"
                + BehaviourPlan.speechOnly(speech).toJson() + "}";
    }
}
