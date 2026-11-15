package ch.zhaw.prometheus.application.study;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import ch.zhaw.prometheus.spi.*;
import static ch.zhaw.prometheus.application.study.StudyGuessingTest.*;

@SpringBootTest(properties="prometheus.runtime.tick.enabled=false")
class StudyGuessingIntegrationTest {
    @Autowired StudySessions sessions;
    @Autowired StudyTurns turns;
    @Autowired StudyProcedure procedure;
    @Autowired StudyMaterials materials;
    @Autowired StudyEvidence evidence;
    @MockitoBean LanguageModelGateway gateway;
    String operation=update("KEEP",null),question=move("QUESTION","Is it used indoors?");
    StudySessions.Invitation ready() {
        var invite=sessions.invite(StudyAttempt.Mode.SYNTHETIC,"guess",null);var token=invite.token();
        sessions.consent(token,StudySessions.SYNTHETIC_CONSENT);var pre=new HashMap<String,String>();
        materials.manifest().pre().forEach(item->pre.put(item.id(),item.kind().equals("rating7")?"4":"None"));
        sessions.form(token,"pre",pre);sessions.form(token,"intention",Map.of("intention","private-intention-fixture"));
        sessions.form(token,"private",Map.of("private","private-secret-umbrella"));
        when(gateway.infer(any())).thenAnswer(call->{
            InferenceRequest request=call.getArgument(0);String prompt=request.messages().toString();
            for(String secret:List.of("private-secret-umbrella","private-intention-fixture","private-prediction-fixture")) assertFalse(prompt.contains(secret));
            return prompt.contains("STUDY_GUESSING_MOVE:")?question:operation;
        });return invite;
    }
    StudyTurns.View say(String token,String text) {
        var request=new StudyTurns.Request(UUID.randomUUID(),sessions.get(token).turnVersion(),text);
        var result=turns.submit(token,request);assertEquals(StudyTurn.Status.COMMITTED,result.status());
        assertEquals(result,turns.submit(token,request));return result;
    }
    Map<String,String> predict(String token) {
        var answer=Map.of("revision",procedure.view(token).revision(),"prediction","private-prediction-fixture");
        procedure.predict(token,answer);return answer;
    }
    @Test void assignedExecutorAndOneRevisionCardAreEnforced() {
        var invite=ready();String token=invite.token();var generic=contract(3).deepCopy();generic.remove("activity");
        operation=update("PROPOSE",generic);say(token,"Propose a generic conversation.");assertNull(turns.conversation(token).draft());
        operation=update("PROPOSE",contract(3));say(token,"Propose a three-question game.");predict(token);
        operation=update("START",null);say(token,"Start now");assertTrue(procedure.view(token).revisionInstruction().contains("five"));
        operation=update("REPLACE",contract(5));say(token,"Change the limit to five in total.");assertTrue(procedure.view(token).revisionInstruction().contains("five"));
        predict(token);operation=update("START",null);say(token,"Start now");assertNull(procedure.view(token).revisionInstruction());
    }
    @Test void clarificationRecordsOnlyTextEvenWhenModelAddsDecorativeModalities(){
        var invite=ready();operation="{\"operation\":\"KEEP\",\"agreement\":null,\"reply\":{\"speech\":\"Which answers should we allow?\",\"nonVerbal\":{\"gesture\":\"OPEN_QUESTION\"},\"display\":{\"text\":\"Clarify\"}}}";
        var result=say(invite.token(),"Please clarify the missing rules.");
        var payload=com.google.gson.JsonParser.parseString(result.responsePayload()).getAsJsonObject();assertEquals(Set.of("speech"),payload.keySet());
        assertNull(turns.conversation(invite.token()).agreement());
    }
    @Test void persistedGameRevisionAndPrivatePredictionsSurviveReloadAndExport() {
        var invite=ready();String token=invite.token();operation=update("PROPOSE",contract(5));say(token,"Propose my game, do not start.");
        assertTrue(procedure.view(token).predictionRequired());
        assertThrows(ResponseStatusException.class,()->say(token,"Start now."));
        var firstPrediction=predict(token);procedure.predict(token,firstPrediction);
        assertThrows(ResponseStatusException.class,()->procedure.predict(token,Map.of("revision",firstPrediction.get("revision"),"prediction","Changed answer")));
        operation=update("START",null);say(token,"I accept the saved proposal. Start now.");
        String pending=procedure.view(token).game();assertTrue(pending.contains("Is it used indoors?"));
        say(token,"Pause");assertEquals("PAUSED",turns.conversation(token).phase());
        assertEquals(pending,procedure.view(token).game());say(token,"Resume");
        operation=update("REPLACE",contract(3));say(token,"Change the question limit to three and preserve everything else.");
        assertEquals("READY",turns.conversation(token).phase());assertEquals(5,turns.conversation(token).agreement().path("activity").path("questionLimit").asInt());
        predict(token);operation=update("START",null);say(token,"I accept the saved proposal. Start now.");
        assertEquals(pending,procedure.view(token).game());assertEquals(1,turns.conversation(token).actions());
        question=move("QUESTION","Is it made of metal?");say(token,"No");
        question=move("GUESS","Is it a kettle?");say(token,"Yes");
        assertEquals(3,turns.conversation(token).actions());assertEquals("EXECUTION",turns.conversation(token).phase());
        say(token,"Yes");assertEquals("REFLECTION",turns.conversation(token).phase());
        assertTrue(procedure.view(token).game().contains("HUMAN_CONFIRMED_GUESS"));
        sessions.finish(token,"ACTIVITY_ENDED");var export=evidence.export(invite.session().id());
        assertTrue(export.path("manifest").path("recordingComplete").asBoolean());assertEquals(2,export.path("agreements").size());
        assertTrue(export.path("answers").toString().contains("private-prediction-fixture"));
        assertFalse(evidence.participantEvents(token).toString().contains("private-secret-umbrella"));
    }
    @Test void failedNextMoveRetainsPendingAnswerAcrossDatabaseReload() {
        var invite=ready();String token=invite.token();operation=update("PROPOSE",contract(2));say(token,"Propose a game.");predict(token);
        operation=update("START",null);say(token,"I accept the saved proposal. Start now.");var before=procedure.view(token).game();
        question="{}";say(token,"No");assertEquals(before,procedure.view(token).game());
        question=move("GUESS","Is it a book?");say(token,"No");assertEquals(2,turns.conversation(token).actions());
        say(token,"No");assertTrue(procedure.view(token).game().contains("QUESTION_LIMIT_REACHED"));
    }
    @Test void inferredAcceptanceCannotBypassThePrivatePredictionGate() {
        var invite=ready();String token=invite.token();operation=update("PROPOSE",contract(2));say(token,"Propose a game.");
        operation=update("START",null);
        var result=turns.submit(token,new StudyTurns.Request(UUID.randomUUID(),sessions.get(token).turnVersion(),"Those terms work for me. Let us begin."));
        assertEquals(StudyTurn.Status.UNCERTAIN,result.status());assertNull(turns.conversation(token).agreement());
        assertNull(procedure.view(token).game());assertEquals(0,evidence.export(invite.session().id()).path("agreements").size());
        turns.cancel(token,result.requestId());assertTrue(procedure.view(token).predictionRequired());
    }
}
