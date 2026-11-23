package ch.zhaw.prometheus.model.hand;

import com.google.gson.*;
import static ch.zhaw.prometheus.model.hand.HandSignObservation.*;

/** A scoped destination and the exact configuration that gave it meaning. */
public record TargetRef(String id,int revision,SpatialFrame frame) {
    public static TargetRef parse(JsonElement raw) {
        var o=raw.getAsJsonObject();exact(o,"id","revision","frame");integer(o.get("revision"),1,Integer.MAX_VALUE);
        return new TargetRef(identifier(o.get("id")),o.get("revision").getAsInt(),SpatialFrame.parse(o.get("frame")));
    }
    public JsonObject json(){return new Gson().toJsonTree(this).getAsJsonObject();}
}
