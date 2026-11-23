package fixtures.behaviour;

import java.time.Instant;
import com.google.gson.*;

public final class ComposedHandFixture {
    private ComposedHandFixture() {}
    public static JsonObject hand(String id,String side,double thumb,double index,double middle,double ring,double little) {
        var hand=new JsonObject();hand.addProperty("id",id);hand.addProperty("handedness",side);
        var fingers=new JsonObject();var states=new JsonObject();var names=new String[]{"thumb","index","middle","ring","little"};
        var values=new double[]{thumb,index,middle,ring,little};
        for(int i=0;i<5;i++){fingers.addProperty(names[i],values[i]);states.addProperty(names[i],values[i]<.4?"straight":values[i]>.55?"bent":"neutral");}
        hand.add("fingers",fingers);hand.add("fingerStates",states);return hand;
    }
    public static JsonObject observation(String id,Instant time,JsonObject... hands) {
        var result=NamedHandSignFixture.observation(id,"peace","right",time);result.addProperty("schemaVersion",2);
        var array=new JsonArray();for(var hand:hands)array.add(hand.deepCopy());result.add("hands",array);return result;
    }
    public static JsonObject condition() { return JsonParser.parseString("""
        {"schemaVersion":1,"hands":[
          {"handedness":"left","all":[{"finger":"index","operator":"eq","value":"straight"}]},
          {"handedness":"right","all":[{"finger":"middle","operator":"eq","value":"bent"}]}]}
        """).getAsJsonObject(); }
    public static JsonObject agreement() {
        var proposal=NamedHandSignFixture.agreement();var check=proposal.getAsJsonArray("checks").get(0).getAsJsonObject();
        check.addProperty("eventType","obs.hand.pose");check.remove("handSign");check.add("handPose",condition());check.addProperty("minConfidence",0);
        var rule=proposal.getAsJsonObject("reactions").getAsJsonArray("rules").get(0).getAsJsonObject();
        rule.addProperty("eventType","obs.hand.pose");rule.remove("handSign");rule.add("handPose",condition());rule.addProperty("minConfidence",0);
        rule.addProperty("action","Show the agreed custom finger pose silently");return proposal;
    }
    public static JsonObject output() { return JsonParser.parseString("""
        {"kind":"pose","hands":[
          {"side":"left","fingers":{"thumb":0,"index":1,"middle":0.5,"ring":1,"little":0}},
          {"side":"right","fingers":{"thumb":1,"index":0,"middle":1,"ring":0.5,"little":1}}]}
        """).getAsJsonObject(); }
}
