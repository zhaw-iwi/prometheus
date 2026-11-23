package fixtures.behaviour;

import com.google.gson.*;
import ch.zhaw.prometheus.model.hand.HandGeometry;

public final class RelationalHandFixture {
    private RelationalHandFixture() {}
    public static JsonObject geometry() {
        var g=new JsonObject();g.addProperty("schemaVersion",1);var tips=new JsonObject();var spreads=new JsonObject();var axes=new JsonObject();
        for(String pair:HandGeometry.PAIRS){tips.add(pair,JsonNull.INSTANCE);spreads.add(pair,JsonNull.INSTANCE);}
        for(String axis:HandGeometry.AXES)axes.add(axis,JsonNull.INSTANCE);
        tips.addProperty("thumb_index",.1);spreads.addProperty("index_middle",.2);axes.add("index_axis",JsonParser.parseString("{\"x\":1,\"y\":0}"));
        g.add("tipDistances",tips);g.add("spreads",spreads);g.add("axes",axes);return g;
    }
    public static JsonArray relations() {return JsonParser.parseString("""
        [{"kind":"spread","fingers":["index","middle"],"operator":"gt","value":0.1},
         {"kind":"orientation","axis":"index_axis","direction":"right","toleranceDegrees":10}]
        """).getAsJsonArray();}
    public static JsonObject condition() {
        var condition=ComposedHandFixture.condition();condition.getAsJsonArray("hands").get(0).getAsJsonObject().add("relations",relations());return condition;
    }
    public static JsonObject agreement() {
        var agreement=ComposedHandFixture.agreement();agreement.getAsJsonArray("checks").get(0).getAsJsonObject().add("handPose",condition());
        var rule=agreement.getAsJsonObject("reactions").getAsJsonArray("rules").get(0).getAsJsonObject();rule.add("handPose",condition());
        rule.addProperty("action","Show an OK ring silently with both hands");return agreement;
    }
    public static JsonObject output() {
        var action=JsonParser.parseString("""
            {"kind":"pose","hands":[{"side":"left","fingers":{"thumb":1,"index":0,"middle":0,"ring":1,"little":1}}]}
            """).getAsJsonObject();action.getAsJsonArray("hands").get(0).getAsJsonObject().add("relations",relations());return action;
    }
}
