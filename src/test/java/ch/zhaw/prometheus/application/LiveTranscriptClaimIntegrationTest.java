package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import ch.zhaw.prometheus.agentdefs.core.LiveMultimodal;
import ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.*;
import ch.zhaw.prometheus.model.access.*;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.repositories.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import jakarta.persistence.EntityManagerFactory;

@SpringBootTest(properties = {"prometheus.runtime.tick.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
class LiveTranscriptClaimIntegrationTest {
    @Autowired AgentRepository agents;
    @Autowired AccessCodeRepository codes;
    @Autowired AccessCodeAgentRepository links;
    @Autowired LiveTranscriptSegmentRepository segments;
    @MockitoSpyBean LiveTranscriptReceiptRepository receipts;
    @Autowired LiveTranscriptIngressService ingress;
    @Autowired ExternalSpeechOwnership ownership;
    @Autowired EntityManagerFactory factory;
    @MockitoBean LanguageModelGateway language;

    record Fixture(UUID agent, ExternalSpeech owner, AccessCode access) {}
    Fixture fixture() {
        var agent = new LiveMultimodal().createAgent();
        var epoch = agent.executionEpoch();
        agent = agents.saveAndFlush(agent);
        var access = codes.saveAndFlush(new AccessCode(UUID.randomUUID().toString().substring(0, 5), true));
        links.saveAndFlush(new AccessCodeAgent(access, agent));
        var owner = new ExternalSpeech(UUID.randomUUID(), epoch);
        ownership.acquire(agent.getId(), owner, access.getId());
        return new Fixture(agent.getId(), owner, access);
    }
    Segment spoken(Fixture f, int count) {
        long now = Instant.now().toEpochMilli();
        var fragments = new ArrayList<Fragment>();
        for (int i = 0; i < count; i++) fragments.add(new Fragment(UUID.randomUUID().toString(), Speaker.ASSISTANT,
                i * 200L, (i + 1) * 200L, now, i + 1, "word" + i + " "));
        // Persist in a different order: bulk reads must not reorder the recorded speech/receipt list.
        for (var fragment : fragments.reversed()) assertTrue(ingress.receipt(f.agent(), f.owner(), fragment));
        return new Segment(UUID.randomUUID(), Speaker.ASSISTANT, fragments, Closure.COMPLETE, "observed_silence", now, now);
    }
    LiveTranscriptIngressService.Outcome commit(Fixture f, Segment segment) {
        return ingress.commit(f.agent(), f.owner(), segment, List.of("conversation")).orElseThrow();
    }
    Segment copy(Segment source, Speaker speaker, List<Fragment> fragments) {
        return new Segment(UUID.randomUUID(), speaker, fragments, Closure.COMPLETE, source.reason(),
                source.firstReceivedMs(), source.lastReceivedMs());
    }
    @Test void bulkClaimCostDoesNotGrowPerFragmentAndRetainsOrderedNativeSpeech() {
        long small = 0;
        for (int count : List.of(1, 32)) {
            var f = fixture(); var segment = spoken(f, count);
            var stats = factory.unwrap(SessionFactory.class).getStatistics(); stats.clear();
            var result = commit(f, segment); long queries = stats.getPrepareStatementCount();
            if (count == 1) small = queries;
            else assertTrue(queries <= small + 1, "Claim must read/update receipts in bulk: " + small + " -> " + queries);
            assertTrue(queries <= 25, "Native segment query budget: " + queries);
            assertEquals("COMPLETE", result.status()); assertNotNull(result.eventId());
            assertEquals(segment.text().trim(), result.text());
            assertEquals(segment.fragments().stream().map(Fragment::eventId).toList(), result.receiptIds());
            assertEquals(1, agents.findById(f.agent()).orElseThrow().getEventHistory().toList().size());
            assertEquals(result.eventId(), commit(f, segment).eventId());
            for (var source : receipts.findAllBySessionIdAndProviderEventIdIn(f.owner().sessionId(), result.receiptIds()))
                assertEquals(segment.id(), source.getSegmentId());
            ownership.release(f.agent(), f.owner().sessionId());
        }
        verifyNoInteractions(language);
    }
    @Test void invalidReceiptSetsCannotClaimOrRunAnAction() {
        var f = fixture(); var valid = spoken(f, 2);
        var missing = new Fragment("missing", Speaker.ASSISTANT, 400L, 600L, System.currentTimeMillis(), 3, "missing");
        var absent = copy(valid, Speaker.ASSISTANT, List.of(valid.fragments().getFirst(), missing));
        assertThrows(IllegalStateException.class, () -> commit(f, absent));
        assertFalse(segments.existsById(absent.id()));
        var wrongSpeaker = copy(valid, Speaker.USER, valid.fragments());
        assertThrows(IllegalArgumentException.class, () -> commit(f, wrongSpeaker));
        assertFalse(segments.existsById(wrongSpeaker.id()));
        var duplicate = copy(valid, Speaker.ASSISTANT, Collections.nCopies(2, valid.fragments().getFirst()));
        assertThrows(IllegalArgumentException.class, () -> commit(f, duplicate));
        commit(f, valid);
        var consumed = copy(valid, Speaker.ASSISTANT, valid.fragments());
        assertThrows(IllegalStateException.class, () -> commit(f, consumed));
        assertFalse(segments.existsById(consumed.id()));
        ownership.release(f.agent(), f.owner().sessionId());
        var other = new ExternalSpeech(UUID.randomUUID(), f.owner().epoch());
        ownership.acquire(f.agent(), other, f.access().getId());
        assertThrows(IllegalStateException.class, () -> ingress.commit(f.agent(), other,
                copy(valid, Speaker.ASSISTANT, valid.fragments()), List.of("conversation")));
        ownership.release(f.agent(), other.sessionId());
        verifyNoInteractions(language);
    }
    @Test void rejectedBulkAssignmentRollsBackClaimAndReceiptChanges() {
        var f = fixture(); var segment = spoken(f, 2);
        doReturn(0).when(receipts).claimForSegment(any(), any(), anyString(), anyList(), any());
        assertThrows(IllegalStateException.class, () -> commit(f, segment));
        assertFalse(segments.existsById(segment.id()));
        segment.fragments().forEach(fragment -> assertNull(receipts.findBySessionIdAndProviderEventId(
                f.owner().sessionId(), fragment.eventId()).orElseThrow().getSegmentId()));
        reset(receipts);
        assertEquals("COMPLETE", commit(f, segment).status());
        ownership.release(f.agent(), f.owner().sessionId());
        verifyNoInteractions(language);
    }
    @Test void scopeRevocationAndEpochChangeRejectClaimBeforeLoadingAgentGraph() {
        for (boolean revoke : List.of(true, false)) {
            var f = fixture(); var segment = spoken(f, 2); var owner = f.owner();
            if (revoke) { f.access().setEnabled(false); codes.saveAndFlush(f.access()); }
            else {
                ownership.release(f.agent(), owner.sessionId());
                owner = new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID());
                ownership.acquire(f.agent(), owner, f.access().getId());
            }
            var stats = factory.unwrap(SessionFactory.class).getStatistics(); stats.clear();
            assertTrue(ingress.commit(f.agent(), owner, segment, List.of("conversation")).isEmpty());
            assertEquals(0, stats.getEntityLoadCount());
            assertFalse(segments.existsById(segment.id()));
            ownership.release(f.agent(), owner.sessionId());
        }
        verifyNoInteractions(language);
    }
}
