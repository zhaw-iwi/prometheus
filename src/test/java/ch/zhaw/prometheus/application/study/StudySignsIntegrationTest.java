package ch.zhaw.prometheus.application.study;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import com.google.gson.*;
import ch.zhaw.prometheus.spi.*;
import static ch.zhaw.prometheus.application.study.StudyGuessingTest.update;
import static ch.zhaw.prometheus.application.study.StudySignsTest.contract;

@SpringBootTest(properties={"prometheus.runtime.tick.enabled=false","prometheus.study.sensing=simulation"})
class StudySignsIntegrationTest {
    @Autowired StudySessions sessions; @Autowired StudyTurns turns; @Autowired StudyProcedure procedure;
    @Autowired StudyMaterials materials; @Autowired StudyEvidence evidence; @MockitoBean LanguageModelGateway gateway;
    String operation;
    StudySessions.Invitation ready(){var invite=sessions.invite(StudyAttempt.Mode.SYNTHETIC,"signs",null);String token=invite.token();
        sessions.consent(token,StudySessions.SYNTHETIC_CONSENT);var pre=new HashMap<String,String>();materials.manifest().pre().forEach(i->pre.put(i.id(),i.kind().equals("rating7")?"4":"None"));
        sessions.form(token,"pre",pre);sessions.form(token,"intention",Map.of("intention","PRIVATE_SIGN_INTENTION"));sessions.form(token,"private",Map.of("private","PRIVATE_SIGN_PREFERENCE"));
        doAnswer(call->{InferenceRequest request=call.getArgument(0);assertFalse(request.messages().toString().contains("PRIVATE_SIGN"));return operation;}).when(gateway).infer(any());return invite;}
    StudyTurns.View say(String token,String text){var result=turns.submit(token,new StudyTurns.Request(UUID.randomUUID(),sessions.get(token).turnVersion(),text));assertEquals(StudyTurn.Status.COMMITTED,result.status());return result;}
    StudyTurns.SignRequest request(String token,String sign){var scope=JsonParser.parseString(procedure.view(token).signScope()).getAsJsonObject();return new StudyTurns.SignRequest(UUID.randomUUID(),sessions.get(token).turnVersion(),UUID.randomUUID(),sign,scope.get("mode").getAsString(),scope.get("revision").getAsString(),scope.get("run").isJsonNull()?null:UUID.fromString(scope.get("run").getAsString()),scope.get("question").isJsonNull()?null:UUID.fromString(scope.get("question").getAsString()),Instant.now().toString());}
    void sign(String token,String sign){var request=request(token,sign);var result=turns.sign(token,request);assertEquals(StudyTurn.Status.COMMITTED,result.status());assertEquals(result,turns.sign(token,request));}
    void demonstrate(String token){for(String sign:List.of("THUMBS_UP","THUMBS_DOWN","OPEN_PALM"))sign(token,sign);}
    void accept(String token){procedure.predict(token,Map.of("revision",procedure.view(token).revision(),"prediction","PRIVATE_SIGN_PREDICTION"));operation=update("START",null);say(token,"I accept the proposal. Start now.");}
    @Test void reloadWaitRevisionSingleConsumptionAndExactSnapshots(){
        var invite=ready();String token=invite.token();operation=update("PROPOSE",contract(true,false));say(token,"Propose my sign check-in.");
        assertFalse(procedure.view(token).predictionRequired());demonstrate(token);accept(token);sign(token,"THUMBS_UP");
        var old=request(token,"THUMBS_DOWN");sign(token,"OPEN_PALM");var paused=procedure.view(token).signs();
        assertEquals("PAUSED",turns.conversation(token).phase());assertEquals(paused,procedure.view(token).signs());
        assertThrows(ResponseStatusException.class,()->turns.sign(token,request(token,"THUMBS_DOWN")));say(token,"Resume");
        operation=update("REPLACE",contract(true,true));say(token,"Swap yes and no mappings, preserving the rest.");demonstrate(token);accept(token);
        var current=JsonParser.parseString(procedure.view(token).signs()).getAsJsonObject();assertEquals(old.question().toString(),current.getAsJsonObject("pending").get("id").getAsString());
        var stale=new StudyTurns.SignRequest(UUID.randomUUID(),sessions.get(token).turnVersion(),old.observationId(),old.sign(),old.mode(),old.revision(),old.run(),old.question(),Instant.now().toString());
        assertThrows(ResponseStatusException.class,()->turns.sign(token,stale));sign(token,"THUMBS_DOWN");sign(token,"THUMBS_UP");
        assertEquals("REFLECTION",turns.conversation(token).phase());sessions.finish(token,"ACTIVITY_ENDED");var data=evidence.export(invite.session().id());
        assertTrue(data.path("manifest").path("recordingComplete").asBoolean());assertEquals(2,data.path("agreements").size());
        assertEquals(3,data.path("agreements").get(1).path("evidence").size());assertFalse(evidence.participantEvents(token).toString().contains("PRIVATE_SIGN"));
        var answers=JsonParser.parseString(procedure.view(token).signs()).getAsJsonObject().getAsJsonArray("answers");assertEquals(3,answers.size());assertEquals("YES",answers.get(1).getAsJsonObject().get("answer").getAsString());
    }
    @Test void requestAndObservationIdentitySurviveRetriesAndCannotAnswerNextQuestion(){
        var invite=ready();String token=invite.token();operation=update("PROPOSE",contract(false,false));say(token,"Propose it.");accept(token);
        var first=request(token,"THUMBS_UP");var result=turns.sign(token,first);assertEquals(result,turns.sign(token,first));
        var next=request(token,"THUMBS_UP");var repeated=new StudyTurns.SignRequest(next.requestId(),next.expectedTurn(),first.observationId(),next.sign(),next.mode(),next.revision(),next.run(),next.question(),next.observedAt());
        assertThrows(ResponseStatusException.class,()->turns.sign(token,repeated));
        var stale=new StudyTurns.SignRequest(UUID.randomUUID(),next.expectedTurn(),UUID.randomUUID(),next.sign(),next.mode(),next.revision(),next.run(),next.question(),Instant.now().minusSeconds(30).toString());
        assertThrows(ResponseStatusException.class,()->turns.sign(token,stale));
        assertEquals(1,JsonParser.parseString(procedure.view(token).signs()).getAsJsonObject().getAsJsonArray("answers").size());
        assertThrows(ResponseStatusException.class,()->turns.sign(ready().token(),next));
    }
}
