package ch.zhaw.prometheus.model.task;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.Event;
import fixtures.behaviour.HandSequenceFixture;

class HandSequenceUnitTest {
    final Instant now=Instant.parse("2026-11-23T12:00:00Z");final Storage storage=new Storage();
    @Test void allElevenCountsUseKnownReadingsOnOneOrTwoDistinctHands(){
        for(int count=0;count<=10;count++){
            var sequence=HandSequence.parse(HandSequenceFixture.counts(count>5?"both":"right",count));
            assertEquals(HandSequence.Result.MATCH,sequence.steps().getFirst().read(HandSequenceFixture.event("frame",count,count>5,now),storage,now).result());
            assertEquals(sequence.json(),HandSequence.parse(sequence.json()).json());
            assertEquals(HandSequence.Result.MISMATCH,sequence.steps().getFirst().read(HandSequenceFixture.event("other",count==0?1:count-1,count>5,now),storage,now).result());
        }
    }
    @Test void unknownNeutralMissingAndAmbiguousHandsCannotManufactureACount(){
        var zero=HandSequence.parse(HandSequenceFixture.counts("both",0)).steps().getFirst();
        for(String unknown:List.of("null","0.5")){
            var p=HandSequenceFixture.pose("frame",0,true,now);var h=p.getAsJsonArray("hands").get(0).getAsJsonObject();h.getAsJsonObject("fingers").add("thumb",JsonParser.parseString(unknown));h.getAsJsonObject("fingerStates").add("thumb",unknown.equals("null")?JsonNull.INSTANCE:new JsonPrimitive("neutral"));
            assertEquals(HandSequence.Result.UNKNOWN,zero.read(Event.observation(Event.TYPE_HAND_POSE,"camera",p.toString()),storage,now).result());
        }
        assertEquals(HandSequence.Result.UNKNOWN,zero.read(HandSequenceFixture.event("missing",0,false,now),storage,now).result());
        var p=HandSequenceFixture.pose("same-side",0,true,now);p.getAsJsonArray("hands").get(1).getAsJsonObject().addProperty("handedness","right");assertEquals(HandSequence.Result.UNKNOWN,zero.read(Event.observation(Event.TYPE_HAND_POSE,"camera",p.toString()),storage,now).result());
        var either=HandSequence.parse(HandSequenceFixture.counts("either",0)).steps().getFirst();assertEquals(HandSequence.Result.UNKNOWN,either.read(HandSequenceFixture.event("two",0,true,now),storage,now).result());
    }
    @Test void bothHandAssociationIsIndependentOfArrayOrder(){
        var step=HandSequence.parse(HandSequenceFixture.counts("both",7)).steps().getFirst();var p=HandSequenceFixture.pose("frame",7,true,now);var original=step.read(Event.observation(Event.TYPE_HAND_POSE,"camera",p.toString()),storage,now);var reversed=new JsonArray();reversed.add(p.getAsJsonArray("hands").get(1));reversed.add(p.getAsJsonArray("hands").get(0));p.add("hands",reversed);
        assertEquals(original,step.read(Event.observation(Event.TYPE_HAND_POSE,"camera",p.toString()),storage,now));assertEquals(Map.of("left","left-track","right","right-track"),original.hands());
    }
    @Test void contractRejectsNestingImpossibleCountsAndUnachievableTiming(){
        for(String key:List.of("dwellMs","maximumGapMs","maximumDurationMs")){
            var invalid=HandSequenceFixture.counts("right",1,2,3);invalid.addProperty(key,299);assertThrows(IllegalArgumentException.class,()->HandSequence.parse(invalid));
        }
        assertThrows(IllegalArgumentException.class,()->HandSequence.parse(HandSequenceFixture.counts("right",6)));
        var shortTotal=HandSequenceFixture.counts("both",0,1,2);shortTotal.addProperty("maximumDurationMs",600);assertThrows(IllegalArgumentException.class,()->HandSequence.parse(shortTotal));
        var nested=HandSequenceFixture.counts("right",1);nested.getAsJsonArray("steps").get(0).getAsJsonObject().addProperty("kind","sequence");assertThrows(IllegalArgumentException.class,()->HandSequence.parse(nested));
        assertThrows(IllegalArgumentException.class,()->HandSequence.parse(HandSequenceFixture.counts("both",new int[13])));
    }
}
