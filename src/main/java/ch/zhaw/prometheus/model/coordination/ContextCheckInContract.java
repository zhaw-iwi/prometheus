package ch.zhaw.prometheus.model.coordination;

import java.util.Set;
import com.google.gson.JsonObject;

/** One fictional question/answer encounter, interrupted by explicit simulated scene evidence. */
public record ContextCheckInContract(String question,String yesReply,String noReply) {
    public static ContextCheckInContract parse(JsonObject value) {
        CoordinationAgreement.exact(value,Set.of("kind","question","yesReply","noReply","startCount","postponeAt","departure","resume"));
        if(!"CONTEXT_CHECKIN".equals(value.get("kind").getAsString()) || integer(value,"startCount")!=1
                || integer(value,"postponeAt")!=2 || !"END".equals(value.get("departure").getAsString())
                || !"EXPLICIT_WHEN_ONE".equals(value.get("resume").getAsString())) throw new IllegalArgumentException("Unsupported context policy");
        String question=words(value,"question"),yes=words(value,"yesReply"),no=words(value,"noReply");
        if(!question.endsWith("?") || question.chars().filter(c->c=='?').count()!=1 || yes.contains("?") || no.contains("?"))
            throw new IllegalArgumentException("A check-in has one binary question and a final acknowledgment");
        return new ContextCheckInContract(question,yes,no);
    }
    private static int integer(JsonObject value,String field) {
        var raw=value.getAsJsonPrimitive(field);if(!raw.isNumber())throw new IllegalArgumentException("Numeric count required");
        return raw.getAsBigDecimal().intValueExact();
    }
    private static String words(JsonObject value,String field) {
        var raw=value.getAsJsonPrimitive(field);
        if(!raw.isString() || raw.getAsString().isBlank() || raw.getAsString().length()>200) throw new IllegalArgumentException("Invalid check-in wording");
        return raw.getAsString().strip();
    }
    public String review() {
        return "After acceptance, a fresh simulated scene with one visible person starts the check-in. "
                +"Two or more postpone it; Resume is required once one person is visible again. "
                +"Departure ends an ongoing encounter; return alone cannot restart it. Unknown evidence pauses without establishing absence. "
                +"One question and one answer response are authorized. Counts do not establish identity or real privacy.";
    }
}
