package ch.zhaw.prometheus.model.hand;

import java.util.*;
import com.google.gson.*;
import static ch.zhaw.prometheus.model.hand.HandSignObservation.*;

/** Projected camera geometry, separate from joint bend, visibility and physical contact. */
public final class HandGeometry {
    public static final List<String> FINGERS=List.of("thumb","index","middle","ring","little");
    public static final Set<String> AXES=Set.of("palm_normal","index_axis","thumb_axis");
    public static final Set<String> PAIRS;
    static {
        var pairs=new HashSet<String>();
        for(int i=0;i<FINGERS.size();i++)for(int j=i+1;j<FINGERS.size();j++)pairs.add(FINGERS.get(i)+"_"+FINGERS.get(j));
        PAIRS=Set.copyOf(pairs);
    }
    private HandGeometry() {}
    public static String pair(List<String> fingers) {
        if(fingers.size()!=2||!FINGERS.containsAll(fingers)||fingers.get(0).equals(fingers.get(1)))throw new IllegalArgumentException("Invalid finger pair");
        return FINGERS.indexOf(fingers.get(0))<FINGERS.indexOf(fingers.get(1))?String.join("_",fingers):fingers.get(1)+"_"+fingers.get(0);
    }
    public static void validate(JsonObject geometry) {
        exact(geometry,"schemaVersion","tipDistances","spreads","axes");integer(geometry.get("schemaVersion"),1,1);
        for(String map:List.of("tipDistances","spreads")) {
            var values=geometry.getAsJsonObject(map);if(!values.keySet().equals(PAIRS))throw new IllegalArgumentException("Invalid geometry pairs");
            for(var value:values.asMap().values())if(!value.isJsonNull())range(value,0,map.equals("spreads")?1:4);
        }
        var axes=geometry.getAsJsonObject("axes");if(!axes.keySet().equals(AXES))throw new IllegalArgumentException("Invalid geometry axes");
        for(var value:axes.asMap().values())if(!value.isJsonNull()) {
            var axis=value.getAsJsonObject();exact(axis,"x","y");
            double x=range(axis.get("x"),-1,1),y=range(axis.get("y"),-1,1);
            if(Math.abs(Math.hypot(x,y)-1)>.001)throw new IllegalArgumentException("Geometry axis must be normalized");
        }
    }
    public static void validateEvidence(JsonObject hand) {
        var geometry=hand.getAsJsonObject("geometry");var fingers=hand.getAsJsonObject("fingers");
        for(String pair:PAIRS) {
            var names=pair.split("_");
            if(fingers.get(names[0]).isJsonNull()||fingers.get(names[1]).isJsonNull())
                for(String map:List.of("tipDistances","spreads"))if(!geometry.getAsJsonObject(map).get(pair).isJsonNull())
                    throw new IllegalArgumentException("Unknown fingers cannot establish geometry");
        }
        for(String name:List.of("index","thumb"))if(fingers.get(name).isJsonNull()&&!geometry.getAsJsonObject("axes").get(name+"_axis").isJsonNull())
            throw new IllegalArgumentException("Unknown finger cannot establish an axis");
        if("unknown".equals(text(hand.get("handedness")))&&!geometry.getAsJsonObject("axes").get("palm_normal").isJsonNull())
            throw new IllegalArgumentException("Unknown side cannot orient the palm normal");
    }
    public static double range(JsonElement value,double min,double max) {
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected finite geometry number");
        double number=value.getAsDouble();if(!Double.isFinite(number)||number<min||number>max)throw new IllegalArgumentException("Geometry number out of range");
        return number;
    }
    public static double angle(String direction) {
        return switch(direction) {
            case "right" -> 0;case "down_right" -> Math.PI/4;case "down" -> Math.PI/2;case "down_left" -> 3*Math.PI/4;
            case "left" -> Math.PI;case "up_left" -> -3*Math.PI/4;case "up" -> -Math.PI/2;case "up_right" -> -Math.PI/4;
            default -> throw new IllegalArgumentException("Unknown plane direction");
        };
    }
}
