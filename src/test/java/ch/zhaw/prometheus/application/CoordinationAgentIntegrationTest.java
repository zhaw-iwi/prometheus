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
    @Test void preparationEvidencePersistsAndARevisedDraftRequiresFreshEvidence() {
        var scope = create();
        var proposal = JsonParser.parseString(AGREEMENT).getAsJsonObject();
        proposal.getAsJsonArray("checks").add(JsonParser.parseString("""
                {"eventType":"obs.hand.sign", "field":"sign", "operator":"eq", "value":"rock", "minConfidence":0.8}
                """));
        turn(scope, "PROPOSE", proposal.toString(), "First check that you can recognise rock, then help with the invitation");
        turn(scope, "START", "null", "I have shown it; start now"); assertFalse(memory(scope).containsKey(CoordinationMemory.AGREEMENT));
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_HAND_SIGN, "camera", Event.KIND_OBSERVATION,
                "{\"sign\":\"rock\",\"confidence\":0.9,\"ts\":\"" + Instant.now() + "\"}"), OutputProfile.FULL_PLAN);
        assertEquals("READY", CoordinationMemory.stage(memory(scope))); assertEquals(1, CoordinationMemory.evidence(memory(scope)).size());
        proposal.addProperty("agentRole", "Clarify references and edit the supplied details");
        turn(scope, "PROPOSE", proposal.toString(), "Revise the proposal to clarify references");
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
        proposal.addProperty("goal", "g".repeat(300)); proposal.addProperty("humanRole", "h".repeat(200));
        proposal.addProperty("agentRole", "a".repeat(200)); proposal.addProperty("success", "s".repeat(300));
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
