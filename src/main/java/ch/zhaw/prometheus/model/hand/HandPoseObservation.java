package ch.zhaw.prometheus.model.hand;

import java.util.*;
import com.google.gson.*;
import static ch.zhaw.prometheus.model.hand.HandSignObservation.*;

/** Strict v2 evidence. Numeric legacy v1 observations retain their separate contract. */
public record HandPoseObservation(HandFrame frame) {
    public static final Set<String> FINGERS=Set.of("thumb","index","middle","ring","little");
    public static HandPoseObservation parse(String payload) {
        try {
            var frame=HandFrame.parse(payload,2);
            for(var item:frame.hands()) {
                var hand=item.getAsJsonObject();
                if(hand.has("geometry")) {exact(hand,"id","handedness","fingers","fingerStates","geometry");HandGeometry.validate(hand.getAsJsonObject("geometry"));}
                else exact(hand,"id","handedness","fingers","fingerStates");
                var fingers=hand.getAsJsonObject("fingers"); var states=hand.getAsJsonObject("fingerStates");
                if(!fingers.keySet().equals(FINGERS)||!states.keySet().equals(FINGERS))throw new IllegalArgumentException();
                for(String name:FINGERS) {
                    var raw=fingers.get(name); var state=states.get(name);
                    if(raw.isJsonNull()) {if(!state.isJsonNull())throw new IllegalArgumentException();continue;}
                    double value=number(raw);
                    // A missing classification remains unknown; contradictory labels are invalid.
                    if(!state.isJsonNull()&&!text(state).equals(value<.4?"straight":value>.55?"bent":"neutral"))throw new IllegalArgumentException();
                }
                if(hand.has("geometry"))HandGeometry.validateEvidence(hand);
            }
            return new HandPoseObservation(frame);
        } catch(RuntimeException invalid) {throw new IllegalArgumentException("invalid_pose_contract",invalid);}
    }
}
