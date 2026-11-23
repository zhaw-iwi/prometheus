package ch.zhaw.prometheus.model.task;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.hand.*;
import ch.zhaw.prometheus.model.behaviour.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.agentdefs.core.CoordinationAgent;
import fixtures.behaviour.*;

class HandSceneUnitTest {
    final Instant now=Instant.parse("2026-11-23T12:00:00Z");final UUID scope=UUID.randomUUID();final Storage storage=new Storage();final EventHistory history=new EventHistory();
    HandSceneUnitTest(){history.appendEvent(Event.observation(Event.TYPE_HAND_POINTING,"camera",PointingFixture.observation("sample","right",now).toString()));}
    JsonObject configure(JsonObject request){return HandScene.configure(storage,scope,request,history,now);}
    JsonObject target(){return HandScene.get(storage).getAsJsonArray("targets").get(0).getAsJsonObject();}
    HandPointObservation point(String direction){return HandPointObservation.parse(PointingFixture.observation("point",direction,now).toString());}
    @Test void projectedIntersectionIsUniqueAmbiguousOrAbsentWithoutGuessingDepth() {
        configure(HandSceneFixture.request(0));var result=HandScene.resolve(storage,point("right"),now);assertEquals("resolved",result.get("status").getAsString());
        var agreement=HandSceneFixture.agreement(target());assertEquals(agreement,ch.zhaw.prometheus.model.coordination.CoordinationAgreement.parse(agreement).json());
        assertEquals(HandScene.ref(target()).json(),result.get("target"));assertEquals("unresolved",HandScene.resolve(storage,point("left"),now).get("status").getAsString());
        var request=HandSceneFixture.request(1);var overlapping=request.getAsJsonArray("targets").get(0).deepCopy().getAsJsonObject();overlapping.addProperty("key","doorway");request.getAsJsonArray("targets").add(overlapping);configure(request);
        result=HandScene.resolve(storage,point("right"),now);assertEquals("ambiguous",result.get("status").getAsString());assertEquals(2,result.getAsJsonArray("candidates").size());
        var sample=PointingFixture.observation("ambiguous","right",now);sample.add("resolution",result);
        assertFalse(HandPointCondition.parse(HandSceneFixture.condition(target())).matches(HandPointObservation.parse(sample.toString())));
    }
    @Test void scopedReferencesCannotSurviveRecalibrationDeletionExpiryOrAnotherAgent() {
        var request=HandSceneFixture.request(0);request.getAsJsonArray("targets").get(0).getAsJsonObject().addProperty("expiresAt",now.plusSeconds(2).toString());configure(request);var ref=HandScene.ref(target());
        assertTrue(HandScene.current(storage,ref,now));assertFalse(HandScene.current(storage,ref,now.plusSeconds(2)));
        var other=new Storage();HandScene.configure(other,UUID.randomUUID(),HandSceneFixture.request(0),history,now);assertFalse(HandScene.current(other,ref,now));
        configure(HandSceneFixture.request(1));assertFalse(HandScene.current(storage,ref,now));assertThrows(IllegalArgumentException.class,()->configure(HandSceneFixture.request(1)));
        var deleted=HandSceneFixture.request(2);deleted.add("targets",new JsonArray());configure(deleted);assertFalse(HandScene.current(storage,ref,now));
        configure(HandSceneFixture.request(3));assertFalse(HandScene.current(storage,ref,now));
    }
    @Test void calibrationRequiresObservedFrameNondegenerateBoundedMappingAndValidRegions() {
        for(String malformed:List.of("null","[]","broken","x".repeat(32769)))assertThrows(IllegalArgumentException.class,()->HandScene.parseRequest(malformed));
        var invalid=HandSceneFixture.request(0);invalid.add("transform",JsonParser.parseString("[0,0,0,0,0,0]"));assertThrows(IllegalArgumentException.class,()->configure(invalid));
        invalid.add("transform",JsonParser.parseString("[8,0,4,0,-3,3]"));assertThrows(IllegalArgumentException.class,()->configure(invalid));
        invalid.add("transform",JsonNull.INSTANCE);invalid.getAsJsonObject("cameraFrame").addProperty("revision",2);assertThrows(IllegalArgumentException.class,()->configure(invalid));
        invalid.getAsJsonObject("cameraFrame").addProperty("revision",1);invalid.getAsJsonArray("targets").get(0).getAsJsonObject().getAsJsonObject("extent").addProperty("x",.5);assertThrows(IllegalArgumentException.class,()->configure(invalid));
        assertThrows(IllegalArgumentException.class,()->HandScene.configure(storage,scope,HandSceneFixture.request(0),history,now.plusSeconds(16)));
    }
    @Test void dispatchUsesTheCurrentMappingAndNeverTrustsAnEarlierResolvedSnapshot() {
        configure(HandSceneFixture.request(0));var action=HandSceneFixture.action(target());
        var raw=new JsonObject();var motion=new JsonObject();motion.add("handAction",HandActions.pose(action));raw.add("motion",motion);
        var plan=HandScene.ground(BehaviourPlan.fromJson(raw.toString()),storage,history,now);var grounded=plan.getMotion().getAsJsonObject().getAsJsonObject("handAction").getAsJsonObject("action");
        assertEquals(3.2,grounded.getAsJsonObject("resolved").getAsJsonObject("position").get("x").getAsDouble(),.0001);
        grounded.getAsJsonObject("resolved").getAsJsonObject("position").addProperty("x",-3);
        assertEquals(3.2,HandScene.ground(plan,storage,history,now).getMotion().getAsJsonObject().getAsJsonObject("handAction").getAsJsonObject("action").getAsJsonObject("resolved").getAsJsonObject("position").get("x").getAsDouble(),.0001);
        assertThrows(IllegalArgumentException.class,()->HandScene.ground(plan,storage,history,now.plusSeconds(16)));
        configure(HandSceneFixture.request(1));assertThrows(IllegalArgumentException.class,()->HandScene.ground(plan,storage,history,now));
    }
    @Test void regionNeedsMappingForOutputButVirtualDestinationsDoNotNeedACamera() {
        var request=HandSceneFixture.request(0);request.add("transform",JsonNull.INSTANCE);configure(request);
        var action=HandSceneFixture.action(target());var plan=BehaviourPlan.fromJson("{\"motion\":{\"handAction\":"+HandActions.pose(action)+"}}");
        assertThrows(IllegalArgumentException.class,()->HandScene.ground(plan,storage,history,now));
        request=HandSceneFixture.request(1);request.add("cameraFrame",JsonNull.INSTANCE);request.add("transform",JsonNull.INSTANCE);var t=request.getAsJsonArray("targets").get(0).getAsJsonObject();t.addProperty("type","virtual");t.getAsJsonObject("position").addProperty("x",3);configure(request);
        var virtual=BehaviourPlan.fromJson("{\"motion\":{\"handAction\":"+HandActions.pose(HandSceneFixture.action(target()))+"}}");
        assertDoesNotThrow(()->HandScene.ground(virtual,storage,new EventHistory(),now.plusSeconds(100)));
        assertThrows(IllegalArgumentException.class,()->HandPointCondition.parse(HandSceneFixture.condition(target())));
        var close=HandSceneFixture.request(2);close.add("cameraFrame",JsonNull.INSTANCE);close.add("transform",JsonNull.INSTANCE);close.getAsJsonArray("targets").get(0).getAsJsonObject().addProperty("type","virtual");configure(close);
        var near=BehaviourPlan.fromJson("{\"motion\":{\"handAction\":"+HandActions.pose(HandSceneFixture.action(target()))+"}}");
        assertThrows(IllegalArgumentException.class,()->HandScene.ground(near,storage,history,now));
    }
    @Test void backendPromptCarriesCurrentPointFrameForNegotiationWithoutWideningGenericCapabilities() {
        var profile=new CoordinationAgent().createAgent().getInteractionProfile();
        var messages=TaskMemory.messages(history,"Configure pointing",new PromptMessageAssembler().forCapabilities(profile),now);
        assertTrue(messages.stream().anyMatch(m->m.getContent().contains("camera-test")&&m.getContent().contains("obs.hand.pointing")));
        assertFalse(TaskMemory.messages(history,"Generic",new PromptMessageAssembler(),now).stream().anyMatch(m->m.getContent().contains("camera-test")));
    }
}
