package ch.zhaw.prometheus.model.task;

import java.time.*;
import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.hand.HandEvidence;

/** Bounded durable progress. Repeated guard evaluation of a capture is idempotent. */
public final class HandSequenceMemory {
    public static final String KEY="hand.sequence.progress.v1";
    private static final String PROCESS=UUID.randomUUID().toString();
    private HandSequenceMemory(){}
    public static JsonObject progress(Storage storage){return storage.containsKey(KEY)?storage.get(KEY).getAsJsonObject().deepCopy():new JsonObject();}
    public static void clear(Storage storage){if(storage.containsKey(KEY))storage.remove(KEY);}
    public static void clearPreparation(Storage storage){if(!storage.containsKey(KEY))return;var root=progress(storage);root.keySet().removeIf(k->k.startsWith("prep-"));storage.put(KEY,root);}
    public static void restartRun(Storage storage){if(!storage.containsKey(KEY))return;var root=progress(storage);root.keySet().removeIf(k->k.startsWith("run-"));storage.put(KEY,root);}

    public static boolean observe(Storage storage,String slot,String epoch,HandSequence sequence,Event event,Instant boundary,Instant now){
        if(!ch.zhaw.prometheus.model.hand.HandFeatures.inputSequences(storage)||!Event.KIND_OBSERVATION.equals(event.getKind())||HandEvidence.consumed(storage,event)||!TaskMemory.fresh(event,now))return false;
        String capture=HandEvidence.identity(event);Instant time=TaskMemory.observed(event);
        if(capture==null||time==null||!time.isAfter(boundary))return false;
        var root=progress(storage);var state=root.has(slot)?root.getAsJsonObject(slot):newState(epoch,sequence);
        if(!epoch.equals(text(state,"epoch"))||!hash(sequence).equals(text(state,"contract")))state=newState(epoch,sequence);
        root.add(slot,state);
        if(state.has("capture")&&!PROCESS.equals(text(state,"process"))){
            state.addProperty("status","restart_required");state.remove("dwellSince");
            if(slot.startsWith("run-"))TaskMemory.pauseForResponse(storage,"sequence_process_restarted");
            return save(storage,root,false);
        }
        state.addProperty("process",PROCESS);
        if("restart_required".equals(text(state,"status")))return false;
        if(capture.equals(text(state,"completeCapture")))return true;
        if(capture.equals(text(state,"capture"))||state.has("lastAt")&&!time.isAfter(Instant.parse(text(state,"lastAt"))))return false;
        int index=state.get("index").getAsInt();boolean complete=index==sequence.steps().size();
        var step=sequence.steps().get(complete?0:index);var reading=step.read(event,storage,now);
        var previous=index>0?sequence.steps().get(index-1):null;
        if(reading.result()==HandSequence.Result.IGNORE)return false;
        var raw=JsonParser.parseString(event.getPayload()).getAsJsonObject();
        var source=new JsonObject();source.add("source",raw.get("source"));source.add("frame",raw.get("frame"));
        if(state.has("source")&&!state.get("source").equals(source)){
            state.addProperty("status","restart_required");state.remove("dwellSince");if(slot.startsWith("run-"))TaskMemory.pauseForResponse(storage,"sequence_source_changed");return save(storage,root,false);
        }
        if(!raw.has("captureIndex"))return false;
        ch.zhaw.prometheus.model.hand.HandSignObservation.integer(raw.get("captureIndex"),1,Integer.MAX_VALUE);
        int ordinal=raw.get("captureIndex").getAsInt();
        if(state.has("ordinal")&&ordinal<=state.get("ordinal").getAsInt())return false;
        if(state.has("ordinal")&&(ordinal!=state.get("ordinal").getAsInt()+1||Duration.between(Instant.parse(text(state,"lastAt")),time).toMillis()>1000)){
            state.remove("dwellSince");state.remove("wrongSince");state.remove("wrong");state.addProperty("status","capture_gap");
        }
        state.addProperty("ordinal",ordinal);
        state.addProperty("capture",capture);state.addProperty("lastAt",time.toString());
        if(reading.result()==HandSequence.Result.UNKNOWN){state.remove("dwellSince");state.remove("wrongSince");state.remove("wrong");state.addProperty("status","unknown_evidence");return save(storage,root,false);}

        var bound=state.has("hands")?state.getAsJsonObject("hands"):new JsonObject();
        boolean changed=state.has("source")&&!state.get("source").equals(source)
                ||reading.hands().entrySet().stream().anyMatch(e->bound.has(e.getKey())&&!bound.get(e.getKey()).getAsString().equals(e.getValue()));
        if(changed){state.addProperty("status","restart_required");state.remove("dwellSince");
            if(slot.startsWith("run-"))TaskMemory.pauseForResponse(storage,"sequence_source_changed");return save(storage,root,false);}
        if(reading.result()==HandSequence.Result.MATCH){state.add("source",source);reading.hands().forEach(bound::addProperty);state.add("hands",bound);}

        if(complete){
            // A different first step can release the previous final pose. An identical one needs a known nonmatch.
            if(previous.json().equals(step.json())&&reading.result()==HandSequence.Result.MATCH)return save(storage,root,false);
            if(reading.result()==HandSequence.Result.MISMATCH&&previous.read(event,storage,now).result()==HandSequence.Result.MATCH)return save(storage,root,false);
            resetAttempt(state,"waiting_first");index=0;
        }
        if(state.has("startedAt")){
            Instant started=Instant.parse(text(state,"startedAt")),lastProgress=Instant.parse(text(state,"progressAt"));
            if(Duration.between(started,now).toMillis()>sequence.maximumDurationMs()||Duration.between(lastProgress,now).toMillis()>sequence.maximumGapMs()){
                resetAttempt(state,"timeout");return save(storage,root,false);
            }
        }
        if(state.has("release")){
            state.addProperty("status","awaiting_release");
            if(previous.read(event,storage,now).result()==HandSequence.Result.MISMATCH){state.remove("release");state.addProperty("status","released");}
            return save(storage,root,false);
        }
        if(reading.result()==HandSequence.Result.MISMATCH){
            state.remove("dwellSince");
            if(previous!=null&&previous.read(event,storage,now).result()==HandSequence.Result.MATCH){state.remove("wrongSince");state.remove("wrong");return save(storage,root,false);}
            String wrong=semantic(raw,event.getType());
            if(!wrong.equals(text(state,"wrong"))){state.addProperty("wrong",wrong);state.addProperty("wrongSince",time.toString());}
            else if(state.has("wrongSince")&&Duration.between(Instant.parse(text(state,"wrongSince")),time).toMillis()>=sequence.dwellMs())resetAttempt(state,"wrong_step");
            return save(storage,root,false);
        }
        state.remove("wrongSince");state.remove("wrong");
        if(!state.has("startedAt")){state.addProperty("startedAt",time.toString());state.addProperty("progressAt",time.toString());}
        state.addProperty("status","dwelling");
        if(!state.has("dwellSince")){state.addProperty("dwellSince",time.toString());return save(storage,root,false);}
        if(Duration.between(Instant.parse(text(state,"dwellSince")),time).toMillis()<sequence.dwellMs())return save(storage,root,false);
        state.remove("dwellSince");state.addProperty("index",++index);state.addProperty("progressAt",time.toString());state.addProperty("status","waiting_step");
        if(index==sequence.steps().size()){state.addProperty("completeCapture",capture);state.addProperty("status","complete");return save(storage,root,true);}
        if(sequence.steps().get(index-1).json().equals(sequence.steps().get(index).json())){state.addProperty("release",true);state.addProperty("status","awaiting_release");}
        return save(storage,root,false);
    }
    private static JsonObject newState(String epoch,HandSequence sequence){var state=new JsonObject();state.addProperty("epoch",epoch);state.addProperty("contract",hash(sequence));state.addProperty("total",sequence.steps().size());state.addProperty("maximumDurationMs",sequence.maximumDurationMs());state.addProperty("maximumGapMs",sequence.maximumGapMs());state.addProperty("index",0);state.addProperty("status","waiting_first");return state;}
    private static String hash(HandSequence sequence){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(sequence.key().getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private static void resetAttempt(JsonObject state,String reason){for(String key:List.of("startedAt","progressAt","dwellSince","wrongSince","wrong","release","completeCapture"))state.remove(key);state.addProperty("index",0);state.addProperty("status",reason);}
    private static String text(JsonObject state,String key){return state.has(key)?state.get(key).getAsString():"";}
    private static boolean save(Storage storage,JsonObject root,boolean result){
        if(root.size()>7||root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32768)throw new IllegalArgumentException("Sequence progress exceeds bounds");
        storage.put(KEY,root);return result;
    }
    private static String semantic(JsonObject raw,String type){
        var result=new JsonObject();result.addProperty("type",type);
        if(raw.has("hands")){var hands=new JsonArray();for(var value:raw.getAsJsonArray("hands")){var h=value.getAsJsonObject();var compact=new JsonObject();for(String key:List.of("id","handedness","fingerStates","sign"))if(h.has(key))compact.add(key,h.get(key));hands.add(compact);}result.add("hands",hands);}
        else for(String key:List.of("direction","resolution"))if(raw.has(key))result.add(key,raw.get(key));
        return result.toString();
    }
}
