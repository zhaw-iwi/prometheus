package ch.zhaw.prometheus.model.hand;

import java.util.Set;
import com.google.gson.JsonPrimitive;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;

/** Additive persisted opt-ins: older named-sign profiles keep their six-sign contract. */
public final class HandFeatures {
    public static final String POSES = "hand.poses.v2";
    public static final Set<String> DERIVED_SIGNS = Set.of("shaka", "horns", "middle_finger");
    private HandFeatures() {}
    public static boolean poses(AgentInteractionProfile profile) { return profile != null && profile.getProfileTags().contains(POSES); }
    public static boolean poses(Storage storage) { return storage.containsKey(POSES) && new JsonPrimitive(true).equals(storage.get(POSES)); }
    public static boolean signAllowed(String sign, boolean poses) { return HandSignObservation.BASIC_SIGNS.contains(sign) || poses && DERIVED_SIGNS.contains(sign); }
}
