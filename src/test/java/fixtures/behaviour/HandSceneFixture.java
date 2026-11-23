package fixtures.behaviour;

import com.google.gson.*;
import ch.zhaw.prometheus.model.hand.HandScene;

public final class HandSceneFixture {
    private HandSceneFixture(){}
    public static JsonObject request(int revision) {
        var o=JsonParser.parseString("""
            {"schemaVersion":1,"expectedRevision":0,"cameraFrame":null,"transform":[8,0,-4,0,-3,3],"targets":[
              {"key":"display","label":"Display","type":"region","position":{"x":0.9,"y":0.5,"z":0},"extent":{"x":0.1,"y":0.3,"z":0},"expiresAt":null}]}
            """).getAsJsonObject();o.addProperty("expectedRevision",revision);o.add("cameraFrame",PointingFixture.frame());return o;
    }
    public static JsonObject action(JsonObject target) {
        var a=new JsonObject();a.addProperty("kind","point");a.addProperty("effector","left_hand");a.add("target",HandScene.ref(target).json());return a;
    }
    public static JsonObject condition(JsonObject target) {var c=new JsonObject();c.add("target",HandScene.ref(target).json());c.addProperty("handedness","any");return c;}
    public static JsonObject agreement(JsonObject target) {
        var a=PointingFixture.agreement();a.addProperty("goal","Explain the display when I point at its registered region");a.getAsJsonObject("reactions").add("goal",a.get("goal"));a.addProperty("humanRole","Point at the display after accepting");a.addProperty("agentRole","Explain the display once");a.getAsJsonArray("checks").get(0).getAsJsonObject().add("handPoint",condition(target));
        var rule=a.getAsJsonObject("reactions").getAsJsonArray("rules").get(0).getAsJsonObject();rule.add("handPoint",condition(target));rule.addProperty("action","Explain the display in one sentence");return a;
    }
}
