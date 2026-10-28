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
import ch.zhaw.prometheus.agentdefs.core.*;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.*;

@SpringBootTest(properties = "prometheus.runtime.tick.enabled=false")
class CoordinationDeliveryIntegrationTest {
    @Autowired AgentRepository agents;
    @Autowired AccessCodeAdminService admin;
    @Autowired ScopedDemoService demo;
    @Autowired LiveAgentContextService contexts;
    @Autowired ExternalSpeechRecordingService recording;
    @Autowired ExternalSpeechOwnership ownership;
    @Autowired AgentActivityService activity;
    @MockitoBean LanguageModelGateway gateway;
    record Scope(String code, UUID id, ExternalSpeech owner) {}
    Storage memory(Scope scope) { return ((TaskPolicy) agents.findById(scope.id()).orElseThrow().getCurrentState().ownPolicy()).storage(); }
    Scope start() {
        String code = UUID.randomUUID().toString().substring(0, 5); var access = admin.createAccessCode(code, true);
        admin.replaceAllowedAgentTypes(access.getId(), List.of(CoordinationAgent.KEY));
        UUID id = demo.createAgent(code, CoordinationAgent.KEY).getID(), session = UUID.randomUUID();
        var proposal = JsonParser.parseString(CoordinationAgentIntegrationTest.AGREEMENT).getAsJsonObject(); proposal.addProperty("dialogueLimit", 1);
        when(gateway.infer(any())).thenReturn("{\"operation\":\"PROPOSE\",\"agreement\":" + proposal + ",\"reply\":{\"speech\":\"Propose invitation\"}}");
        demo.acknowledge(code, id, new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION, "Propose an invitation task"), OutputProfile.FULL_PLAN);
        var snapshot = contexts.claim(code, id, session).orElseThrow(); var owner = new ExternalSpeech(session, snapshot.epoch());
        when(gateway.infer(any())).thenReturn("{\"operation\":\"START\",\"agreement\":null,\"reply\":{\"speech\":\"Invite the class to the lab\"}}");
        demo.acknowledge(code, id, new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION, "Start the saved proposal now"), OutputProfile.FULL_PLAN);
        var scope = new Scope(code, id, owner); assertEquals("FINISHING", CoordinationMemory.stage(memory(scope))); return scope;
    }
    void hear(Scope scope, boolean complete) {
        Instant started = Instant.parse(memory(scope).get(TaskResponse.KEY).getAsJsonObject().get("created").getAsString()).plusNanos(1);
        when(gateway.infer(any())).thenAnswer(call -> {
            var request = (InferenceRequest) call.getArgument(0);
            var input = JsonParser.parseString(request.messages().getLast().getContent()).getAsJsonObject();
            var result = new JsonObject(); for (String key : List.of("responseId", "revision", "segmentId")) result.add(key, input.get(key));
            result.addProperty("verdict", "MATCH"); return result.toString();
        });
        assertTrue(recording.record(scope.id(), scope.owner(), "final-contribution", "The class is invited to the lab.",
                List.of(), SpeechProvenance.Association.NONE, complete, started).isPresent());
    }
    @Test void finishingReloadsAndOnlyScopedAssociatedPlaybackCompletesIt() throws Exception {
        var scope = start();
        try {
            var journal = memory(scope).get(CoordinationMemory.LOG).getAsJsonArray();
            var log = journal.get(journal.size()-1).getAsJsonObject();
            assertEquals("READY", log.get("beforePhase").getAsString()); assertEquals("FINISHING", log.get("phase").getAsString());
            assertEquals("LIMIT_REACHED", log.get("taskOutcome").getAsString());
            long position = log.get("sourcePosition").getAsLong();
            var persisted = agents.findById(scope.id()).orElseThrow().getEventHistory().toList().stream()
                    .filter(e -> Long.valueOf(position).equals(e.sourcePosition())).findFirst().orElseThrow();
            var entries = activity.snapshot(scope.id()).recent();
            var operation = entries.stream().filter(e -> e.stage().equals("coordination")
                    && Long.valueOf(position).equals(e.details().get("sourcePosition"))).findFirst().orElseThrow();
            assertTrue(entries.stream().anyMatch(e -> operation.operationId().equals(e.operationId()) && persisted.getId().equals(e.sourceId())));
            var pending = contexts.refresh(scope.id(), scope.owner()).orElseThrow().context().activity().playbackCue();
            assertNotNull(pending); assertNull(pending.segmentId());
            assertFalse(contexts.playbackSettled(scope.code(), scope.id(), scope.owner(), pending.id(), "final-contribution"));
            hear(scope, true);
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (!"CAPTURED".equals(TaskResponse.status(memory(scope))) && System.nanoTime() < deadline) Thread.sleep(20);
            assertEquals("CAPTURED", TaskResponse.status(memory(scope))); assertEquals("FINISHING", CoordinationMemory.stage(memory(scope)));
            assertFalse(contexts.playbackSettled(scope.code(), scope.id(), new ExternalSpeech(UUID.randomUUID(), scope.owner().epoch()), pending.id(), "final-contribution"));
            assertFalse(contexts.playbackSettled(scope.code(), scope.id(), scope.owner(), pending.id(), "different-segment"));
            assertTrue(contexts.playbackSettled(scope.code(), scope.id(), scope.owner(), pending.id(), "final-contribution"));
            assertEquals("REFLECTION", CoordinationMemory.stage(memory(scope)));
            assertEquals("Coordination reflection", agents.findById(scope.id()).orElseThrow().getCurrentState().getName());
            assertFalse(contexts.playbackSettled(scope.code(), scope.id(), scope.owner(), pending.id(), "final-contribution"));
        } finally { ownership.release(scope.id(), scope.owner().sessionId()); }
    }
    @Test void incompleteCaptureRoutesToUncertaintyWithoutWaitingForTimeout() {
        var scope = start();
        try {
            hear(scope, false); assertEquals("UNCERTAIN", CoordinationMemory.stage(memory(scope)));
            assertEquals("Coordination uncertain", agents.findById(scope.id()).orElseThrow().getCurrentState().getName());
            assertEquals("LIMIT_REACHED", memory(scope).get(CoordinationMemory.OUTCOME).getAsString());
        } finally { ownership.release(scope.id(), scope.owner().sessionId()); }
    }
    @Test void aFreshSessionAfterOwnershipLossCannotReplayAPersistedFinalResponse() {
        var scope = start(); ownership.release(scope.id(), scope.owner().sessionId());
        var session = UUID.randomUUID();
        try {
            var fresh = contexts.claim(scope.code(), scope.id(), session).orElseThrow();
            assertEquals("UNCERTAIN", CoordinationMemory.stage(memory(scope)));
            assertEquals(List.of("Coordination uncertain"), fresh.statePath());
            assertNull(fresh.activity().continuation()); assertNull(fresh.activity().playbackCue());
            assertEquals(1, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        } finally { ownership.release(scope.id(), session); }
    }
}
