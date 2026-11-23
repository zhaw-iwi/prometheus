package ch.zhaw.prometheus.model.hand;

import java.util.Set;
import com.google.gson.*;
import static ch.zhaw.prometheus.model.hand.HandSignObservation.*;

/** Frame identity is part of an agreement, never inferred from a direction label. */
public record SpatialFrame(String id, int revision, String space, boolean mirrored) {
    public static final SpatialFrame ROBOT = new SpatialFrame("valerian.robot",1,"robot_local",false);
    public static SpatialFrame parse(JsonElement raw) {
        var o=raw.getAsJsonObject();exact(o,"id","revision","space","mirrored");
        String id=identifier(o.get("id")),space=text(o.get("space"));
        integer(o.get("revision"),1,Integer.MAX_VALUE);int revision=o.get("revision").getAsInt();
        if(!Set.of("camera_image","robot_local","virtual_scene").contains(space)
                ||!o.get("mirrored").isJsonPrimitive()||!o.getAsJsonPrimitive("mirrored").isBoolean()||o.get("mirrored").getAsBoolean())
            throw new IllegalArgumentException("Unsupported coordinate frame");
        return new SpatialFrame(id,revision,space,false);
    }
    public JsonObject json() { return new Gson().toJsonTree(this).getAsJsonObject(); }
}
