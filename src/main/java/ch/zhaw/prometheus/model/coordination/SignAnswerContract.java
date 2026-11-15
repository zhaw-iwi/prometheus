package ch.zhaw.prometheus.model.coordination;

import java.util.*;
import com.google.gson.*;

/** A bounded list of agreed questions answered by scoped sign observations. */
public record SignAnswerContract(List<String> questions,String yesSign,String noSign,boolean demonstrate) {
    public static final Set<String> SIGNS=Set.of("THUMBS_UP","THUMBS_DOWN","OPEN_PALM");
    public static SignAnswerContract parse(JsonObject value) {
        CoordinationAgreement.exact(value,Set.of("kind","questions","yesSign","noSign","waitSign","resume","demonstrate"));
        if(!"SIGN_ANSWERS".equals(value.get("kind").getAsString()) || !"OPEN_PALM".equals(value.get("waitSign").getAsString())
                || !"EXPLICIT_TEXT".equals(value.get("resume").getAsString()))throw new IllegalArgumentException("Unsupported sign policy");
        String yes=value.get("yesSign").getAsString(),no=value.get("noSign").getAsString();
        if(!Set.of("THUMBS_UP","THUMBS_DOWN").containsAll(List.of(yes,no)) || yes.equals(no))throw new IllegalArgumentException("Distinct answer signs required");
        var questions=new ArrayList<String>();
        for(var raw:value.getAsJsonArray("questions")) {
            if(!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Question text required");
            String words=raw.getAsString().strip();
            if(words.length()>160 || !words.endsWith("?") || words.chars().filter(c->c=='?').count()!=1)throw new IllegalArgumentException("One question per entry required");
            questions.add(words);
        }
        if(questions.isEmpty() || questions.size()>8 || new HashSet<>(questions).size()!=questions.size()
                || !value.getAsJsonPrimitive("demonstrate").isBoolean())throw new IllegalArgumentException("Invalid question list or demonstration policy");
        return new SignAnswerContract(List.copyOf(questions),yes,no,value.get("demonstrate").getAsBoolean());
    }
    public boolean prepared(JsonObject evidence) { return !demonstrate || evidence.keySet().containsAll(SIGNS.stream().map(s->"sign."+s).toList()); }
    public String review() { return "Ask the "+questions.size()+" saved questions in order. "+yesSign.replace('_',' ').toLowerCase(Locale.ROOT)+" means yes; "+noSign.replace('_',' ').toLowerCase(Locale.ROOT)
            +" means no. Open palm waits without answering. Explicit text Resume continues the same question. "
            +(demonstrate?"Demonstrate all three signs before acceptance. ":"")+"Each fresh sign answers only its current question; Stop can end earlier."; }
}
