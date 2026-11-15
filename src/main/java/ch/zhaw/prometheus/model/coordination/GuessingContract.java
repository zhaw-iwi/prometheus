package ch.zhaw.prometheus.model.coordination;

import java.util.*;
import com.google.gson.*;

/** Executable question budget; the human's secret is deliberately absent. */
public record GuessingContract(int questionLimit,List<String> answers) {
    public static GuessingContract parse(JsonObject value) {
        CoordinationAgreement.exact(value,Set.of("kind","secretOwner","answers","questionLimit","guessesCount"));
        if (!"GUESSING".equals(value.get("kind").getAsString()) || !"HUMAN".equals(value.get("secretOwner").getAsString())
                || !value.get("guessesCount").isJsonPrimitive() || !value.getAsJsonPrimitive("guessesCount").isBoolean()
                || !value.get("guessesCount").getAsBoolean()) throw new IllegalArgumentException("Unsupported guessing roles or budget");
        if (!value.getAsJsonPrimitive("questionLimit").isNumber()) throw new IllegalArgumentException("Numeric question limit required");
        int limit=value.get("questionLimit").getAsBigDecimal().intValueExact();
        var answers=new ArrayList<String>();
        for (var item:value.getAsJsonArray("answers")) answers.add(item.getAsString());
        if (limit<1 || limit>20 || !(answers.equals(List.of("YES","NO")) || answers.equals(List.of("YES","NO","UNKNOWN"))))
            throw new IllegalArgumentException("Unsupported guessing terms");
        return new GuessingContract(limit,List.copyOf(answers));
    }
    public String review() {
        return "You keep the secret and answer; the assistant asks and guesses. Limit: "+questionLimit
                +" questions, including guesses. Answers: "+String.join(", ",answers)
                +". Only your confirmation of a specific guess establishes reported success. Pause preserves progress; Stop ends early.";
    }
}
