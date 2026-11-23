package ch.zhaw.prometheus.model.hand;

import java.util.Set;
import com.google.gson.*;
import static ch.zhaw.prometheus.model.hand.HandSignObservation.*;

public final class PointingAction {
    private PointingAction() {}
    public static void validate(JsonObject action) {
        exact(action,"kind","effector","direction","frame");
        if(!"point".equals(text(action.get("kind")))||!Set.of("left_hand","right_hand").contains(text(action.get("effector"))))throw new IllegalArgumentException();
        HandGeometry.angle(text(action.get("direction")));
        if(!SpatialFrame.ROBOT.equals(SpatialFrame.parse(action.get("frame"))))throw new IllegalArgumentException("Unregistered output frame");
    }
    public static String guidance() {
        return "\nDirectional output: handAction.action={kind:point,effector:left_hand|right_hand,direction:left|right|up|down|up_left|up_right|down_left|down_right,frame:{id:valerian.robot,revision:1,space:robot_local,mirrored:false}}. "
                + "Immediate commands use kind POINT and value equal to this action (no top-level effector); task actions use the same motion.handAction envelope. "
                + "Directions are the ROBOT's own left/right, appearing opposite in the front view. An unqualified 'point left' means robot-left; disclose that convention. "
                + "Choose left_hand for left directions, otherwise right_hand unless a hand is specified. Up/down are vertical. Preserve requested silence. "
                + "This is a brief digital arm-and-index demonstration, not target selection or physical room pointing. Reject forward/back and unresolved person/item targets.\n";
    }
}
