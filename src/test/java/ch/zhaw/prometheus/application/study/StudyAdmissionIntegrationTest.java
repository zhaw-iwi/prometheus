package ch.zhaw.prometheus.application.study;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest(properties={"prometheus.study.sensing=simulation","prometheus.runtime.tick.enabled=false","prometheus.study.max-active=1","prometheus.study.max-turns=2","prometheus.study.interaction-minutes=1"})
class StudyAdmissionIntegrationTest {
    @Autowired StudySessions sessions;
    @Autowired StudyAdmission admission;
    @Autowired StudyAttemptRepository attempts;
    @Autowired StudyFormRepository forms;
    @Autowired StudyTurns turns;
    @Autowired StudyMaterials materials;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean LanguageModelGateway gateway;
    List<UUID> owned=new ArrayList<>();
    @AfterEach void release(){owned.forEach(admission::release);}
    StudySessions.Invitation prepared(){
        var i=sessions.invite(StudyAttempt.Mode.SYNTHETIC,"guess",null,true);owned.add(i.session().id());
        sessions.consent(i.token(),StudySessions.SYNTHETIC_CONSENT);
        var pre=new HashMap<String,String>();materials.manifest().pre().forEach(x->pre.put(x.id(),x.kind().equals("rating7")?"4":"None"));
        sessions.form(i.token(),"pre",pre);sessions.form(i.token(),"intention",Map.of("intention","Short activity"));return i;
    }
    void start(StudySessions.Invitation i){sessions.form(i.token(),"private",Map.of("private","Synthetic private preference"));}
    @Test void fullWaveRollsBackProvisioningAndFinishReleasesItsSlot(){
        var a=prepared();start(a);var b=prepared();
        assertEquals(429,assertThrows(ResponseStatusException.class,()->start(b)).getStatusCode().value());
        assertEquals(StudyAttempt.Stage.PRIVATE,sessions.get(b.token()).stage());
        assertTrue(forms.findByAttemptIdAndKind(b.session().id(),"private").isEmpty());
        assertNull(attempts.findById(b.session().id()).orElseThrow().agentId);
        sessions.finish(a.token(),"TIMEOUT");start(b);assertEquals(1,admission.status().active());
        sessions.withdraw(b.token());assertEquals(0,admission.status().active());
    }
    @Test void rollbackDoesNotLeakOrPrematurelyReleaseSlots(){
        var id=UUID.randomUUID();new TransactionTemplate(transactions).executeWithoutResult(tx->{admission.acquire(id);tx.setRollbackOnly();});
        assertEquals(0,admission.status().active());var a=prepared();start(a);
        new TransactionTemplate(transactions).executeWithoutResult(tx->{sessions.revoke(a.session().id());tx.setRollbackOnly();});
        assertEquals(1,admission.status().active());assertEquals(StudyAttempt.Stage.COORDINATION,sessions.get(a.token()).stage());
        sessions.revoke(a.session().id());assertEquals(0,admission.status().active());
        var b=prepared();start(b);var replacement=sessions.invite(StudyAttempt.Mode.SYNTHETIC,"guess",b.session().id(),true);
        owned.add(replacement.session().id());assertEquals(0,admission.status().active());
    }
    @Test void limitsRejectNewWorkButReceiptsAndAssessmentRemainAvailable(){
        var a=prepared();start(a);var first=new StudyTurns.Request(UUID.randomUUID(),0,"What is our current state?");
        var saved=turns.submit(a.token(),first);assertEquals(StudyTurn.Status.COMMITTED,saved.status());
        turns.submit(a.token(),new StudyTurns.Request(UUID.randomUUID(),1,"What is our current state?"));
        assertEquals(429,assertThrows(ResponseStatusException.class,()->turns.submit(a.token(),new StudyTurns.Request(UUID.randomUUID(),2,"What is our current state?"))).getStatusCode().value());
        admission.release(a.session().id());var b=prepared();start(b);
        assertEquals(saved,turns.submit(a.token(),first));
        assertEquals(StudyAttempt.Stage.ASSESSMENT,sessions.finish(a.token(),"TIMEOUT").stage());
        assertEquals(1,admission.status().active());
    }
    @Test void elapsedInteractionBoundUsesPersistedStartTime(){
        var a=prepared();start(a);var persisted=attempts.findById(a.session().id()).orElseThrow();
        persisted.interactionStartedAt=Instant.now().minusSeconds(61);attempts.saveAndFlush(persisted);
        var failure=assertThrows(ResponseStatusException.class,()->turns.submit(a.token(),new StudyTurns.Request(UUID.randomUUID(),0,"What is our current state?")));
        assertEquals(429,failure.getStatusCode().value());assertTrue(failure.getReason().contains("study limit"));
        assertEquals(StudyAttempt.Stage.ASSESSMENT,sessions.finish(a.token(),"TIMEOUT").stage());
    }
}
