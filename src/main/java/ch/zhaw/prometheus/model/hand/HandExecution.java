package ch.zhaw.prometheus.model.hand;

import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.model.task.TaskMemory;

/** One scoped digital playback lease. Receipts never establish physical actuation. */
public final class HandExecution {
    public static final String KEY="hand.execution.v1", INTENT="hand.execution.taskIntent", HISTORY="hand.execution.history";
    private static final String PROCESS=UUID.randomUUID().toString();
    private HandExecution(){}
    public static void prepareTask(Storage storage,BehaviourPlan plan){
        if(!HandActions.sequence(plan))return;
        var intent=new JsonObject();intent.addProperty("run",TaskMemory.text(storage,CoordinationMemory.RUN,""));intent.addProperty("action",TaskMemory.text(storage,TaskMemory.ACTIONS,"0"));
        intent.addProperty("fingerprint",digest(plan.getMotion().toString()));
        storage.put(INTENT,intent);
    }
    public static boolean pending(Storage storage,Instant now){
        expire(storage,now);return storage.containsKey(KEY)&&Set.of("PUBLISHED","RUNNING").contains(text(storage.get(KEY).getAsJsonObject(),"status"));
    }
    public static void publish(Storage storage,Event event,UUID epoch,ExternalSpeech speech,Instant now){
        if(!Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType()))return;
        var plan=BehaviourPlan.fromJson(event.getPayload());if(!HandActions.sequence(plan))return;
        cancel(storage,"superseded",now);
        if(storage.containsKey(KEY)){
            var history=storage.containsKey(HISTORY)?storage.get(HISTORY).getAsJsonArray().deepCopy():new JsonArray();var last=storage.get(KEY).getAsJsonObject().deepCopy();last.remove("steps");last.remove("ownerDigest");history.add(last);while(history.size()>8)history.remove(0);storage.put(HISTORY,history);
        }
        var e=new JsonObject();e.addProperty("id",UUID.randomUUID().toString());e.addProperty("epoch",epoch.toString());e.addProperty("process",PROCESS);e.addProperty("position",event.sourcePosition());
        e.addProperty("status","PUBLISHED");e.addProperty("published",now.toString());e.addProperty("deadline",now.plusSeconds(5).toString());e.addProperty("step",-1);
        e.add("steps",plan.getMotion().getAsJsonObject().getAsJsonObject("handAction").getAsJsonObject("action").get("steps").deepCopy());
        if(speech!=null)e.addProperty("session",speech.sessionId().toString());
        if(storage.containsKey(INTENT)){if(digest(plan.getMotion().toString()).equals(text(storage.get(INTENT).getAsJsonObject(),"fingerprint")))e.add("task",storage.get(INTENT).deepCopy());storage.remove(INTENT);}
        storage.put(KEY,e);
    }
    public static JsonObject receipt(Storage storage,EventHistory events,UUID epoch,ExternalSpeech speech,JsonObject request,Instant now){
        HandSignObservation.exact(request,"eventId","owner","operation","step");
        String owner=UUID.fromString(HandSignObservation.text(request.get("owner"))).toString();UUID eventId=UUID.fromString(HandSignObservation.text(request.get("eventId")));
        String op=HandSignObservation.text(request.get("operation"));if(!Set.of("START","STEP","COMPLETE","CANCEL").contains(op))throw new IllegalArgumentException("Unknown playback operation");
        HandSignObservation.integer(request.get("step"),0,11);int index=request.get("step").getAsInt();
        expire(storage,now);if(!storage.containsKey(KEY))throw new IllegalArgumentException("No current hand sequence");var e=storage.get(KEY).getAsJsonObject().deepCopy();
        var event=events.toList().stream().filter(v->eventId.equals(v.getId())).findFirst().orElseThrow(()->new IllegalArgumentException("Sequence event is outside this agent"));
        if(!epoch.toString().equals(text(e,"epoch"))||event.sourcePosition()==null||event.sourcePosition()!=e.get("position").getAsLong())throw new IllegalArgumentException("Stale sequence event");
        if(e.has("ownerDigest")&&!digest(owner).equals(text(e,"ownerDigest")))throw new IllegalArgumentException("Sequence belongs to another renderer");
        if(e.has("session")&&(speech==null||!text(e,"session").equals(speech.sessionId().toString()))){cancel(storage,"voice_owner_lost",now);return view(storage,now);}
        if(!Set.of("PUBLISHED","RUNNING").contains(text(e,"status")))return view(storage,now);
        if("CANCEL".equals(op)){cancel(storage,"renderer_interrupted",now);return view(storage,now);}
        var steps=e.getAsJsonArray("steps");int current=e.get("step").getAsInt();
        if("START".equals(op)){
            if(index!=0||current>0)throw new IllegalArgumentException("Cannot replay a started sequence");
            if(current==0)return view(storage,now);
            e.addProperty("ownerDigest",digest(owner));e.addProperty("status","RUNNING");e.addProperty("step",0);e.addProperty("stepStarted",now.toString());e.addProperty("started",now.toString());
            long duration=steps.asList().stream().mapToLong(s->s.getAsJsonObject().get("holdMs").getAsInt()+700).sum();e.addProperty("deadline",now.plusMillis(duration+15000).toString());
        }else{
            if(!e.has("ownerDigest")||current<0)throw new IllegalArgumentException("Sequence has not started");
            if("STEP".equals(op)&&index==current)return view(storage,now);
            long required=steps.get(current).getAsJsonObject().get("holdMs").getAsInt()+700;
            if(Duration.between(Instant.parse(text(e,"stepStarted")),now).toMillis()<required)throw new IllegalArgumentException("Previous step has not finished");
            if("COMPLETE".equals(op)){
                if(current!=steps.size()-1||index!=current)throw new IllegalArgumentException("Sequence is incomplete");
                e.addProperty("status","COMPLETED");e.addProperty("ended",now.toString());storage.put(KEY,e);taskResult(storage,e,true,now);return view(storage,now);
            }
            if(index!=current+1||index>=steps.size())throw new IllegalArgumentException("Steps must be acknowledged in order");
            e.addProperty("step",index);e.addProperty("stepStarted",now.toString());
        }
        try{
            int currentStep=e.get("step").getAsInt();var step=steps.get(currentStep).getAsJsonObject();var motion=new JsonObject();motion.add("handAction",HandActions.pose(step.getAsJsonObject("action")));
            var grounded=HandScene.ground(new BehaviourPlan(null,null,motion,null),storage,events,now);
            step.add("action",grounded.getMotion().getAsJsonObject().getAsJsonObject("handAction").get("action"));
        }catch(RuntimeException stale){storage.put(KEY,e);cancel(storage,"target_unavailable",now);return view(storage,now);}
        storage.put(KEY,e);return view(storage,now);
    }
    public static JsonObject view(Storage storage,Instant now){
        expire(storage,now);if(!storage.containsKey(KEY))return new JsonObject();var e=storage.get(KEY).getAsJsonObject().deepCopy();e.remove("ownerDigest");e.remove("process");e.remove("task");e.remove("notify");e.addProperty("schemaVersion",1);
        var steps=e.remove("steps").getAsJsonArray();e.addProperty("total",steps.size());int i=e.get("step").getAsInt();if(i>=0&&"RUNNING".equals(text(e,"status")))e.add("current",steps.get(i).deepCopy());return e;
    }
    public static void cancel(Storage storage,String reason,Instant now){
        if(!storage.containsKey(KEY))return;var e=storage.get(KEY).getAsJsonObject().deepCopy();if(!Set.of("PUBLISHED","RUNNING").contains(text(e,"status")))return;
        e.addProperty("status","INTERRUPTED");e.addProperty("reason",reason);e.addProperty("ended",now.toString());storage.put(KEY,e);taskResult(storage,e,false,now);
    }
    private static void expire(Storage storage,Instant now){
        if(!storage.containsKey(KEY))return;var e=storage.get(KEY).getAsJsonObject().deepCopy();if(!Set.of("PUBLISHED","RUNNING").contains(text(e,"status")))return;
        if(!PROCESS.equals(text(e,"process"))||!now.isBefore(Instant.parse(text(e,"deadline")))){
            e.addProperty("status","UNKNOWN");e.addProperty("reason",!PROCESS.equals(text(e,"process"))?"process_restarted":"receipt_deadline");e.addProperty("ended",now.toString());storage.put(KEY,e);taskResult(storage,e,false,now);
        }
    }
    private static void taskResult(Storage storage,JsonObject execution,boolean success,Instant now){
        if(!execution.has("task"))return;var task=execution.getAsJsonObject("task");
        if(!text(task,"run").equals(TaskMemory.text(storage,CoordinationMemory.RUN,""))||!text(task,"action").equals(TaskMemory.text(storage,TaskMemory.ACTIONS,"0")))return;
        ch.zhaw.prometheus.agentdefs.core.CoordinationDelivery.handFinished(storage,success,now);
        var changed=storage.get(KEY).getAsJsonObject().deepCopy();changed.addProperty("notify",true);storage.put(KEY,changed);
    }
    public static boolean notification(Storage storage,Instant now){pending(storage,now);return storage.containsKey(KEY)&&storage.get(KEY).getAsJsonObject().has("notify");}
    public static void notified(Storage storage){if(storage.containsKey(KEY)){var e=storage.get(KEY).getAsJsonObject().deepCopy();e.remove("notify");storage.put(KEY,e);}}
    public static boolean confirmed(Storage storage){return storage.containsKey(KEY)&&"COMPLETED".equals(text(storage.get(KEY).getAsJsonObject(),"status"));}
    public static void clear(Storage storage){if(storage==null)return;for(String key:List.of(KEY,INTENT,HISTORY))if(storage.containsKey(key))storage.remove(key);}
    private static String text(JsonObject o,String key){return o.has(key)?o.get(key).getAsString():"";}
    private static String digest(String value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
