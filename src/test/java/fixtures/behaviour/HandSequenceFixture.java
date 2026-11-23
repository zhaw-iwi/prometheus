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
    /** Near-capacity contract: 12 steps, both hands, all fingers, three checks and maximal UTF-8 terms. */
    public static JsonObject largeAgreement(){
        var sequence=counts("both",0,1,2,3,4,5,6,7,8,9,10,0);
        var steps=new JsonArray();
        for(int i=0;i<12;i++) {var step=new JsonObject();step.addProperty("kind","pose");
            var pose=ComposedHandFixture.condition();
            for(var hand:pose.getAsJsonArray("hands")){var all=new JsonArray();
                for(String finger:java.util.List.of("thumb","index","middle","ring","little")){var p=new JsonObject();p.addProperty("finger",finger);p.addProperty("operator","eq");p.addProperty("value",i%2==0?"straight":"bent");all.add(p);}
                hand.getAsJsonObject().add("all",all);
            }step.add("condition",pose);steps.add(step);
        }
        sequence.add("steps",steps);
        var proposal=agreement(sequence);
        for(String field:java.util.List.of("goal","humanRole","agentRole","success"))proposal.addProperty(field,"界".repeat(300));
        proposal.getAsJsonObject("reactions").add("goal",proposal.get("goal"));
        var check=proposal.getAsJsonArray("checks").get(0).getAsJsonObject();
        for(int i=1;i<3;i++){var next=check.deepCopy();next.getAsJsonObject("handSequence").addProperty("dwellMs",300+i);if(i==2)while(next.getAsJsonObject("handSequence").getAsJsonArray("steps").size()>6)next.getAsJsonObject("handSequence").getAsJsonArray("steps").remove(6);proposal.getAsJsonArray("checks").add(next);}
        return proposal;
    }

}
