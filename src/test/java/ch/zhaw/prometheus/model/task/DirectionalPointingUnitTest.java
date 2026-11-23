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
