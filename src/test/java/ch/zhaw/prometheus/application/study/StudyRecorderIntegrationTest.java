package ch.zhaw.prometheus.application.study;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.web.server.ResponseStatusException;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.task.TaskPolicy;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import com.google.gson.*;

@SpringBootTest(properties={"prometheus.runtime.tick.enabled=false","prometheus.study.sensing=simulation"})
class StudyRecorderIntegrationTest {
    @Autowired StudySessions sessions;
    @Autowired StudyTurns turns;
    @Autowired StudyEvidence evidence;
    @Autowired StudyMaterials materials;
    @Autowired StudyRecordRepository records;
    @Autowired StudyAttemptRepository attempts;
    @Autowired AgentRepository agents;
    @MockitoSpyBean StudyRecorder recorder;
    @MockitoBean LanguageModelGateway gateway;
    StudySessions.Invitation ready() {
        var invitation=sessions.invite(StudyAttempt.Mode.SYNTHETIC,"guess",null,true);
        sessions.consent(invitation.token(),StudySessions.SYNTHETIC_CONSENT);
        var pre=new HashMap<String,String>();
        materials.manifest().pre().forEach(i -> pre.put(i.id(),i.kind().equals("rating7")?"4":"None"));
        sessions.form(invitation.token(),"pre",pre);
        sessions.form(invitation.token(),"intention",Map.of("intention","A friendly greeting"));
        sessions.form(invitation.token(),"private",Map.of("private","private-umbrella")); return invitation;
    }
    JsonObject agreement(String goal) {
        var value=JsonParser.parseString("""
            {"goal":"Greeting","humanRole":"Judge the wording","agentRole":"Write a greeting",
             "success":"I accept the wording","dialogueLimit":5,"questions":[],"checks":[],"reactions":null}
            """).getAsJsonObject(); value.addProperty("goal",goal); return value;
    }
    StudyTurns.View turn(StudySessions.Invitation invitation,String text,String operation,JsonObject agreement) {
        var answer=new JsonObject(); answer.addProperty("operation",operation);
        answer.add("agreement",agreement); answer.add("reply",JsonParser.parseString("{\"speech\":\"Welcome.\"}"));
        when(gateway.infer(any())).thenReturn(answer.toString());
        return turns.submit(invitation.token(),new StudyTurns.Request(UUID.randomUUID(),sessions.get(invitation.token()).turnVersion(),text));
    }
    void accept(StudySessions.Invitation invitation,String goal) {
        assertEquals(StudyTurn.Status.COMMITTED,turn(invitation,"Propose a greeting, do not start","PROPOSE",agreement(goal)).status());
        assertEquals(StudyTurn.Status.COMMITTED,turn(invitation,"I accept the proposal. Start now.","START",null).status());
    }
    @Test void acceptedSnapshotsAreExactIndependentAndPrivateAnswersStayOutOfPublicData() {
        var invitation=ready(); accept(invitation,"Greeting");
        var initial=evidence.export(invitation.session().id()).path("agreements").get(0).deepCopy();
        turn(invitation,"Make the greeting warmer","PROPOSE",agreement("Warm greeting"));
        var accepted=turn(invitation,"I accept the revised proposal. Start now.","START",null);
        var exported=evidence.export(invitation.session().id()); var snapshots=exported.path("agreements");
        assertEquals(2,snapshots.size()); assertEquals(initial,snapshots.get(0));
        assertEquals("Warm greeting",snapshots.get(1).path("agreement").path("goal").asText());
        assertEquals(accepted.inputEventId().toString(),snapshots.get(1).path("sourceEventId").asText());
        assertNotEquals(snapshots.get(0).path("run"),snapshots.get(1).path("run"));
        assertTrue(exported.path("manifest").path("recordingComplete").asBoolean());
        assertFalse(exported.path("manifest").path("procedureComplete").asBoolean());
        assertTrue(exported.path("answers").toString().contains("private-umbrella"));
        assertFalse(exported.toString().contains(invitation.token())); assertFalse(exported.toString().contains("tokenHash"));
        assertFalse(evidence.participantEvents(invitation.token()).toString().contains("private-umbrella"));
        for (var call:mockingDetails(gateway).getInvocations())
            assertFalse(Arrays.toString(call.getArguments()).contains("private-umbrella"));
    }
    @Test void recorderOutlivesBoundedJournalsAndOutcomes() {
        var invitation=ready();
        for (int run=0;run<10;run++) {
            accept(invitation,"Greeting"); turn(invitation,"Stop the task.","STOP",null);
        }
        var attempt=attempts.findById(invitation.session().id()).orElseThrow();
        var storage=((TaskPolicy)agents.findById(attempt.agentId).orElseThrow().getCurrentState().ownPolicy()).storage();
        assertEquals(12,storage.get(CoordinationMemory.LOG).getAsJsonArray().size());
        assertEquals(8,storage.get(CoordinationMemory.OUTCOMES).getAsJsonArray().size());
        var exported=evidence.export(attempt.id); assertEquals(30,exported.path("requests").size());
        assertEquals(10,exported.path("agreements").size());
        long stopped=0;
        for (var record:exported.path("records")) for (var entry:record.path("data").path("journal"))
            if ("STOP".equals(entry.path("operation").asText())) stopped++;
        assertEquals(10,stopped); assertTrue(exported.path("manifest").path("recordingComplete").asBoolean());
    }
    @Test void recorderFailureRollsBackAcceptanceAndExportShowsUncertainty() {
        var invitation=ready(); turn(invitation,"Propose a greeting","PROPOSE",agreement("Greeting"));
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("Injected after recorder insert"); })
                .when(recorder).turn(any(),any(),any(),anyLong());
        var failed=turn(invitation,"I accept the proposal. Start now.","START",null);
        assertEquals(StudyTurn.Status.UNCERTAIN,failed.status());
        assertNull(turns.conversation(invitation.token()).agreement());
        var exported=evidence.export(invitation.session().id()); assertEquals(0,exported.path("agreements").size());
        assertEquals(1,exported.path("manifest").path("unresolvedRequests").asInt());
        assertEquals(1,records.countByAttemptIdAndKind(invitation.session().id(),"TURN"));
    }
    @Test void displayEvidenceIsScopedIdempotentAndDoesNotImplyReading() {
        var invitation=ready(); accept(invitation,"Greeting"); var other=ready();
        var generated=evidence.participantEvents(invitation.token()).stream()
                .filter(e -> "resp.behaviour_plan".equals(e.path("type").asText())).toList();
        for (var event:generated) {
            var receipt=new StudyEvidence.Display(UUID.fromString(event.path("id").asText()),Instant.now().toString());
            assertThrows(ResponseStatusException.class,() -> evidence.display(other.token(),receipt));
            evidence.display(invitation.token(),receipt); evidence.display(invitation.token(),receipt);
        }
        var exported=evidence.export(invitation.session().id());
        assertTrue(exported.path("manifest").path("displayComplete").asBoolean());
        assertEquals(generated.size(),records.countByAttemptIdAndKind(invitation.session().id(),"DISPLAY"));
        assertEquals(2,exported.path("requests").size());
        assertFalse(exported.path("manifest").has("read"));
        var client=new StudyEvidence.ClientEvent(UUID.randomUUID(),"DISCONNECT",Instant.now().toString(),"network");
        evidence.client(invitation.token(),client); evidence.client(invitation.token(),client);
        assertEquals(1,records.countByAttemptIdAndKind(invitation.session().id(),"CLIENT"));
    }
    @Test void missingRecordCannotProduceACompleteExport() {
        var invitation=ready(); accept(invitation,"Greeting");
        var lost=records.findByAttemptIdOrderByRecordedAt(invitation.session().id()).stream().filter(r -> r.kind.equals("TURN")).findFirst().orElseThrow();
        records.deleteById(lost.id); records.flush();
        var exported=evidence.export(invitation.session().id());
        assertFalse(exported.path("manifest").path("recordingComplete").asBoolean());
        assertTrue(exported.path("manifest").path("issues").toString().contains("turn_record_mismatch"));
    }
    @Test void missingCanonicalAgentStillExportsDurableEvidenceAsIncomplete(){
        var invitation=ready();accept(invitation,"Greeting");var attempt=attempts.findById(invitation.session().id()).orElseThrow();
        agents.deleteById(attempt.agentId);agents.flush();var exported=evidence.export(attempt.id);
        assertFalse(exported.path("manifest").path("recordingComplete").asBoolean());
        assertTrue(exported.path("manifest").path("issues").toString().contains("canonical_agent_missing"));
        assertEquals(1,exported.path("agreements").size());assertEquals(2,exported.path("requests").size());
    }
}
