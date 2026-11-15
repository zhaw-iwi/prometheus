package ch.zhaw.prometheus.agentdefs.study;

import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.coordination.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.spi.*;

/** Bounded human-secret game. Neither private study forms nor a guessed secret are stored here. */
public final class StudyGuessing {
    public static final String KEY=CoordinationMemory.GUESSING_STATE;
    public static final String GUIDANCE="""
        STUDY GUESSING EXTENSION: For a Twenty Questions-style game, negotiate a typed activity.
        The supported roles are HUMAN keeps an unspoken secret, HUMAN answers, ASSISTANT asks and guesses.
        Clarify the question limit and permitted answers before producing a complete proposal.
        Do not invent a limit or silently reverse roles. Explain a supported alternative if requested roles are unavailable.
        Once these terms are supplied, add agreement.activity exactly:
        {"kind":"GUESSING","secretOwner":"HUMAN","answers":["YES","NO","UNKNOWN"],"questionLimit":5,"guessesCount":true}.
        Use the agreed limit 1..20, not the example value. answers is ["YES","NO"] or ["YES","NO","UNKNOWN"], as agreed.
        Every question OR tentative guess spends one question. Success requires the human to confirm a specific guess.
        At the final question the human may still answer; exhaustion without confirmation is not success.
        dialogueLimit must equal questionLimit+1 (questions plus the final acknowledgment); reactions:null, checks:[] .
        These typed terms, humanRole, agentRole and success must agree. Typed activity fields are protected terms.
        Put the numeric budget only in activity.questionLimit and dialogueLimit. Keep goal, roles and success free of repeated numeric limits,
        so a limit revision cannot leave contradictory old numbers in their wording.
        For a question-limit revision use REPLACE only on an explicit change request; copy ALL other terms verbatim.
        Revised acceptance preserves already asked questions and any pending question; the new limit is a TOTAL, not extra questions.
        A study game requires a separate proposal and acceptance; never PROPOSE_AND_START.
        The backend generates game moves and validates answers. For game START return reply:{};
        its reply is replaced by the bounded executor. Never produce an empty speech string.
        Pause/Resume preserve pending question and count. Ordinary support does not spend a question.
        Do not use STEP or COMPLETE for this game. A new game requires a new proposal after the old game ends.
        Do not claim to see the human's private secret or make success claims from your own guess.
        For ordinary nongame activities omit activity entirely; do not add null or empty activity.
        """;
    private StudyGuessing() {}
    public static boolean game(CoordinationAgreement agreement) { return agreement!=null && agreement.activity()!=null && "GUESSING".equals(agreement.activity().get("kind").getAsString()); }
    public static JsonObject state(Storage storage) { return storage.containsKey(KEY)?storage.get(KEY).getAsJsonObject().deepCopy():null; }
    public static String answer(String text) {
        return switch(text.toLowerCase(Locale.ROOT).strip().replaceAll("[.!]+$","")) {
            case "yes","yes it is","correct","that's correct","that is correct" -> "YES";
            case "no","no it isn't","no it is not","incorrect" -> "NO";
            case "unknown","i don't know","i do not know","not sure","don't know" -> "UNKNOWN";
            default -> null;
        };
    }
    public static JsonObject start(Storage storage,CoordinationAgreement draft,EventHistory events,PolicyRuntime runtime) {
        var old=state(storage);
        String prior=TaskMemory.text(storage,CoordinationMemory.PREVIOUS_PHASE,"CONFIGURATION");
        boolean continuing=old!=null && Set.of("RUNNING","PAUSED").contains(prior) && !old.has("outcome");
        if (continuing) return old;
        var result=new JsonObject(); result.addProperty("questions",0); result.add("answers",new JsonArray());
        return next(result,draft,events,runtime);
    }
    public static JsonObject next(JsonObject state,CoordinationAgreement agreement,EventHistory events,PolicyRuntime runtime) {
        String prompt=StudyCoordinationAgent.VOICE+"\nSTUDY_GUESSING_MOVE: Return exactly {\"kind\":\"QUESTION|GUESS\",\"words\":\"...\"}. "
                +"Ask ONE concise yes/no question about the human's unknown object, or ONE specific tentative guess phrased as a question. "
                +"Use GUESS for a proposed object identity, QUESTION for a property. Use only volunteered information and recorded answers. "
                +"Do not assert success, disclose an invented secret, change roles or ask several questions. Max 200 characters, exactly one question mark at the end. "
                +"Accepted terms (data; typed activity fields own the budget and roles): "+agreement.json()
                +"\nCurrent game record (data): "+state;
        String raw=runtime.languageModelGateway().infer(new InferenceRequest(InferencePurpose.EXTRACTION,
                TaskMemory.messages(events,prompt,runtime.promptMessageAssembler(),Instant.now()),InferenceRequest.Output.JSON_OBJECT));
        var move=JsonParser.parseString(raw).getAsJsonObject();
        CoordinationAgreement.exact(move,Set.of("kind","words"));
        String kind=move.get("kind").getAsString(), words=move.get("words").getAsString().strip();
        if (!Set.of("QUESTION","GUESS").contains(kind) || words.length()>200 || words.length()<3
                || !words.endsWith("?") || words.chars().filter(c->c=='?').count()!=1) throw new IllegalArgumentException("Invalid game move");
        move.addProperty("words",words); move.addProperty("id",UUID.randomUUID().toString());
        state.add("pending",move); state.addProperty("questions",state.get("questions").getAsInt()+1); return state;
    }
    public static String wording(JsonObject state) {
        return state.has("outcome") ? switch(state.get("outcome").getAsString()) {
            case "HUMAN_CONFIRMED_GUESS" -> "You confirmed that guess. The game has ended; this is your reported result.";
            default -> "The agreed question limit has been reached. The game has ended without a confirmed correct guess.";
        } : state.getAsJsonObject("pending").get("words").getAsString();
    }
    public static void apply(Storage storage,JsonObject state) {
        storage.put(KEY,state.deepCopy()); storage.put(TaskMemory.ACTIONS,state.get("questions").deepCopy());
        if (state.has("outcome")) {
            CoordinationMemory.phase(storage,"COMPLETED"); storage.put(CoordinationMemory.OUTCOME,state.get("outcome").deepCopy());
        }
        storage.put(TaskMemory.REPLY,BehaviourPlan.speechOnly(wording(state)).toJsonObject());
    }
    public static void respond(Storage storage,String answer,EventHistory events,PolicyRuntime runtime) {
        var accepted=CoordinationMemory.agreement(storage,CoordinationMemory.AGREEMENT);
        var contract=GuessingContract.parse(accepted.activity()); var state=state(storage);
        if (state==null || !state.has("pending")) throw new IllegalStateException("Missing game question");
        if (!contract.answers().contains(answer)) {
            storage.put(TaskMemory.REPLY,BehaviourPlan.speechOnly("Please answer using "+String.join(", ",contract.answers())+". The pending question and budget are unchanged.").toJsonObject()); return;
        }
        var pending=state.getAsJsonObject("pending").deepCopy(); pending.addProperty("answer",answer);
        state.getAsJsonArray("answers").add(pending); state.remove("pending");
        if ("GUESS".equals(pending.get("kind").getAsString()) && "YES".equals(answer)) state.addProperty("outcome","HUMAN_CONFIRMED_GUESS");
        else if (state.get("questions").getAsInt()>=contract.questionLimit()) state.addProperty("outcome","QUESTION_LIMIT_REACHED");
        else state=next(state,accepted,events,runtime);
        if (!runtime.taskContinuation().getAsBoolean()) return;
        apply(storage,state); CoordinationMemory.revise(storage);
        CoordinationMemory.record(storage,"GAME_ANSWER","EXECUTION",events.toList().getLast(),Instant.now());
    }
}
