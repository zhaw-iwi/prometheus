package fixtures.behaviour;

import java.time.Instant;
import com.google.gson.*;
import ch.zhaw.prometheus.model.hand.*;

public final class PointingFixture {
    private PointingFixture() {}
    public static JsonObject frame() {return new SpatialFrame("camera-test",1,"camera_image",false).json();}
    public static JsonObject observation(String id,String direction,Instant at) {
        var o=JsonParser.parseString("""
            {"schemaVersion":1,"source":"valerian.hand.camera","handId":"hand-0","handedness":"right",
             "validity":"valid","resolution":{"status":"unresolved"}}
            """).getAsJsonObject();o.addProperty("observationId",id);o.addProperty("ts",at.toString());o.add("frame",frame());
        o.addProperty("direction",direction);double angle=HandGeometry.angle(direction);
        var ray=new JsonObject();ray.add("origin",JsonParser.parseString("{\"x\":0.5,\"y\":0.5}"));
        var vector=new JsonObject();vector.addProperty("x",Math.cos(angle));vector.addProperty("y",Math.sin(angle));ray.add("vector",vector);ray.addProperty("aspectRatio",4.0/3);
        o.add("ray",ray);return o;
    }
    public static JsonObject condition() {
        var o=new JsonObject();o.addProperty("direction","right");o.addProperty("handedness","any");o.add("frame",frame());return o;
    }
    public static JsonObject agreement() {
        var a=ComposedHandFixture.agreement();a.addProperty("goal","When I point to image-right while facing the camera, point to your right once");
        a.getAsJsonObject("reactions").add("goal",a.get("goal"));
        var c=a.getAsJsonArray("checks").get(0).getAsJsonObject();c.remove("handPose");c.addProperty("eventType","obs.hand.pointing");c.add("handPoint",condition());
        var r=a.getAsJsonObject("reactions").getAsJsonArray("rules").get(0).getAsJsonObject();r.remove("handPose");r.addProperty("eventType","obs.hand.pointing");r.add("handPoint",condition());
        r.addProperty("action","Point to your right silently");return a;
    }
    public static JsonObject action(String direction) {
        var a=new JsonObject();a.addProperty("kind","point");a.addProperty("effector",direction.contains("left")?"left_hand":"right_hand");
        a.addProperty("direction",direction);a.add("frame",SpatialFrame.ROBOT.json());return a;
    }
}
