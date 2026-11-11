package fixtures.behaviour;

import java.time.Instant;
import com.google.gson.*;
import ch.zhaw.prometheus.model.event.Event;

public final class HandPoseFixture {
    private HandPoseFixture() {}
    public static JsonObject task() {
        return JsonParser.parseString("""
                {"goal":"Tell a short dry joke on the agreed finger pattern","maxActions":3,
                "sessionBound":true,"firstAction":"ON_CUE","rules":[
                {"eventType":"obs.hand.pose","handPose":{"handedness":"any","all":[
                {"finger":"index","operator":"gt","value":0.5},
                {"finger":"ring","operator":"gt","value":0.5},
                {"finger":"little","operator":"gt","value":0.5},
                {"finger":"middle","operator":"lt","value":0.4}]},
                "action":"Tell one short dry joke","effect":"ACT","minConfidence":0,"samples":1,"cooldownSeconds":3}]}
                """).getAsJsonObject();
    }
    public static JsonObject hand(String side, double index, double middle, double ring, double little) {
        var hand = new JsonObject(); hand.addProperty("handedness", side);
        hand.addProperty("handednessConfidence", 0.99);
        var fingers = new JsonObject(); fingers.add("thumb", JsonNull.INSTANCE);
        fingers.addProperty("index", index); fingers.addProperty("middle", middle);
        fingers.addProperty("ring", ring); fingers.addProperty("little", little);
        hand.add("fingers", fingers); return hand;
    }
    public static JsonObject matchingHand() { return hand("right", .7, .3, .6, .8); }
    public static JsonObject stateTask() {
        var task = task();
        var all = task.getAsJsonArray("rules").get(0).getAsJsonObject().getAsJsonObject("handPose").getAsJsonArray("all");
        for (var item : all) {
            var comparison = item.getAsJsonObject(); comparison.addProperty("operator", "eq");
            comparison.addProperty("value", comparison.get("finger").getAsString().equals("middle") ? "straight" : "bent");
        }
        return task;
    }
    public static JsonObject classifiedHand() {
        var hand = hand("right", .7, .2, .8, .9);
        hand.add("fingerStates", JsonParser.parseString("""
                {"thumb":null,"index":"bent","middle":"straight","ring":"bent","little":"bent"}
                """));
        return hand;
    }
    public static Event pose(Instant at, JsonObject... hands) {
        var payload = new JsonObject(); payload.addProperty("schemaVersion", 1);
        payload.addProperty("source", "valerian.hand.camera"); payload.addProperty("recognizer", "mediapipe-fingers");
        payload.addProperty("ts", at.toString()); payload.addProperty("handCount", hands.length);
        var values = new JsonArray(); for (var hand : hands) values.add(hand);
        payload.add("hands", values);
        return Event.observation(Event.TYPE_HAND_POSE, "user", payload.toString());
    }
}
