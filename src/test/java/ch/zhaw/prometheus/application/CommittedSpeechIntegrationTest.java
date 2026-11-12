package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ch.zhaw.prometheus.agentdefs.core.CoordinationAgent;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.*;

@SpringBootTest(properties = "prometheus.runtime.tick.enabled=false")
class CommittedSpeechIntegrationTest {
    @Autowired AgentRepository agents;
    @Autowired AccessCodeAdminService admin;
    @Autowired ScopedDemoService demo;
    @Autowired LiveAgentContextService contexts;
    @Autowired ExternalSpeechOwnership ownership;
    @Autowired ExternalSpeechRecordingService recording;
    @Autowired AgentApplicationService turns;
    @Autowired LiveCommittedSpeechService speech;
    @MockitoBean LanguageModelGateway gateway;
    @MockitoBean SpeechSynthesisGateway synthesis;
    record Scope(String code, UUID id, ExternalSpeech owner) {}
    Storage memory(Scope scope) { return ((TaskPolicy) agents.findById(scope.id()).orElseThrow().getCurrentState().ownPolicy()).storage(); }
    Scope create() {
        String code = UUID.randomUUID().toString().substring(0, 5); var access = admin.createAccessCode(code, true);
        admin.replaceAllowedAgentTypes(access.getId(), List.of(CoordinationAgent.KEY));
        UUID id = demo.createAgent(code, CoordinationAgent.KEY).getID(), session = UUID.randomUUID();
        var snapshot = contexts.claim(code, id, session).orElseThrow(); assertTrue(snapshot.committedSpeechOutput());
        when(synthesis.synthesize(anyString(), eq("alloy"), eq(1.0), eq(SpeechAudioFormat.PCM)))
                .thenAnswer(call -> new SpeechAudio(new byte[48000], "audio/pcm"));
        return new Scope(code, id, new ExternalSpeech(session, snapshot.epoch()));
    }
    void turn(Scope scope, String user, String operation, JsonElement agreement, String reply) {
        var result = new JsonObject(); result.addProperty("operation", operation); result.add("agreement", agreement);
        var plan = new JsonObject(); plan.addProperty("speech", reply); result.add("reply", plan);
        when(gateway.infer(any())).thenReturn(result.toString());
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION, user), OutputProfile.FULL_PLAN);
    }
    UUID response(Scope scope) { return UUID.fromString(memory(scope).get("core.task.response").getAsJsonObject().get("id").getAsString()); }
    @Test void aReloadedFinalContributionRequiresReleasePlaybackAndTheFinalCueBeforeReflection() throws Exception {
        var scope = create(); var window = new LiveSpeechApprovalService.Window();
        try {
            var agreement = JsonParser.parseString(CoordinationAgentIntegrationTest.AGREEMENT).getAsJsonObject(); agreement.addProperty("dialogueLimit", 1);
            turn(scope, "Start an invitation task now", "PROPOSE_AND_START", agreement, "Welcome to the lab.");
            assertEquals("FINISHING", CoordinationMemory.stage(memory(scope))); UUID response = response(scope);
            // Stray native output remains an unheard audit event and cannot steal the committed response.
            recording.record(scope.id(), scope.owner(), UUID.randomUUID().toString(), "An invented native response.", List.of(),
                    SpeechProvenance.Association.NONE, true, Instant.now());
            assertEquals("PENDING", TaskResponse.status(memory(scope)));
            when(synthesis.synthesize(anyString(), anyString(), anyDouble(), any())).thenAnswer(call -> {
                assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                CompletableFuture.runAsync(() -> turns.serialized(scope.id(), () -> null)).get(2, TimeUnit.SECONDS);
                return new SpeechAudio(new byte[48000], "audio/pcm");
            });
            var result = speech.synthesize(scope.id(), scope.owner(), window, response); assertEquals("APPROVED", result.status());
            assertEquals("Welcome to the lab.", result.text()); assertEquals(1, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
            assertEquals("SYNTHESIZED", memory(scope).get(TaskResponse.KEY).getAsJsonObject().get("origin").getAsString());
            assertFalse(contexts.playbackSettled(scope.code(), scope.id(), scope.owner(), response, result.segmentId()));
            assertTrue(speech.validate(scope.id(), scope.owner(), window, response, result.segmentId(), false));
            assertFalse(speech.validate(scope.id(), scope.owner(), window, response, result.segmentId(), false));
            assertEquals("DISCARDED", speech.synthesize(scope.id(), scope.owner(), new LiveSpeechApprovalService.Window(), response).status());
            assertEquals("FINISHING", CoordinationMemory.stage(memory(scope)));
            assertTrue(speech.validate(scope.id(), scope.owner(), window, response, result.segmentId(), true));
            var heard = agents.findById(scope.id()).orElseThrow().getEventHistory().toList().stream()
                    .filter(e -> result.text().equals(ConversationProjection.speech(e))).toList();
            assertEquals(1, heard.size()); assertEquals(SpeechProvenance.Origin.SYNTHESIZED, heard.getFirst().speechProvenance().origin());
            assertTrue(contexts.playbackSettled(scope.code(), scope.id(), scope.owner(), response, result.segmentId()));
            assertEquals("REFLECTION", CoordinationMemory.stage(memory(scope)));
            assertEquals("PLAYBACK_CONFIRMED", memory(scope).get("coordination.delivery").getAsJsonObject().get("status").getAsString());
            assertEquals("LIMIT_REACHED", memory(scope).get(CoordinationMemory.OUTCOME).getAsString());
        } finally { ownership.release(scope.id(), scope.owner().sessionId()); }
    }
    @Test void onlyPlayedSynthesizedProposalEstablishesTheNaturalAcceptanceInvitation() {
        var scope = create(); var window = new LiveSpeechApprovalService.Window();
        try {
            var agreement = JsonParser.parseString(CoordinationAgentIntegrationTest.AGREEMENT).getAsJsonObject(); agreement.addProperty("dialogueLimit", 2);
            turn(scope, "Propose an invitation task", "PROPOSE", agreement, "Propose an invitation");
            UUID response = response(scope); var result = speech.synthesize(scope.id(), scope.owner(), window, response);
            assertEquals("APPROVED", result.status());
            for (String field : List.of("goal", "humanRole", "agentRole", "success")) assertTrue(result.text().contains(agreement.get(field).getAsString()));
            assertFalse(memory(scope).containsKey("core.task.spokenPrompt"));
            assertTrue(speech.validate(scope.id(), scope.owner(), window, response, result.segmentId(), false));
            assertFalse(memory(scope).containsKey("core.task.spokenPrompt"));
            assertTrue(speech.validate(scope.id(), scope.owner(), window, response, result.segmentId(), true));
            assertEquals(result.segmentId(), memory(scope).get("core.task.spokenPrompt").getAsString());
            turn(scope, "Yes please.", "START", JsonNull.INSTANCE, "Welcome to the lab.");
            assertEquals("RUNNING", TaskMemory.phase(memory(scope))); assertEquals(1, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
            assertEquals(agreement, memory(scope).get(CoordinationMemory.AGREEMENT));
        } finally { ownership.release(scope.id(), scope.owner().sessionId()); }
    }
}
