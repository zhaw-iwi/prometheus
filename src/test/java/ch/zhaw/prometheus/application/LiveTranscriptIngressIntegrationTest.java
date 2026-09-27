package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ch.zhaw.prometheus.agentdefs.core.CoreRpsRevealPolicy;
import ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.commons.decisions.LatestEventTypeDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.rps.*;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest(properties = "prometheus.runtime.tick.enabled=false")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LiveTranscriptIngressIntegrationTest {
    @Autowired AgentRepository agents;
    @Autowired ch.zhaw.prometheus.repositories.StorageRepository storages;
    @Autowired LiveTranscriptIngressService ingress;
    @Autowired ExternalSpeechOwnership ownership;
    @MockitoBean LanguageModelGateway gateway;
    static UUID agentId;
    static ExternalSpeech owner;
    static Segment accepted;
    static UUID acceptedEvent;
    static Object oldIngress;
    static String acceptedActionVersion;
    static UUID storageId;

    Fragment fragment(String id, Speaker speaker, long start, String text) {
        return new Fragment(id, speaker, start, start + 500, Instant.now().toEpochMilli(), start, text);
    }
    Segment segment(Fragment fragment, Closure closure) {
        return new Segment(UUID.nameUUIDFromBytes((owner.sessionId() + fragment.eventId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                fragment.speaker(), List.of(fragment), closure, closure == Closure.COMPLETE ? "observed_silence" : "test_incomplete",
                fragment.receivedMs(), fragment.receivedMs());
    }
    int rounds() { return agents.findById(agentId).orElseThrow().getStorage().get(RpsStorageKeys.CURRENT_ROUND_NUMBER).getAsInt(); }
    String actionVersion() { return storages.findById(storageId).orElseThrow().writeVersion(RpsStorageKeys.CURRENT_AGENT_SIGN); }
    @Test @Order(1) void commitsRealBlockingActionOnceAndQuarantinesUncertainInput() {
        Storage storage = new Storage();
        State state = new State("game", new CoreRpsRevealPolicy(storage), List.of());
        state.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE), new RpsSelectAgentSignAction(storage).blocking(), state));
        Agent agent = new Agent("Live ingress ledger", "", state, storage);
        var epoch = agent.executionEpoch(); agent = agents.saveAndFlush(agent); agentId = agent.getId();
        storageId = storage.getID();
        owner = new ExternalSpeech(UUID.randomUUID(), epoch); ownership.acquire(agentId, owner);
        Fragment input = fragment("first", Speaker.USER, 0, "My hand is ready for a round"); accepted = segment(input, Closure.COMPLETE);
        assertTrue(ingress.receipt(agentId, owner, input));
        var result = ingress.commit(agentId, owner, accepted, List.of("game")).orElseThrow(); acceptedEvent = result.eventId();
        assertEquals("COMPLETE", result.status()); assertNotNull(acceptedEvent); assertEquals(1, rounds());
        assertEquals(List.of("first"), result.receiptIds());
        acceptedActionVersion = actionVersion();
        assertEquals(acceptedEvent, ingress.commit(agentId, owner, accepted, List.of("game")).orElseThrow().eventId()); assertEquals(1, rounds());
        Fragment nativeSpeech = fragment("native", Speaker.ASSISTANT, 600, "Rock, scissor, paper");
        ingress.receipt(agentId, owner, nativeSpeech);
        var nativeResult = ingress.commit(agentId, owner, segment(nativeSpeech, Closure.COMPLETE), List.of("game")).orElseThrow();
        assertNotNull(nativeResult.eventId()); assertEquals(1, rounds());
        Fragment shortReply = fragment("short", Speaker.USER, 1200, "yes"); ingress.receipt(agentId, owner, shortReply);
        assertEquals("CLARIFICATION", ingress.commit(agentId, owner, segment(shortReply, Closure.COMPLETE), List.of("game")).orElseThrow().status());
        Fragment unfinished = fragment("unfinished", Speaker.USER, 1800, "Another round please"); ingress.receipt(agentId, owner, unfinished);
        assertEquals("INCOMPLETE", ingress.commit(agentId, owner, segment(unfinished, Closure.INCOMPLETE), List.of("game")).orElseThrow().status());
        Fragment late = fragment("late", Speaker.USER, 0, "Actually no"); ingress.receipt(agentId, owner, late);
        assertEquals("LATE", ingress.commit(agentId, owner, segment(late, Closure.LATE), List.of("game")).orElseThrow().status());
        assertEquals(1, rounds()); verifyNoInteractions(gateway); oldIngress = ingress;
        assertEquals(acceptedActionVersion, actionVersion());
    }
    @Test @Order(2) void applicationContextRestartCannotReplayReceiptOrTaskButNewIdentityCanRepeatWords() {
        assertNotSame(oldIngress, ingress); // @DirtiesContext rebuilt application services and ownership.
        Agent agent = agents.findById(agentId).orElseThrow(); assertNull(ownership.current(agent));
        // A trusted adapter retry is simulated deliberately; production reconnect creates a fresh session.
        ownership.acquire(agentId, owner);
        assertFalse(ingress.receipt(agentId, owner, accepted.fragments().getFirst()));
        assertEquals(acceptedEvent, ingress.commit(agentId, owner, accepted, List.of("game")).orElseThrow().eventId());
        assertEquals(List.of("first"), ingress.commit(agentId, owner, accepted, List.of("game")).orElseThrow().receiptIds());
        assertEquals(1, rounds());
        assertEquals(acceptedActionVersion, actionVersion());
        Fragment repeatedWords = fragment("second-distinct-turn", Speaker.USER, 2200, accepted.text());
        assertTrue(ingress.receipt(agentId, owner, repeatedWords));
        assertEquals("COMPLETE", ingress.commit(agentId, owner, segment(repeatedWords, Closure.COMPLETE), List.of("game")).orElseThrow().status());
        // Selection writes again; no round was evaluated, so the round number remains one.
        assertNotEquals(acceptedActionVersion, actionVersion());
        assertEquals(1, rounds()); verifyNoInteractions(gateway);
        String beforeStop = actionVersion();
        Fragment stopped = fragment("queued-before-stop", Speaker.USER, 3000, "My hand is ready again");
        assertTrue(ingress.receipt(agentId, owner, stopped));
        ownership.pauseInput(agentId, owner.sessionId());
        assertEquals("input_stopped_before_admission", ingress.commit(agentId, owner, segment(stopped, Closure.COMPLETE), List.of("game")).orElseThrow().reason());
        assertEquals(beforeStop, actionVersion());
    }
    @Test @Order(3) void failedProcessingKeepsDurableClaimAndNeverRetriesAutomatically() {
        PromptPolicy policy = new PromptPolicy("Speak", null, null); policy.setNonVerbalPlanPrompt("Use a gesture");
        Agent agent = new Agent("Failed capture", "", new State("failed", policy, List.of())); UUID epoch = agent.executionEpoch();
        agent = agents.saveAndFlush(agent); var failedOwner = new ExternalSpeech(UUID.randomUUID(), epoch);
        ownership.acquire(agent.getId(), failedOwner);
        Fragment fragment = fragment("failed-fragment", Speaker.USER, 0, "Please show a calm expression");
        Segment segment = new Segment(UUID.randomUUID(), Speaker.USER, List.of(fragment), Closure.COMPLETE, "observed_silence",
                fragment.receivedMs(), fragment.receivedMs());
        UUID id = agent.getId(); ingress.receipt(id, failedOwner, fragment);
        when(gateway.infer(any())).thenThrow(new IllegalStateException("synthetic inference failure"));
        assertThrows(IllegalStateException.class, () -> ingress.commit(id, failedOwner, segment, List.of("failed")));
        assertEquals("FAILED", ingress.commit(id, failedOwner, segment, List.of("failed")).orElseThrow().status());
        assertTrue(agents.findById(id).orElseThrow().getEventHistory().isEmpty()); verify(gateway, times(1)).infer(any());
    }
}
