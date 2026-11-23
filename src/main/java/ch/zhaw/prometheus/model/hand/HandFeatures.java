package ch.zhaw.prometheus.model.hand;

import java.util.Set;
import com.google.gson.JsonPrimitive;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;

/** Additive persisted opt-ins: older named-sign profiles keep their six-sign contract. */
public final class HandFeatures {
    public static final String POSES = "hand.poses.v2";
    public static final String POINTING = "hand.pointing.v1";
    public static final String TARGETS = "hand.targets.v1";
    public static final String GEOMETRY = "hand.geometry.v1";
    public static final Set<String> DERIVED_SIGNS = Set.of("shaka", "horns", "middle_finger", "ok_ring");
    private HandFeatures() {}
    public static boolean poses(AgentInteractionProfile profile) { return profile != null && profile.getProfileTags().contains(POSES); }
    public static boolean poses(Storage storage) { return storage.containsKey(POSES) && new JsonPrimitive(true).equals(storage.get(POSES)); }
    public static boolean pointing(AgentInteractionProfile profile) { return profile!=null && profile.getProfileTags().contains(POINTING); }
    public static boolean pointing(Storage storage) { return storage.containsKey(POINTING) && new JsonPrimitive(true).equals(storage.get(POINTING)); }
    public static boolean targets(AgentInteractionProfile profile) {return pointing(profile)&&profile.getProfileTags().contains(TARGETS);}
    public static boolean targets(Storage storage) {return pointing(storage)&&storage.containsKey(TARGETS)&&new JsonPrimitive(true).equals(storage.get(TARGETS));}
    public static boolean geometry(AgentInteractionProfile profile) { return poses(profile) && profile.getProfileTags().contains(GEOMETRY); }
    public static boolean geometry(Storage storage) { return poses(storage) && storage.containsKey(GEOMETRY) && new JsonPrimitive(true).equals(storage.get(GEOMETRY)); }
    public static boolean requiresGeometry(ch.zhaw.prometheus.model.task.HandPoseCondition pose,ch.zhaw.prometheus.model.task.HandSignCondition sign) {
        return pose!=null&&pose.relational() || sign!=null&&"ok_ring".equals(sign.sign());
    }
    public static boolean signAllowed(String sign, boolean poses) { return signAllowed(sign,poses,false); }
    public static boolean signAllowed(String sign, boolean poses, boolean geometry) {
        return HandSignObservation.BASIC_SIGNS.contains(sign) || poses && ("ok_ring".equals(sign)?geometry:DERIVED_SIGNS.contains(sign));
    }
}
