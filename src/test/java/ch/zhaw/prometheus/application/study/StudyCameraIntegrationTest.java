package ch.zhaw.prometheus.application.study;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import ch.zhaw.prometheus.agentdefs.study.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import static ch.zhaw.prometheus.application.study.StudyGuessingTest.update;

@SpringBootTest(properties={"prometheus.runtime.tick.enabled=false","prometheus.study.sensing=camera"})
class StudyCameraIntegrationTest {
    @Autowired StudySessions sessions; @Autowired StudyTurns turns; @Autowired StudyProcedure procedure;
    @Autowired StudyMaterials materials; @Autowired StudyEvidence evidence;
    @MockitoBean LanguageModelGateway gateway;
    StudySessions.Invitation ready(String scenario){
        var invite=sessions.invite(StudyAttempt.Mode.SYNTHETIC,scenario,null);var token=invite.token();
        sessions.consent(token,StudySessions.SYNTHETIC_CONSENT);var pre=new HashMap<String,String>();
        materials.manifest().pre().forEach(i->pre.put(i.id(),i.kind().equals("rating7")?"4":"None"));
        sessions.form(token,"pre",pre);sessions.form(token,"intention",Map.of("intention","PRIVATE_CHOICE"));sessions.form(token,"private",Map.of("private","PRIVATE_CHOICE"));
        assertTrue(sessions.page(token).camera());assertEquals("hri-text-v3-draft",invite.session().materialsVersion());vision(token,1);return invite;
    }
    StudyTurns.View say(String token,String words){return turns.submit(token,new StudyTurns.Request(UUID.randomUUID(),sessions.get(token).turnVersion(),words));}
    StudyTurns.View vision(String token,Integer count){
        var request=new StudyTurns.VisionRequest(UUID.randomUUID(),procedure.view(token).sceneVersion(),count,count!=null&&count==1?"neutral":null,count!=null&&count==1?.9:null,Instant.now().toString());
        var result=turns.vision(token,request);assertEquals(StudyTurn.Status.COMMITTED,result.status());assertEquals(result,turns.vision(token,request));return result;
    }
    void accept(String token){procedure.predict(token,Map.of("revision",procedure.view(token).revision(),"prediction","PRIVATE_PREDICTION"));when(gateway.infer(any())).thenReturn(update("START",null));assertEquals(StudyTurn.Status.COMMITTED,say(token,"I accept this proposal. Start now.").status());}
    @Test void cameraSceneDrivesPostponementExplicitResumeAndOutcomeWithoutModelCalls(){
        var invite=ready("context");var token=invite.token();vision(token,1);
        when(gateway.infer(any())).thenReturn(update("PROPOSE",StudyContextTest.contract()));assertFalse(say(token,"Propose the fictional check-in.").responsePayload().contains("simulated"));accept(token);
        clearInvocations(gateway);vision(token,1);assertEquals(1,turns.conversation(token).actions());
        vision(token,2);assertEquals("PAUSED",turns.conversation(token).phase());vision(token,1);assertEquals("PAUSED",turns.conversation(token).phase());
        assertEquals(3,procedure.view(token).sceneProbeStep());say(token,"Resume");say(token,"No");
        assertEquals("REFLECTION",turns.conversation(token).phase());verifyNoInteractions(gateway);
        sessions.finish(token,"ACTIVITY_ENDED");var exported=evidence.export(invite.session().id());
        assertTrue(exported.path("manifest").path("recordingComplete").asBoolean());assertTrue(exported.toString().contains("study.browser.camera"));
        assertFalse(evidence.participantEvents(token).toString().contains("PRIVATE_CHOICE"));
    }
    @Test void unknownCameraIsNotDepartureAndStaleOrSimulationInputCannotAuthorize(){
        var invite=ready("context");var token=invite.token();
        assertThrows(ResponseStatusException.class,()->turns.scene(token,new StudyTurns.SceneRequest(UUID.randomUUID(),0,1,Instant.now().toString())));
        assertThrows(ResponseStatusException.class,()->turns.vision(token,new StudyTurns.VisionRequest(UUID.randomUUID(),0,1,"neutral",.9,Instant.now().minusSeconds(20).toString())));
        when(gateway.infer(any())).thenReturn(update("PROPOSE",StudyContextTest.contract()));say(token,"Propose it.");accept(token);vision(token,1);vision(token,null);
        assertEquals("PAUSED",turns.conversation(token).phase());assertFalse(JsonParser.parseString(procedure.view(token).context()).getAsJsonObject().has("outcome"));
        vision(token,0);assertEquals("OBSERVED_DEPARTURE",JsonParser.parseString(procedure.view(token).context()).getAsJsonObject().get("outcome").getAsString());
        sessions.withdraw(token);assertThrows(ResponseStatusException.class,()->vision(token,1));
    }
    @Test void faceIsFallibleContextAndNeverStartsAnActivity(){
        var token=ready("guess").token();clearInvocations(gateway);vision(token,1);
        var result=say(token,"What facial expression do you see?");assertTrue(result.responsePayload().contains("neutral"));assertTrue(result.responsePayload().contains("does not establish"));
        assertNull(turns.conversation(token).agreement());verifyNoInteractions(gateway);
        var storage=new Storage();var old=new JsonObject();old.addProperty("simulation",false);old.addProperty("source","study.browser.camera");old.addProperty("ts",Instant.now().minusSeconds(20).toString());old.addProperty("humanCount",1);
        storage.put(CoordinationMemory.STUDY_SCENE,old);assertEquals(-1,StudyContext.count(storage));
        // A missed Stop/network loss must not silently reopen an active question on return.
        old.addProperty("sequence",1);storage.put(CoordinationMemory.STUDY_SCENE,old);
        storage.put(CoordinationMemory.AGREEMENT,StudyContextTest.contract());CoordinationMemory.phase(storage,"RUNNING");
        storage.put(CoordinationMemory.CONTEXT_STATE,JsonParser.parseString("{\"status\":\"WAITING_FOR_ANSWER\",\"pending\":{\"words\":\"Fictional question?\"},\"actions\":1,\"afterScene\":0}"));
        var returned=old.deepCopy();returned.addProperty("sequence",2);returned.addProperty("ts",Instant.now().toString());
        StudyContext.observe(storage,ch.zhaw.prometheus.model.event.Event.observation(StudyVision.EVENT,"study-camera",returned.toString()),Instant.now());
        assertEquals("PAUSED",CoordinationMemory.stage(storage));assertTrue(StudyContext.state(storage).has("pending"));
    }
    @Test void cameraSignsKeepQuestionScopeAndRejectSimulatedOrLowConfidenceEvidence(){
        var token=ready("signs").token();when(gateway.infer(any())).thenReturn(update("PROPOSE",StudySignsTest.contract(false,false)));assertFalse(say(token,"Propose the sign activity.").responsePayload().contains("simulated"));accept(token);
        var scope=JsonParser.parseString(procedure.view(token).signScope()).getAsJsonObject();
        var request=new StudyTurns.SignRequest(UUID.randomUUID(),sessions.get(token).turnVersion(),UUID.randomUUID(),"THUMBS_UP","ANSWER",scope.get("revision").getAsString(),UUID.fromString(scope.get("run").getAsString()),UUID.fromString(scope.get("question").getAsString()),Instant.now().toString(),true,.9,3);
        var result=turns.sign(token,request);assertEquals(StudyTurn.Status.COMMITTED,result.status());assertEquals(result,turns.sign(token,request));
        assertThrows(ResponseStatusException.class,()->turns.sign(token,new StudyTurns.SignRequest(UUID.randomUUID(),sessions.get(token).turnVersion(),UUID.randomUUID(),request.sign(),request.mode(),request.revision(),request.run(),request.question(),Instant.now().toString(),true,.9,3)));
        assertEquals(1,JsonParser.parseString(procedure.view(token).signs()).getAsJsonObject().getAsJsonArray("answers").size());
        assertThrows(ResponseStatusException.class,()->turns.sign(token,new StudyTurns.SignRequest(UUID.randomUUID(),sessions.get(token).turnVersion(),UUID.randomUUID(),request.sign(),request.mode(),request.revision(),request.run(),request.question(),Instant.now().toString(),true,.4,3)));
    }
}
