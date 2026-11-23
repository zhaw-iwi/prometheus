package fixtures.behaviour;
import com.google.gson.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.hand.HandActions;
public final class OutputSequenceFixture {
    private OutputSequenceFixture(){}
    public static JsonObject counting(int from,int to,int hold){
        var action=new JsonObject();action.addProperty("kind","sequence");var steps=new JsonArray();
        for(int n=from;n<=to;n++){
            var pose=new JsonObject();pose.addProperty("kind","pose");var hands=new JsonArray();
            for(int side=0;side<(n>5?2:1);side++){
                var hand=new JsonObject();hand.addProperty("side",side==0?"right":"left");var values=new JsonObject();int i=side*5;
                for(String finger:new String[]{"thumb","index","middle","ring","little"})values.addProperty(finger,i++<n?0:1);
                hand.add("fingers",values);hands.add(hand);
            }pose.add("hands",hands);var step=new JsonObject();step.add("action",pose);step.addProperty("holdMs",hold);steps.add(step);
        }action.add("steps",steps);return action;
    }
    public static BehaviourPlan plan(JsonObject sequence){var motion=new JsonObject();motion.add("handAction",HandActions.pose(sequence));return new BehaviourPlan(null,null,motion,null);}
    public static JsonObject command(JsonObject sequence){var command=new JsonObject();command.addProperty("intent","ACTION");command.addProperty("kind","HAND_SEQUENCE");command.add("value",sequence);command.addProperty("silent",true);var root=new JsonObject();root.add("command",command);return root;}
}
