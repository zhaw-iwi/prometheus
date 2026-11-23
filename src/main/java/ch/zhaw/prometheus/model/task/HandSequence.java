package ch.zhaw.prometheus.model.task;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.hand.*;

/** A finite input contract. Timing and progress belong to HandSequenceMemory. */
public record HandSequence(List<Step> steps, int dwellMs, int maximumGapMs, int maximumDurationMs) {
    public static final String RULE_TYPE="obs.hand.sequence";
    public enum Result { IGNORE, UNKNOWN, MATCH, MISMATCH }
    public record Reading(Result result, Map<String,String> hands) {
        static Reading of(Result result){return new Reading(result,Map.of());}
    }
    public record Step(String kind, JsonObject json, HandSignCondition sign, HandPoseCondition pose,
            HandPointCondition point, Integer count, String hands) {
        public String eventType(){return switch(kind){case "sign"->Event.TYPE_HAND_SIGNS;case "point"->Event.TYPE_HAND_POINTING;default->Event.TYPE_HAND_POSE;};}
        public String description(){return switch(kind){case "sign"->sign.description();case "point"->point.description();case "pose"->pose.description();default->count+" straight fingers on "+hands+" hand(s)";};}
        public Reading read(Event event,Storage storage,Instant now){
            if(!eventType().equals(event.getType()))return Reading.of(Result.IGNORE);
            try {
                var raw=JsonParser.parseString(event.getPayload()).getAsJsonObject();
                if(kind.equals("point")){
                    var observation=HandPointObservation.parse(event.getPayload());
                    if(!"valid".equals(observation.validity())||"unknown".equals(observation.handedness())
                            ||!point.current(storage,now)||!point.frame().equals(observation.frame()))return Reading.of(Result.UNKNOWN);
                    if(point.target()!=null&&!"resolved".equals(observation.resolution().get("status").getAsString()))return Reading.of(Result.UNKNOWN);
                    return new Reading(point.matches(observation)?Result.MATCH:Result.MISMATCH,Map.of(observation.handedness(),observation.handId()));
                }
                JsonArray observed;
                if(kind.equals("sign")){HandSignObservation.parse(event.getPayload());observed=raw.getAsJsonArray("hands");}
                else observed=HandPoseObservation.parse(event.getPayload()).frame().hands();
                var available=observed.asList().stream().map(JsonElement::getAsJsonObject).toList();
                if(available.stream().anyMatch(h->"unknown".equals(h.get("handedness").getAsString())))return Reading.of(Result.UNKNOWN);
                if(available.stream().map(h->h.get("handedness").getAsString()).distinct().count()!=available.size())return Reading.of(Result.UNKNOWN);
                List<JsonObject> selected;boolean matches;
                if(kind.equals("count")){
                    selected=select(available,hands);
                    if(selected.isEmpty()||!knownFingers(selected))return Reading.of(Result.UNKNOWN);
                    long total=selected.stream().flatMap(h->h.getAsJsonObject("fingerStates").asMap().values().stream()).filter(v->"straight".equals(v.getAsString())).count();
                    if(selected.stream().anyMatch(h->h.getAsJsonObject("fingerStates").asMap().values().stream().anyMatch(v->"neutral".equals(v.getAsString()))))return Reading.of(Result.UNKNOWN);
                    matches=total==count;
                }else if(kind.equals("sign")){
                    selected=select(available,sign.handedness());
                    if(selected.isEmpty())return Reading.of(Result.UNKNOWN);
                    var h=selected.getFirst();if(h.get("sign").isJsonNull())return Reading.of(Result.UNKNOWN);
                    var one=raw.deepCopy();var a=new JsonArray();a.add(h);one.add("hands",a);
                    matches=sign.matches(HandSignObservation.parse(one.toString()),0);
                }else{
                    // One or two known sides; ambiguous arbitrary assignment never binds a source.
                    if(pose.hands().size()==2){if(available.size()!=2)return Reading.of(Result.UNKNOWN);selected=available;}
                    else selected=select(available,pose.hands().getFirst().handedness());
                    if(selected.isEmpty())return Reading.of(Result.UNKNOWN);
                    var one=raw.deepCopy();var a=new JsonArray();selected.forEach(a::add);one.add("hands",a);matches=pose.matches(one);
                    if(!matches&&(!knownFingers(selected)||pose.relational()&&selected.stream().anyMatch(h->!h.has("geometry")||containsNull(h.get("geometry")))))return Reading.of(Result.UNKNOWN);
                }
                var association=new TreeMap<String,String>();selected.forEach(h->association.put(h.get("handedness").getAsString(),h.get("id").getAsString()));
                return new Reading(matches?Result.MATCH:Result.MISMATCH,Map.copyOf(association));
            }catch(RuntimeException invalid){return Reading.of(Result.UNKNOWN);}
        }
        private static List<JsonObject> select(List<JsonObject> available,String side){
            if(side.equals("both"))return available.size()==2?available:List.of();
            var selected=available.stream().filter(h->side.equals("any")||side.equals("either")||side.equals(h.get("handedness").getAsString())).toList();
            return selected.size()==1?selected:List.of();
        }
        private static boolean knownFingers(List<JsonObject> hands){return hands.stream().allMatch(h->!containsNull(h.get("fingers"))&&!containsNull(h.get("fingerStates")));}
        private static boolean containsNull(JsonElement value){
            if(value==null||value.isJsonNull())return true;
            if(value.isJsonObject())return value.getAsJsonObject().asMap().values().stream().anyMatch(Step::containsNull);
            if(value.isJsonArray())return value.getAsJsonArray().asList().stream().anyMatch(Step::containsNull);
            return false;
        }
    }
    public HandSequence {steps=List.copyOf(steps);}
    public static HandSequence parse(JsonElement raw){
        try {
            if(raw==null||raw.toString().getBytes(StandardCharsets.UTF_8).length>8192)throw new IllegalArgumentException();
            var o=raw.getAsJsonObject();HandSignObservation.exact(o,"schemaVersion","steps","dwellMs","maximumGapMs","maximumDurationMs");HandSignObservation.integer(o.get("schemaVersion"),1,1);
            int dwell=number(o,"dwellMs",300,5000),gap=number(o,"maximumGapMs",300,30000),duration=number(o,"maximumDurationMs",300,120000);
            var array=o.getAsJsonArray("steps");if(array.isEmpty()||array.size()>12||gap<dwell||duration<(long)array.size()*dwell)throw new IllegalArgumentException();
            var steps=new ArrayList<Step>();
            for(var item:array){
                var s=item.getAsJsonObject();String kind=HandSignObservation.text(s.get("kind"));HandSignCondition sign=null;HandPoseCondition pose=null;HandPointCondition point=null;Integer count=null;String hands=null;
                if(kind.equals("count")){
                    HandSignObservation.exact(s,"kind","count","hands");count=number(s,"count",0,10);hands=HandSignObservation.text(s.get("hands"));
                    if(!Set.of("either","left","right","both").contains(hands)||count>5&&!hands.equals("both"))throw new IllegalArgumentException();
                }else{
                    HandSignObservation.exact(s,"kind","condition");
                    switch(kind){case "sign"->sign=HandSignCondition.parse(s.get("condition"));case "point"->point=HandPointCondition.parse(s.get("condition"));case "pose"->{pose=HandPoseCondition.parse(s.get("condition"));if(!pose.versioned())throw new IllegalArgumentException();}default->throw new IllegalArgumentException();}
                }
                steps.add(new Step(kind,s.deepCopy(),sign,pose,point,count,hands));
            }
            return new HandSequence(steps,dwell,gap,duration);
        }catch(RuntimeException invalid){throw new IllegalArgumentException("Invalid bounded hand sequence",invalid);}
    }
    private static int number(JsonObject o,String key,int min,int max){HandSignObservation.integer(o.get(key),min,max);return o.get(key).getAsInt();}
    public JsonObject json(){var o=new JsonObject();o.addProperty("schemaVersion",1);var a=new JsonArray();steps.forEach(s->a.add(s.json().deepCopy()));o.add("steps",a);o.addProperty("dwellMs",dwellMs);o.addProperty("maximumGapMs",maximumGapMs);o.addProperty("maximumDurationMs",maximumDurationMs);return o;}
    public String key(){return canonical(json()).toString();}
    private static JsonElement canonical(JsonElement value){
        if(value.isJsonObject()){var sorted=new JsonObject();value.getAsJsonObject().keySet().stream().sorted().forEach(k->sorted.add(k,canonical(value.getAsJsonObject().get(k))));return sorted;}
        if(value.isJsonArray()){var array=new JsonArray();value.getAsJsonArray().forEach(v->array.add(canonical(v)));return array;}return value;
    }
    public String description(){String full=steps.stream().map(Step::description).collect(java.util.stream.Collectors.joining(" then "));return full.getBytes(StandardCharsets.UTF_8).length<=600?full:"the agreed "+steps.size()+"-step hand sequence (full conditions in task details)";}
    public static String instructions(){return """
            
            Ordered input extension: a rule/check uses eventType:obs.hand.sequence and handSequence
            (no handPose/handSign/handPoint/scalar fields). This is a logical rule, never a client event.
            handSequence={schemaVersion:1,steps:[...],dwellMs:300,maximumGapMs:5000,maximumDurationMs:120000}.
            Each step is {kind:sign|pose|point,condition:<the corresponding typed condition>} or
            {kind:count,count:0..10,hands:either|left|right|both}. Counts use straight fingers only;
            every participating finger must be known straight or bent. Both means two distinct known sides
            and ten known fingers; one-hand counts stop at five. Disclose this counting convention.
            Keep the other rule fields; minConfidence=0, samples=1, firstAction=ON_CUE. One completed
            sequence spends one contribution, partial steps spend none. Preparation demonstrates the
            whole sequence; acceptance requires a new sequence. At most twelve steps, no nesting.
            Dwell is 300..5000 ms; gap is dwell..30000 ms; total <=120000 ms and >=steps*dwell.
            Hold each step until recognized. Identical adjacent steps require a known nonmatching release.
            Unknown/dropped captures reset dwell; wrong stable steps reset the attempt. The original
            deadline remains fixed. Changed camera/hand association requires an explicit restart;
            resume restarts the sequence from its first step with the task's remaining budget.
            Never substitute a cached final pose, unknown fingers or a verbal claim for the sequence.
            """;}
    public void requireSupported(Storage storage,EventHistory history,Instant now){
        if(!HandFeatures.inputSequences(storage))throw new IllegalArgumentException("Create a sequence-capable Coordination instance");
        for(var step:steps){
            if((step.pose()!=null||step.count()!=null)&&!HandFeatures.poses(storage))throw new IllegalArgumentException("Pose input unavailable");
            if(step.sign()!=null&&!HandFeatures.signAllowed(step.sign().sign(),HandFeatures.poses(storage),HandFeatures.geometry(storage)))throw new IllegalArgumentException("Sign input unavailable");
            if(HandFeatures.requiresGeometry(step.pose(),step.sign())&&!HandFeatures.geometry(storage))throw new IllegalArgumentException("Geometry input unavailable");
            if(step.point()!=null){if(!HandFeatures.pointing(storage))throw new IllegalArgumentException("Pointing input unavailable");step.point().requireCurrentFrame(history,now,storage);}
        }
    }
}
