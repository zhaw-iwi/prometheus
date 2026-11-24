package ch.zhaw.prometheus.model.hand;

import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.*;
import static ch.zhaw.prometheus.model.hand.HandSignObservation.*;

/** Ephemeral geometry, separate from operator registration and never biometric identity. */
public final class ObservedTargets {
    public static final String KEY="hand.scene.observed.v1",COUNTER="hand.scene.generation",BINDINGS="hand.scene.bindings.v1",SELECTED="hand.scene.selected.v1";
    public static final Set<String> TYPES=Set.of("person","cup","bottle","book");
    public static final long TTL_SECONDS=3;
    private ObservedTargets(){}
    public static JsonObject snapshot(Storage storage){return storage.containsKey(KEY)?storage.get(KEY).getAsJsonObject().deepCopy():new JsonObject();}
    public static List<JsonObject> fresh(Storage storage,Instant now){
        var snapshot=snapshot(storage);if(!snapshot.has("targets")||!ObservationEvidence.fresh(Instant.parse(text(snapshot.get("ts"))),now,TTL_SECONDS,1))return List.of();
        return snapshot.getAsJsonArray("targets").asList().stream().map(JsonElement::getAsJsonObject).filter(t->Instant.parse(text(t.get("expiresAt"))).isAfter(now)).map(JsonObject::deepCopy).toList();
    }
    /** Returns false for an exact retry. All other accepted snapshots replace the visible set. */
    public static boolean admit(Storage storage,UUID scope,Event event,Instant now){
        try{return admitValidated(storage,scope,event,now);}catch(RuntimeException invalid){throw new IllegalArgumentException("Invalid or stale detected target snapshot",invalid);}
    }
    private static boolean admitValidated(Storage storage,UUID scope,Event event,Instant now){
        String payload=event.getPayload();if(payload==null||payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16384)throw new IllegalArgumentException();
        var o=JsonParser.parseString(payload).getAsJsonObject();exact(o,"schemaVersion","observationId","source","ts","frame","targets");integer(o.get("schemaVersion"),1,1);
        identifier(o.get("observationId"));identifier(o.get("source"));Instant at=Instant.parse(text(o.get("ts")));
        if(!ObservationEvidence.fresh(at,now,2,1))throw new IllegalArgumentException("Stale source time");
        var frame=SpatialFrame.parse(o.get("frame"));if(!frame.space().equals("camera_image"))throw new IllegalArgumentException();
        var previous=snapshot(storage);String digest;
        try{digest=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        if(previous.has("observationId")&&previous.get("observationId").equals(o.get("observationId"))){if(previous.get("digest").getAsString().equals(digest))return false;throw new IllegalArgumentException("Changed retry");}
        if(previous.has("ts")&&java.time.Duration.between(Instant.parse(text(previous.get("ts"))),at).toMillis()<200)throw new IllegalArgumentException("Out-of-order or excessive snapshots");
        var targets=o.getAsJsonArray("targets");if(targets.size()>16)throw new IllegalArgumentException();var ids=new HashSet<String>();var canonical=new JsonArray();
        int generation=storage.containsKey(COUNTER)?storage.get(COUNTER).getAsInt():0;
        var old=fresh(storage,now);
        for(var raw:targets){
            var t=raw.getAsJsonObject();exact(t,"id","revision","label","type","provenance","frame","position","extent","observedAt","expiresAt");
            String id=identifier(t.get("id")),type=text(t.get("type"));if(id.length()>40||!ids.add(id)||!TYPES.contains(type)||!type.equals(text(t.get("label")))||!"detected".equals(text(t.get("provenance"))))throw new IllegalArgumentException();
            integer(t.get("revision"),1,1);if(!frame.equals(SpatialFrame.parse(t.get("frame")))||!at.equals(Instant.parse(text(t.get("observedAt"))))||!at.plusSeconds(TTL_SECONDS).equals(Instant.parse(text(t.get("expiresAt")))))throw new IllegalArgumentException();
            var p=t.getAsJsonObject("position");HandScene.position(p,true);var e=t.getAsJsonObject("extent");exact(e,"x","y","z");
            for(String axis:List.of("x","y")){double extent=HandGeometry.range(e.get(axis),0,1),center=p.get(axis).getAsDouble();if(extent<=0||center-extent/2< -1e-8||center+extent/2>1+1e-8)throw new IllegalArgumentException();}
            if(HandGeometry.range(e.get("z"),0,0)!=0)throw new IllegalArgumentException();
            String scoped=scope+":"+id;
            var same=old.stream().filter(t0->scoped.equals(text(t0.get("id")))&&type.equals(text(t0.get("type")))&&frame.equals(SpatialFrame.parse(t0.get("frame")))
                    &&java.time.Duration.between(Instant.parse(text(t0.get("observedAt"))),at).toMillis()<=1500).findFirst();
            var entry=t.deepCopy();entry.addProperty("id",scoped);entry.addProperty("revision",same.isPresent()?same.get().get("revision").getAsInt():Math.incrementExact(generation));
            if(same.isEmpty())generation=entry.get("revision").getAsInt();canonical.add(entry);
        }
        var saved=o.deepCopy();saved.add("targets",canonical);var wire=saved.deepCopy();saved.addProperty("digest",digest);
        storage.put(COUNTER,new JsonPrimitive(generation));storage.put(KEY,saved);event.setPayload(wire.toString());return true;
    }
    public static JsonObject require(Storage storage,TargetRef ref,Instant now){return fresh(storage,now).stream().filter(t->HandScene.ref(t).equals(ref)).findFirst().orElseThrow(()->new IllegalArgumentException("Detected target expired or changed"));}
    public static List<JsonObject> near(Storage storage,HandPointObservation point,Instant now){return fresh(storage,now).stream().filter(t->point.frame().equals(SpatialFrame.parse(t.get("frame")))
            &&java.time.Duration.between(Instant.parse(text(t.get("observedAt"))),point.observed()).toMillis()>=0
            &&java.time.Duration.between(Instant.parse(text(t.get("observedAt"))),point.observed()).toMillis()<=1500).toList();}
    public static JsonObject source(Storage storage,HandPointObservation point,Instant now){
        var result=new JsonObject();result.addProperty("status","unresolved");if(point.handOrigin()==null||!"valid".equals(point.validity()))return result;
        var people=near(storage,point,now).stream().filter(t->text(t.get("type")).equals("person")&&contains(t,point.handOrigin())).toList();
        if(people.size()==1){result.addProperty("status","projected");result.add("person",HandScene.ref(people.getFirst()).json());}return result;
    }
    private static boolean contains(JsonObject target,JsonObject point){for(String axis:List.of("x","y")){double center=target.getAsJsonObject("position").get(axis).getAsDouble(),half=target.getAsJsonObject("extent").get(axis).getAsDouble()/2;if(Math.abs(point.get(axis).getAsDouble()-center)>half)return false;}return true;}
    public static JsonObject bind(Storage storage,JsonObject request,Instant now){
        try{
            exact(request,"target","label","destination");var ref=TargetRef.parse(request.get("target"));require(storage,ref,now);
            String label=text(request.get("label")).strip();if(label.isEmpty()||label.length()>80||label.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException();
            if(!request.get("destination").isJsonNull()){
                var destination=TargetRef.parse(request.get("destination"));var registered=HandScene.requireConfigured(storage,destination,now);
                if(!"virtual".equals(text(registered.get("type"))))throw new IllegalArgumentException("Select a registered virtual counterpart");
            }
            var values=storage.containsKey(BINDINGS)?storage.get(BINDINGS).getAsJsonArray().deepCopy():new JsonArray();
            for(int i=values.size()-1;i>=0;i--)if(TargetRef.parse(values.get(i).getAsJsonObject().get("target")).equals(ref))values.remove(i);
            if(values.size()>=16)values.remove(0);
            var binding=request.deepCopy();binding.add("target",ref.json());if(!request.get("destination").isJsonNull())binding.add("destination",TargetRef.parse(request.get("destination")).json());binding.addProperty("label",label);values.add(binding);storage.put(BINDINGS,values);storage.put(SELECTED,ref.json());return binding;
        }catch(RuntimeException invalid){throw new IllegalArgumentException("Select a fresh detected target and a registered virtual counterpart",invalid);}
    }
    public static JsonObject binding(Storage storage,TargetRef ref,Instant now){
        require(storage,ref,now);
        if(storage.containsKey(BINDINGS))for(var value:storage.get(BINDINGS).getAsJsonArray())if(TargetRef.parse(value.getAsJsonObject().get("target")).equals(ref))return value.getAsJsonObject().deepCopy();
        throw new IllegalArgumentException("Detected target has no registered virtual counterpart");
    }
    public static String guidance(Storage storage,Instant now){
        if(!storage.containsKey(KEY))return "";
        return "\nFresh detected scene targets (data): "+fresh(storage,now)+"\nCurrent target bindings (data; may be expired): "+(storage.containsKey(BINDINGS)?storage.get(BINDINGS):"[]")
                +"\nSelected reference: "+(storage.containsKey(SELECTED)?storage.get(SELECTED):"none")
                +"\nUse exact current refs for target cues or POINT commands. For a selected person/item, target is the selected DETECTED reference, not its registered virtual destination. "
                +"The backend resolves that destination and rechecks the observed target's freshness; do not substitute the destination reference. "
                +"Temporary tracks and user labels are not identity. Output needs an explicitly registered virtual counterpart; never invent room coordinates. Unknown or multiple projected candidates require clarification. The backend rechecks expiry after inference.\n";
    }
    private static boolean containsTarget(JsonObject scene, TargetRef ref) {
        return scene.getAsJsonArray("targets").asList().stream().map(JsonElement::getAsJsonObject)
                .anyMatch(target -> HandScene.ref(target).equals(ref));
    }
    /** Only the latest fresh scene in the requested frame can establish that its selection is absent. */
    public static boolean selectedAbsent(Storage storage, TargetRef ref, Instant now) {
        if (!storage.containsKey(SELECTED) || !storage.containsKey(KEY) || !ref.json().equals(storage.get(SELECTED))) return false;
        try {
            var scene = storage.get(KEY).getAsJsonObject();
            return ObservationEvidence.fresh(ObservationEvidence.observed(scene), now, TTL_SECONDS, 0)
                    && ref.frame().equals(SpatialFrame.parse(scene.get("frame"))) && !containsTarget(scene, ref);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
    /** Enrich one fresh scene source with its verified selection, never a new observation or action. */
    public static String selectedContext(Storage storage,String payload,Instant now){
        if(!storage.containsKey(SELECTED))return "";
        try{
            var ref=TargetRef.parse(storage.get(SELECTED));
            var source=JsonParser.parseString(payload).getAsJsonObject();
            if(!ObservationEvidence.fresh(ObservationEvidence.observed(source),now,TTL_SECONDS,0))return "";
            if(!containsTarget(source,ref)){
                var latest=storage.get(KEY);
                if(latest==null || !Objects.equals(source.get("observationId"),latest.getAsJsonObject().get("observationId"))
                        || !Objects.equals(source.get("ts"),latest.getAsJsonObject().get("ts"))
                        || !ref.frame().equals(SpatialFrame.parse(source.get("frame"))))return "";
                return "The previous selection is absent from this scene. Pointing at it again needs a fresh selection.\n";
            }
            var target=require(storage,ref,now);
            var binding=binding(storage,ref,now);boolean mapped=false;
            if(!binding.get("destination").isJsonNull())try{
                mapped="virtual".equals(HandScene.requireConfigured(storage,TargetRef.parse(binding.get("destination")),now).get("type").getAsString());
            }catch(IllegalArgumentException unavailable){/* The alias remains known, but its output mapping does not. */}
            var selected=new JsonObject();selected.add("label",binding.get("label"));selected.add("type",target.get("type"));selected.addProperty("outputMapped",mapped);
            var facts=new JsonObject();facts.add("selectedTarget",selected);
            return "Selected scene data: "+facts+". The user label is not personal identity. No action is implied.\n";
        }catch(RuntimeException unavailable){return "";}
    }
}
