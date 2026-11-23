package fixtures.behaviour;

import java.time.Instant;
import com.google.gson.*;
import ch.zhaw.prometheus.model.event.Event;

public final class HandSequenceFixture {
    private HandSequenceFixture(){}
    public static JsonObject counts(String hands,int... values){
        var sequence=new JsonObject();sequence.addProperty("schemaVersion",1);sequence.addProperty("dwellMs",300);sequence.addProperty("maximumGapMs",5000);sequence.addProperty("maximumDurationMs",120000);
        var steps=new JsonArray();for(int value:values){var step=new JsonObject();step.addProperty("kind","count");step.addProperty("count",value);step.addProperty("hands",hands);steps.add(step);}sequence.add("steps",steps);return sequence;
    }
    public static JsonObject pose(String capture,int count,boolean both,Instant at){
        var p=NamedHandSignFixture.observation(capture,"fist","right",at);p.addProperty("schemaVersion",2);p.addProperty("captureIndex",1);var hands=new JsonArray();
        String[] fingers={"thumb","index","middle","ring","little"};
        for(int side=0;side<(both?2:1);side++){
            var h=new JsonObject();h.addProperty("id",side==0?"right-track":"left-track");h.addProperty("handedness",side==0?"right":"left");var values=new JsonObject();var states=new JsonObject();
            for(int i=0;i<5;i++){boolean extended=side*5+i<count;values.addProperty(fingers[i],extended?0:1);states.addProperty(fingers[i],extended?"straight":"bent");}
            h.add("fingers",values);h.add("fingerStates",states);hands.add(h);
        }p.add("hands",hands);return p;
    }
    public static Event event(String capture,int count,boolean both,Instant at){return Event.observation(Event.TYPE_HAND_POSE,"camera",pose(capture,count,both,at).toString());}
    public static JsonObject agreement(JsonObject sequence){
        var a=NamedHandSignFixture.agreement();a.addProperty("goal","Acknowledge one completed finger-count sequence");a.getAsJsonObject("reactions").add("goal",a.get("goal"));a.addProperty("humanRole","Show the agreed counts in order and release repeated poses");a.addProperty("agentRole","Acknowledge only a complete sequence");
        var check=a.getAsJsonArray("checks").get(0).getAsJsonObject();check.remove("handSign");check.addProperty("minConfidence",0);check.addProperty("eventType","obs.hand.sequence");check.add("handSequence",sequence.deepCopy());
        var rule=a.getAsJsonObject("reactions").getAsJsonArray("rules").get(0).getAsJsonObject();rule.remove("handSign");rule.addProperty("minConfidence",0);rule.addProperty("eventType","obs.hand.sequence");rule.add("handSequence",sequence.deepCopy());rule.addProperty("action","Acknowledge the completed sequence once");return a;
    }
}
