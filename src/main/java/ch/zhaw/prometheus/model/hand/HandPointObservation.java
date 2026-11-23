package ch.zhaw.prometheus.model.hand;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import com.google.gson.*;
import static ch.zhaw.prometheus.model.hand.HandSignObservation.*;

/** One unambiguous pointing hand, or an explicit unavailable estimate. */
public record HandPointObservation(String observationId,String source,Instant observed,SpatialFrame frame,
        String handId,String handedness,String direction,String validity,JsonObject ray,JsonObject resolution) {
    public String identity() {return source+"/"+observationId;}
    public static HandPointObservation parse(String payload) {
        try {
            if(payload==null||payload.getBytes(StandardCharsets.UTF_8).length>16384)throw new IllegalArgumentException();
            var o=JsonParser.parseString(payload).getAsJsonObject();
            exact(o,"schemaVersion","observationId","source","ts","frame","handId","handedness","direction","validity","ray","resolution");
            integer(o.get("schemaVersion"),1,1);String id=identifier(o.get("observationId")),source=identifier(o.get("source"));
            String ts=text(o.get("ts"));if(ts.length()>40)throw new IllegalArgumentException();
            var frame=SpatialFrame.parse(o.get("frame"));if(!frame.space().equals("camera_image"))throw new IllegalArgumentException();
            String hand=identifier(o.get("handId")),side=text(o.get("handedness")),validity=text(o.get("validity"));
            if(!Set.of("left","right","unknown").contains(side)||!Set.of("valid","unavailable","ambiguous","boundary","foreshortened").contains(validity))throw new IllegalArgumentException();
            String direction=o.get("direction").isJsonNull()?null:text(o.get("direction"));
            JsonObject ray=o.get("ray").isJsonNull()?null:o.getAsJsonObject("ray");
            if(validity.equals("valid")) {
                if(direction==null||ray==null)throw new IllegalArgumentException();HandGeometry.angle(direction);
                exact(ray,"origin","vector","aspectRatio");var origin=ray.getAsJsonObject("origin");var vector=ray.getAsJsonObject("vector");
                if(HandGeometry.range(ray.get("aspectRatio"),0,10)==0)throw new IllegalArgumentException();
                exact(origin,"x","y");exact(vector,"x","y");
                HandGeometry.range(origin.get("x"),0,1);HandGeometry.range(origin.get("y"),0,1);
                double x=HandGeometry.range(vector.get("x"),-1,1),y=HandGeometry.range(vector.get("y"),-1,1);
                if(Math.abs(Math.hypot(x,y)-1)>.001)throw new IllegalArgumentException();
                double delta=Math.atan2(Math.sin(Math.atan2(y,x)-HandGeometry.angle(direction)),Math.cos(Math.atan2(y,x)-HandGeometry.angle(direction)));
                if(Math.abs(Math.toDegrees(delta))>18.5)throw new IllegalArgumentException("Ambiguous direction boundary");
            } else if(direction!=null||ray!=null)throw new IllegalArgumentException("Unavailable pointing cannot claim a ray");
            var resolution=o.getAsJsonObject("resolution");exact(resolution,"status");
            if(!"unresolved".equals(text(resolution.get("status"))))throw new IllegalArgumentException("Target selection is not enabled");
            return new HandPointObservation(id,source,Instant.parse(ts),frame,hand,side,direction,validity,ray==null?null:ray.deepCopy(),resolution.deepCopy());
        } catch(RuntimeException invalid) {throw new IllegalArgumentException("Invalid planar pointing observation",invalid);}
    }
}
