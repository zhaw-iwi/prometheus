package ch.zhaw.prometheus.model.hand;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import static ch.zhaw.prometheus.model.hand.HandSignObservation.*;

/** Common camera capture header. Different interpretations keep this same identity. */
public record HandFrame(String observationId, String source, Instant observed, JsonArray hands) {
    public String identity() { return source + "/" + observationId; }
    public static HandFrame parse(String payload, int version) {
        if (payload == null || payload.getBytes(StandardCharsets.UTF_8).length > 16384) throw new IllegalArgumentException("invalid_contract");
        var data = JsonParser.parseString(payload).getAsJsonObject();
        if(data.has("captureIndex")){exact(data,"schemaVersion","observationId","source","ts","frame","hands","captureIndex");integer(data.get("captureIndex"),1,Integer.MAX_VALUE);}
        else exact(data,"schemaVersion","observationId","source","ts","frame","hands");
        integer(data.get("schemaVersion"),version,version);
        String id=identifier(data.get("observationId")),source=identifier(data.get("source")),ts=text(data.get("ts"));
        if(ts.length()>40)throw new IllegalArgumentException();
        Instant observed=Instant.parse(ts);
        var frame=data.getAsJsonObject("frame");exact(frame,"id","revision","space","mirrored");
        identifier(frame.get("id"));integer(frame.get("revision"),1,Integer.MAX_VALUE);
        if(!"camera_image".equals(text(frame.get("space")))||!frame.get("mirrored").isJsonPrimitive()
                ||!frame.get("mirrored").getAsJsonPrimitive().isBoolean()||frame.get("mirrored").getAsBoolean())throw new IllegalArgumentException();
        var hands=data.getAsJsonArray("hands");if(hands.size()>2)throw new IllegalArgumentException();
        var ids=new HashSet<String>();
        for(var item:hands){var hand=item.getAsJsonObject();
            if(!ids.add(identifier(hand.get("id")))||!Set.of("left","right","unknown").contains(text(hand.get("handedness"))))throw new IllegalArgumentException();
        }
        return new HandFrame(id,source,observed,hands.deepCopy());
    }
}
