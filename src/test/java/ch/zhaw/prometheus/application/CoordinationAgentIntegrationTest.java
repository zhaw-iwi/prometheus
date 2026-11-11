package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ch.zhaw.prometheus.agentdefs.AgentDefinitionRegistry;
import ch.zhaw.prometheus.agentdefs.core.CoordinationAgent;
import ch.zhaw.prometheus.agentdefs.core.CoordinationPolicy;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import ch.zhaw.prometheus.spi.SpeechSynthesisGateway;

/** Run only with the disposable local MySQL runner documented in README. */
@SpringBootTest(properties = {"prometheus.runtime.tick.enabled=false"})
class CoordinationAgentIntegrationTest {
    @Autowired AgentDefinitionRegistry registry;
    @Autowired AgentRepository agents;
    @Autowired AccessCodeAdminService admin;
    @Autowired ScopedDemoService demo;
    @Autowired GenericTaskSessionLifecycleService lifecycle;
    @Autowired PlatformTransactionManager transactions;
    @Autowired LiveTranscriptIngressService ingress;
    @Autowired ExternalSpeechOwnership ownership;
    @MockitoBean LanguageModelGateway gateway;
    @MockitoBean SpeechSynthesisGateway speech;
    static final String AGREEMENT = """
            {"goal":"Draft an invitation", "humanRole":"Supply the details", "agentRole":"Edit only those details",
            "success":"The organiser accepts the invitation", "questions":[], "checks":[], "dialogueLimit":4, "reactions":null}
            """;
    record Scope(String code, UUID id) {}
    Scope create() {
        String code = UUID.randomUUID().toString().substring(0, 5);
        var access = admin.createAccessCode(code, true);
        admin.replaceAllowedAgentTypes(access.getId(), List.of(CoordinationAgent.KEY));
        return new Scope(code, demo.createAgent(code, CoordinationAgent.KEY).getID());
    }
    void turn(Scope scope, String op, String agreement, String utterance) {
        when(gateway.infer(any())).thenReturn("{\"operation\":\"" + op + "\",\"agreement\":" + agreement + ",\"reply\":{\"speech\":\"Shall we start now?\"}}");
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION, utterance), OutputProfile.FULL_PLAN).orElseThrow();
    }
    Storage memory(Scope scope) {
        var agent = agents.findById(scope.id()).orElseThrow();
        assertInstanceOf(CoordinationPolicy.class, agent.getCurrentState().ownPolicy());
        return ((TaskPolicy) agent.getCurrentState().ownPolicy()).storage();
    }
    @Test void deniedCombinedStartRemainsUnacceptedAfterReload() {
        var scope = create();
        turn(scope, "PROPOSE_AND_START", AGREEMENT, "Propose this invitation task, but do not start it");
        var reloaded = memory(scope);
        assertEquals("CONVERSATION", CoordinationMemory.stage(reloaded));
        assertFalse(reloaded.containsKey(CoordinationMemory.AGREEMENT));
        assertFalse(reloaded.containsKey(CoordinationMemory.RUN));
        assertFalse(reloaded.containsKey(TaskMemory.ACTIONS));
    }
    @Test void supportReloadsWithoutChangingAuthorityAndExistingPauseResumeRemainAvailable() {
        var scope = create(); turn(scope, "PROPOSE", AGREEMENT, "Propose our invitation activity");
        var before = memory(scope).get(CoordinationMemory.DRAFT).deepCopy();
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_USER_UTTERANCE, "user",
                Event.KIND_OBSERVATION, "I don't understand"), OutputProfile.FULL_PLAN).orElseThrow();
        assertEquals("READY", CoordinationMemory.stage(memory(scope)));
        assertEquals(before, memory(scope).get(CoordinationMemory.DRAFT));
        var history = agents.findById(scope.id()).orElseThrow().getEventHistory().toList();
        assertTrue(history.getLast().getPayload().contains("no further demonstration"));
        turn(scope, "START", "null", "Accept and begin the saved invitation");
        var count = memory(scope).get(TaskMemory.ACTIONS).deepCopy();
        turn(scope, "PAUSE", "null", "Pause");
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_USER_UTTERANCE, "user",
                Event.KIND_OBSERVATION, "Help me"), OutputProfile.FULL_PLAN).orElseThrow();
        assertEquals("PAUSED", CoordinationMemory.stage(memory(scope)));
        assertEquals(count, memory(scope).get(TaskMemory.ACTIONS));
        turn(scope, "RESUME", "null", "Resume"); assertEquals("EXECUTION", CoordinationMemory.stage(memory(scope)));
        assertEquals(count, memory(scope).get(TaskMemory.ACTIONS));
    }

    @Test void preparedSensorProposalAcceptsContextualGermanReplyAfterReloadWithoutInference() {
        var scope = create(); var proposal = JsonParser.parseString(AGREEMENT).getAsJsonObject();
        proposal.getAsJsonArray("checks").add(JsonParser.parseString("""
                {"eventType":"obs.hand.sign","field":"sign","operator":"eq","value":"rock","minConfidence":0}
                """));
        var reactions = JsonParser.parseString("""
                {"goal":"Draft an invitation","maxActions":1,"sessionBound":true,"firstAction":"ON_CUE",
                "rules":[{"eventType":"obs.hand.sign","field":"sign","operator":"eq","value":"rock",
                "action":"Offer one invitation idea","effect":"ACT","minConfidence":0,"samples":1,"cooldownSeconds":3}]}
                """).getAsJsonObject(); proposal.add("reactions", reactions);
        turn(scope, "PROPOSE", proposal.toString(), "Prepare recognition before the agreed cue task");
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_HAND_SIGN, "camera", Event.KIND_OBSERVATION,
                "{\"sign\":\"rock\",\"confidence\":0.75,\"ts\":\"" + Instant.now() + "\"}"), OutputProfile.FULL_PLAN).orElseThrow();
        assertEquals("READY", CoordinationMemory.stage(memory(scope)));
        clearInvocations(gateway);
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_USER_UTTERANCE, "user",
                Event.KIND_OBSERVATION, "Bereit"), OutputProfile.FULL_PLAN).orElseThrow();
        verifyNoInteractions(gateway);
        assertEquals("EXECUTION", CoordinationMemory.stage(memory(scope)));
        assertEquals(0, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        assertEquals(1, memory(scope).get(CoordinationMemory.ACCEPTED_EVIDENCE).getAsJsonObject().size());
        turn(scope, "REPLACE", AGREEMENT, "Explicitly replace this sensor task with the invitation dialogue");
        assertEquals("READY", CoordinationMemory.stage(memory(scope)));
        assertEquals(0, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        turn(scope, "START", "null", "Accept this replacement and begin");
        assertFalse(memory(scope).containsKey(TaskMemory.SPEC));
        assertTrue(memory(scope).get(CoordinationMemory.AGREEMENT).getAsJsonObject().get("reactions").isJsonNull());
    }

    @Test void boundedRiddleDialogueReloadsQuestionFeedbackAndDistinctBudgetOutcome() {
        var scope = create(); var proposal = JsonParser.parseString(AGREEMENT).getAsJsonObject();
        proposal.addProperty("goal", "Try one short riddle together"); proposal.addProperty("humanRole", "Answer the question");
        proposal.addProperty("agentRole", "Ask a riddle and give feedback"); proposal.addProperty("dialogueLimit", 2);
        turn(scope, "PROPOSE", proposal.toString(), "Propose a bounded question and feedback exchange");
        assertTrue(agents.findById(scope.id()).orElseThrow().getEventHistory().toList().getLast().getPayload().contains("Limit: 2"));
        when(gateway.infer(any())).thenReturn("{\"operation\":\"START\",\"agreement\":null,\"reply\":{\"speech\":\"What becomes shorter while it works?\"}}");
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION, "Start"), OutputProfile.FULL_PLAN).orElseThrow();
        assertEquals(1, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        assertFalse(agents.findById(scope.id()).orElseThrow().getEventHistory().toList().getLast().getPayload().contains("candle"));
        when(gateway.infer(any())).thenReturn("{\"operation\":\"STEP\",\"agreement\":null,\"reply\":{\"speech\":\"A candle fits that riddle.\"}}");
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION, "A candle"), OutputProfile.FULL_PLAN).orElseThrow();
        assertEquals("REFLECTION", CoordinationMemory.stage(memory(scope)));
        assertEquals(2, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        assertEquals("LIMIT_REACHED", memory(scope).get(CoordinationMemory.OUTCOME).getAsString());
        var history = agents.findById(scope.id()).orElseThrow().getEventHistory().toList();
        assertTrue(history.getLast().getPayload().contains("candle"));
        assertTrue(history.stream().anyMatch(e -> Event.TYPE_USER_UTTERANCE.equals(e.getType()) && "A candle".equals(e.getPayload())));
    }

    @Test void reviewedSocialCountAlternativeReloadsAndIgnoresUnrelatedObservations() throws Exception {
        var scope = create(); JsonObject proposal;
        try (var input = getClass().getResourceAsStream("/coordination/social-count-alternative.json")) {
            proposal = JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(input),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        turn(scope, "PROPOSE", proposal.toString(), "Propose this explicit visible-count alternative for review");
        assertEquals("READY", CoordinationMemory.stage(memory(scope)));
        turn(scope, "START", "null", "Start"); assertEquals(0, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        var rule = memory(scope).get(TaskMemory.SPEC).getAsJsonObject().getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals("humanCount", rule.get("field").getAsString()); assertEquals(1, rule.get("value").getAsInt());
        when(gateway.infer(any())).thenReturn("{\"speech\":\"We can continue another time.\"}");
        for (var observation : List.of(Event.TYPE_SOCIAL_CONTEXT, Event.TYPE_HUMAN_PRESENCE))
            demo.acknowledge(scope.code(), scope.id(), new EventRequest(observation, "camera", Event.KIND_OBSERVATION,
                    "{\"humanCount\":1,\"avgDetectionConfidence\":0.9,\"ts\":\"" + Instant.now() + "\"}"), OutputProfile.FULL_PLAN).orElseThrow();
        assertEquals(0, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        long waitMillis = java.time.Duration.between(Instant.now(), Instant.parse(memory(scope).get(TaskMemory.AFTER).getAsString())).toMillis();
        assertTrue(waitMillis < 5000, "Unexpected arming window in the bounded fixture");
        if (waitMillis >= 0) Thread.sleep(waitMillis + 25);
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_HUMAN_PRESENCE, "camera", Event.KIND_OBSERVATION,
                "{\"humanCount\":2,\"avgDetectionConfidence\":0.9,\"ts\":\"" + Instant.now() + "\"}"), OutputProfile.FULL_PLAN).orElseThrow();
        assertEquals("REFLECTION", CoordinationMemory.stage(memory(scope)));
        assertEquals(1, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
    }

    @Test void currentPerceptionQuestionAndTechnicalFailurePreserveSavedTaskAcrossReload() {
        var scope = create(); turn(scope, "PROPOSE", AGREEMENT, "Propose the invitation task");
        var before = memory(scope).get(CoordinationMemory.DRAFT).deepCopy();
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_HAND_SIGN, "camera", Event.KIND_OBSERVATION,
                "{\"sign\":\"scissor\",\"confidence\":0.868,\"ts\":\"" + Instant.now() + "\"}"), OutputProfile.FULL_PLAN).orElseThrow();
        clearInvocations(gateway);
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION,
                "Siehst du mein Handzeichen?"), OutputProfile.FULL_PLAN).orElseThrow();
        verifyNoInteractions(gateway);
        assertTrue(agents.findById(scope.id()).orElseThrow().getEventHistory().toList().getLast().getPayload().contains("scissors"));
        assertEquals(before, memory(scope).get(CoordinationMemory.DRAFT));
        when(gateway.infer(any())).thenThrow(new IllegalStateException("private provider transport details"));
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION,
                "Explain your capabilities"), OutputProfile.FULL_PLAN).orElseThrow();
        var failure = agents.findById(scope.id()).orElseThrow().getEventHistory().toList().getLast().getPayload();
        assertFalse(failure.contains("private")); assertTrue(failure.contains("cause is not established"));
        assertEquals("READY", CoordinationMemory.stage(memory(scope)));
        assertEquals(before, memory(scope).get(CoordinationMemory.DRAFT));
    }

    @Test void rejectedUpdateEvidenceReloadsWithoutChangingPreparationAndClearsOnProgress() {
        var scope = create(); turn(scope, "PROPOSE", AGREEMENT, "Propose our invitation activity");
        var draft = memory(scope).get(CoordinationMemory.DRAFT).deepCopy();
        when(gateway.infer(any())).thenReturn("private invalid inference");
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_USER_UTTERANCE, "user",
                Event.KIND_OBSERVATION, "Begin the saved activity"), OutputProfile.FULL_PLAN).orElseThrow();
        var restored = memory(scope);
        var failure = ch.zhaw.prometheus.model.coordination.CoordinationRecovery.current(restored, Instant.now());
        assertNotNull(failure); assertEquals("update_shape", failure.get("category").getAsString());
        assertTrue(failure.has("sourcePosition")); assertFalse(failure.get("mutationCommitted").getAsBoolean());
        assertFalse(failure.toString().contains("private")); assertEquals(draft, restored.get(CoordinationMemory.DRAFT));
        assertEquals("READY", CoordinationMemory.stage(restored));
        turn(scope, "START", "null", "Accept the current proposal and begin");
        assertEquals("EXECUTION", CoordinationMemory.stage(memory(scope)));
        assertFalse(memory(scope).containsKey(ch.zhaw.prometheus.model.coordination.CoordinationRecovery.KEY));
    }
    @Test void rawAndCalibratedObservationsRetainTheirSourceAgesAfterReload() throws Exception {
        JsonObject fixture;
        try (var input = getClass().getResourceAsStream("/coordination/observation-timing.json")) {
            fixture = JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(input),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        var rawScope = create(); var calibratedScope = create();
        Instant captured = Instant.now().minusMillis(100);
        var original = fixture.getAsJsonObject("observation").deepCopy();
        original.addProperty("ts", captured.plusMillis(fixture.get("browserClockLeadMs").getAsLong()).toString());
        var calibrated = original.deepCopy(); calibrated.addProperty("ts", captured.toString());
        for (var trial : Map.of(rawScope, original, calibratedScope, calibrated).entrySet())
            demo.acknowledge(trial.getKey().code(), trial.getKey().id(), new EventRequest(Event.TYPE_HAND_SIGN, "camera",
                    Event.KIND_OBSERVATION, trial.getValue().toString()), OutputProfile.FULL_PLAN).orElseThrow();
        // Repository methods open separate transactions: these are reloaded events, not managed originals.
        var raw = agents.findById(rawScope.id()).orElseThrow().getEventHistory().toList().stream()
                .filter(e -> Event.TYPE_HAND_SIGN.equals(e.getType())).findFirst().orElseThrow();
        var reloaded = agents.findById(calibratedScope.id()).orElseThrow();
        var corrected = reloaded.getEventHistory().toList().stream()
                .filter(e -> Event.TYPE_HAND_SIGN.equals(e.getType())).findFirst().orElseThrow();
        assertEquals(original.toString(), raw.getPayload());
        assertEquals(calibrated.toString(), corrected.getPayload());
        assertNotNull(corrected.getId());
        assertFalse(TaskMemory.fresh(raw, captured.plusMillis(26)));
        assertTrue(TaskMemory.fresh(corrected, captured.plusMillis(26)));
        for (int ageSeconds : List.of(0, 15)) {
            var projection = new ch.zhaw.prometheus.application.live.LiveContextProjection(
                    new ch.zhaw.prometheus.model.policy.PromptMessageAssembler(),
                    new ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter(),
                    java.time.Clock.fixed(captured.plusSeconds(ageSeconds), java.time.ZoneOffset.UTC));
            var item = projection.project(reloaded).items().stream().filter(i -> Event.TYPE_HAND_SIGN.equals(i.type())).findFirst().orElseThrow();
            assertEquals(captured, item.observedAt());
            assertEquals(captured.plusSeconds(15), item.expiresAt());
            assertEquals(ageSeconds == 0 ? "fresh" : "expired", item.freshness());
            assertEquals(List.of(corrected.getId()), item.sourceIds());
            assertEquals(ageSeconds == 0, TaskMemory.fresh(corrected, captured.plusSeconds(ageSeconds)));
        }
    }
    @Test void scopedDialogueAgreementReloadsPausesOnLiveCloseAndResetsWithoutLosingPersona() {
        assertTrue(registry.findByKey(CoordinationAgent.KEY).isPresent());
        var scope = create(); turn(scope, "PROPOSE", AGREEMENT, "Please propose an invitation task");
        assertEquals("READY", CoordinationMemory.stage(memory(scope)));
        assertTrue(TaskCueStatus.of(agents.findById(scope.id()).orElseThrow(), Instant.now()).draft());
        turn(scope, "START", "null", "I accept this proposal. Start now");
        assertEquals("EXECUTION", CoordinationMemory.stage(memory(scope)));
        assertFalse(memory(scope).containsKey(TaskMemory.SPEC));
        turn(scope, "STEP", "null", "The audience is students; the visit is Friday at two");
        assertEquals(2, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        var session = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var agent = agents.findById(scope.id()).orElseThrow();
            ((TaskPolicy) agent.getCurrentState().ownPolicy()).storage().put(TaskMemory.SESSION, new JsonPrimitive(session.toString()));
            agent.executionEpoch(); agents.saveAndFlush(agent);
        });
        assertEquals(session.toString(), memory(scope).get(TaskMemory.SESSION).getAsString());
        lifecycle.closed(scope.id(), agents.findById(scope.id()).orElseThrow().executionEpoch(), session);
        assertEquals("PAUSED", CoordinationMemory.stage(memory(scope))); assertEquals(2, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        turn(scope, "RESUME", "null", "Resume the task");
        turn(scope, "COMPLETE", "null", "The invitation meets my requirements; finish the task");
        turn(scope, "REFLECT", "null", "Clarifying the roles was useful");
        assertEquals("REFLECTION", CoordinationMemory.stage(memory(scope)));
        assertTrue(memory(scope).get(CoordinationMemory.REVIEW).toString().contains("Clarifying the roles"));
        demo.reset(scope.code(), scope.id());
        assertEquals("CONVERSATION", CoordinationMemory.stage(memory(scope))); assertFalse(memory(scope).containsKey(CoordinationMemory.AGREEMENT));
        assertTrue(agents.findById(scope.id()).orElseThrow().getCurrentState().getTotalPolicy().contains("Laureline"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(doubles = {0, .8})
    void preparationEvidencePersistsAndARevisedDraftRequiresFreshEvidence(double minimum) {
        var scope = create();
        var proposal = JsonParser.parseString(AGREEMENT).getAsJsonObject();
        proposal.getAsJsonArray("checks").add(JsonParser.parseString("""
                {"eventType":"obs.hand.sign", "field":"sign", "operator":"eq", "value":"rock", "minConfidence":0.8}
                """));
        proposal.getAsJsonArray("checks").get(0).getAsJsonObject().addProperty("minConfidence", minimum);
        turn(scope, "PROPOSE", proposal.toString(), "First check that you can recognise rock, then help with the invitation");
        turn(scope, "START", "null", "I have shown it; start now"); assertFalse(memory(scope).containsKey(CoordinationMemory.AGREEMENT));
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_HAND_SIGN, "camera", Event.KIND_OBSERVATION,
                "{\"sign\":\"rock\",\"confidence\":" + (minimum == 0 ? .757 : .9) + ",\"ts\":\"" + Instant.now() + "\"}"), OutputProfile.FULL_PLAN);
        assertEquals("READY", CoordinationMemory.stage(memory(scope))); assertEquals(1, CoordinationMemory.evidence(memory(scope)).size());
        proposal.addProperty("agentRole", "Clarify references and edit the supplied details");
        turn(scope, "PROPOSE", proposal.toString(), "Revise the proposal to clarify references");
        assertEquals(proposal, memory(scope).get(CoordinationMemory.DRAFT));
        assertEquals("PREPARATION", CoordinationMemory.stage(memory(scope))); assertEquals(0, CoordinationMemory.evidence(memory(scope)).size());
    }
    @Test void returnAndRestartReloadWithHistoryAndEarlierOutcomesPreserved() {
        var scope = create(); turn(scope, "PROPOSE", AGREEMENT, "Propose an invitation task");
        turn(scope, "START", "null", "Start now");
        String run = memory(scope).get(CoordinationMemory.RUN).getAsString();
        turn(scope, "COMPLETE", "null", "The invitation is accepted; complete the task");
        int history = agents.findById(scope.id()).orElseThrow().getEventHistory().toList().size();
        turn(scope, "RETURN_TO_CONVERSATION", "null", "Go back to the beginning state");
        assertEquals("CONVERSATION", CoordinationMemory.stage(memory(scope)));
        assertEquals("USER_REPORTED_COMPLETION", memory(scope).get(CoordinationMemory.OUTCOME).getAsString());
        assertTrue(agents.findById(scope.id()).orElseThrow().getEventHistory().toList().size() > history);
        turn(scope, "RESTART", "null", "Restart the same task");
        assertEquals("EXECUTION", CoordinationMemory.stage(memory(scope)));
        assertNotEquals(run, memory(scope).get(CoordinationMemory.RUN).getAsString());
        assertEquals(1, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        assertEquals(1, memory(scope).get(CoordinationMemory.OUTCOMES).getAsJsonArray().size());
    }
    @Test void validLargeAgreementAndContentRevisionSurviveReload() {
        var scope = create(); var proposal = JsonParser.parseString(AGREEMENT).getAsJsonObject();
        proposal.addProperty("goal", "g".repeat(300)); proposal.addProperty("humanRole", "h".repeat(300));
        proposal.addProperty("agentRole", "a".repeat(300)); proposal.addProperty("success", "s".repeat(300));
        for (int i = 0; i < 4; i++) proposal.getAsJsonArray("questions").add("q".repeat(160));
        for (String sign : List.of("rock", "paper", "scissor")) proposal.getAsJsonArray("checks").add(JsonParser.parseString(
                "{\"eventType\":\"obs.hand.sign\",\"field\":\"sign\",\"operator\":\"eq\",\"value\":\"" + sign + "\",\"minConfidence\":0.8}"));
        assertTrue(proposal.toString().length() > 2048);
        turn(scope, "PROPOSE", proposal.toString(), "Propose these detailed terms");
        assertEquals(proposal, memory(scope).get(CoordinationMemory.DRAFT));
        proposal.addProperty("agentRole", "Keep the same terms and use a concise response");
        turn(scope, "PROPOSE", proposal.toString(), "Make the response concise");
        assertEquals(proposal, memory(scope).get(CoordinationMemory.DRAFT));
    }
    @Test void twoReceiptsCommitOneRevisionAndDuplicatesCannotRepeatIt() {
        var scope = create(); turn(scope, "PROPOSE", AGREEMENT, "Propose an invitation task");
        var agent = agents.findById(scope.id()).orElseThrow();
        var owner = new ch.zhaw.prometheus.model.policy.ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
        agents.saveAndFlush(agent); ownership.acquire(scope.id(), owner);
        var changed = JsonParser.parseString(AGREEMENT).getAsJsonObject(); changed.addProperty("agentRole", "Use a dry one-liner");
        when(gateway.infer(any())).thenReturn("{\"operation\":\"PROPOSE\",\"agreement\":" + changed + ",\"reply\":{\"speech\":\"Propose concise wording\"}}");
        clearInvocations(gateway);
        long now = Instant.now().toEpochMilli();
        var first = new ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Fragment("dry", ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Speaker.USER, 0L, 200L, now, 1, "A dry");
        var last = new ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Fragment("line", first.speaker(), 1400L, 1800L, now + 1400, 2, " one-liner");
        assertTrue(ingress.receipt(scope.id(), owner, first)); assertTrue(ingress.receipt(scope.id(), owner, last));
        assertFalse(ingress.receipt(scope.id(), owner, first));
        var segment = new ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Segment(UUID.randomUUID(), first.speaker(), List.of(first, last),
                ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Closure.COMPLETE, "observed_silence", now, now + 1400);
        var committed = ingress.commit(scope.id(), owner, segment, List.of("Coordination awaiting acceptance")).orElseThrow();
        assertEquals(List.of("dry", "line"), committed.receiptIds()); assertEquals("COMPLETE", committed.status());
        assertEquals(changed, memory(scope).get(CoordinationMemory.DRAFT));
        assertEquals(committed.eventId(), ingress.commit(scope.id(), owner, segment, List.of()).orElseThrow().eventId());
        verify(gateway, times(1)).infer(any()); ownership.release(scope.id(), owner.sessionId());
    }
}
