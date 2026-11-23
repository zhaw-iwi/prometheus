package ch.zhaw.prometheus.model.task;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.hand.*;
import fixtures.behaviour.HandSequenceFixture;

class HandSequenceMemoryUnitTest {
    final Instant start=Instant.parse("2026-11-23T12:00:00Z");final Storage storage=new Storage();
    final java.util.Map<String,Integer> captures=new java.util.HashMap<>();
    HandSequenceMemoryUnitTest(){for(String key:java.util.List.of(HandEvidence.ENABLED,HandFeatures.POSES,HandFeatures.INPUT_SEQUENCES))storage.put(key,new JsonPrimitive(true));storage.put(TaskMemory.PHASE,new JsonPrimitive("RUNNING"));}
    HandSequence sequence(int... counts){return HandSequence.parse(HandSequenceFixture.counts("right",counts));}
    Event event(int count,int millis){return HandSequenceFixture.event("frame-"+millis,count,false,start.plusMillis(millis));}
    boolean observe(HandSequence seq,int count,int millis){return observe(seq,event(count,millis),millis);}
    boolean observe(HandSequence seq,Event event,int millis){var p=JsonParser.parseString(event.getPayload()).getAsJsonObject();p.addProperty("captureIndex",captures.computeIfAbsent(p.get("observationId").getAsString(),id->captures.size()+1));event.setPayload(p.toString());return HandSequenceMemory.observe(storage,"run-0","run",seq,event,start.minusSeconds(1),start.plusMillis(millis));}
    JsonObject progress(){return HandSequenceMemory.progress(storage).getAsJsonObject("run-0");}
    @Test void orderedDwellCompletesOnceAndHeldFinalPoseCannotRestartIt(){
        var seq=sequence(1,2,3);assertFalse(observe(seq,1,0));assertFalse(observe(seq,1,300));assertEquals(1,progress().get("index").getAsInt());
        assertFalse(observe(seq,2,600));assertFalse(observe(seq,2,900));assertFalse(observe(seq,3,1200));assertTrue(observe(seq,3,1500));
        assertTrue(observe(seq,event(3,1500),1500),"Guard re-evaluation is idempotent until action consumption");HandEvidence.consume(storage,event(3,1500));assertFalse(observe(seq,event(3,1500),1500));
        assertFalse(observe(seq,3,5000));assertFalse(observe(seq,3,5300));assertEquals(3,progress().get("index").getAsInt());
        assertFalse(observe(seq,1,5600));assertFalse(observe(seq,1,5900));assertEquals(1,progress().get("index").getAsInt());
    }
    @Test void repeatedStepsNeedAKnownInterveningRelease(){
        var seq=sequence(1,1);observe(seq,1,0);observe(seq,1,300);assertEquals("awaiting_release",progress().get("status").getAsString());
        assertFalse(observe(seq,1,600));assertFalse(observe(seq,1,900));assertFalse(observe(seq,0,1200));assertFalse(observe(seq,1,1500));assertTrue(observe(seq,1,1800));
    }
    @Test void unknownSamplesResetDwellWithoutManufacturingFoldedFingers(){
        var seq=sequence(0);observe(seq,0,0);var p=HandSequenceFixture.pose("unknown",0,false,start.plusMillis(150));var hand=p.getAsJsonArray("hands").get(0).getAsJsonObject();hand.getAsJsonObject("fingers").add("index",JsonNull.INSTANCE);hand.getAsJsonObject("fingerStates").add("index",JsonNull.INSTANCE);
        assertFalse(observe(seq,Event.observation(Event.TYPE_HAND_POSE,"camera",p.toString()),150));assertFalse(observe(seq,0,300));assertTrue(observe(seq,0,600));
    }
    @Test void duplicateAndOutOfOrderSourceCapturesCannotAccumulateDwell(){
        var seq=sequence(2);var first=event(2,0);assertFalse(observe(seq,first,0));assertFalse(observe(seq,first,500));assertEquals(0,progress().get("index").getAsInt());
        assertFalse(observe(seq,event(2,-100),600));assertEquals(start.toString(),progress().get("lastAt").getAsString());assertFalse(observe(seq,2,650));assertTrue(observe(seq,2,950));
    }
    @Test void wrongStableStepResetsPartialProgress(){
        var seq=sequence(1,2,3);observe(seq,1,0);observe(seq,1,300);observe(seq,3,600);observe(seq,3,900);assertEquals("wrong_step",progress().get("status").getAsString());assertEquals(0,progress().get("index").getAsInt());
        observe(seq,2,1200);assertFalse(observe(seq,2,1500));assertEquals(0,progress().get("index").getAsInt());
    }
    @Test void gapAndOriginalDeadlineEndTheAttempt(){
        var raw=HandSequenceFixture.counts("right",1,2);raw.addProperty("maximumGapMs",500);var gap=HandSequence.parse(raw);observe(gap,1,0);observe(gap,1,300);assertFalse(observe(gap,2,900));assertEquals("timeout",progress().get("status").getAsString());
        HandSequenceMemory.clear(storage);raw.addProperty("maximumGapMs",5000);raw.addProperty("maximumDurationMs",1200);var total=HandSequence.parse(raw);observe(total,1,0);observe(total,1,300);observe(total,2,1100);assertFalse(observe(total,2,1400));assertEquals("timeout",progress().get("status").getAsString());
    }
    @Test void changedHandOrCameraPausesAndRequiresExplicitRestart(){
        for(boolean frameChange:new boolean[]{false,true}){
            HandSequenceMemory.clear(storage);storage.put(TaskMemory.PHASE,new JsonPrimitive("RUNNING"));var seq=sequence(1,2);observe(seq,1,0);observe(seq,1,300);
            var p=HandSequenceFixture.pose("changed",2,false,start.plusMillis(600));if(frameChange)p.getAsJsonObject("frame").addProperty("revision",2);else p.getAsJsonArray("hands").get(0).getAsJsonObject().addProperty("id","other-hand");
            assertFalse(observe(seq,Event.observation(Event.TYPE_HAND_POSE,"camera",p.toString()),600));assertEquals("PAUSED",TaskMemory.phase(storage));assertEquals("restart_required",progress().get("status").getAsString());assertFalse(observe(seq,2,900));
        }
    }
    @Test void persistedProgressIsInspectableAndNewAttemptEpochCannotReuseItsSteps(){
        var seq=sequence(1,2);observe(seq,1,0);observe(seq,1,300);var saved=HandSequenceMemory.progress(storage);var copy=new Storage();for(var entry:storage.toMap().entrySet())copy.put(entry.getKey(),entry.getValue().deepCopy());
        assertEquals(saved,HandSequenceMemory.progress(copy));assertFalse(HandSequenceMemory.observe(copy,"run-0","explicit-new-run",seq,event(2,600),start.plusMillis(400),start.plusMillis(600)));assertEquals(0,HandSequenceMemory.progress(copy).getAsJsonObject("run-0").get("index").getAsInt());
    }
    @Test void serverRestartRequiresExplicitResumeEvenWithAnUnchangedCamera(){
        var seq=sequence(1,2);observe(seq,1,0);observe(seq,1,300);var saved=HandSequenceMemory.progress(storage);saved.getAsJsonObject("run-0").addProperty("process","previous-process");storage.put(HandSequenceMemory.KEY,saved);
        assertFalse(observe(seq,2,600));assertEquals("PAUSED",TaskMemory.phase(storage));assertEquals("restart_required",progress().get("status").getAsString());
        HandSequenceMemory.restartRun(storage);assertFalse(observe(seq,2,900));assertEquals(0,progress().get("index").getAsInt());
    }
    @Test void mixedSignPoseAndPointStepsCannotReuseTheSameCapture(){
        storage.put(HandFeatures.POINTING,new JsonPrimitive(true));
        var raw=HandSequenceFixture.counts("right",1);var steps=new JsonArray();
        steps.add(JsonParser.parseString("{\"kind\":\"sign\",\"condition\":{\"sign\":\"thumbs_up\",\"handedness\":\"right\"}}"));
        steps.add(JsonParser.parseString("{\"kind\":\"pose\",\"condition\":{\"schemaVersion\":1,\"hands\":[{\"handedness\":\"right\",\"all\":[{\"finger\":\"index\",\"operator\":\"eq\",\"value\":\"straight\"}]}]}}"));
        var condition=fixtures.behaviour.PointingFixture.condition();condition.add("frame",HandSequenceFixture.pose("x",1,false,start).get("frame"));var pointStep=new JsonObject();pointStep.addProperty("kind","point");pointStep.add("condition",condition);steps.add(pointStep);raw.add("steps",steps);var seq=HandSequence.parse(raw);
        for(int i=0;i<6;i++){
            int ms=i*300;String id="mixed-"+ms;JsonObject p;String type;
            if(i<2){p=fixtures.behaviour.NamedHandSignFixture.observation(id,"thumbs_up","right",start.plusMillis(ms));p.getAsJsonArray("hands").get(0).getAsJsonObject().addProperty("id","right-track");type=Event.TYPE_HAND_SIGNS;}
            else if(i<4){p=HandSequenceFixture.pose(id,2,false,start.plusMillis(ms));type=Event.TYPE_HAND_POSE;}
            else{p=fixtures.behaviour.PointingFixture.observation(id,"right",start.plusMillis(ms));p.add("frame",condition.get("frame"));p.addProperty("source","test.camera");p.addProperty("handId","right-track");type=Event.TYPE_HAND_POINTING;}
            assertEquals(i==5,observe(seq,Event.observation(type,"camera",p.toString()),ms),"step "+i+" progress "+progress());
            if(i==1){var companion=HandSequenceFixture.pose(id,2,false,start.plusMillis(ms));assertFalse(observe(seq,Event.observation(Event.TYPE_HAND_POSE,"camera",companion.toString()),ms));assertEquals(1,progress().get("index").getAsInt());}
        }
    }
}
