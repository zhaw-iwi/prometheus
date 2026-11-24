package ch.zhaw.prometheus.model.task;

import java.util.Set;
import com.google.gson.*;
import ch.zhaw.prometheus.model.hand.*;

public record HandPointCondition(String direction,SpatialFrame frame,String handedness,TargetRef target) {
    public HandPointCondition(String direction,SpatialFrame frame,String handedness){this(direction,frame,handedness,null);}
    public static HandPointCondition parse(JsonElement raw) {
        var o=raw.getAsJsonObject();
        if(o.has("target")){
            TaskSpec.exact(o,Set.of("target","handedness"));String side=TaskSpec.string(o,"handedness",5);var target=TargetRef.parse(o.get("target"));
            if(!Set.of("any","left","right").contains(side)||!target.frame().space().equals("camera_image"))throw new IllegalArgumentException("Input targets need an image region");
            return new HandPointCondition(null,target.frame(),side,target);
        }
        TaskSpec.exact(o,Set.of("direction","frame","handedness"));
        String direction=TaskSpec.string(o,"direction",16),side=TaskSpec.string(o,"handedness",5);HandGeometry.angle(direction);
        var frame=SpatialFrame.parse(o.get("frame"));
        if(!frame.space().equals("camera_image")||!Set.of("any","left","right").contains(side))throw new IllegalArgumentException("Pointing needs a declared camera frame");
        return new HandPointCondition(direction,frame,side);
    }
    public boolean matches(HandPointObservation observation) {
        boolean destination=target==null?direction.equals(observation.direction()):"resolved".equals(observation.resolution().get("status").getAsString())&&target.equals(TargetRef.parse(observation.resolution().get("target")));
        return frame.equals(observation.frame())&&destination&&"valid".equals(observation.validity())
                &&(handedness.equals("any")||handedness.equals(observation.handedness()));
    }
    public String description() {return handedness+" hand pointing "+(target==null?direction+" in the unmirrored camera plane":"at the agreed region");}
    public JsonObject json(){var o=new JsonObject();o.addProperty("handedness",handedness);if(target!=null)o.add("target",target.json());else{o.addProperty("direction",direction);o.add("frame",frame.json());}return o;}
    public String key() {return json().toString();}
    public boolean current(ch.zhaw.prometheus.model.Storage storage,java.time.Instant now){return target==null||HandScene.current(storage,target,now);}
    public void requireCurrentFrame(ch.zhaw.prometheus.model.event.EventHistory history,java.time.Instant now,ch.zhaw.prometheus.model.Storage storage){
        var latest=latestPoint(history);
        var pointAt=latest==null?null:TaskMemory.observed(latest);
        var scene=HandFeatures.targets(storage)&&storage.containsKey(HandFeatures.DYNAMIC_TARGETS)
                &&new JsonPrimitive(true).equals(storage.get(HandFeatures.DYNAMIC_TARGETS))?ObservedTargets.snapshot(storage):new JsonObject();
        var sceneAt=ch.zhaw.prometheus.model.event.ObservationEvidence.observed(scene);
        // A validated passive scene capture establishes camera coordinates, never a
        // pointing cue. It can arrive while the serialized turn waits for inference.
        if(sceneAt!=null&&(pointAt==null||!sceneAt.isBefore(pointAt))){
            if(!ch.zhaw.prometheus.model.event.ObservationEvidence.fresh(sceneAt,now,ObservedTargets.TTL_SECONDS,1)
                    ||!frame.equals(SpatialFrame.parse(scene.get("frame")))
                    ||sceneAt.equals(pointAt)&&!frame.equals(HandPointObservation.parse(latest.getPayload()).frame()))
                throw new IllegalArgumentException("Camera frame is stale or changed");
        }else requireCurrentFrame(history,now);
        if(target!=null){if(!HandFeatures.targets(storage))throw new IllegalArgumentException("Targets not declared");HandScene.require(storage,target,now);}
    }
    private static ch.zhaw.prometheus.model.event.Event latestPoint(ch.zhaw.prometheus.model.event.EventHistory history){
        return history.toList().reversed().stream().filter(e->ch.zhaw.prometheus.model.event.Event.TYPE_HAND_POINTING.equals(e.getType())).findFirst().orElse(null);
    }
    public void requireCurrentFrame(ch.zhaw.prometheus.model.event.EventHistory history,java.time.Instant now) {
        var latest=latestPoint(history);
        if(latest==null)throw new IllegalArgumentException("Supply a current camera-frame observation before agreeing pointing");
        if(!TaskMemory.fresh(latest,now)||!frame.equals(HandPointObservation.parse(latest.getPayload()).frame()))
            throw new IllegalArgumentException("Pointing frame is stale or does not match the current camera");
    }
    public static String instructions() {
        return "\nPointing input: obs.hand.pointing rules/checks use handPoint:{direction:left|right|up|down|up_left|up_right|down_left|down_right,frame:{id,revision,space:camera_image,mirrored:false},handedness:any|left|right}. "
                + "Other rule/check fields are unchanged; minConfidence=0. Copy the current observed camera frame exactly; if none is supplied, request a camera sample before proposing a complete task. "
                + "Coordinates are unmirrored image-right/image-down. For relative left/right requests only, a person facing the camera has their left at image-right and vice versa: disclose and agree this convention; do not infer body orientation. Target references already specify a frame and require no left/right convention. "
                + "Preview mirroring never changes stored directions. Up/down are image up/down. Index-up may mean attention or upward pointing: use the user's agreed interpretation, never infer intent. "
                + "Only one unambiguous pointing hand is admitted. Unknown, foreshortened, boundary or multiple-pointing-hand readings cannot match. A changed camera frame needs a revised agreement. "
                + "Never guess person/item targets: they require a supported scoped registry and unambiguous source association. Forward/back direction and room depth remain unsupported.\n";
    }
}
