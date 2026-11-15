package ch.zhaw.prometheus.application.study;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import com.google.gson.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import static ch.zhaw.prometheus.application.study.StudyAttempt.*;

@SpringBootTest(properties={"prometheus.runtime.tick.enabled=false","prometheus.admin.token=study-test-admin"})
@AutoConfigureMockMvc
class StudyOperationsIntegrationTest {
    @Autowired StudySessions sessions;@Autowired StudyOperations operations;@Autowired StudyEvidence evidence;@Autowired StudyProtocol protocol;
    @Autowired StudyAttemptRepository attempts;@Autowired StudyFormRepository forms;@Autowired StudyTurns turns;@Autowired MockMvc http;
    @MockitoSpyBean StudyMaterials materials;@MockitoBean LanguageModelGateway gateway;
    StudySessions.Invitation ready(){var invitation=sessions.invite(Mode.SYNTHETIC,"guess",null);String token=invitation.token();sessions.consent(token,StudySessions.SYNTHETIC_CONSENT);
        var answers=new HashMap<String,String>();materials.manifest().pre().forEach(i->answers.put(i.id(),i.kind().equals("rating7")?"4":"None"));sessions.form(token,"pre",answers);
        sessions.form(token,"intention",Map.of("intention","Private intention"));sessions.form(token,"private",Map.of("private","Private secret"));return invitation;}
    Map<String,String> postAnswers(){var answers=new HashMap<String,String>();materials.manifest().post().forEach(i->answers.put(i.id(),i.kind().equals("rating7")?"4":""));return answers;}
    @Test void unsuccessfulAttemptCompletesLockedAssessmentsBeforeDebrief(){
        var invite=ready();String token=invite.token();assertThrows(ResponseStatusException.class,()->sessions.form(token,"post",postAnswers()));
        sessions.finish(token,"NO_AGREEMENT");assertNull(sessions.page(token).debrief());assertThrows(ResponseStatusException.class,()->sessions.complete(token));
        var understanding=new HashMap<String,String>();sessions.page(token).fields().forEach(i->understanding.put(i.id(),"Not applicable."));
        sessions.form(token,"understanding",understanding);assertEquals(Stage.POST,sessions.page(token).session().stage());assertNull(sessions.page(token).debrief());
        sessions.form(token,"understanding",understanding);var changed=new HashMap<>(understanding);changed.put(changed.keySet().iterator().next(),"Changed after submission");assertThrows(ResponseStatusException.class,()->sessions.form(token,"understanding",changed));
        var post=postAnswers();post.put("post.umux.capabilities","SKIP");post.put("post.effort","NOT_APPLICABLE");sessions.form(token,"post",post);
        assertEquals(Stage.DEBRIEF,sessions.get(token).stage());assertTrue(sessions.page(token).debrief().contains("controlled simulations"));
        sessions.complete(token);sessions.complete(token);assertEquals(Stage.COMPLETE,sessions.get(token).stage());
        assertThrows(ResponseStatusException.class,()->turns.submit(token,new StudyTurns.Request(UUID.randomUUID(),0,"Resume")));
        var data=evidence.export(invite.session().id());assertTrue(data.path("manifest").path("procedureComplete").asBoolean());assertTrue(data.path("manifest").path("recordingComplete").asBoolean());
        assertEquals("NO_AGREEMENT",data.path("assessment").path("finishReason").asText());assertFalse(data.path("assessment").path("agreementAssessmentApplicable").asBoolean());assertTrue(data.path("assessment").path("umuxLite").path("raw0to100").isNull());
        assertTrue(data.path("records").toString().contains("ASSESSMENT_REFERENCE"));verifyNoInteractions(gateway);
    }
    @Test void frozenMaterialSnapshotSurvivesCurrentMaterialChange(){
        var invite=ready();String original=sessions.page(invite.token()).task();var modified=new Gson().toJsonTree(materials.manifest()).getAsJsonObject();
        modified.addProperty("version","hri-text-v99-draft");modified.getAsJsonArray("scenarios").get(0).getAsJsonObject().addProperty("task","Changed current task wording");
        doReturn(StudyMaterials.parse(modified.toString())).when(materials).manifest();
        assertEquals(original,sessions.page(invite.token()).task());assertEquals(invite.session().materialsVersion(),protocol.materials(attempts.findById(invite.session().id()).orElseThrow()).version());
    }
    @Test void concurrentBatchesAreBalancedAndReplaysNeverCreateMoreAttempts() throws Exception {
        long before=attempts.count();var request=new StudyOperations.BatchRequest(UUID.randomUUID(),Mode.SYNTHETIC,6);StudyOperations.BatchResult first,second;
        try(var pool=Executors.newFixedThreadPool(2)){var barrier=new CyclicBarrier(2);Callable<StudyOperations.BatchResult> work=()->{barrier.await();return operations.provision(request);};var a=pool.submit(work);var b=pool.submit(work);first=a.get(30,TimeUnit.SECONDS);second=b.get(30,TimeUnit.SECONDS);}
        assertEquals(before+6,attempts.count());assertNotEquals(first.replay(),second.replay());var fresh=first.replay()?second:first;var replay=first.replay()?first:second;
        assertTrue(replay.invitations().stream().allMatch(i->i.token()==null));assertTrue(fresh.invitations().stream().allMatch(i->i.token()!=null));
        for(String scenario:List.of("guess","context","signs"))assertEquals(2,fresh.invitations().stream().filter(i->i.session().scenario().equals(scenario)).count());
        var original=fresh.invitations().getFirst();var replacement=sessions.invite(Mode.SYNTHETIC,null,original.session().id());
        assertEquals(original.session().participantId(),replacement.session().participantId());assertEquals(original.session().scenario(),replacement.session().scenario());
        assertThrows(ResponseStatusException.class,()->operations.provision(new StudyOperations.BatchRequest(request.requestId(),Mode.SYNTHETIC,3)));
        assertThrows(ResponseStatusException.class,()->operations.provision(new StudyOperations.BatchRequest(UUID.randomUUID(),Mode.MAIN,3)));
    }
    @Test void assistanceAndDatasetPreserveDenominatorsAndAuthorization() throws Exception {
        var invite=ready();var note=new StudyOperations.Assistance(UUID.randomUUID(),"TECHNICAL","Explained how to recover a pending submission.");operations.assist(invite.session().id(),note);operations.assist(invite.session().id(),note);
        sessions.withdraw(invite.token());var data=operations.dataset(Mode.SYNTHETIC);assertEquals(attempts.findByModeOrderByCreatedAt(Mode.SYNTHETIC).size(),data.path("counts").path("attempts").asInt());
        var record=evidence.export(invite.session().id());assertEquals(1,java.util.stream.StreamSupport.stream(record.path("records").spliterator(),false).filter(r->r.path("kind").asText().equals("ASSISTANCE")).count());
        assertTrue(StudyOperations.csv(data).contains("WITHDRAWN"));assertFalse(data.toString().contains(invite.token()));
        http.perform(get("/admin/study/attempts?mode=SYNTHETIC").header("X-Prometheus-Study-Token",invite.token())).andExpect(status().isUnauthorized());
        http.perform(get("/admin/study/assessments.csv?mode=SYNTHETIC")).andExpect(status().isUnauthorized());
        http.perform(get("/admin/study/export?mode=SYNTHETIC").header("X-Prometheus-Admin-Token","study-test-admin")).andExpect(status().isOk()).andExpect(jsonPath("$.schema").value("hri-dataset-v1"));
    }
}
