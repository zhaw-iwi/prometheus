package ch.zhaw.prometheus.model.task;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.hand.*;
import ch.zhaw.prometheus.model.behaviour.DirectCommands;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import fixtures.behaviour.ComposedHandFixture;
import fixtures.behaviour.RelationalHandFixture;

class RelationalHandUnitTest {
    JsonObject left() {var h=ComposedHandFixture.hand("left","left",0,0,.5,1,0);h.add("geometry",RelationalHandFixture.geometry());return h;}
    JsonObject right() {return ComposedHandFixture.hand("right","right",1,1,1,.5,1);}
    JsonObject observed(JsonObject left) {return ComposedHandFixture.observation("frame",Instant.now(),left,right());}
    AgentInteractionProfile profile(boolean geometry) {return AgentInteractionProfile.of(List.of(Event.TYPE_HAND_POSE,Event.TYPE_HAND_SIGNS),List.of(HandActions.MODALITY),
            geometry?List.of("demo.valerian.coordination",HandFeatures.POSES,HandFeatures.GEOMETRY):List.of("demo.valerian.coordination",HandFeatures.POSES));}

    @Test void relationalConditionsUseOneHandAndRetainLegacyNumericComparisons() {
        var condition=HandPoseCondition.parse(RelationalHandFixture.condition());assertTrue(condition.matches(observed(left())));
        var wrong=left();wrong.getAsJsonObject("geometry").getAsJsonObject("axes").add("index_axis",JsonParser.parseString("{\"x\":0,\"y\":1}"));
        assertFalse(condition.matches(observed(wrong)));
        wrong=left();wrong.getAsJsonObject("geometry").getAsJsonObject("spreads").add("index_middle",JsonNull.INSTANCE);assertFalse(condition.matches(observed(wrong)));
        wrong=left();wrong.remove("geometry");assertFalse(condition.matches(observed(wrong)));
        var legacy=HandPoseCondition.parse(JsonParser.parseString("{\"handedness\":\"left\",\"all\":[{\"finger\":\"middle\",\"operator\":\"gt\",\"value\":0.4}]}"));
        assertTrue(legacy.matches(observed(left())));assertFalse(legacy.relational());
        var relationOnly=RelationalHandFixture.condition();relationOnly.getAsJsonArray("hands").get(0).getAsJsonObject().add("all",new JsonArray());
        assertTrue(HandPoseCondition.parse(relationOnly).matches(observed(left())));
        relationOnly.getAsJsonArray("hands").get(0).getAsJsonObject().add("relations",new JsonArray());assertThrows(IllegalArgumentException.class,()->HandPoseCondition.parse(relationOnly));
    }
    @Test void invalidConflictingUnknownAndOutOfRangeGeometryRejects() {
        var hand=left();hand.getAsJsonObject("fingers").add("index",JsonNull.INSTANCE);hand.getAsJsonObject("fingerStates").add("index",JsonNull.INSTANCE);
        assertThrows(IllegalArgumentException.class,()->HandPoseObservation.parse(observed(hand).toString()));
        var bad=left();bad.getAsJsonObject("geometry").getAsJsonObject("axes").add("index_axis",JsonParser.parseString("{\"x\":1,\"y\":1}"));
        assertThrows(IllegalArgumentException.class,()->HandPoseObservation.parse(observed(bad).toString()));
        for(String raw:List.of("[{\"kind\":\"spread\",\"fingers\":[\"index\",\"index\"],\"operator\":\"gt\",\"value\":0.1}]",
                "[{\"kind\":\"spread\",\"fingers\":[\"index\",\"middle\"],\"operator\":\"lt\",\"value\":0}]",
                "[{\"kind\":\"orientation\",\"axis\":\"palm_normal\",\"direction\":\"forward\",\"toleranceDegrees\":10}]"))
            assertThrows(IllegalArgumentException.class,()->HandRelation.parse(JsonParser.parseString(raw)));
        var conflict=RelationalHandFixture.relations();var upper=conflict.get(0).deepCopy().getAsJsonObject();upper.addProperty("operator","lt");upper.addProperty("value",.1);conflict.add(upper);
        assertThrows(IllegalArgumentException.class,()->HandRelation.parse(conflict));
    }
    @Test void oldProfilesRejectGeometryAndRingWithoutWideningTheirPoseCapability() {
        var event=Event.observation(Event.TYPE_HAND_POSE,"camera",observed(left()).toString());
        assertThrows(IllegalArgumentException.class,()->HandObservationAdmission.validate(event,profile(false),List.of(),Instant.now()));
        assertDoesNotThrow(()->HandObservationAdmission.validate(event,profile(true),List.of(),Instant.now()));
        var ring=fixtures.behaviour.NamedHandSignFixture.observation("ring","ok_ring","left",Instant.now());ring.getAsJsonArray("hands").get(0).getAsJsonObject().add("confidence",JsonNull.INSTANCE);
        var sign=Event.observation(Event.TYPE_HAND_SIGNS,"camera",ring.toString());
        assertThrows(IllegalArgumentException.class,()->HandObservationAdmission.validate(sign,profile(false),List.of(),Instant.now()));
        assertTrue(new HandSignCondition("ok_ring","left").matches(HandSignObservation.parse(ring.toString()),0));
        var storage=new Storage();storage.put(HandEvidence.ENABLED,new JsonPrimitive(true));storage.put(HandFeatures.POSES,new JsonPrimitive(true));
        assertTrue(HandEvidence.consumed(storage,event));assertTrue(HandEvidence.consumed(storage,sign));
    }
    @Test void directRelationalOutputRequiresCapabilityAndPreservesIndependentTaskState() {
        var command=new JsonObject();command.addProperty("intent","ACTION");command.addProperty("kind","HAND_POSE");command.add("value",RelationalHandFixture.output());command.addProperty("silent",true);
        var envelope=new JsonObject();envelope.add("command",command);var request=DirectCommands.take(envelope);
        var history=new EventHistory();history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE,"user","Show the spread pose silently"));
        var storage=new Storage();storage.put(TaskMemory.ACTIONS,new JsonPrimitive(2));
        assertEquals("UNSUPPORTED",DirectCommands.evaluate(request,profile(false),new Storage(),history,false).status());
        var result=DirectCommands.evaluate(request,profile(true),storage,history,false);assertEquals("ACCEPTED",result.status());assertNull(result.plan().getSpeech());
        assertEquals(2,storage.get(TaskMemory.ACTIONS).getAsInt());assertEquals(HandActions.pose(RelationalHandFixture.output()),result.plan().getMotion().getAsJsonObject().get("handAction"));
        var invalid=RelationalHandFixture.output();invalid.getAsJsonArray("hands").get(0).getAsJsonObject().getAsJsonObject("fingers").addProperty("index",.5);
        assertThrows(IllegalArgumentException.class,()->HandActions.pose(invalid));
        assertEquals("ok_ring",HandActions.sign("diver's OK"));assertEquals("ok",HandActions.sign("OK"),"An ambiguous label is not silently assigned a shape");
    }
}
