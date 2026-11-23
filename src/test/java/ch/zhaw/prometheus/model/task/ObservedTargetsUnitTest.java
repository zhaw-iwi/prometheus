package ch.zhaw.prometheus.model.task;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.hand.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.*;
import fixtures.behaviour.*;

class ObservedTargetsUnitTest {
    final Instant now=Instant.parse("2026-11-23T12:00:00Z");final UUID scope=UUID.randomUUID();final Storage storage=new Storage();
    boolean admit(JsonObject o,Instant at){return ObservedTargets.admit(storage,scope,Event.observation(Event.TYPE_SCENE_TARGETS,"camera",o.toString()),at);}
    JsonObject target(Instant at){return ObservedTargets.fresh(storage,at).getFirst();}
    HandPointObservation point(Instant at,boolean origin){var p=PointingFixture.observation("point","right",at);if(origin)p.add("handOrigin",JsonParser.parseString("{\"x\":0.4,\"y\":0.5}"));return HandPointObservation.parse(p.toString());}
    BehaviourPlan plan(JsonObject target){return BehaviourPlan.fromJson("{\"motion\":{\"handAction\":"+HandActions.pose(HandSceneFixture.action(target))+"}}");}
    @Test void undeclaredProfilesCannotAdmitOrBindDynamicTargets(){
        var old=new ch.zhaw.prometheus.agentdefs.core.GenericMultimodalBehaviourAtSocialSpace().createAgent();
        var event=Event.observation(Event.TYPE_SCENE_TARGETS,"camera",ObservedTargetsFixture.cup(now).toString());
        assertThrows(IllegalArgumentException.class,()->old.admitSceneTargets(event,now));
        assertThrows(IllegalArgumentException.class,()->old.bindHandTarget(new JsonObject(),now));
    }
    @Test void retriesOrderingBoundsAndClassAdmissionAreStrictAndAtomic(){
        var good=ObservedTargetsFixture.cup(now);assertTrue(admit(good,now));var saved=ObservedTargets.snapshot(storage);assertFalse(admit(good,now));
        var changed=good.deepCopy();changed.getAsJsonArray("targets").get(0).getAsJsonObject().getAsJsonObject("position").addProperty("x",.8);assertThrows(IllegalArgumentException.class,()->admit(changed,now));
        for(var at:List.of(now.minusSeconds(1),now.plusMillis(100),now.minusSeconds(3),now.plusSeconds(2)))assertThrows(IllegalArgumentException.class,()->admit(ObservedTargetsFixture.cup(at),now));
        var next=now.plusMillis(500);
        for(String type:List.of("dog","region","virtual"))assertThrows(IllegalArgumentException.class,()->admit(ObservedTargetsFixture.snapshot(next,ObservedTargetsFixture.target("x",type,.5,.5,.1,.1,next)),next));
        var invalid=ObservedTargetsFixture.cup(next);invalid.getAsJsonArray("targets").get(0).getAsJsonObject().getAsJsonObject("extent").addProperty("x",.5);assertThrows(IllegalArgumentException.class,()->admit(invalid,next));
        var duplicate=ObservedTargetsFixture.cup(next);duplicate.getAsJsonArray("targets").add(duplicate.getAsJsonArray("targets").get(0).deepCopy());assertThrows(IllegalArgumentException.class,()->admit(duplicate,next));assertEquals(saved,ObservedTargets.snapshot(storage));
    }
    @Test void labelsNeverTransferAcrossDisappearanceFrameChangeExpiryOrAnotherScope(){
        admit(ObservedTargetsFixture.cup(now),now);var original=target(now);var ref=HandScene.ref(original);ObservedTargets.bind(storage,ObservedTargetsFixture.binding(original,null),now);
        var next=now.plusMillis(500);admit(ObservedTargetsFixture.cup(next),next);assertEquals(ref,HandScene.ref(target(next)));assertEquals("My cup",ObservedTargets.binding(storage,ref,next).get("label").getAsString());
        admit(ObservedTargetsFixture.snapshot(now.plusSeconds(1)),now.plusSeconds(1));admit(ObservedTargetsFixture.cup(now.plusMillis(1500)),now.plusMillis(1500));var reused=HandScene.ref(target(now.plusMillis(1500)));assertNotEquals(ref,reused);assertThrows(IllegalArgumentException.class,()->ObservedTargets.binding(storage,reused,now.plusMillis(1500)));
        var frame=ObservedTargetsFixture.cup(now.plusSeconds(2));frame.getAsJsonObject("frame").addProperty("revision",2);frame.getAsJsonArray("targets").get(0).getAsJsonObject().add("frame",frame.get("frame").deepCopy());admit(frame,now.plusSeconds(2));assertNotEquals(reused,HandScene.ref(target(now.plusSeconds(2))));
        assertTrue(ObservedTargets.fresh(storage,now.plusSeconds(5)).isEmpty());assertThrows(IllegalArgumentException.class,()->ObservedTargets.require(new Storage(),ref,now));
        var other=new Storage();ObservedTargets.admit(other,UUID.randomUUID(),Event.observation(Event.TYPE_SCENE_TARGETS,"camera",ObservedTargetsFixture.cup(now).toString()),now);assertNotEquals(ref,HandScene.ref(ObservedTargets.fresh(other,now).getFirst()));
    }
    @Test void sourceExclusionRequiresOneFreshOverlappingPersonAndNeverEstablishesIdentity(){
        var person=ObservedTargetsFixture.target("person-1","person",.5,.5,.4,.8,now);var cup=ObservedTargetsFixture.target("cup-1","cup",.9,.5,.1,.2,now);admit(ObservedTargetsFixture.snapshot(now,person,cup),now);
        assertEquals("unresolved",HandScene.resolve(storage,point(now,false),now).get("status").getAsString());assertEquals("source_unknown",HandScene.resolve(storage,point(now,false),now).get("reason").getAsString());
        assertEquals("projected",ObservedTargets.source(storage,point(now,true),now).get("status").getAsString());var result=HandScene.resolve(storage,point(now,true),now);assertEquals("resolved",result.get("status").getAsString());assertTrue(result.getAsJsonObject("target").get("id").getAsString().endsWith(":cup-1"));
        assertEquals("unresolved",ObservedTargets.source(storage,point(now.plusMillis(1501),true),now.plusMillis(1501)).get("status").getAsString());assertEquals("unresolved",HandScene.resolve(storage,point(now.plusMillis(1501),true),now.plusMillis(1501)).get("status").getAsString());
        var later=now.plusMillis(500);var two=ObservedTargetsFixture.snapshot(later,ObservedTargetsFixture.target("p1","person",.5,.5,.4,.8,later),ObservedTargetsFixture.target("p2","person",.5,.5,.4,.8,later));admit(two,later);assertEquals("unresolved",ObservedTargets.source(storage,point(later,true),later).get("status").getAsString());assertEquals("unresolved",HandScene.resolve(storage,point(later,true),later).get("status").getAsString());
    }
    @Test void pairingRejectsFutureAndChangedFramesAndAlignedObjectsRemainAmbiguous(){
        admit(ObservedTargetsFixture.cup(now),now);assertTrue(ObservedTargets.near(storage,point(now.minusMillis(1),false),now).isEmpty());
        var p=PointingFixture.observation("changed","right",now);p.getAsJsonObject("frame").addProperty("revision",2);assertTrue(ObservedTargets.near(storage,HandPointObservation.parse(p.toString()),now).isEmpty());
        var later=now.plusMillis(500);admit(ObservedTargetsFixture.snapshot(later,ObservedTargetsFixture.target("source","person",.4,.5,.2,.8,later),ObservedTargetsFixture.target("c1","cup",.7,.5,.1,.1,later),ObservedTargetsFixture.target("c2","cup",.9,.5,.1,.1,later)),later);assertEquals("ambiguous",HandScene.resolve(storage,point(later,true),later).get("status").getAsString());
    }
    @Test void dispatchRechecksExpiryAndExplicitCounterpartWithoutTrustingOldCoordinates(){
        admit(ObservedTargetsFixture.cup(now),now);var cup=target(now);var plan=plan(cup);var history=new EventHistory();assertThrows(IllegalArgumentException.class,()->HandScene.ground(plan,storage,history,now));
        var scene=HandScene.configure(storage,scope,ObservedTargetsFixture.virtualRequest(0),history,now);ObservedTargets.bind(storage,ObservedTargetsFixture.binding(cup,scene.getAsJsonArray("targets").get(0).getAsJsonObject()),now);
        var grounded=HandScene.ground(plan,storage,history,now);assertEquals(3,grounded.getMotion().getAsJsonObject().getAsJsonObject("handAction").getAsJsonObject("action").getAsJsonObject("resolved").getAsJsonObject("position").get("x").getAsDouble());
        assertThrows(IllegalArgumentException.class,()->HandScene.ground(grounded,storage,history,now.plusSeconds(3)));
        HandScene.configure(storage,scope,ObservedTargetsFixture.virtualRequest(1),history,now);assertThrows(IllegalArgumentException.class,()->HandScene.ground(grounded,storage,history,now));
    }
}
