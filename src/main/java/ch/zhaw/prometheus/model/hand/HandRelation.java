package ch.zhaw.prometheus.model.hand;

import java.util.*;
import com.google.gson.*;
import static ch.zhaw.prometheus.model.hand.HandSignObservation.*;

/** Bounded, inspectable predicates; no executable model-authored geometry. */
public record HandRelation(String kind,List<String> fingers,String operator,Double value,
                           String axis,String direction,Double toleranceDegrees) {
    public static List<HandRelation> parse(JsonElement raw) {
        var array=raw.getAsJsonArray();if(array.size()>6)throw new IllegalArgumentException("Too many hand relations");
        var result=new ArrayList<HandRelation>();var axes=new HashSet<String>();var bounds=new HashMap<String,double[]>();
        for(var item:array) {
            var o=item.getAsJsonObject();String kind=text(o.get("kind"));
            if(kind.equals("orientation")) {
                exact(o,"kind","axis","direction","toleranceDegrees");String axis=text(o.get("axis")),direction=text(o.get("direction"));
                if(!HandGeometry.AXES.contains(axis)||!axes.add(axis))throw new IllegalArgumentException("Conflicting orientation constraints");
                HandGeometry.angle(direction);double tolerance=HandGeometry.range(o.get("toleranceDegrees"),5,45);
                result.add(new HandRelation(kind,null,null,null,axis,direction,tolerance));
            } else {
                exact(o,"kind","fingers","operator","value");if(!Set.of("tip_proximity","spread").contains(kind))throw new IllegalArgumentException("Unknown relation");
                var pair=new ArrayList<String>();for(var finger:o.getAsJsonArray("fingers"))pair.add(text(finger));String key=kind+"/"+HandGeometry.pair(pair);
                pair.sort(Comparator.comparingInt(HandGeometry.FINGERS::indexOf));String op=text(o.get("operator"));
                if(!Set.of("lt","gt").contains(op))throw new IllegalArgumentException("Invalid relation operator");
                double max=kind.equals("spread")?1:2,value=HandGeometry.range(o.get("value"),0,max);
                if(op.equals("lt")&&value==0||op.equals("gt")&&kind.equals("spread")&&value==1)throw new IllegalArgumentException("Impossible relation");
                var limits=bounds.computeIfAbsent(key,ignored->new double[]{0,kind.equals("spread")?1:4});
                if(op.equals("lt"))limits[1]=Math.min(limits[1],value);else limits[0]=Math.max(limits[0],value);
                if(limits[0]>=limits[1])throw new IllegalArgumentException("Conflicting relation bounds");
                result.add(new HandRelation(kind,List.copyOf(pair),op,value,null,null,null));
            }
        }
        return List.copyOf(result);
    }
    public boolean matches(JsonObject geometry) {
        if(geometry==null)return false;
        if(kind.equals("orientation")) {
            var raw=geometry.getAsJsonObject("axes").get(axis);if(raw==null||raw.isJsonNull())return false;
            var vector=raw.getAsJsonObject();double measured=Math.atan2(vector.get("y").getAsDouble(),vector.get("x").getAsDouble());
            double difference=Math.atan2(Math.sin(measured-HandGeometry.angle(direction)),Math.cos(measured-HandGeometry.angle(direction)));
            return Math.abs(Math.toDegrees(difference))<=toleranceDegrees;
        }
        var reading=geometry.getAsJsonObject(kind.equals("spread")?"spreads":"tipDistances").get(HandGeometry.pair(fingers));
        if(reading==null||reading.isJsonNull())return false;
        return operator.equals("lt")?reading.getAsDouble()<value:reading.getAsDouble()>value;
    }
    public static void requireOutput(List<HandRelation> relations,JsonObject fingers) {
        for(var relation:relations) {
            if(relation.kind.equals("orientation")) {
                if(!relation.axis.equals("index_axis")||fingers.get("index").getAsDouble()!=0)
                    throw new IllegalArgumentException("Digital orientation requires a straight index axis");
            } else if(relation.kind.equals("spread")) {
                if(!HandGeometry.pair(relation.fingers).equals("index_middle")||fingers.get("index").getAsDouble()!=0||fingers.get("middle").getAsDouble()!=0
                        ||relation.operator.equals("gt")&&relation.value>=.3)
                    throw new IllegalArgumentException("Digital spread supports straight index/middle up to 54 degrees");
            } else if(!HandGeometry.pair(relation.fingers).equals("thumb_index")||!relation.operator.equals("lt")||relation.value<.05
                    ||fingers.get("thumb").getAsDouble()!=.7||fingers.get("index").getAsDouble()!=.735)
                throw new IllegalArgumentException("Digital proximity requires the supported thumb/index ring preset (.7/.735)");
        }
    }
    public String description() {
        return kind.equals("orientation")?axis+" "+direction+" within "+toleranceDegrees+" degrees"
                :kind+" "+String.join("/",fingers)+" "+operator+" "+value;
    }
}
