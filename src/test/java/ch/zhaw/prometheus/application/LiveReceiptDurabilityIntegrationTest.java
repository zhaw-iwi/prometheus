package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ch.zhaw.prometheus.agentdefs.core.LiveMultimodal;
import ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.*;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.repositories.*;

@SpringBootTest(properties = {"prometheus.runtime.tick.enabled=false", "spring.datasource.hikari.maximum-pool-size=2"})
class LiveReceiptDurabilityIntegrationTest {
    @Autowired AgentRepository agents;
    @Autowired StorageRepository storages;
    @Autowired LiveTranscriptReceiptRepository receipts;
    @Autowired LiveTranscriptIngressService ingress;
    @Autowired ExternalSpeechOwnership ownership;
    @Autowired PlatformTransactionManager transactions;

    @Test void receiptSurvivesOuterRollbackAndDuplicateCannotReplaceOriginalFields() {
        var agent = new LiveMultimodal().createAgent();
        var owner = new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
        var id = agents.saveAndFlush(agent).getId();
        ownership.acquire(id, owner);
        long received = Instant.parse("2026-10-22T08:12:30.123Z").toEpochMilli();
        var fragment = new Fragment("durable", Speaker.ASSISTANT, null, 450L, received, 7, "Grüezi — مرحباً");
        var transaction = new TransactionTemplate(transactions);
        var unrelated = new ch.zhaw.prometheus.model.Storage();
        unrelated.put("rolled-back", new com.google.gson.JsonPrimitive(true));
        transaction.executeWithoutResult(status -> {
            storages.saveAndFlush(unrelated); // Hold a real outer transaction/connection across receipt admission.
            assertTrue(ingress.receipt(id, owner, fragment));
            status.setRollbackOnly();
        });
        assertFalse(storages.existsById(unrelated.getID()));
        var stored = receipts.findBySessionIdAndProviderEventId(owner.sessionId(), "durable").orElseThrow();
        assertEquals(owner.epoch(), stored.getEpoch());
        assertEquals("ASSISTANT", stored.getSpeaker());
        assertNull(stored.getStartMs()); assertEquals(450L, stored.getEndMs());
        assertEquals(Instant.ofEpochMilli(received), stored.getReceivedAt());
        assertEquals(7, stored.getArrivalSequence()); assertEquals(fragment.text(), stored.getTranscript());
        assertNull(stored.getSegmentId());
        assertFalse(ingress.receipt(id, owner, new Fragment("durable", Speaker.USER, 2L, 3L, received + 1000, 99, "replacement")));
        assertEquals(fragment.text(), receipts.findById(stored.getId()).orElseThrow().getTranscript());
        ownership.release(id, owner.sessionId());
    }

    @Test void invalidInsertFailsWithoutFalseDurabilityAndPersistedResetFencesOldOwner() {
        var agent = new LiveMultimodal().createAgent();
        var owner = new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
        var id = agents.saveAndFlush(agent).getId();
        ownership.acquire(id, owner);
        long now = Instant.now().toEpochMilli();
        assertThrows(org.springframework.dao.DataAccessException.class, () -> ingress.receipt(id, owner,
                new Fragment("too-long", Speaker.USER, 0L, 100L, now, 1, "x".repeat(3001))));
        assertTrue(receipts.findBySessionIdAndProviderEventId(owner.sessionId(), "too-long").isEmpty());
        assertTrue(ingress.receipt(id, owner, new Fragment("valid", Speaker.USER, 0L, 100L, now, 2, "valid")));
        agent = agents.findById(id).orElseThrow(); agent.reset(); agents.saveAndFlush(agent);
        assertFalse(ingress.receipt(id, owner, new Fragment("old-epoch", Speaker.USER, 100L, 200L, now, 3, "obsolete")));
        assertTrue(receipts.findBySessionIdAndProviderEventId(owner.sessionId(), "old-epoch").isEmpty());
        assertFalse(ownership.hasOwner(id));
    }
}
