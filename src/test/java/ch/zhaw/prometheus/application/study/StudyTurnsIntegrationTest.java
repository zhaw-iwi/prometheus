package ch.zhaw.prometheus.application.study;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import ch.zhaw.prometheus.application.AgentApplicationService;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest(properties="prometheus.runtime.tick.enabled=false")
class StudyTurnsIntegrationTest {
    @Autowired StudySessions sessions;
    @Autowired StudyTurns turns;
    @Autowired StudyMaterials materials;
    @Autowired StudyAttemptRepository attempts;
    @MockitoSpyBean AgentRepository agents;
    @Autowired StudyTurnRepository receipts;
    @MockitoSpyBean AgentApplicationService runtime;
    @MockitoBean LanguageModelGateway gateway;
    @MockitoBean ch.zhaw.prometheus.logging.AgentMonitorBroadcaster monitor;
    static final String ANSWER="{\"operation\":\"KEEP\",\"agreement\":null,\"reply\":{\"speech\":\"Tell me about your task.\"}}";
    StudySessions.Invitation ready() {
        var invitation=sessions.invite(StudyAttempt.Mode.SYNTHETIC,"guess",null);
        sessions.consent(invitation.token(),StudySessions.SYNTHETIC_CONSENT);
        var pre=new HashMap<String,String>();
        materials.manifest().pre().forEach(i -> pre.put(i.id(),i.kind().equals("rating7")?"4":"None"));
        sessions.form(invitation.token(),"pre",pre);
        sessions.form(invitation.token(),"intention",Map.of("intention","A short activity"));
        sessions.form(invitation.token(),"private",Map.of("private","hidden-umbrella")); return invitation;
    }
    StudyTurns.Request request() { return new StudyTurns.Request(UUID.randomUUID(),0,"I want to discuss an activity."); }
    int history(StudySessions.Invitation invite) {
        UUID id=attempts.findById(invite.session().id()).orElseThrow().agentId;
        return agents.findById(id).orElseThrow().getEventHistory().toList().size();
    }
    @Test void sameIdentityReplaysOneCanonicalMutationAndConflictsAreRejected() {
        var invitation=ready(); var request=request(); int before=history(invitation);
        when(gateway.infer(any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"Provider must not hold a DB transaction");
            assertFalse(call.getArgument(0).toString().contains("hidden-umbrella")); return ANSWER;
        });
        var first=turns.submit(invitation.token(),request);
        assertEquals(StudyTurn.Status.COMMITTED,first.status()); assertNotNull(first.inputEventId()); assertNotNull(first.responseEventId());
        assertEquals(first,turns.submit(invitation.token(),request)); assertEquals(first,turns.lookup(invitation.token(),request.requestId()));
        assertEquals(before+2,history(invitation)); assertEquals(1,sessions.get(invitation.token()).turnVersion());
        assertThrows(ResponseStatusException.class,() -> turns.submit(invitation.token(),new StudyTurns.Request(request.requestId(),0,"Different text")));
        assertThrows(ResponseStatusException.class,() -> turns.submit(invitation.token(),request()));
        var other=ready(); assertThrows(ResponseStatusException.class,() -> turns.lookup(other.token(),request.requestId()));
        verify(gateway,times(1)).infer(any());
    }
    @Test void concurrentRetryReturnsPendingAndDifferentTurnCannotOvertake() throws Exception {
        var invitation=ready(); var request=request(); var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        when(gateway.infer(any())).thenAnswer(call -> { entered.countDown(); assertTrue(release.await(20,TimeUnit.SECONDS)); return ANSWER; });
        try (var pool=Executors.newSingleThreadExecutor()) {
            var running=pool.submit(() -> turns.submit(invitation.token(),request));
            try {
                assertTrue(entered.await(20,TimeUnit.SECONDS));
                assertEquals(StudyTurn.Status.PENDING,turns.submit(invitation.token(),request).status());
                assertThrows(ResponseStatusException.class,() -> turns.submit(invitation.token(),request()));
            } finally { release.countDown(); }
            assertEquals(StudyTurn.Status.COMMITTED,running.get(20,TimeUnit.SECONDS).status());
        }
        assertEquals(1,receipts.findByAttemptIdOrderByReceivedAt(invitation.session().id()).size());
        verify(gateway,times(1)).infer(any());
    }
    @Test void failureBeforeCommitRollsBackAgentAndRequiresExplicitReconciliation() {
        var invitation=ready(); var request=request(); int before=history(invitation);
        clearInvocations(monitor);
        when(gateway.infer(any())).thenReturn(ANSWER);
        doThrow(new IllegalStateException("Injected failure after agent save")).when(agents).flush();
        assertEquals(StudyTurn.Status.UNCERTAIN,turns.submit(invitation.token(),request).status());
        assertEquals(before,history(invitation)); assertEquals(0,sessions.get(invitation.token()).turnVersion());
        verifyNoInteractions(monitor);
        assertEquals(StudyTurn.Status.UNCERTAIN,turns.submit(invitation.token(),request).status());
        assertThrows(ResponseStatusException.class,() -> turns.submit(invitation.token(),request()));
        assertEquals(StudyTurn.Status.REJECTED,turns.cancel(invitation.token(),request.requestId()).status());
        assertNull(sessions.get(invitation.token()).pendingRequest());
        verify(gateway,times(1)).infer(any());
    }
    @Test void lossAfterCommitReturnsDurableResultWithoutReplay() {
        var invitation=ready(); var request=request(); when(gateway.infer(any())).thenReturn(ANSWER);
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("Injected lost response after commit"); })
                .when(runtime).acknowledge(any(UUID.class),any(EventRequest.class),any(OutputProfile.class),any(AgentApplicationService.TurnCommit.class));
        var result=turns.submit(invitation.token(),request);
        assertEquals(StudyTurn.Status.COMMITTED,result.status()); assertNotNull(result.responseEventId());
        assertEquals(result,turns.lookup(invitation.token(),request.requestId()));
        assertEquals(result,turns.submit(invitation.token(),request)); verify(gateway,times(1)).infer(any());
    }
    @Test void cancellationAndRevocationFenceLateInference() throws Exception {
        for (boolean revoke:List.of(false,true)) {
            var invitation=ready(); var request=request(); int before=history(invitation);
            var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
            when(gateway.infer(any())).thenAnswer(call -> { entered.countDown(); assertTrue(release.await(20,TimeUnit.SECONDS)); return ANSWER; });
            try (var pool=Executors.newSingleThreadExecutor()) {
                var running=pool.submit(() -> turns.submit(invitation.token(),request));
                try {
                    assertTrue(entered.await(20,TimeUnit.SECONDS));
                    if (revoke) sessions.revoke(invitation.session().id()); else turns.cancel(invitation.token(),request.requestId());
                } finally { release.countDown(); }
                assertEquals(StudyTurn.Status.REJECTED,running.get(20,TimeUnit.SECONDS).status());
            }
            assertEquals(before,history(invitation));
        }
    }
    @Test void expiredPendingReceiptBecomesUncertainWithoutProviderReplay() {
        var invitation=ready(); var request=request(); var attempt=attempts.findById(invitation.session().id()).orElseThrow();
        var receipt=new StudyTurn(attempt,request.requestId(),0,request.text(),Instant.now().minusSeconds(180));
        receipts.saveAndFlush(receipt); attempt.pendingRequest=request.requestId(); attempts.saveAndFlush(attempt);
        assertEquals(StudyTurn.Status.UNCERTAIN,turns.lookup(invitation.token(),request.requestId()).status());
        assertEquals(StudyTurn.Status.UNCERTAIN,turns.submit(invitation.token(),request).status());
        verifyNoInteractions(gateway);
    }
}
