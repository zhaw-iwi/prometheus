package fixtures.behaviour;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Equivalent provider answers; no production encoder is used to construct the expected result. */
public final class TaskReplyFixture {
    private TaskReplyFixture() {}

    public static JsonObject plan(String speech, boolean compact) {
        var plan = JsonParser.parseString(compact ? """
                {"nv":{"g":"ACKNOWLEDGE","f":["gentleSmile",0.35],
                 "z":["toward_user","person"],"m":[0.85,0.2]},"motion":{"handSign":"paper"}}
                """ : """
                {"nonVerbal":{"gesture":"ACKNOWLEDGE",
                 "facialExpression":{"type":"gentleSmile","intensity":0.35},
                 "gaze":{"direction":"toward_user","focus":"person"},
                 "motion":{"stillness":0.85,"energy":0.2}},"motion":{"handSign":"paper"}}
                """).getAsJsonObject();
        if (speech != null) plan.addProperty("speech", speech);
        return plan;
    }

    public static String update(String operation, String task, String speech, boolean compact) {
        var update = new JsonObject();
        update.addProperty("operation", operation);
        update.add("task", JsonParser.parseString(task));
        update.add("reply", plan(speech, compact));
        return update.toString();
    }
}
