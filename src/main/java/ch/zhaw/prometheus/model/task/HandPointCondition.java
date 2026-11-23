package ch.zhaw.prometheus.model.task;

import java.util.Set;
import com.google.gson.*;
import ch.zhaw.prometheus.model.hand.*;

public record HandPointCondition(String direction,SpatialFrame frame,String handedness) {
    public static HandPointCondition parse(JsonElement raw) {
        var o=raw.getAsJsonObject();TaskSpec.exact(o,Set.of("direction","frame","handedness"));
        String direction=TaskSpec.string(o,"direction",16),side=TaskSpec.string(o,"handedness",5);HandGeometry.angle(direction);
        var frame=SpatialFrame.parse(o.get("frame"));
        if(!frame.space().equals("camera_image")||!Set.of("any","left","right").contains(side))throw new IllegalArgumentException("Pointing needs a declared camera frame");
        return new HandPointCondition(direction,frame,side);
    }
    public boolean matches(HandPointObservation observation) {
        return frame.equals(observation.frame())&&direction.equals(observation.direction())&&"valid".equals(observation.validity())
                &&(handedness.equals("any")||handedness.equals(observation.handedness()));
    }
    public String description() {return handedness+" hand pointing "+direction+" in the unmirrored camera plane";}
    public String key() {return new Gson().toJson(this);}
    public void requireCurrentFrame(ch.zhaw.prometheus.model.event.EventHistory history,java.time.Instant now) {
        var latest=history.toList().reversed().stream().filter(e->ch.zhaw.prometheus.model.event.Event.TYPE_HAND_POINTING.equals(e.getType())).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Supply a current camera-frame observation before agreeing pointing"));
        if(!TaskMemory.fresh(latest,now)||!frame.equals(HandPointObservation.parse(latest.getPayload()).frame()))
            throw new IllegalArgumentException("Pointing frame is stale or does not match the current camera");
    }
    public static String instructions() {
        return "\nPointing input: obs.hand.pointing rules/checks use handPoint:{direction:left|right|up|down|up_left|up_right|down_left|down_right,frame:{id,revision,space:camera_image,mirrored:false},handedness:any|left|right}. "
                + "Other rule/check fields are unchanged; minConfidence=0. Copy the current observed camera frame exactly; if none is supplied, request a camera sample before proposing a complete task. "
                + "Coordinates are unmirrored image-right/image-down. For a person facing the camera, their left maps to image-right and vice versa: disclose and agree this facing-camera convention; do not infer their body orientation. "
                + "Preview mirroring never changes stored directions. Up/down are image up/down. Index-up may mean attention or upward pointing: use the user's agreed interpretation, never infer intent. "
                + "Only one unambiguous pointing hand is admitted. Unknown, foreshortened, boundary or multiple-pointing-hand readings cannot match. A changed camera frame needs a revised agreement. "
                + "No person/item target, forward/back direction or depth is supported yet.\n";
    }
}
