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
        hear(scope, complete, "final-contribution", "The class is invited to the lab.");
    }
    void hear(Scope scope, boolean complete, String segment, String words) {
        Instant started = Instant.parse(memory(scope).get(TaskResponse.KEY).getAsJsonObject().get("created").getAsString()).plusNanos(1);
        doAnswer(call -> {
            var request = (InferenceRequest) call.getArgument(0);
            var input = JsonParser.parseString(request.messages().getLast().getContent()).getAsJsonObject();
            var result = new JsonObject(); for (String key : List.of("responseId", "revision", "segmentId")) result.add(key, input.get(key));
            result.addProperty("verdict", "MATCH"); return result.toString();
        }).when(gateway).infer(any());
        assertTrue(recording.record(scope.id(), scope.owner(), segment, words,
                List.of(), SpeechProvenance.Association.NONE, complete, started).isPresent());
    }
    @Test void scissorsConversationReachesReflectionOnlyAfterOneCapturedAndPlayedContribution() throws Exception {
        JsonObject fixture;
        try (var input = getClass().getResourceAsStream("/coordination/scissors-interaction.json")) {
            fixture = JsonParser.parseReader(new java.io.InputStreamReader(java.util.Objects.requireNonNull(input), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        String code = UUID.randomUUID().toString().substring(0, 5); var access = admin.createAccessCode(code, true);
        admin.replaceAllowedAgentTypes(access.getId(), List.of(CoordinationAgent.KEY));
        UUID id = demo.createAgent(code, CoordinationAgent.KEY).getID(), session = UUID.randomUUID();
        var snapshot = contexts.claim(code, id, session).orElseThrow();
        var scope = new Scope(code, id, new ExternalSpeech(session, snapshot.epoch()));
        var words = fixture.getAsJsonObject("turns");
        try {
            assertTrue(snapshot.instructions().contains("Valerian"));
            assertTrue(snapshot.instructions().contains("Laureline"));
            for (String name : List.of("persona", "capabilities")) {
                interactionTurn(scope, words.get(name).getAsString(), "KEEP", JsonNull.INSTANCE);
                assertEquals("CONVERSATION", CoordinationMemory.stage(memory(scope)));
            }
            sign(scope, Instant.now());
            interactionTurn(scope, words.get("sense").getAsString(), "KEEP", JsonNull.INSTANCE);
            var evidence = contexts.refresh(id, scope.owner()).orElseThrow().context().items().stream()
                    .filter(item -> Event.TYPE_HAND_SIGN.equals(item.type())).findFirst().orElseThrow();
            assertTrue(evidence.text().contains("scissor"));
            assertFalse(evidence.sourceIds().isEmpty()); assertTrue(evidence.expiresAt().isAfter(Instant.now()));
            interactionTurn(scope, words.get("proposal").getAsString(), "PROPOSE", fixture.get("agreement"));
            assertEquals("READY", CoordinationMemory.stage(memory(scope)));
            assertFalse(memory(scope).containsKey(CoordinationMemory.AGREEMENT));
            var proposal = memory(scope).get(CoordinationMemory.DRAFT).deepCopy();
            interactionTurn(scope, words.get("accept").getAsString(), "START", JsonNull.INSTANCE);
            assertEquals("EXECUTION", CoordinationMemory.stage(memory(scope)));
            assertEquals(proposal, memory(scope).get(CoordinationMemory.AGREEMENT));
            assertEquals(0, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
            hear(scope, true, "interaction-arming", "The task is active. Show a fresh scissor sign for one joke.");
            awaitCaptured(scope);
            // The real three-second cue cooldown is part of the contract; do not replace it with a storage edit.
            Instant after = Instant.parse(memory(scope).get(TaskMemory.AFTER).getAsString());
            long delay = java.time.Duration.between(Instant.now(), after).toMillis();
            if (delay >= 0) Thread.sleep(delay + 20);
            doReturn("{\"speech\":\"Why did the robot bring scissors? To cut through the small talk.\"}").when(gateway).infer(any());
            sign(scope, Instant.now());
            assertEquals("FINISHING", CoordinationMemory.stage(memory(scope)));
            assertEquals(1, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
            hear(scope, true, "interaction-joke", "Why did the robot bring scissors? To cut through the small talk."); awaitCaptured(scope);
            var cue = contexts.refresh(id, scope.owner()).orElseThrow().context().activity().playbackCue();
            assertNotNull(cue.segmentId());
            assertEquals("FINISHING", CoordinationMemory.stage(memory(scope)));
            assertTrue(contexts.playbackSettled(code, id, scope.owner(), cue.id(), cue.segmentId()));
            assertEquals("REFLECTION", CoordinationMemory.stage(memory(scope)));
            interactionTurn(scope, words.get("acknowledge").getAsString(), "REFLECT", JsonNull.INSTANCE);
            assertEquals("REFLECTION", CoordinationMemory.stage(memory(scope)));
            assertEquals(words.get("acknowledge").getAsString(), memory(scope).get(CoordinationMemory.REVIEW).getAsJsonObject().get("userReport").getAsString());
            sign(scope, Instant.now()); assertEquals(1, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
            long historySize = agents.findById(id).orElseThrow().getEventHistory().toList().size();
            interactionTurn(scope, words.get("back").getAsString(), "RETURN_TO_CONVERSATION", JsonNull.INSTANCE);
            assertEquals("CONVERSATION", CoordinationMemory.stage(memory(scope)));
            var archived = memory(scope).get(CoordinationMemory.OUTCOMES).getAsJsonArray().get(0).getAsJsonObject();
            assertEquals("1", archived.get("actions").getAsString());
            assertEquals("PLAYBACK_CONFIRMED", archived.getAsJsonObject("delivery").get("status").getAsString());
            assertTrue(agents.findById(id).orElseThrow().getEventHistory().toList().size() > historySize);
        } finally { ownership.release(id, session); }
    }
    void interactionTurn(Scope scope, String text, String operation, JsonElement agreement) {
        var reply = new JsonObject(); reply.addProperty("operation", operation); reply.add("agreement", agreement);
        reply.add("reply", JsonParser.parseString("{\"speech\":\"Use the confirmed current facts.\"}"));
        doReturn(reply.toString()).when(gateway).infer(any());
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION, text), OutputProfile.FULL_PLAN).orElseThrow();
    }
    void sign(Scope scope, Instant observed) {
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_HAND_SIGN, "camera", Event.KIND_OBSERVATION,
                "{\"sign\":\"scissor\",\"confidence\":0.93,\"ts\":\"" + observed + "\"}"), OutputProfile.FULL_PLAN).orElseThrow();
    }
    void awaitCaptured(Scope scope) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!"CAPTURED".equals(TaskResponse.status(memory(scope))) && System.nanoTime() < deadline) Thread.sleep(20);
        assertEquals("CAPTURED", TaskResponse.status(memory(scope)));
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
    @Test void exactCommittedLiteralNeedsNoSemanticCallButStillWaitsForScopedPlayback() throws Exception {
        var scope = start();
        try {
            var agent = agents.findById(scope.id()).orElseThrow();
            var storage = ((TaskPolicy) agent.getCurrentState().ownPolicy()).storage();
            TaskResponse.contribution(storage, "Required spoken content: The class is invited to the lab.", "The class is invited to the lab.");
            agents.saveAndFlush(agent); clearInvocations(gateway);
            var started = Instant.parse(memory(scope).get(TaskResponse.KEY).getAsJsonObject().get("created").getAsString()).plusNanos(1);
            recording.record(scope.id(), scope.owner(), "literal", "The class is invited to the lab!", List.of(), SpeechProvenance.Association.NONE, true, started).orElseThrow();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (!"CAPTURED".equals(TaskResponse.status(memory(scope))) && System.nanoTime() < deadline) Thread.sleep(20);
            assertEquals("CAPTURED", TaskResponse.status(memory(scope))); verifyNoInteractions(gateway);
            assertEquals("FINISHING", CoordinationMemory.stage(memory(scope)));
            var cue = contexts.refresh(scope.id(), scope.owner()).orElseThrow().context().activity().playbackCue();
            assertTrue(contexts.playbackSettled(scope.code(), scope.id(), scope.owner(), cue.id(), "literal"));
            assertEquals("REFLECTION", CoordinationMemory.stage(memory(scope)));
        } finally { ownership.release(scope.id(), scope.owner().sessionId()); }
    }
    @Test void oneUnrelatedSegmentCannotStealTheActualResponseWhileItsCheckIsRunning() throws Exception {
        var scope = start();
        var checking = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        try {
            var response = memory(scope).get(TaskResponse.KEY).getAsJsonObject();
            Instant started = Instant.parse(response.get("created").getAsString()).plusNanos(1);
            when(gateway.infer(any())).thenAnswer(call -> {
                var input = JsonParser.parseString(((InferenceRequest) call.getArgument(0)).messages().getLast().getContent()).getAsJsonObject();
                int n = calls.incrementAndGet();
                if (n == 1) { checking.countDown(); assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS)); }
                var result = new JsonObject(); for (String key : List.of("responseId", "revision", "segmentId")) result.add(key, input.get(key));
                result.addProperty("verdict", n == 1 ? "UNRELATED" : "MATCH"); return result.toString();
            });
            recording.record(scope.id(), scope.owner(), "unrelated", "I am waiting for further conversation.", List.of(), SpeechProvenance.Association.NONE, true, started).orElseThrow();
            assertTrue(checking.await(5, java.util.concurrent.TimeUnit.SECONDS));
            recording.record(scope.id(), scope.owner(), "actual", "The class is invited to the lab.", List.of(), SpeechProvenance.Association.NONE, true, started.plusNanos(1)).orElseThrow();
            assertEquals("actual", memory(scope).get(TaskResponse.KEY).getAsJsonObject().getAsJsonObject("candidate").get("segment").getAsString());
            release.countDown();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (!"CAPTURED".equals(TaskResponse.status(memory(scope))) && System.nanoTime() < deadline) Thread.sleep(20);
            assertEquals("CAPTURED", TaskResponse.status(memory(scope))); assertEquals(2, calls.get());
            var captured = memory(scope).get(TaskResponse.KEY).getAsJsonObject();
            assertEquals(response.get("id"), captured.get("id")); assertEquals(response.get("deadline"), captured.get("deadline"));
            assertEquals("actual", captured.get("segment").getAsString()); assertFalse(captured.has("candidate"));
            assertEquals("FINISHING", CoordinationMemory.stage(memory(scope))); assertEquals(1, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
            var cue = contexts.refresh(scope.id(), scope.owner()).orElseThrow().context().activity().playbackCue();
            assertFalse(contexts.playbackSettled(scope.code(), scope.id(), scope.owner(), cue.id(), "unrelated"));
            assertTrue(contexts.playbackSettled(scope.code(), scope.id(), scope.owner(), cue.id(), "actual"));
            assertEquals("REFLECTION", CoordinationMemory.stage(memory(scope)));
        } finally { release.countDown(); ownership.release(scope.id(), scope.owner().sessionId()); }
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
