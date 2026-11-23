package ch.zhaw.prometheus.model.task;

import static fixtures.behaviour.ComposedHandFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.hand.*;
import ch.zhaw.prometheus.model.behaviour.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.coordination.CoordinationAgreement;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;

class ComposedHandPoseUnitTest {
    final Instant now=Instant.parse("2026-11-23T12:00:00Z");
    final JsonObject left=hand("left-id","left",.5,0,.5,.5,.5),right=hand("right-id","right",.5,.5,1,.5,.5);
    @Test void twoDistinctHandsMatchWithinOneFrameRegardlessOfArrayOrder() {
        var condition=HandPoseCondition.parse(condition());
        assertTrue(condition.matches(observation("one",now,left,right)));
        assertTrue(condition.matches(observation("one",now,right,left)));
        assertFalse(condition.matches(observation("one",now,left)));
        assertFalse(condition.matches(observation("two",now,right)));
        var any=condition();any.getAsJsonArray("hands").forEach(h->h.getAsJsonObject().addProperty("handedness","any"));
        var both=hand("one","unknown",0,0,1,0,0);
        assertFalse(HandPoseCondition.parse(any).matches(observation("one",now,both)),"One hand cannot fill two slots");
        assertEquals(agreement(),CoordinationAgreement.parse(agreement()).json());
    }
    @Test void unknownContradictoryAmbiguousAndLegacyEvidenceCannotSatisfyNewPoses() {
        var condition=HandPoseCondition.parse(condition());
        var unknown=left.deepCopy();unknown.getAsJsonObject("fingers").add("index",JsonNull.INSTANCE);unknown.getAsJsonObject("fingerStates").add("index",JsonNull.INSTANCE);
        assertFalse(condition.matches(observation("one",now,unknown,right)));
        var duplicate=right.deepCopy();duplicate.addProperty("id","other");
        assertFalse(condition.matches(observation("one",now,right,duplicate)));
        var invalid=observation("one",now,left,right);invalid.getAsJsonArray("hands").get(0).getAsJsonObject().getAsJsonObject("fingerStates").addProperty("index","bent");
        assertThrows(IllegalArgumentException.class,()->HandPoseObservation.parse(invalid.toString()));
        var legacy=observation("one",now,left,right);legacy.addProperty("schemaVersion",1);
        assertThrows(IllegalArgumentException.class,()->condition.matches(legacy));
        var conflict=condition();conflict.getAsJsonArray("hands").get(1).getAsJsonObject().addProperty("handedness","left");
        assertThrows(IllegalArgumentException.class,()->HandPoseCondition.parse(conflict));
    }
    @Test void everyFingerAndNeutralBoundaryRetainsNumericAndStateMeaning() {
        for(String finger:HandPoseObservation.FINGERS)for(double bend:new double[]{0,.399,.4,.5,.55,.551,1}) {
            String state=bend<.4?"straight":bend>.55?"bent":"neutral";
            var h=left.deepCopy();h.getAsJsonObject("fingers").addProperty(finger,bend);h.getAsJsonObject("fingerStates").addProperty(finger,state);
            var raw=JsonParser.parseString("{\"schemaVersion\":1,\"hands\":[{\"handedness\":\"left\",\"all\":[{\"finger\":\""+finger+"\",\"operator\":\"eq\",\"value\":\""+state+"\"}]}]}");
            assertTrue(HandPoseCondition.parse(raw).matches(observation("one",now,h)));
        }
    }
    @Test void sharedIdentityAndRateCountOneCaptureAcrossNamedAndPoseInterpretations() {
        var named=fixtures.behaviour.NamedHandSignFixture.event("one","peace","right",now);
        var pose=Event.observation(Event.TYPE_HAND_POSE,"camera",observation("one",now,left,right).toString());
        var storage=new Storage();storage.put(HandEvidence.ENABLED,new JsonPrimitive(true));storage.put(HandFeatures.POSES,new JsonPrimitive(true));
        HandEvidence.consume(storage,named);assertTrue(HandEvidence.consumed(storage,pose));
        var profile=AgentInteractionProfile.of(List.of(Event.TYPE_HAND_SIGNS,Event.TYPE_HAND_POSE),List.of(),List.of(HandFeatures.POSES));
        assertDoesNotThrow(()->HandObservationAdmission.validate(pose,profile,List.of(named),Instant.now()));
        var old=AgentInteractionProfile.of(profile.getSupportedObservations(),List.of(),List.of());
        assertThrows(IllegalArgumentException.class,()->HandObservationAdmission.validate(pose,old,List.of(),Instant.now()));
    }
    @Test void structuredImmediateOutputPreservesTheTaskAndRejectsMissingOrDuplicateFingersAndSides() {
        var profile=AgentInteractionProfile.of(List.of(),List.of(HandActions.MODALITY),List.of("demo.valerian.coordination",HandFeatures.POSES));
        var storage=new Storage();storage.put(TaskMemory.ACTIONS,new JsonPrimitive(2));storage.put("coordination.agreement",agreement());
        var history=new EventHistory();history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE,"user","Show this finger pose silently"));
        var command=new JsonObject();command.addProperty("intent","ACTION");command.addProperty("kind","HAND_POSE");command.add("value",output());command.addProperty("silent",true);
        var envelope=new JsonObject();envelope.add("command",command);
        var request=DirectCommands.take(envelope);var result=DirectCommands.evaluate(request,profile,storage,history,false);
        assertEquals("ACCEPTED",result.status());assertNull(result.plan().getSpeech());assertEquals(HandActions.pose(output()),result.plan().getMotion().getAsJsonObject().get("handAction"));
        assertEquals(2,storage.get(TaskMemory.ACTIONS).getAsInt());assertEquals(agreement(),storage.get("coordination.agreement"));
        var old=AgentInteractionProfile.of(List.of(),List.of(HandActions.MODALITY),List.of("demo.valerian.coordination"));
        assertEquals("UNSUPPORTED",DirectCommands.evaluate(request,old,new Storage(),history,false).status());
        for(String finger:HandPoseObservation.FINGERS){var bad=output();bad.getAsJsonArray("hands").get(0).getAsJsonObject().getAsJsonObject("fingers").remove(finger);assertThrows(IllegalArgumentException.class,()->HandActions.pose(bad));}
        var repeated=output();repeated.getAsJsonArray("hands").get(1).getAsJsonObject().addProperty("side","left");assertThrows(IllegalArgumentException.class,()->HandActions.pose(repeated));
    }
    @Test void derivedSignsRequireExplicitSupportAndDoNotInventConfidence() {
        for(String sign:HandFeatures.DERIVED_SIGNS) {
            var raw=fixtures.behaviour.NamedHandSignFixture.observation("one",sign,"right",now);raw.getAsJsonArray("hands").get(0).getAsJsonObject().add("confidence",JsonNull.INSTANCE);
            var condition=new HandSignCondition(sign,"right");assertTrue(condition.matches(HandSignObservation.parse(raw.toString()),0));assertFalse(condition.matches(HandSignObservation.parse(raw.toString()),.1));
            var old=AgentInteractionProfile.of(List.of(Event.TYPE_HAND_SIGNS),List.of(HandActions.MODALITY),List.of("demo.valerian.coordination"));
            var event=Event.observation(Event.TYPE_HAND_SIGNS,"camera",raw.toString());assertThrows(IllegalArgumentException.class,()->HandObservationAdmission.validate(event,old,List.of(),now));
        }
    }
}
