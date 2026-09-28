package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.google.gson.JsonObject;
import ch.zhaw.prometheus.agentdefs.core.*;
import ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.*;
import ch.zhaw.prometheus.controllers.dto.LiveSessionRequest;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.access.AccessCodeAgent;
import ch.zhaw.prometheus.model.commons.decisions.LatestEventTypeDecision;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.rps.*;
import ch.zhaw.prometheus.repositories.*;
import ch.zhaw.prometheus.spi.*;
import ch.zhaw.prometheus.spi.live.LiveSessionGateway;

@SpringBootTest(properties = {"prometheus.live.enabled=true", "prometheus.runtime.tick.enabled=false"})
class LiveMultimodalBridgeIntegrationTest {
    @Autowired AgentRepository agents;
    @Autowired AccessCodeRepository codes;
    @Autowired AccessCodeAgentRepository links;
    @Autowired AccessCodeAdminService admin;
    @Autowired ScopedLiveSessionService live;
    @Autowired LiveAgentContextService contexts;
    @Autowired LiveContextBridgeService bridge;
    @Autowired LiveTranscriptIngressService ingress;
    @Autowired AgentApplicationService turns;
    @Autowired ExternalSpeechRecordingService recording;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean LiveSessionGateway gateway;
    @MockitoBean LanguageModelGateway language;
    @MockitoBean SpeechSynthesisGateway speech;

    @Test void committedTaskSensoryAndVisualChangesUseSidebandOnceWithoutTtsOrNativeFeedback() {
        Storage storage = new Storage();
        State result = new State("result", new CoreRpsResultPolicy(storage), List.of());
        State reveal = new State("reveal", new CoreRpsRevealPolicy(storage), List.of());
        reveal.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_HAND_SIGN), new RpsEvaluateRoundAction(storage), result));
        State waiting = new State("waiting", new PromptPolicy("Wait for the user", null, null), List.of());
        waiting.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE), new RpsSelectAgentSignAction(storage), reveal));
        Agent agent = new Agent("Live bridge fixture", "", waiting, storage);
        agent.setInteractionProfile(new MultimodalBehaviour().createAgent().getInteractionProfile());
        UUID epoch = agent.executionEpoch(); agent = agents.saveAndFlush(agent); UUID id = agent.getId();
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 5);
        var access = admin.createAccessCode(code, true);
        links.saveAndFlush(new AccessCodeAgent(codes.findById(access.getId()).orElseThrow(), agent));
        var sent = new CopyOnWriteArrayList<JsonObject>(); var receive = new AtomicReference<Consumer<JsonObject>>();
        when(gateway.create(any())).thenReturn(new LiveSessionGateway.Session("live_bridge", "v=0 answer"));
        when(gateway.attach(anyString(), any(), any())).thenAnswer(call -> {
            receive.set(call.getArgument(1));
            return new LiveSessionGateway.Connection() {
                public boolean isOpen() { return true; }
                public void close() {}
                public void send(JsonObject event) {
                    sent.add(event.deepCopy()); JsonObject ack = new JsonObject();
                    String type = event.get("type").getAsString();
                    ack.addProperty("type", type.equals("session.close") ? "session.closed" : type.replace(".append", ".appended"));
                    if (event.has("event_id")) ack.addProperty("client_event_id", event.get("event_id").getAsString());
                    receive.get().accept(ack);
                }
            };
        });
        var session = live.create(code, id, new LiveSessionRequest("v=0 offer", "marin")).orElseThrow();
        var owner = new ExternalSpeech(session.handle(), epoch);
        try {
            JsonObject delegation = new JsonObject(); delegation.addProperty("type", "session.delegation.created"); delegation.addProperty("offset_ms", 1000);
            JsonObject metadata = new JsonObject(); metadata.addProperty("id", "request1"); metadata.addProperty("target", "client"); delegation.add("delegation", metadata);
            receive.get().accept(delegation); receive.get().accept(delegation.deepCopy());
            Fragment input = new Fragment("user-one", Speaker.USER, 0L, 1000L, Instant.now().toEpochMilli(), 1, "I want to play a round");
            var segment = new Segment(UUID.randomUUID(), Speaker.USER, List.of(input), Closure.COMPLETE, "observed_silence", input.receivedMs(), input.receivedMs());
            ingress.receipt(id, owner, input); var outcome = ingress.commit(id, owner, segment, List.of("waiting")).orElseThrow();
            bridge.captured(new LiveTranscriptCaptureService.Committed(id, owner, outcome));
            // Allow the 5s commit window, scheduler tick and local persistence; fake-clock tests assert exact cadence.
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(1, commentary(sent)));
            assertEquals("reveal", agents.findById(id).orElseThrow().getCurrentState().getName());
            assertEquals(1, agents.findById(id).orElseThrow().getEventHistory().toList().stream().filter(event -> Event.TYPE_USER_UTTERANCE.equals(event.getType())).count());
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertTrue(sent.stream().anyMatch(event -> event.has("delegation_id") && !event.get("delegation_id").isJsonNull())));

            turns.acknowledge(id, new EventRequest(Event.TYPE_WEATHER_CURRENT, "sensor", Event.KIND_OBSERVATION,
                    "{\"temperature\":17,\"condition\":\"rain\",\"observed_at\":\"" + Instant.now() + "\"}"));
            String weatherRevision = contexts.snapshot(code, id).orElseThrow().revision();
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(weatherRevision, live.status(code, id, session.handle()).orElseThrow().context().revision()));
            assertTrue(sent.stream().anyMatch(event -> event.has("content") && event.get("content").getAsString().contains("obs.weather.current")));
            assertEquals("reveal", agents.findById(id).orElseThrow().getCurrentState().getName()); assertEquals(1, commentary(sent));

            // A rolled-back observation cannot become provider context.
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                turns.acknowledge(id, new EventRequest(Event.TYPE_WEATHER_CURRENT, "sensor", Event.KIND_OBSERVATION, "{\"condition\":\"ROLLBACK_SENTINEL\"}"));
                status.setRollbackOnly();
            });
            turns.acknowledge(id, new EventRequest(Event.TYPE_HAND_SIGN, "sensor", Event.KIND_OBSERVATION, "{\"sign\":\"rock\",\"confidence\":1}"));
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(2, commentary(sent)));
            Agent reloaded = agents.findById(id).orElseThrow(); assertEquals("result", reloaded.getCurrentState().getName());
            assertEquals(1, reloaded.getStorage().get(RpsStorageKeys.ROUNDS).getAsJsonArray().size());
            Event intent = reloaded.getEventHistory().toList().getLast(); assertTrue(ConversationProjection.isIntent(intent));
            var plan = ch.zhaw.prometheus.model.behaviour.BehaviourPlan.fromJson(intent.getPayload()); assertNotNull(plan.getNonVerbal()); assertNotNull(plan.getDisplay());
            recording.record(id, owner, "native-result", "The round is complete", List.of(intent.getId()), SpeechProvenance.Association.AMBIGUOUS, true);
            String nativeRevision = contexts.snapshot(code, id).orElseThrow().revision(); bridge.refresh();
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(nativeRevision, live.status(code, id, session.handle()).orElseThrow().context().revision()));
            assertEquals(2, commentary(sent)); assertTrue(sent.stream().noneMatch(event -> event.toString().contains("ROLLBACK_SENTINEL")));
            verifyNoInteractions(language, speech);
        } finally { live.close(code, id, session.handle()); }
    }
    private long commentary(List<JsonObject> events) { return events.stream().filter(event -> "session.commentary.append".equals(event.get("type").getAsString())).count(); }
}
