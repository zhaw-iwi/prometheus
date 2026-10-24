package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import java.time.Instant;
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

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void galaMixedMinuteHasBoundedQueriesAndPreservesGameAndCapture(boolean dropout) throws Exception {
        var f = fixture(new ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReception().createAgent());
        for (int i = 0; i < 2; i++) demo.acknowledge(f.code(), f.id(), sensor(Event.TYPE_SOCIAL_GROUPING,
                "{\"humanCount\":1,\"ts\":\"" + Instant.now() + "\"}"), ch.zhaw.prometheus.model.policy.OutputProfile.FULL_PLAN);
        var session = live.create(f.code(), f.id(), new LiveSessionRequest("v=0 offline", "marin")).orElseThrow();
        var owner = new ExternalSpeech(session.handle(), agents.findById(f.id()).orElseThrow().executionEpoch());
        var control = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call -> "{\"operation\":\"" + (control.getAndIncrement() == 0 ? "START_GUESS" : "WAIT")
                + "\",\"gesture\":\"NONE\",\"expression\":\"attentive\",\"gaze\":\"toward_user\"}")
                .when(language).infer(any());
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        long before = questions(); stats.clear();
        try {
            long revision = -1;
            // Replay one minute's cadence without wall-clock sleeps: 12 camera/context cycles,
            // 60 polls, 12 lease checks, 4 user turns and 8 native segments (72 receipts).
            // Workers are mocked, so explicit refreshes account for background context work.
            for (int cycle = 0; cycle < 12; cycle++) {
                // One additional transient-zero ingress; the normal fresh sample immediately
                // following it restores visibility. Timed boundaries live in fake-clock tests.
                if (dropout && cycle == 4) assertTrue(demo.acknowledge(f.code(), f.id(), sensor(Event.TYPE_SOCIAL_GROUPING,
                        "{\"humanCount\":0,\"ts\":\"" + Instant.now() + "\"}"),
                        ch.zhaw.prometheus.model.policy.OutputProfile.FULL_PLAN).isPresent());
                String timestamp = Instant.now().toString();
                var result = demo.acknowledgeObservations(f.code(), f.id(), List.of(
                        sensor(Event.TYPE_HUMAN_PRESENCE, "{\"humanCount\":1,\"ts\":\"" + timestamp + "\"}"),
                        sensor(Event.TYPE_SOCIAL_GROUPING, "{\"humanCount\":1,\"groupCount\":0,\"singletonCount\":1,\"largestGroupSize\":1,\"ts\":\"" + timestamp + "\"}"),
                        sensor(Event.TYPE_SOCIAL_CONTEXT, "{\"humanCount\":1,\"people\":[],\"ts\":\"" + timestamp + "\"}")));
                assertEquals(List.of(200, 200, 200), result.stream().map(ScopedDemoService.ObservationResult::status).toList());
                assertTrue(demo.acknowledge(f.code(), f.id(), sensor(Event.TYPE_FACE_EMOTION,
                        "{\"emotion\":\"neutral\",\"confidence\":0.9,\"ts\":\"" + timestamp + "\"}"),
                        ch.zhaw.prometheus.model.policy.OutputProfile.FULL_PLAN).isPresent());
                Speaker speaker = cycle % 3 == 0 ? Speaker.USER : Speaker.ASSISTANT;
                String text = speaker == Speaker.ASSISTANT ? "Ja, ich habe Ihre Frage verstanden."
                        : cycle == 0 ? "Ich moechte eine bekannte Person erraten." : "Ist die Person ein bekannter Physiker?";
                var fragments = new java.util.ArrayList<Fragment>();
                long now = System.currentTimeMillis();
                for (int part = 0; part < 6; part++) {
                    var fragment = new Fragment("gala-" + cycle + "-" + part, speaker,
                            cycle * 5000L + part * 200L, cycle * 5000L + (part + 1) * 200L,
                            now, cycle * 6L + part + 1, text.substring(text.length() * part / 6, text.length() * (part + 1) / 6));
                    fragments.add(fragment); assertTrue(ingress.receipt(f.id(), owner, fragment));
                }
                var segment = new Segment(UUID.randomUUID(), speaker, fragments, Closure.COMPLETE, "observed_silence", now, now);
                assertEquals("COMPLETE", ingress.commit(f.id(), owner, segment, List.of(
                        cycle == 0 ? "Gala reception" : "Gala guess the person")).orElseThrow().status());
                assertTrue(contexts.refresh(f.id(), owner).isPresent());
                assertTrue(ownership.isCurrent(f.id(), owner));
                for (int poll = 0; poll < 5; poll++) revision = live.updates(f.code(), f.id(), session.handle(), revision)
                        .orElseThrow().transcriptRevision();
            }
            long total = questions() - before - 1;
            System.out.printf("GALA_MIXED_MINUTE dropout=%s databaseQuestions=%d statements=%d entities=%d%n", dropout, total,
                    stats.getPrepareStatementCount(), stats.getEntityLoadCount());
            assertTrue(total <= 1700, "Mixed Gala workload query budget: " + total);
            assertEquals(4, control.get());
            var saved = agents.findById(f.id()).orElseThrow();
            assertEquals("Gala guess the person", saved.getCurrentState().getName());
            assertEquals(4, saved.getEventHistory().toList().stream().filter(e -> Event.TYPE_USER_UTTERANCE.equals(e.getType())).count());
            var ledger = ingress.history(f.id(), owner.sessionId());
            assertEquals(12, ledger.size()); assertTrue(ledger.stream().allMatch(s -> "COMPLETE".equals(s.status())));
            assertTrue(ledger.stream().allMatch(s -> s.receiptIds().size() == 6));
        } finally { live.close(f.code(), f.id(), session.handle()); }
    }

    /** Opt-in wall-clock capacity gate; never runs against the production schema. */
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PROMETHEUS_GALA_CAPACITY", matches = "true")
    void galaReceptionCapacitySoak() throws Exception {
        int peakSeconds = Integer.parseInt(System.getenv().getOrDefault("PROMETHEUS_GALA_CAPACITY_SECONDS", "180"));
        assertTrue(peakSeconds >= 60 && peakSeconds <= 600 && peakSeconds % 60 == 0,
                "Use complete 60-second game cycles, between 60 and 600 seconds");
        String profile = System.getenv().getOrDefault("PROMETHEUS_GALA_CAPACITY_PROFILE", "reception");
        assertTrue(Set.of("reception", "stress").contains(profile), "Unknown capacity profile");
        boolean stress = profile.equals("stress");
        var f = fixture(new ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReception().createAgent());
        demo.acknowledge(f.code(), f.id(), sensor(Event.TYPE_SOCIAL_GROUPING,
                "{\"humanCount\":1,\"ts\":\"" + Instant.now() + "\"}"), ch.zhaw.prometheus.model.policy.OutputProfile.FULL_PLAN);
        var session = live.create(f.code(), f.id(), new LiveSessionRequest("v=0 offline", "marin")).orElseThrow();
        var owner = new ExternalSpeech(session.handle(), agents.findById(f.id()).orElseThrow().executionEpoch());
        var operation = new java.util.concurrent.atomic.AtomicReference<>("CHAT");
        doAnswer(call -> "{\"operation\":\"" + operation.get()
                + "\",\"gesture\":\"NONE\",\"expression\":\"attentive\",\"gaze\":\"toward_user\"}")
                .when(language).infer(any());
        var streams = new ArrayList<org.springframework.test.web.servlet.MvcResult>();
        var report = new com.google.gson.JsonObject();
        report.addProperty("instances", 1); report.addProperty("operatorPages", 1);
        report.addProperty("profile", profile);
        report.addProperty("quotaPerHour", 36000); report.addProperty("targetPerHour", 27000);
        report.addProperty("providerAndCaptureWorkers", "offline; explicit context reads approximate scheduling, not a production throughput guarantee");
        report.addProperty("load", stress
                ? "original stress: status 1/s; social batch and face 1/5s; held hand 1/2.5s; context each work tick; SSE reconnect 1/min including phase starts"
                : "reception: status 1/s; Live social batch and face 1/5s; hand held 5s after RPS start (3 samples/min); context coalesced 5s except confirmed game occasions; one SSE reconnect at peak +60s; after Stop one changed social/face sample at +10s, unchanged samples suppressed, no visible hand");
        report.addProperty("speech", "4 user + 8 native segments/min, 6 durable receipts/segment; synthetic fragmentation");
        var phases = new com.google.gson.JsonArray(); report.add("phases", phases);
        long revision = -1, receiptSequence = 0; int segments = 0; boolean closed = false;
        try {
            for (String phase : List.of("live_idle", "live_peak", "camera_after_stop")) {
                if (phase.equals("camera_after_stop")) { live.close(f.code(), f.id(), session.handle()); closed = true; }
                var meter = new CapacityMeter(); long before = questions(), began = System.nanoTime();
                var stats = emf.unwrap(SessionFactory.class).getStatistics(); stats.clear();
                int seconds = phase.equals("live_peak") ? peakSeconds : Math.min(30, peakSeconds / 3);
                long maximumLatenessMs = 0; int nextContextTick = 0; boolean contextDirty = false;
                for (int tick = 0; tick < seconds * 2; tick++) {
                    long due = began + tick * 500_000_000L, wait = due - System.nanoTime();
                    if (wait > 0) Thread.sleep(java.time.Duration.ofNanos(wait));
                    maximumLatenessMs = Math.max(maximumLatenessMs, Math.max(0, (System.nanoTime() - due) / 1_000_000));
                    if (stress ? tick % 120 == 0 : streams.isEmpty() || phase.equals("live_peak") && tick == 120)
                        meter.run("sse_connect_or_reconnect", () -> {
                        for (var stream : streams) stream.getRequest().getAsyncContext().complete(); streams.clear();
                        for (String route : List.of("/behaviour/stream?projection=conversation", "/monitor/stream")) {
                            var stream = mvc.perform(get("/demo/agents/" + f.id() + route)
                                    .header("X-Prometheus-Access-Code", f.code())).andExpect(status().isOk()).andReturn();
                            assertTrue(stream.getRequest().isAsyncStarted()); streams.add(stream);
                        }
                    });
                    String timestamp = Instant.now().toString(); boolean changed = false;
                    if (closed && !stress ? tick == 20 : tick % 10 == 0) {
                        int count = closed && !stress ? 2 : 1;
                        meter.run("social_batch", () -> {
                            var result = demo.acknowledgeObservations(f.code(), f.id(), List.of(
                                    sensor(Event.TYPE_HUMAN_PRESENCE, "{\"humanCount\":" + count + ",\"ts\":\"" + timestamp + "\"}"),
                                    sensor(Event.TYPE_SOCIAL_GROUPING, "{\"humanCount\":" + count + ",\"groupCount\":0,\"singletonCount\":" + count + ",\"largestGroupSize\":1,\"ts\":\"" + timestamp + "\"}"),
                                    sensor(Event.TYPE_SOCIAL_CONTEXT, "{\"humanCount\":" + count + ",\"people\":[],\"ts\":\"" + timestamp + "\"}")));
                            assertEquals(List.of(200, 200, 200), result.stream().map(ScopedDemoService.ObservationResult::status).toList());
                        });
                        meter.run("face", () -> assertTrue(demo.acknowledge(f.code(), f.id(), sensor(Event.TYPE_FACE_EMOTION,
                                "{\"emotion\":\"" + (count == 2 ? "happy" : "neutral") + "\",\"confidence\":0.9,\"ts\":\"" + timestamp + "\"}"),
                                ch.zhaw.prometheus.model.policy.OutputProfile.FULL_PLAN).isPresent()));
                        changed = true;
                    }
                    boolean hand = stress ? !phase.equals("live_idle") && tick % 5 == 0
                            : phase.equals("live_peak") && Set.of(95, 100, 105).contains(tick % 120);
                    if (hand) {
                        // Echo the published capture like the cockpit, without a benchmark-only DB read.
                        String captureId = latestHandCapture(streams.getFirst().getResponse().getContentAsString());
                        meter.run("hand", () -> assertTrue(demo.acknowledge(f.code(), f.id(), sensor(Event.TYPE_HAND_SIGN,
                                "{\"sign\":\"paper\",\"confidence\":0.9,\"source\":\"valerian.hand.camera\",\"stabilityFrames\":3,\"captureId\":\"" + captureId + "\",\"ts\":\"" + timestamp + "\"}"),
                                ch.zhaw.prometheus.model.policy.OutputProfile.FULL_PLAN).isPresent()));
                        changed = true;
                    }
                    if (phase.equals("live_peak") && tick % 10 == 0) {
                        Speaker speaker = tick % 30 == 0 ? Speaker.USER : Speaker.ASSISTANT;
                        int step = (tick / 30) % 4;
                        operation.set(List.of("START_GUESS", "WAIT", "GIVE_UP", "START_RPS").get(step));
                        String text = speaker == Speaker.ASSISTANT ? "Eine kurze Testantwort ohne physische Ausgabe."
                                : List.of("Personenraten bitte.", "Lebt die bekannte Person noch?", "Ich gebe auf", "Schere Stein Papier bitte.").get(step);
                        var fragments = new ArrayList<Fragment>(); long now = System.currentTimeMillis();
                        for (int part = 0; part < 6; part++) {
                            long sequence = ++receiptSequence;
                            var fragment = new Fragment("capacity-" + sequence, speaker, sequence * 200, (sequence + 1) * 200,
                                    now, sequence, text.substring(text.length() * part / 6, text.length() * (part + 1) / 6));
                            fragments.add(fragment); meter.run("receipt", () -> assertTrue(ingress.receipt(f.id(), owner, fragment)));
                        }
                        var segment = new Segment(UUID.randomUUID(), speaker, fragments, Closure.COMPLETE, "observed_silence", now, now);
                        meter.run("segment_commit", () -> assertEquals("COMPLETE", ingress.commit(f.id(), owner, segment, List.of("Gala capacity trial")).orElseThrow().status()));
                        segments++; changed = true;
                    }
                    contextDirty |= changed;
                    // Game start/reveal/result notifications bypass normal coalescing in the real bridge.
                    boolean occasion = phase.equals("live_peak") && Set.of(0, 60, 90, 95).contains(tick % 120);
                    if (!closed && (stress ? changed : occasion || contextDirty && tick >= nextContextTick)) {
                        meter.run("context_refresh", () -> assertTrue(contexts.refresh(f.id(), owner).isPresent()));
                        contextDirty = false; nextContextTick = tick + 10;
                    }
                    if (!closed && tick % 10 == 0) meter.run("lease_check", () -> assertTrue(ownership.isCurrent(f.id(), owner)));
                    if (!closed && tick % 2 == 0) {
                        long previous = revision; long[] next = { revision };
                        meter.run("status_poll", () -> next[0] = live.updates(f.code(), f.id(), session.handle(), previous).orElseThrow().transcriptRevision());
                        revision = next[0];
                        if (revision != previous) meter.run("conversation_refresh", () -> mvc.perform(get("/demo/agents/" + f.id() + "/live/history")
                                .header("X-Prometheus-Access-Code", f.code())).andExpect(status().isOk()));
                    }
                }
                long remainder = began + seconds * 1_000_000_000L - System.nanoTime();
                if (remainder > 0) Thread.sleep(java.time.Duration.ofNanos(remainder));
                double elapsed = (System.nanoTime() - began) / 1_000_000_000.0;
                long total = questions() - before - 1 - 2 * meter.calls;
                var result = new com.google.gson.JsonObject(); result.addProperty("phase", phase);
                result.addProperty("seconds", elapsed); result.addProperty("questions", total);
                result.addProperty("statements", stats.getPrepareStatementCount()); result.addProperty("transactions", stats.getTransactionCount());
                result.addProperty("questionsPerHour", Math.ceil(total * 3600 / elapsed)); result.addProperty("maximumLatenessMs", maximumLatenessMs);
                long reconnectQuestions = meter.paths.getOrDefault("sse_connect_or_reconnect", new long[2])[1];
                result.addProperty("questionsPerHourWithoutReconnects", Math.ceil((total - reconnectQuestions) * 3600 / elapsed));
                result.add("paths", new com.google.gson.Gson().toJsonTree(meter.paths));
                result.add("ormOperations", new com.google.gson.Gson().toJsonTree(meter.operations)); phases.add(result);
                assertEquals(total, meter.paths.values().stream().mapToLong(value -> value[1]).sum());
                if (!stress && closed) {
                    assertEquals(seconds > 10 ? 1 : 0, meter.paths.getOrDefault("social_batch", new long[2])[0]);
                    assertEquals(seconds > 10 ? 1 : 0, meter.paths.getOrDefault("face", new long[2])[0]);
                    assertFalse(meter.paths.containsKey("hand")); assertFalse(meter.paths.containsKey("context_refresh"));
                }
                System.out.println("GALA_CAPACITY_PHASE " + result);
            }
            var ledger = ingress.history(f.id(), owner.sessionId()); assertEquals(segments, ledger.size());
            assertTrue(ledger.stream().allMatch(row -> "COMPLETE".equals(row.status()) && row.receiptIds().size() == 6));
            assertEquals("Gala reception", agents.findById(f.id()).orElseThrow().getCurrentState().getName());
            assertFalse(ownership.isCurrent(f.id(), owner));
            boolean headroom = phases.asList().stream().allMatch(value -> value.getAsJsonObject().get("questionsPerHour").getAsDouble() <= 27000);
            report.addProperty("headroomMet", headroom); report.addProperty("durableSegments", segments);
            String reportFile = "target/gala-capacity-" + profile + "-report.json";
            java.nio.file.Files.writeString(java.nio.file.Path.of(reportFile), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
            assertTrue(headroom, "Capacity gate unmet; see " + reportFile + ". Do not silently change sensing/durability/authorization.");
        } finally {
            for (var stream : streams) stream.getRequest().getAsyncContext().complete();
            if (!closed) live.close(f.code(), f.id(), session.handle());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void galaRoundCaptureComesFromExistingBehaviourStreamWithoutADatabaseLookup(boolean prepareRps) throws Exception {
        var definition = prepareRps ? new ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReceptionRpsPreparation()
                : new ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReception();
        var f = fixture(definition.createAgent());
        demo.acknowledge(f.code(), f.id(), sensor(Event.TYPE_SOCIAL_GROUPING,
                "{\"humanCount\":1,\"ts\":\"" + Instant.now() + "\"}"), ch.zhaw.prometheus.model.policy.OutputProfile.FULL_PLAN);
        var session = live.create(f.code(), f.id(), new LiveSessionRequest("v=0 offline", "marin")).orElseThrow();
        var stream = mvc.perform(get("/demo/agents/" + f.id() + "/behaviour/stream?projection=conversation")
                .header("X-Prometheus-Access-Code", f.code())).andExpect(status().isOk()).andReturn();
        doReturn("{\"operation\":\"START_RPS\",\"gesture\":\"NONE\",\"expression\":\"attentive\",\"gaze\":\"toward_user\"}").when(language).infer(any());
        try {
            String previous = "";
            for (int round = 0; round < 2; round++) {
                demo.acknowledge(f.code(), f.id(), sensor(Event.TYPE_USER_UTTERANCE, "Schere Stein Papier bitte"),
                        ch.zhaw.prometheus.model.policy.OutputProfile.FULL_PLAN);
                String preparationCapture = null;
                if (prepareRps && round == 0) {
                    preparationCapture = latestHandCapture(stream.getResponse().getContentAsString());
                    for (String sign : List.of("rock", "paper", "scissor"))
                        demo.acknowledge(f.code(), f.id(), sensor(Event.TYPE_HAND_SIGN,
                                "{\"sign\":\"" + sign + "\",\"confidence\":0.9,\"source\":\"valerian.hand.camera\",\"stabilityFrames\":3,\"captureId\":\""
                                        + preparationCapture + "\",\"ts\":\"" + Instant.now() + "\"}"), ch.zhaw.prometheus.model.policy.OutputProfile.FULL_PLAN);
                }
                if (prepareRps) {
                    var cue = latestSpeechCue(stream.getResponse().getContentAsString());
                    var owner = ownership.current(agents.findById(f.id()).orElseThrow());
                    String receipt = "count-" + UUID.randomUUID();
                    assertTrue(ingress.receipt(f.id(), owner, new Fragment(receipt, Speaker.ASSISTANT,
                            round * 10000L, round * 10000L + 500, Instant.now().toEpochMilli(), round, "Schere, Stein, Papier!")));
                    mvc.perform(post("/demo/agents/" + f.id() + "/live/sessions/" + session.handle() + "/speech-cues")
                            .header("X-Prometheus-Access-Code", f.code()).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("cueId", cue.path("id").asText(), "roundId", cue.path("roundId").asText(),
                                    "outcome", "complete", "receiptIds", List.of(receipt)))))
                            .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("true"));
                }
                assertEquals("Gala rock-scissor-paper", agents.findById(f.id()).orElseThrow().getCurrentState().getName());
                long before = questions();
                String capture = latestHandCapture(stream.getResponse().getContentAsString());
                if (prepareRps && round == 0) assertNotEquals(preparationCapture, capture);
                assertEquals(0, questions() - before - 1);
                assertFalse(capture.isBlank()); assertNotEquals(previous, capture);
                demo.acknowledge(f.code(), f.id(), sensor(Event.TYPE_HAND_SIGN,
                        "{\"sign\":\"paper\",\"confidence\":0.9,\"source\":\"valerian.hand.camera\",\"stabilityFrames\":3,\"captureId\":\"" + capture
                                + "\",\"ts\":\"" + Instant.now() + "\"}"), ch.zhaw.prometheus.model.policy.OutputProfile.FULL_PLAN);
                assertEquals("Gala reception", agents.findById(f.id()).orElseThrow().getCurrentState().getName());
                previous = capture;
            }
        } finally {
            stream.getRequest().getAsyncContext().complete();
            live.close(f.code(), f.id(), session.handle());
        }
    }

    private com.fasterxml.jackson.databind.JsonNode latestSpeechCue(String sse) throws Exception {
        com.fasterxml.jackson.databind.JsonNode cue = null;
        for (String line : sse.split("\\R")) {
            if (!line.startsWith("data:")) continue;
            var envelope = json.readTree(line.substring(5));
            if (!Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(envelope.path("type").asText())) continue;
            var candidate = json.readTree(envelope.path("payload").asText()).path("display").path("speechCue");
            if (candidate.isObject()) cue = candidate;
        }
        assertNotNull(cue); return cue;
    }
    private String latestHandCapture(String sse) throws Exception {
        String capture = "";
        for (String line : sse.split("\\R")) {
            if (!line.startsWith("data:")) continue;
            var envelope = json.readTree(line.substring(5));
            if (!Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(envelope.path("type").asText())) continue;
            var display = json.readTree(envelope.path("payload").asText()).path("display");
            if (display.path("captureId").isTextual()) capture = display.path("captureId").asText();
        }
        return capture;
    }

    private final class CapacityMeter {
        long calls;
        final Map<String, long[]> paths = new LinkedHashMap<>();
        final Map<String, Map<String, Long>> operations = new LinkedHashMap<>();
        Map<String, Long> statistics() {
            var stats = emf.unwrap(SessionFactory.class).getStatistics();
            return Map.of("statements", stats.getPrepareStatementCount(), "transactions", stats.getTransactionCount(),
                    "entityLoads", stats.getEntityLoadCount(), "inserts", stats.getEntityInsertCount(),
                    "updates", stats.getEntityUpdateCount(), "deletes", stats.getEntityDeleteCount(),
                    "collectionUpdates", stats.getCollectionUpdateCount());
        }
        void run(String name, Check work) throws Exception {
            boolean sqlDiagnostics = "true".equals(System.getenv("PROMETHEUS_GALA_SQL_DIAGNOSTICS"));
            var prior = statistics(); long before = questions();
            if (sqlDiagnostics) System.out.println("CAPACITY_SQL_BEGIN " + name);
            try { work.run(); }
            finally { if (sqlDiagnostics) System.out.println("CAPACITY_SQL_END " + name); }
            long cost = questions() - before - 1; calls++;
            var values = paths.computeIfAbsent(name, key -> new long[2]); values[0]++; values[1] += cost;
            var totals = operations.computeIfAbsent(name, key -> new LinkedHashMap<>());
            statistics().forEach((key, value) -> totals.merge(key, value - prior.get(key), Long::sum));
        }
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
