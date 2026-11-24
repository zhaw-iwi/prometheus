package ch.zhaw.prometheus.model.task;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.DirectCommands;
import ch.zhaw.prometheus.model.coordination.CoordinationAgreement;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.hand.*;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import fixtures.behaviour.PointingFixture;

class DirectionalPointingUnitTest {
    @Test void freshSceneCaptureEstablishesFrameAfterSlowInferenceWithoutBecomingACue() {
        var now=Instant.parse("2026-11-24T12:00:00Z");var history=new EventHistory();var storage=new Storage();
        for(String feature:List.of(HandFeatures.POINTING,HandFeatures.TARGETS,HandFeatures.DYNAMIC_TARGETS))storage.put(feature,new JsonPrimitive(true));
        var old=event(PointingFixture.observation("before-inference","right",now.minusSeconds(24)));history.appendEvent(old);
        var agreement=CoordinationAgreement.parse(PointingFixture.agreement());var condition=agreement.checks().getFirst().handPoint();
        assertThrows(IllegalArgumentException.class,()->condition.requireCurrentFrame(history,now,storage));
        var scene=fixtures.behaviour.ObservedTargetsFixture.snapshot(now);
        scene.add("targets",new JsonArray());
        ObservedTargets.admit(storage,java.util.UUID.randomUUID(),Event.observation(Event.TYPE_SCENE_TARGETS,"camera",scene.toString()),now);
        assertDoesNotThrow(()->condition.requireCurrentFrame(history,now,storage));
        assertDoesNotThrow(()->condition.requireCurrentFrame(new EventHistory(),now,storage),"Empty scene capture establishes coordinates without a pointing cue");
        var simultaneous=new EventHistory();var conflict=PointingFixture.observation("conflicting-camera","right",now);conflict.getAsJsonObject("frame").addProperty("revision",2);simultaneous.appendEvent(event(conflict));
        assertThrows(IllegalArgumentException.class,()->condition.requireCurrentFrame(simultaneous,now,storage),"Equal-time conflicting frames must reject");
        assertFalse(TaskDecision.matchesObservation(agreement.checks().getFirst().rule(),old,now),"A fresh frame never refreshes an old pointing cue");
        assertThrows(IllegalArgumentException.class,()->condition.requireCurrentFrame(history,now.plusSeconds(3),storage));
        storage.put(HandFeatures.DYNAMIC_TARGETS,new JsonPrimitive(false));
        assertThrows(IllegalArgumentException.class,()->condition.requireCurrentFrame(history,now,storage));
        storage.put(HandFeatures.DYNAMIC_TARGETS,new JsonPrimitive(true));
        var changed=PointingFixture.observation("new-camera","right",now.plusMillis(500));changed.getAsJsonObject("frame").addProperty("revision",2);history.appendEvent(event(changed));
        assertThrows(IllegalArgumentException.class,()->condition.requireCurrentFrame(history,now.plusMillis(500),storage),"Older scene cannot mask a newer pointing frame");
        var next=fixtures.behaviour.ObservedTargetsFixture.snapshot(now.plusSeconds(1));next.getAsJsonObject("frame").addProperty("revision",2);
        ObservedTargets.admit(storage,java.util.UUID.randomUUID(),Event.observation(Event.TYPE_SCENE_TARGETS,"camera",next.toString()),now.plusSeconds(1));
        assertThrows(IllegalArgumentException.class,()->condition.requireCurrentFrame(history,now.plusSeconds(1),storage));
    }
    AgentInteractionProfile profile(boolean point) {return AgentInteractionProfile.of(List.of(Event.TYPE_HAND_POINTING),List.of(HandActions.MODALITY),
            point?List.of("demo.valerian.coordination",HandFeatures.POINTING):List.of("demo.valerian.coordination"));}
    Event event(JsonObject o) {return Event.observation(Event.TYPE_HAND_POINTING,"camera",o.toString());}
    @Test void directionRequiresConsistentRayAndExactUnmirroredFrame() {
        var condition=HandPointCondition.parse(PointingFixture.condition());
        for(var direction:List.of("left","right","up","down","up_left","up_right","down_left","down_right")) {
            var data=PointingFixture.observation("one",direction,Instant.now());var observed=HandPointObservation.parse(data.toString());
            assertEquals(direction.equals("right"),condition.matches(observed));
            data.getAsJsonObject("frame").addProperty("revision",2);assertFalse(condition.matches(HandPointObservation.parse(data.toString())));
            data.getAsJsonObject("frame").addProperty("mirrored",true);assertThrows(IllegalArgumentException.class,()->HandPointObservation.parse(data.toString()));
        }
        var bad=PointingFixture.observation("bad","right",Instant.now());bad.addProperty("direction","left");
        assertThrows(IllegalArgumentException.class,()->HandPointObservation.parse(bad.toString()));
        bad.addProperty("validity","unavailable");assertThrows(IllegalArgumentException.class,()->HandPointObservation.parse(bad.toString()));
        bad.add("direction",JsonNull.INSTANCE);bad.add("ray",JsonNull.INSTANCE);assertFalse(condition.matches(HandPointObservation.parse(bad.toString())));
    }
    @Test void negotiationRequiresCurrentFrameAndPersistsTypedPreparationWithoutChangingLegacyTerms() {
        var a=CoordinationAgreement.parse(PointingFixture.agreement());assertEquals(PointingFixture.agreement(),a.json());
        var history=new EventHistory();var now=Instant.now();var point=a.checks().getFirst().handPoint();
        assertThrows(IllegalArgumentException.class,()->point.requireCurrentFrame(history,now));
        history.appendEvent(event(PointingFixture.observation("old","right",now.minusSeconds(20))));
        assertThrows(IllegalArgumentException.class,()->point.requireCurrentFrame(history,now));
        var fresh=event(PointingFixture.observation("fresh","right",now));history.appendEvent(fresh);
        assertDoesNotThrow(()->point.requireCurrentFrame(history,now));assertTrue(TaskDecision.matchesObservation(a.checks().getFirst().rule(),fresh,now));
        assertFalse(TaskDecision.matchesObservation(a.checks().getFirst().rule(),event(PointingFixture.observation("wrong","left",now)),now));
        var changed=PointingFixture.observation("changed","right",now);changed.getAsJsonObject("frame").addProperty("revision",2);history.appendEvent(event(changed));
        assertThrows(IllegalArgumentException.class,()->point.requireCurrentFrame(history,now));
    }
    @Test void pointingSharesConsumedCaptureIdentityAndRejectsUndeclaredIngress() {
        var now=Instant.now();var event=event(PointingFixture.observation("capture","right",now));var memory=new Storage();
        assertFalse(HandFeatures.pointing(memory));assertFalse(HandFeatures.geometry(memory));
        memory.put(HandFeatures.POSES,new JsonPrimitive(true));assertFalse(HandFeatures.geometry(memory));
        memory.put(HandEvidence.ENABLED,new JsonPrimitive(true));assertTrue(HandEvidence.consumed(memory,event));
        memory.put(HandFeatures.POINTING,new JsonPrimitive(true));assertFalse(HandEvidence.consumed(memory,event));
        assertThrows(IllegalArgumentException.class,()->HandObservationAdmission.validate(event,profile(false),List.of(),now));
        HandObservationAdmission.validate(event,profile(true),List.of(),now);HandEvidence.consume(memory,event);assertTrue(HandEvidence.consumed(memory,event));
        var sign=fixtures.behaviour.NamedHandSignFixture.observation("capture","index_up","right",now);
        sign.addProperty("source","valerian.hand.camera");
        assertTrue(HandEvidence.consumed(memory,Event.observation(Event.TYPE_HAND_SIGNS,"camera",sign.toString())));
    }
    @Test void directPointingIsSilentCapabilityGatedTaskNeutralAndBoundedToTheRobotFrame() {
        var input=JsonParser.parseString("{\"command\":{\"intent\":\"ACTION\",\"kind\":\"POINT\",\"silent\":true}}").getAsJsonObject();
        input.getAsJsonObject("command").add("value",PointingFixture.action("left"));var request=DirectCommands.take(input);
        var history=new EventHistory();history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE,"user","Point left silently"));
        var storage=new Storage();storage.put(TaskMemory.ACTIONS,new JsonPrimitive(2));
        assertEquals("UNSUPPORTED",DirectCommands.evaluate(request,profile(false),new Storage(),history,false).status());
        var result=DirectCommands.evaluate(request,profile(true),storage,history,false);assertEquals("ACCEPTED",result.status());assertNull(result.plan().getSpeech());
        assertEquals(2,storage.get(TaskMemory.ACTIONS).getAsInt());assertDoesNotThrow(()->TaskMemory.plan(result.plan().toJsonObject(),profile(true)));
        assertThrows(IllegalArgumentException.class,()->TaskMemory.plan(result.plan().toJsonObject(),profile(false)));
        assertEquals("DUPLICATE",DirectCommands.evaluate(request,profile(true),storage,history,false).status());
        var invalid=PointingFixture.action("left");invalid.getAsJsonObject("frame").addProperty("space","camera_image");assertThrows(IllegalArgumentException.class,()->HandActions.pose(invalid));
        invalid.add("frame",SpatialFrame.ROBOT.json());invalid.addProperty("direction","forward");assertThrows(IllegalArgumentException.class,()->HandActions.pose(invalid));
    }
}
