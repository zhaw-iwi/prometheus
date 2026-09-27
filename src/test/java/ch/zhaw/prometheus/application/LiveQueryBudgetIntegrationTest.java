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
    @Test void receiptDoesNotLoadHistoryAndStillRejectsDuplicateWrongEpochAndReleasedOwner() {
        Agent agent = agent(200); UUID id = agent.getId(); var owner = own(agent);
        stats().clear();
        assertTrue(ingress.receipt(id, owner, fragment("one")));
        long queries = stats().getPrepareStatementCount(), entities = stats().getEntityLoadCount();
        System.out.println("Live receipt queries=" + queries + ", loaded entities=" + entities);
        assertTrue(queries <= 5, "Receipt must use bounded identity/ledger queries: " + queries);
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
    @Test void scopedReceiptUsesScalarChecksAndRejectsRevokedAccessImmediately() {
        Agent agent = agent(200); UUID id = agent.getId();
        var access = codes.saveAndFlush(new ch.zhaw.prometheus.model.access.AccessCode(
                UUID.randomUUID().toString().substring(0, 5), true));
        links.saveAndFlush(new ch.zhaw.prometheus.model.access.AccessCodeAgent(access, agent));
        var owner = new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
        ownership.acquire(id, owner, access.getId());
        stats().clear(); assertTrue(ingress.receipt(id, owner, fragment("scoped")));
        long queries = stats().getPrepareStatementCount();
        System.out.println("Scoped Live receipt queries=" + queries);
        assertTrue(queries <= 7, "Scoped receipt query budget: " + queries);
        assertEquals(0, stats().getEntityLoadCount());
        access.setEnabled(false); codes.saveAndFlush(access);
        assertFalse(ingress.receipt(id, owner, fragment("revoked")));
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
}
