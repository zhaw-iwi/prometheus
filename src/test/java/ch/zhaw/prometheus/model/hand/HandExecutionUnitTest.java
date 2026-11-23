package ch.zhaw.prometheus.model.hand;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.google.gson.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.behaviour.*;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.model.task.*;
import fixtures.behaviour.OutputSequenceFixture;

class HandExecutionUnitTest {
    final Storage storage=new Storage();final EventHistory history=new EventHistory();final UUID epoch=UUID.randomUUID(),owner=UUID.randomUUID();final Instant now=Instant.parse("2026-11-23T12:00:00Z");
    Event publish(JsonObject sequence,ExternalSpeech speech){var event=history.appendEvent(Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN,"assistant",OutputSequenceFixture.plan(sequence).toJson()));ReflectionTestUtils.setField(event,"id",UUID.randomUUID());HandExecution.publish(storage,event,epoch,speech,now);return event;}
    JsonObject receipt(Event event,String operation,int step,Instant at){return receipt(event,operation,step,at,owner,epoch,null);}
    JsonObject receipt(Event event,String operation,int step,Instant at,UUID who,UUID version,ExternalSpeech speech){var r=new JsonObject();r.addProperty("eventId",event.getId().toString());r.addProperty("owner",who.toString());r.addProperty("operation",operation);r.addProperty("step",step);return HandExecution.receipt(storage,history,version,speech,r,at);}
    @Test void orderedReceiptsRespectDurationAndNeverTurnPublicationIntoCompletion(){
        var event=publish(OutputSequenceFixture.counting(1,5,500),null);assertEquals("PUBLISHED",HandExecution.view(storage,now).get("status").getAsString());
        assertThrows(IllegalArgumentException.class,()->receipt(event,"COMPLETE",4,now));
        receipt(event,"START",0,now);assertThrows(IllegalArgumentException.class,()->receipt(event,"STEP",1,now.plusMillis(1199)));
        assertThrows(IllegalArgumentException.class,()->receipt(event,"STEP",2,now.plusMillis(1200)));
        for(int i=1;i<5;i++)assertEquals(i,receipt(event,"STEP",i,now.plusMillis(i*1200)).get("step").getAsInt());
        assertEquals("COMPLETED",receipt(event,"COMPLETE",4,now.plusMillis(6000)).get("status").getAsString());
        assertEquals("COMPLETED",receipt(event,"START",0,now.plusMillis(6100)).get("status").getAsString());assertTrue(HandExecution.confirmed(storage));
    }
    @Test void ownersEpochsCancellationSupersessionAndLateReceiptsAreFenced(){
        var event=publish(OutputSequenceFixture.counting(1,5,500),null);receipt(event,"START",0,now);
        assertThrows(IllegalArgumentException.class,()->receipt(event,"STEP",1,now.plusMillis(1200),UUID.randomUUID(),epoch,null));
        assertThrows(IllegalArgumentException.class,()->receipt(event,"STEP",1,now.plusMillis(1200),owner,UUID.randomUUID(),null));
        receipt(event,"STEP",1,now.plusMillis(1200));assertEquals("INTERRUPTED",receipt(event,"CANCEL",1,now.plusMillis(1400)).get("status").getAsString());
        assertEquals("INTERRUPTED",receipt(event,"STEP",2,now.plusMillis(2400)).get("status").getAsString());publish(OutputSequenceFixture.counting(1,2,500),null);
        assertThrows(IllegalArgumentException.class,()->receipt(event,"COMPLETE",4,now.plusMillis(6000)));assertEquals(1,storage.get(HandExecution.HISTORY).getAsJsonArray().size());
    }
    @Test void reloadProcessDeadlineAndVoiceOwnershipNeverResumeUncertainOutput(){
        var speech=new ExternalSpeech(UUID.randomUUID(),epoch);var event=publish(OutputSequenceFixture.counting(1,3,500),speech);
        assertEquals("INTERRUPTED",receipt(event,"START",0,now).get("status").getAsString());
        event=publish(OutputSequenceFixture.counting(1,3,500),null);receipt(event,"START",0,now);var stored=storage.get(HandExecution.KEY).deepCopy();var copy=new Storage();copy.put(HandExecution.KEY,stored);
        assertEquals("RUNNING",HandExecution.view(copy,now).get("status").getAsString());stored.getAsJsonObject().addProperty("process","old-process");
        assertEquals("UNKNOWN",HandExecution.view(copy,now).get("status").getAsString());
        assertEquals("UNKNOWN",HandExecution.view(storage,now.plusSeconds(20)).get("status").getAsString());
    }
    @Test void finalTaskWaitsForDigitalReceiptAndAnInterruptedTaskRemainsUncertain(){
        for(boolean success:new boolean[]{true,false}){
            HandExecution.clear(storage);storage.put(TaskMemory.PHASE,new JsonPrimitive("FINISHING"));storage.put(TaskMemory.ACTIONS,new JsonPrimitive(1));storage.put("coordination.run",new JsonPrimitive("run"));
            var action=OutputSequenceFixture.counting(1,1,500);HandExecution.prepareTask(storage,OutputSequenceFixture.plan(action));var event=publish(action,null);receipt(event,"START",0,now);
            assertEquals("FINISHING",TaskMemory.phase(storage));receipt(event,success?"COMPLETE":"CANCEL",0,now.plusMillis(1200));
            assertEquals(success?"COMPLETED":"UNCERTAIN",TaskMemory.phase(storage));assertEquals(1,storage.get(TaskMemory.ACTIONS).getAsInt());
        }
    }
    @Test void disappearingTargetAbortsBeforeItsStep(){
        // The first pose can play, but an absent registered reference cannot authorize pointing.
        var sequence=OutputSequenceFixture.counting(1,2,500);var target=JsonParser.parseString("{\"id\":\"missing\",\"revision\":1,\"frame\":{\"id\":\"scene\",\"revision\":1,\"space\":\"robot_scene\",\"mirrored\":false}}");
        target.getAsJsonObject().add("frame",new SpatialFrame("scene",1,"virtual_scene",false).json());
        var point=new JsonObject();point.addProperty("kind","point");point.addProperty("effector","right_hand");point.add("target",target);sequence.getAsJsonArray("steps").get(1).getAsJsonObject().add("action",point);
        var event=publish(sequence,null);receipt(event,"START",0,now);var result=receipt(event,"STEP",1,now.plusMillis(1200));assertEquals("INTERRUPTED",result.get("status").getAsString());assertEquals("target_unavailable",result.get("reason").getAsString());
    }
}
