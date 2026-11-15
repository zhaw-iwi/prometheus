package ch.zhaw.prometheus.application.study;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import ch.zhaw.prometheus.spi.*;
import static ch.zhaw.prometheus.application.study.StudyGuessingTest.update;

@SpringBootTest(properties={"prometheus.runtime.tick.enabled=false","prometheus.study.sensing=simulation"})
class StudyContextIntegrationTest {
    @Autowired StudySessions sessions;
    @Autowired StudyTurns turns;
    @Autowired StudyProcedure procedure;
    @Autowired StudyMaterials materials;
    @Autowired StudyEvidence evidence;
    @MockitoBean LanguageModelGateway gateway;
    StudySessions.Invitation ready() {
        var invite=sessions.invite(StudyAttempt.Mode.SYNTHETIC,"context",null);String token=invite.token();
        sessions.consent(token,StudySessions.SYNTHETIC_CONSENT);var pre=new HashMap<String,String>();
        materials.manifest().pre().forEach(i->pre.put(i.id(),i.kind().equals("rating7")?"4":"None"));sessions.form(token,"pre",pre);
        sessions.form(token,"intention",Map.of("intention","A fictional routine"));sessions.form(token,"private",Map.of("private","private-context-preference"));
        when(gateway.infer(any())).thenReturn(update("PROPOSE",StudyContextTest.contract()));say(token,"Propose my fictional check-in.");
        procedure.predict(token,Map.of("revision",procedure.view(token).revision(),"prediction","private-context-prediction"));
        when(gateway.infer(any())).thenReturn(update("START",null));say(token,"Start now");return invite;
    }
    StudyTurns.View say(String token,String text){var result=turns.submit(token,new StudyTurns.Request(UUID.randomUUID(),sessions.get(token).turnVersion(),text));assertEquals(StudyTurn.Status.COMMITTED,result.status());return result;}
    StudyTurns.SceneRequest sceneRequest(String token,Integer count){return new StudyTurns.SceneRequest(UUID.randomUUID(),procedure.view(token).sceneVersion(),count,Instant.now().toString());}
    StudyTurns.View scene(String token,Integer count){var request=sceneRequest(token,count);var result=turns.scene(token,request);assertEquals(StudyTurn.Status.COMMITTED,result.status());assertEquals(result,turns.scene(token,request));return result;}
    @Test void sceneChangeFencesHeldGenerationAndPersistsPostponement() throws Exception {
        var invite=ready();String token=invite.token();scene(token,1);var before=procedure.view(token).context();
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(gateway.infer(any())).thenAnswer(call->{
            assertFalse(((InferenceRequest)call.getArgument(0)).messages().toString().contains("private-context"));
            entered.countDown();assertTrue(release.await(20,TimeUnit.SECONDS));
            return "{\"operation\":\"KEEP\",\"agreement\":null,\"reply\":{\"speech\":\"SENSITIVE_LATE_REPLY\"}}";
        });
        var request=new StudyTurns.Request(UUID.randomUUID(),sessions.get(token).turnVersion(),"Rephrase the current question.");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var pending=pool.submit(()->turns.submit(token,request));assertTrue(entered.await(10,TimeUnit.SECONDS));
            var sceneRequest=sceneRequest(token,2);var changing=pool.submit(()->turns.scene(token,sceneRequest));
            try {
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
                while(procedure.view(token).sceneVersion()<2 && System.nanoTime()<deadline)Thread.sleep(20);
                assertEquals(2,procedure.view(token).sceneVersion());
                assertEquals(StudyTurn.Status.REJECTED,turns.lookup(token,request.requestId()).status());
            } finally {release.countDown();}
            assertEquals(StudyTurn.Status.REJECTED,pending.get(10,TimeUnit.SECONDS).status());
            assertEquals(StudyTurn.Status.COMMITTED,changing.get(10,TimeUnit.SECONDS).status());
        }
        assertEquals("PAUSED",turns.conversation(token).phase());assertFalse(evidence.participantEvents(token).toString().contains("SENSITIVE_LATE_REPLY"));
        assertTrue(procedure.view(token).context().contains("POSTPONED"));
        scene(token,1);assertEquals("PAUSED",turns.conversation(token).phase());say(token,"Resume");
        var beforePending=com.google.gson.JsonParser.parseString(before).getAsJsonObject().get("pending");
        assertEquals(beforePending,com.google.gson.JsonParser.parseString(procedure.view(token).context()).getAsJsonObject().get("pending"));
        say(token,"No");assertEquals("REFLECTION",turns.conversation(token).phase());
        assertTrue(evidence.export(invite.session().id()).path("manifest").path("recordingComplete").asBoolean());
    }
    @Test void delayedDisplayIsWithheldButConfirmedPriorDisplayAndRawResearchEvidenceSurvive() {
        var invite=ready();String token=invite.token();var first=scene(token,1);
        scene(token,2);var withheld=evidence.participantEvents(token).stream().filter(e->e.path("id").asText().equals(first.responseEventId().toString())).findFirst().orElseThrow();
        assertTrue(withheld.path("withheld").asBoolean());assertFalse(withheld.path("payload").asText().contains("medication"));
        evidence.display(token,new StudyEvidence.Display(first.responseEventId(),Instant.now().toString(),"WITHHELD"));
        var exported=evidence.export(invite.session().id());assertEquals(1,exported.path("manifest").path("withheldRenderReports").asInt());
        assertTrue(exported.toString().contains("medication"));
        scene(token,1);var resumed=say(token,"Resume");evidence.display(token,new StudyEvidence.Display(resumed.responseEventId(),Instant.now().toString()));scene(token,2);
        assertFalse(evidence.participantEvents(token).stream().filter(e->e.path("id").asText().equals(resumed.responseEventId().toString())).findFirst().orElseThrow().path("withheld").asBoolean());
        var stale=new StudyTurns.SceneRequest(UUID.randomUUID(),procedure.view(token).sceneVersion(),0,Instant.now().minusSeconds(90).toString());
        assertThrows(ResponseStatusException.class,()->turns.scene(token,stale));assertEquals("PAUSED",turns.conversation(token).phase());
    }
    @Test void plannedSceneSequenceIsOrderedAndDurableWithoutCountingDuplicates() {
        var invite=ready();String token=invite.token();
        assertThrows(ResponseStatusException.class,()->turns.scene(token,new StudyTurns.SceneRequest(UUID.randomUUID(),0,2,Instant.now().toString(),1)));
        int[] script={1,2,1};
        for(int step=0;step<script.length;step++) {
            var request=new StudyTurns.SceneRequest(UUID.randomUUID(),procedure.view(token).sceneVersion(),script[step],Instant.now().toString(),step);
            var result=turns.scene(token,request);assertEquals(StudyTurn.Status.COMMITTED,result.status());assertEquals(result,turns.scene(token,request));
            assertEquals(step+1,procedure.view(token).sceneProbeStep());
        }
        assertEquals("PAUSED",turns.conversation(token).phase());
        assertThrows(ResponseStatusException.class,()->turns.scene(token,new StudyTurns.SceneRequest(UUID.randomUUID(),3,1,Instant.now().toString(),3)));
    }
}
