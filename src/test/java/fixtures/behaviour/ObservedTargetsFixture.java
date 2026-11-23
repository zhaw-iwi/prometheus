package fixtures.behaviour;

import java.time.Instant;
import com.google.gson.*;

public final class ObservedTargetsFixture {
    private ObservedTargetsFixture(){}
    public static JsonObject target(String id,String type,double x,double y,double width,double height,Instant at){
        var t=new JsonObject();t.addProperty("id",id);t.addProperty("revision",1);t.addProperty("label",type);t.addProperty("type",type);t.addProperty("provenance","detected");t.add("frame",PointingFixture.frame());
        var p=new JsonObject();p.addProperty("x",x);p.addProperty("y",y);p.addProperty("z",0);t.add("position",p);
        var e=new JsonObject();e.addProperty("x",width);e.addProperty("y",height);e.addProperty("z",0);t.add("extent",e);
        t.addProperty("observedAt",at.toString());t.addProperty("expiresAt",at.plusSeconds(3).toString());return t;
    }
    public static JsonObject snapshot(Instant at,JsonObject... targets){
        var o=new JsonObject();o.addProperty("schemaVersion",1);o.addProperty("observationId","scene-"+at.toEpochMilli());o.addProperty("source","valerian.scene.camera");o.addProperty("ts",at.toString());o.add("frame",PointingFixture.frame());
        var a=new JsonArray();for(var t:targets)a.add(t);o.add("targets",a);return o;
    }
    public static JsonObject cup(Instant at){return snapshot(at,target("cup-1","cup",.9,.5,.1,.2,at));}
    public static JsonObject virtualRequest(int revision){
        var r=HandSceneFixture.request(revision);r.add("cameraFrame",JsonNull.INSTANCE);r.add("transform",JsonNull.INSTANCE);var t=r.getAsJsonArray("targets").get(0).getAsJsonObject();t.addProperty("type","virtual");t.getAsJsonObject("position").addProperty("x",3);return r;
    }
    public static JsonObject binding(JsonObject target,JsonObject destination){
        var r=new JsonObject();r.add("target",ch.zhaw.prometheus.model.hand.HandScene.ref(target).json());r.addProperty("label","My cup");r.add("destination",destination==null?JsonNull.INSTANCE:ch.zhaw.prometheus.model.hand.HandScene.ref(destination).json());return r;
    }
}
