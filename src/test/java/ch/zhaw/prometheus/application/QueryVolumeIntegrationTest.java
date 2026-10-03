package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import java.util.concurrent.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import jakarta.persistence.EntityManagerFactory;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.agentdefs.core.*;
import ch.zhaw.prometheus.controllers.dto.LiveSessionRequest;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.commons.decisions.LatestEventTypeDecision;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.*;
import ch.zhaw.prometheus.model.access.*;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.repositories.*;
import ch.zhaw.prometheus.spi.*;
import ch.zhaw.prometheus.spi.live.*;

/** Real HTTP boundary costs and lifecycle, with offline providers and no outer transaction. */
@SpringBootTest(properties = {"prometheus.runtime.tick.enabled=false", "prometheus.live.enabled=true",
        "prometheus.live.close-timeout-ms=100", "spring.jpa.open-in-view=false",
        "spring.datasource.hikari.maximum-pool-size=1", "spring.datasource.hikari.minimum-idle=1",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
@AutoConfigureMockMvc
class QueryVolumeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManagerFactory emf;
    @Autowired AgentRepository agents;
    @Autowired AccessCodeRepository codes;
    @Autowired AccessCodeAgentRepository links;
    @Autowired LiveAgentContextService contexts;
    @Autowired ExternalSpeechOwnership ownership;
    @Autowired ScopedDemoService demo;
    @Autowired LiveTranscriptIngressService ingress;
    @Autowired AgentApplicationService turns;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @MockitoSpyBean ScopedLiveSessionService live;
    @MockitoBean LiveSessionGateway gateway;
    @MockitoBean LiveContextBridgeService bridge;
    @MockitoBean LiveTranscriptCaptureService captures;
    @MockitoBean LanguageModelGateway language;

    @FunctionalInterface interface Check { void run() throws Exception; }
    long questions() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SHOW SESSION STATUS LIKE 'Questions'")) {
            assertTrue(result.next()); return result.getLong(2);
        }
    }
    long measure(String label, Check work) throws Exception {
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        long before = questions();
        stats.clear(); work.run();
        long databaseQuestions = questions() - before - 1; // Exclude the measuring SHOW itself.
        if (label.contains("idle-status") || label.contains("unchanged-updates")) {
            assertEquals(1, stats.getPrepareStatementCount(), label); assertEquals(0, stats.getEntityLoadCount(), label);
            assertEquals(1, databaseQuestions, label);
        }
        if (label.contains("idle-transcripts")) { assertEquals(2, stats.getPrepareStatementCount(), label); assertEquals(0, stats.getEntityLoadCount(), label); }
        if (label.contains("-face")) {
            int limit = label.startsWith("core.multimodal_behaviour") ? 21 : 13;
            assertTrue(stats.getPrepareStatementCount() <= limit, label + " statements=" + stats.getPrepareStatementCount());
            assertTrue(databaseQuestions <= limit + 6, label + " databaseQuestions=" + databaseQuestions);
        }
        System.out.printf("QUERY_VOLUME %s statements=%d databaseQuestions=%d entities=%d inserts=%d updates=%d collectionUpdates=%d%n",
                label, stats.getPrepareStatementCount(), databaseQuestions, stats.getEntityLoadCount(), stats.getEntityInsertCount(),
                stats.getEntityUpdateCount(), stats.getCollectionUpdateCount());
        return stats.getPrepareStatementCount();
    }
    @BeforeEach void providers() {
        doNothing().when(live).expire();
        when(language.infer(any())).thenAnswer(call -> {
            InferenceRequest request = call.getArgument(0);
            if (request.output() == InferenceRequest.Output.BOOLEAN) return "false";
            boolean externalSpeech = request.messages().stream().anyMatch(message -> message.getContent().contains("External speech owns"));
            return externalSpeech ? "{\"nonVerbal\":{\"gesture\":\"NONE\"}}"
                    : "{\"speech\":\"Offline diagnostic\",\"nonVerbal\":{\"gesture\":\"NONE\"}}";
        });
        when(language.complete(any())).thenReturn("Offline diagnostic");
        when(language.extract(any())).thenReturn(new com.google.gson.JsonPrimitive("saved"));
        when(gateway.create(any())).thenReturn(new LiveSessionGateway.Session("offline", "v=0 offline"));
        var connection = mock(LiveSessionGateway.Connection.class);
        when(connection.isOpen()).thenReturn(true);
        when(gateway.attach(any(), any(), any())).thenReturn(connection);
    }

    record Fixture(UUID id, String code, UUID scope) {}
    Fixture fixture(Agent agent) {
        agent.executionEpoch();
        agent = agents.saveAndFlush(agent);
        AccessCode code = codes.saveAndFlush(new AccessCode(UUID.randomUUID().toString().substring(0, 5), true));
        links.saveAndFlush(new AccessCodeAgent(code, agent));
        return new Fixture(agent.getId(), code.getCode(), code.getId());
    }
    EventRequest sensor(String type, String payload) {
        return new EventRequest(type, Event.ACTOR_USER, Event.KIND_OBSERVATION, payload);
    }
    Agent transitionAgent() {
        Storage storage = new Storage();
        State ready = new State("ready", new PromptPolicy("Respond", null, null), List.of());
        State initial = new State("initial", new PromptPolicy("Respond", null, null), List.of());
        initial.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_FACE_EMOTION),
                new StaticExtractionAction("Extract", storage, "saved").blocking(), ready));
        return new Agent("Persistence test", "", initial, storage);
    }
    List<String> events(UUID id) {
        return agents.findById(id).orElseThrow().getEventHistory().toList().stream()
                .map(event -> {
                    String payload = event.getPayload();
                    if (Event.TYPE_SOCIAL_SITUATION_CHANGE.equals(event.getType())) {
                        var normalized = com.google.gson.JsonParser.parseString(payload).getAsJsonObject();
                        normalized.remove("ts"); payload = normalized.toString();
                    }
                    return event.getType() + ":" + payload + ":" + event.getStatePath();
                }).toList();
    }

    @Test void batchPreservesIndividualTransitionsStorageAndDerivedEvents() {
        var separate = fixture(transitionAgent());
        var batched = fixture(transitionAgent());
        var requests = List.of(sensor(Event.TYPE_FACE_EMOTION, "{\"emotion\":\"neutral\"}"),
                sensor(Event.TYPE_SOCIAL_GROUPING, "{\"humanCount\":0,\"groupCount\":0,\"singletonCount\":0,\"largestGroupSize\":0}"),
                sensor(Event.TYPE_HUMAN_PRESENCE, "{\"humanCount\":0}"));
        requests.forEach(request -> assertTrue(demo.acknowledge(separate.code(), separate.id(), request,
                ch.zhaw.prometheus.model.policy.OutputProfile.FULL_PLAN).isPresent()));
        var result = demo.acknowledgeObservations(batched.code(), batched.id(), requests);
        assertEquals(List.of(200, 200, 200), result.stream().map(ScopedDemoService.ObservationResult::status).toList());
        assertEquals(events(separate.id()), events(batched.id()));
        var saved = agents.findById(batched.id()).orElseThrow();
        assertEquals("ready", saved.getCurrentState().getName());
        assertEquals(agents.findById(separate.id()).orElseThrow().getCurrentState().isStarting(), saved.getCurrentState().isStarting());
        assertTrue(saved.getStorage().containsKey("saved"));
        assertEquals(1, saved.getEventHistory().toList().stream().filter(e -> Event.TYPE_SOCIAL_SITUATION_CHANGE.equals(e.getType())).count());
        assertTrue(saved.getEventHistory().toList().stream().allMatch(e -> e.getId() != null));
    }

    @Test void failedBatchItemDoesNotLeakChangesIntoFollowingItem() {
        var fixture = fixture(transitionAgent());
        doThrow(new IllegalStateException("Offline failure")).when(language).infer(any());
        var result = demo.acknowledgeObservations(fixture.code(), fixture.id(), List.of(
                sensor(Event.TYPE_FACE_EMOTION, "{}"), sensor(Event.TYPE_HUMAN_PRESENCE, "{}")));
        assertEquals(List.of(500, 200), result.stream().map(ScopedDemoService.ObservationResult::status).toList());
        var saved = agents.findById(fixture.id()).orElseThrow();
        assertEquals("initial", saved.getCurrentState().getName());
        assertTrue(saved.getStorage().isEmpty());
        assertEquals(List.of(Event.TYPE_HUMAN_PRESENCE), saved.getEventHistory().toList().stream().map(Event::getType).toList());
    }

    @Test void scopedProviderWaitReleasesTransactionAndOnlyPoolConnection() throws Exception {
        var fixture = fixture(new MultimodalBehaviour().createAgent());
        var owner = new ExternalSpeech(UUID.randomUUID(), agents.findById(fixture.id()).orElseThrow().executionEpoch());
        ownership.acquire(fixture.id(), owner, fixture.scope());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS));
            return "{\"nonVerbal\":{\"gesture\":\"NONE\"}}";
        }).when(language).infer(any());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = executor.submit(() -> turns.generate(fixture.id(), List.of()));
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                assertEquals(0, dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class).getHikariPoolMXBean().getActiveConnections());
                questions(); // The only connection remains available while inference is waiting.
            } finally { release.countDown(); }
            assertEquals(BehaviourGenerationOutcome.GENERATED, pending.get(10, TimeUnit.SECONDS));
        } finally { ownership.revoke(fixture.id()); }
        assertEquals(1, agents.findById(fixture.id()).orElseThrow().getEventHistory().toList().size());
    }

    @Test void committedTranscriptChangesRevisionAndRevokedAccessIsImmediate() throws Exception {
        var fixture = fixture(new MultimodalBehaviour().createAgent());
        var session = live.create(fixture.code(), fixture.id(), new LiveSessionRequest("v=0 offline", "marin")).orElseThrow();
        var owner = new ExternalSpeech(session.handle(), agents.findById(fixture.id()).orElseThrow().executionEpoch());
        assertEquals(0, live.updates(fixture.code(), fixture.id(), session.handle(), -1).orElseThrow().transcriptRevision());
        long now = System.currentTimeMillis();
        var fragment = new Fragment("receipt", Speaker.USER, 0L, 500L, now, 1L, "Incomplete thought");
        assertTrue(ingress.receipt(fixture.id(), owner, fragment));
        var segment = new Segment(UUID.randomUUID(), Speaker.USER, List.of(fragment), Closure.INCOMPLETE, "test", now, now);
        assertEquals("INCOMPLETE", ingress.commit(fixture.id(), owner, segment, List.of("conversation")).orElseThrow().status());
        var update = live.updates(fixture.code(), fixture.id(), session.handle(), 0).orElseThrow();
        assertEquals(2, update.transcriptRevision());
        assertEquals(1, update.transcripts().size());
        assertNull(live.updates(fixture.code(), fixture.id(), session.handle(), 2).orElseThrow().transcripts());
        var code = codes.findById(fixture.scope()).orElseThrow(); code.setEnabled(false); codes.saveAndFlush(code);
        mvc.perform(get("/demo/agents/" + fixture.id() + "/live/sessions/" + session.handle() + "/updates")
                .header("X-Prometheus-Access-Code", fixture.code())).andExpect(status().isUnauthorized());
        assertFalse(ownership.isCurrent(fixture.id(), owner));
        code.setEnabled(true); codes.saveAndFlush(code);
        live.close(fixture.code(), fixture.id(), session.handle());
    }

    @Test void batchRejectsInvalidInputBeforeAcknowledgingAnything() throws Exception {
        var fixture = fixture(new MultimodalBehaviour().createAgent());
        String endpoint = "/demo/agents/" + fixture.id() + "/observations";
        var valid = sensor(Event.TYPE_FACE_EMOTION, "{}");
        for (var requests : List.of(List.of(), Collections.nCopies(5, valid),
                List.of(valid, sensor(Event.TYPE_USER_UTTERANCE, "hello")), List.of(sensor(null, "{}")))) {
            mvc.perform(post(endpoint).header("X-Prometheus-Access-Code", fixture.code())
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(requests))).andExpect(status().isBadRequest());
        }
        mvc.perform(post(endpoint).header("X-Prometheus-Access-Code", "zzzzz").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(List.of(valid)))).andExpect(status().isUnauthorized());
        mvc.perform(post("/demo/agents/" + UUID.randomUUID() + "/observations").header("X-Prometheus-Access-Code", fixture.code())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(List.of(valid)))).andExpect(status().isNotFound());
        assertTrue(agents.findById(fixture.id()).orElseThrow().getEventHistory().isEmpty());
    }

    @Test void measureCurrentPaths() throws Exception {
        for (AgentDefinition definition : List.of(new MultimodalBehaviour(), new LiveMultimodal())) {
            for (int history : List.of(5, 205)) {
                Agent agent = definition.createAgent();
                agent.executionEpoch();
                for (int i = 0; i < history; i++) agent.getEventHistory().appendEvent(
                        Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Offline observation " + i).withStatePath("conversation"));
                agent = agents.saveAndFlush(agent);
                UUID id = agent.getId();
                String code = UUID.randomUUID().toString().substring(0, 5);
                AccessCode access = new AccessCode(code, true);
                access.replaceAllowedAgentTypes(List.of(definition.key()));
                access = codes.saveAndFlush(access);
                links.saveAndFlush(new AccessCodeAgent(access, agent));
                String base = "/demo/agents/" + id;
                String label = definition.key() + " history=" + history;
                String face = "{\"type\":\"obs.emotion.face\",\"actor\":\"user\",\"kind\":\"observation\",\"payload\":\"{\\\"emotion\\\":\\\"neutral\\\",\\\"confidence\\\":0.9,\\\"valence\\\":0.0,\\\"arousal\\\":0.2}\"}";
                measure(label + " ordinary-face", () -> mvc.perform(post(base + "/acknowledge")
                        .header("X-Prometheus-Access-Code", code).contentType(MediaType.APPLICATION_JSON).content(face)).andExpect(status().isOk()));
                var session = live.create(code, id, new LiveSessionRequest("v=0 offline", "marin")).orElseThrow();
                measure(label + " initial-updates", () -> mvc.perform(get(base + "/live/sessions/" + session.handle() + "/updates")
                        .header("X-Prometheus-Access-Code", code)).andExpect(status().isOk()));
                for (int cycle = 0; cycle < 2; cycle++) {
                    measure(label + " unchanged-updates-" + cycle, () -> mvc.perform(get(base + "/live/sessions/" + session.handle() + "/updates")
                            .param("transcriptRevision", "0").header("X-Prometheus-Access-Code", code)).andExpect(status().isOk()));
                    measure(label + " idle-status-" + cycle, () -> mvc.perform(get(base + "/live/sessions/" + session.handle())
                            .header("X-Prometheus-Access-Code", code)).andExpect(status().isOk()));
                    measure(label + " idle-transcripts-" + cycle, () -> mvc.perform(get(base + "/live/transcripts")
                            .param("sessionId", session.handle().toString()).header("X-Prometheus-Access-Code", code)).andExpect(status().isOk()));
                }
                measure(label + " live-face", () -> mvc.perform(post(base + "/acknowledge")
                        .header("X-Prometheus-Access-Code", code).contentType(MediaType.APPLICATION_JSON).content(face)).andExpect(status().isOk()));
                measure(label + " live-history", () -> mvc.perform(get(base + "/live/history")
                        .header("X-Prometheus-Access-Code", code)).andExpect(status().isOk()));
                var owner = new ExternalSpeech(session.handle(), agent.executionEpoch());
                measure(label + " scoped-context", () -> assertTrue(contexts.refresh(id, owner).isPresent()));
                measure(label + " scope-check", () -> assertTrue(ownership.isCurrent(id, owner)));
                measure(label + " ui-state-and-storage", () -> {
                    mvc.perform(get(base + "/state").header("X-Prometheus-Access-Code", code)).andExpect(status().isOk());
                    mvc.perform(get(base + "/storage").header("X-Prometheus-Access-Code", code)).andExpect(status().isOk());
                });
                var requests = List.of(sensor(Event.TYPE_HUMAN_PRESENCE, "{\"humanCount\":1}"),
                        sensor(Event.TYPE_SOCIAL_GROUPING, "{\"humanCount\":1,\"groupCount\":0,\"singletonCount\":1,\"largestGroupSize\":1}"),
                        sensor(Event.TYPE_SOCIAL_CONTEXT, "{\"humanCount\":1,\"people\":[]}"));
                var separate = fixture(definition.createAgent());
                var otherSession = live.create(separate.code(), separate.id(), new LiveSessionRequest("v=0 offline", "marin")).orElseThrow();
                long individualCost = measure(label + " sensor-separate", () -> {
                    for (var request : requests) mvc.perform(post("/demo/agents/" + separate.id() + "/acknowledge")
                            .header("X-Prometheus-Access-Code", separate.code()).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(request))).andExpect(status().isOk());
                });
                long batchCost = measure(label + " sensor-batch", () -> {
                    var response = mvc.perform(post(base + "/observations")
                        .header("X-Prometheus-Access-Code", code).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(requests)))
                        .andExpect(status().isOk()).andReturn().getResponse();
                    var results = json.readTree(response.getContentAsString());
                    assertEquals(3, results.size());
                    results.forEach(result -> assertEquals(200, result.get("status").asInt()));
                });
                assertTrue(batchCost < individualCost, "Batch must avoid reloading the graph for every observation");
                live.close(separate.code(), separate.id(), otherSession.handle());
                live.close(code, id, session.handle());
            }
        }
    }
}
