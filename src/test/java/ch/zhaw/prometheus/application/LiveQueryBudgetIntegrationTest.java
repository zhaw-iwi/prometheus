package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ch.zhaw.prometheus.agentdefs.core.LiveMultimodal;
import ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.*;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.repositories.AgentRepository;
import jakarta.persistence.EntityManagerFactory;

@SpringBootTest(properties = {"prometheus.runtime.tick.enabled=false",
        "spring.datasource.hikari.maximum-pool-size=1", "spring.datasource.hikari.minimum-idle=1",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
class LiveQueryBudgetIntegrationTest {
    @Autowired AgentRepository agents;
    @Autowired LiveTranscriptIngressService ingress;
    @Autowired LiveAgentContextService contexts;
    @Autowired ExternalSpeechOwnership ownership;
    @Autowired EntityManagerFactory factory;
    @Autowired ch.zhaw.prometheus.repositories.AccessCodeRepository codes;
    @Autowired ch.zhaw.prometheus.repositories.AccessCodeAgentRepository links;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired ScopedDemoService demo;
    @Autowired AgentApplicationService application;

    long questions() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SHOW SESSION STATUS LIKE 'Questions'")) {
            assertTrue(result.next()); return result.getLong(2);
        }
    }

    Agent agent(int events) {
        Agent agent = new LiveMultimodal().createAgent();
        agent.executionEpoch();
        for (int i = 0; i < events; i++) agent.getEventHistory().appendEvent(
                Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Observation " + i)
                        .withStatePath("conversation", "nested"));
        return agents.saveAndFlush(agent);
    }
    Statistics stats() { return factory.unwrap(SessionFactory.class).getStatistics(); }
    ExternalSpeech own(Agent agent) {
        var owner = new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
        ownership.acquire(agent.getId(), owner); return owner;
    }
    Fragment fragment(String id) {
        return new Fragment(id, Speaker.USER, 0L, 500L, Instant.now().toEpochMilli(), 1, "Hello");
    }
    @Test void receiptDoesNotLoadHistoryAndStillRejectsDuplicateWrongEpochAndReleasedOwner() throws Exception {
        Agent agent = agent(200); UUID id = agent.getId(); var owner = own(agent);
        stats().clear();
        long before = questions();
        assertTrue(ingress.receipt(id, owner, fragment("one")));
        assertEquals(1, questions() - before - 1, "One autocommitted statement, including durable epoch/duplicate checks");
        long queries = stats().getPrepareStatementCount(), entities = stats().getEntityLoadCount();
        System.out.println("Live receipt queries=" + queries + ", loaded entities=" + entities);
        assertTrue(queries <= 3, "Receipt must use bounded identity/ledger queries: " + queries);
        assertEquals(0, entities, "Receipt must not load the agent graph");
        assertFalse(ingress.receipt(id, owner, fragment("one")));
        ownership.release(id, owner.sessionId());
        assertFalse(ingress.receipt(id, owner, fragment("released")));
        var stale = new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID());
        ownership.acquire(id, stale);
        assertFalse(ingress.receipt(id, stale, fragment("wrong-epoch")));
        ownership.release(id, stale.sessionId());
    }
    @Test void historyStatePathsLoadTogetherAndContextQueryCountDoesNotGrowPerEvent() {
        long small = contextQueries(5), large = contextQueries(205);
        System.out.println("Live context queries: 5 events=" + small + ", 205 events=" + large);
        assertTrue(large <= small + 1, "History must not add one query per event: " + small + " -> " + large);
        assertTrue(large <= 25, "Context query budget: " + large);
    }
    @Test void scopedReceiptUsesScalarChecksAndRejectsRevokedAccessImmediately() throws Exception {
        Agent agent = agent(200); UUID id = agent.getId();
        var access = codes.saveAndFlush(new ch.zhaw.prometheus.model.access.AccessCode(
                UUID.randomUUID().toString().substring(0, 5), true));
        links.saveAndFlush(new ch.zhaw.prometheus.model.access.AccessCodeAgent(access, agent));
        var owner = new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
        ownership.acquire(id, owner, access.getId());
        stats().clear(); long before = questions();
        assertTrue(ingress.receipt(id, owner, fragment("scoped")));
        assertEquals(1, questions() - before - 1, "One durable statement including fresh scope and epoch checks");
        long queries = stats().getPrepareStatementCount();
        System.out.println("Scoped Live receipt queries=" + queries);
        assertTrue(queries <= 4, "Scoped receipt query budget: " + queries);
        assertEquals(0, stats().getEntityLoadCount());
        access.setEnabled(false); codes.saveAndFlush(access);
        assertFalse(ingress.receipt(id, owner, fragment("revoked")));
        assertFalse(ownership.hasOwner(id));
        access.setEnabled(true); codes.saveAndFlush(access);
        ownership.acquire(id, owner, access.getId());
        links.delete(links.findByAccessCode_IdAndAgent_Id(access.getId(), id).orElseThrow());
        assertFalse(ingress.receipt(id, owner, fragment("unlinked")));
        assertFalse(ownership.hasOwner(id));
    }
    long contextQueries(int size) {
        Agent agent = agent(size); UUID id = agent.getId(); var owner = own(agent);
        stats().clear();
        assertTrue(contexts.refresh(id, owner).isPresent());
        long queries = stats().getPrepareStatementCount();
        // Eager detached history and path order are still available to ordinary clients.
        var loaded = agents.findById(id).orElseThrow();
        assertEquals(size, loaded.getEventHistory().toList().size());
        assertTrue(loaded.getEventHistory().toList().stream().allMatch(event ->
                event.getStatePath().equals(java.util.List.of("conversation", "nested"))));
        ownership.release(id, owner.sessionId()); return queries;
    }

    @Test void scopedHistoryReadsOnlyOrderedHistoryAndImmediatelyReflectsAppendAndRevocation() throws Exception {
        Agent agent = agent(205); UUID id = agent.getId();
        var access = codes.saveAndFlush(new ch.zhaw.prometheus.model.access.AccessCode(
                UUID.randomUUID().toString().substring(0, 5), true));
        links.saveAndFlush(new ch.zhaw.prometheus.model.access.AccessCodeAgent(access, agent));
        var expected = agent.getEventHistory().toList().stream().map(Event::getId).toList();
        stats().clear(); long before = questions();
        var history = demo.getAgentEventHistory(access.getCode(), id).orElseThrow();
        long cost = questions() - before - 1;
        assertTrue(cost <= 4, "Scoped history must read only authorization, history, events and paths: " + cost);
        assertEquals(expected, history.stream().map(Event::getId).toList());
        assertTrue(history.stream().allMatch(event -> event.getStatePath().equals(java.util.List.of("conversation", "nested"))));
        assertEquals(0, stats().getEntityStatistics(Agent.class.getName()).getLoadCount());
        assertEquals(0, stats().getEntityStatistics(ch.zhaw.prometheus.model.State.class.getName()).getLoadCount());
        agent = agents.findById(id).orElseThrow();
        agent.getEventHistory().appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Fresh append"));
        agents.saveAndFlush(agent);
        assertEquals("Fresh append", demo.getAgentEventHistory(access.getCode(), id).orElseThrow().getLast().getPayload());
        access.setEnabled(false); codes.saveAndFlush(access);
        assertThrows(DemoAccessDeniedException.class, () -> demo.getAgentEventHistory(access.getCode(), id));
        assertTrue(application.getAgentEventHistory(UUID.randomUUID()).isEmpty());
        assertTrue(application.getAgentEventHistory(agent(0).getId()).orElseThrow().isEmpty());
    }
}
