package ch.zhaw.prometheus.agentdefs.tdsr.aisha;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ch.zhaw.prometheus.agentdefs.tdsr.aisha.AishaCatalog.Candidate;
import ch.zhaw.prometheus.agentdefs.tdsr.aisha.AishaCatalog.Entry;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

@Entity
public class AishaCatalogPolicy extends Policy {
    static final int MAX_CANDIDATES = 3;
    static final int MAX_CONTEXT_EVENTS = 4;
    static final int MAX_SPEECH_CODE_POINTS = 700;

    static final String GREETING = "مرحباً بكم. أنا عائشة من استثمر قطر، ويسعدني أن أجيب عن "
            + "أسئلتكم حول الاستثمار وممارسة الأعمال في دولة قطر.";
    static final String OUT_OF_SCOPE = "عذراً، لا أملك إجابة معتمدة عن هذا السؤال ضمن دليلنا الحالي. "
            + "يمكنكم التواصل معنا للحصول على المساعدة.";

    private static final String SYSTEM_PROMPT = """
            أنت عائشة، الممثلة الرسمية لوكالة ترويج الاستثمار في قطر «استثمر قطر».
            أجيبي بالعربية الفصحى الحديثة فقط وبصوت مهني ودافئ وواثق.
            اختاري answerId واحداً فقط من المرشحين المقدمين، ثم أعيدي صياغة الإجابة المعتمدة
            باختصار من دون إضافة معلومات أو أرقام أو وعود أو روابط غير موجودة فيها.
            حافظي حرفياً على كل حقيقة محمية. لا تستخدمي Markdown أو HTML أو قوائم.
            أعيدي كائن JSON فقط بالشكل: {"answerId":"id","speech":"النص العربي"}
            """;

    @Transient
    private AishaCatalog injectedCatalog;

    public AishaCatalogPolicy() {
    }

    AishaCatalogPolicy(AishaCatalog catalog) {
        this.injectedCatalog = catalog;
    }

    @Override
    public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        return plan(GREETING, "POLITE");
    }

    @Override
    public BehaviourPlan onRespond(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        LatestUtterance latest = latestUserUtterance(events);
        if (latest == null) {
            return null;
        }
        List<Candidate> candidates = catalog().candidates(latest.payload(), MAX_CANDIDATES);
        if (candidates.isEmpty()) {
            return plan(OUT_OF_SCOPE, "UNCERTAIN");
        }

        List<PromptMessage> messages = promptMessages(events, latest.index(), assembler, latest.payload(), candidates);
        JsonElement extracted = languageModelGateway.extract(messages);
        ValidatedAnswer answer = validate(extracted, candidates);
        if (answer == null) {
            Entry safest = candidates.get(0).entry();
            return plan(safest.approvedAnswerAr(), safest.gesture());
        }
        return plan(answer.speech(), answer.entry().gesture());
    }

    @Override
    public String summarise(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        return null;
    }

    @Override
    public String describe() {
        return "Aisha Invest Qatar catalog policy: Arabic-only, candidate-bounded, one structured model call, "
                + "approved-fact fallback, and deterministic semantic gestures.";
    }

    private AishaCatalog catalog() {
        if (this.injectedCatalog == null) {
            this.injectedCatalog = AishaCatalog.loadDefault();
        }
        return this.injectedCatalog;
    }

    private static List<PromptMessage> promptMessages(EventHistory events, int latestIndex,
            PromptMessageAssembler assembler, String question, List<Candidate> candidates) {
        List<PromptMessage> messages = new ArrayList<>();
        messages.add(PromptMessage.system(SYSTEM_PROMPT));
        if (events != null && assembler != null) {
            List<Event> history = events.toList();
            int start = Math.max(0, latestIndex - MAX_CONTEXT_EVENTS);
            for (int index = start; index < latestIndex; index++) {
                Event event = history.get(index);
                if (isConversationEvent(event)) {
                    messages.add(assembler.toPromptMessage(event));
                }
            }
        }
        messages.add(PromptMessage.user(candidatePrompt(question, candidates)));
        return List.copyOf(messages);
    }

    private static String candidatePrompt(String question, List<Candidate> candidates) {
        StringBuilder prompt = new StringBuilder("السؤال الحالي:\n")
                .append(question.trim())
                .append("\n\nالإجابات المرشحة المعتمدة:\n");
        for (Candidate candidate : candidates) {
            Entry entry = candidate.entry();
            prompt.append("- answerId: ").append(entry.id()).append('\n')
                    .append("  الإجابة المعتمدة: ").append(entry.approvedAnswerAr()).append('\n');
            if (!entry.protectedFacts().isEmpty()) {
                prompt.append("  الحقائق المحمية: ")
                        .append(String.join(" | ", entry.protectedFacts()))
                        .append('\n');
            }
        }
        return prompt.toString().trim();
    }

    private static ValidatedAnswer validate(JsonElement extracted, List<Candidate> candidates) {
        if (extracted == null || !extracted.isJsonObject()) {
            return null;
        }
        JsonObject object = extracted.getAsJsonObject();
        if (!isString(object, "answerId") || !isString(object, "speech")) {
            return null;
        }
        String answerId = object.get("answerId").getAsString().trim();
        Entry selected = candidates.stream()
                .map(Candidate::entry)
                .filter(entry -> entry.id().equals(answerId))
                .findFirst()
                .orElse(null);
        if (selected == null) {
            return null;
        }
        String speech = object.get("speech").getAsString().trim().replaceAll("\\s+", " ");
        if (!validArabicSpeech(speech) || !preservesProtectedFacts(speech, selected.protectedFacts())) {
            return null;
        }
        return new ValidatedAnswer(selected, speech);
    }

    private static boolean isString(JsonObject object, String property) {
        return object.has(property) && object.get(property).isJsonPrimitive()
                && object.getAsJsonPrimitive(property).isString();
    }

    private static boolean validArabicSpeech(String speech) {
        if (speech.isBlank() || speech.codePointCount(0, speech.length()) > MAX_SPEECH_CODE_POINTS
                || speech.indexOf('<') >= 0 || speech.indexOf('>') >= 0 || speech.indexOf('`') >= 0
                || speech.indexOf('{') >= 0 || speech.indexOf('}') >= 0) {
            return false;
        }
        boolean containsLatinLetters = speech.codePoints()
                .anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN);
        if (containsLatinLetters) {
            return false;
        }
        long arabicLetters = speech.codePoints()
                .filter(codePoint -> codePoint >= 0x0600 && codePoint <= 0x06ff)
                .count();
        return arabicLetters >= 10;
    }

    private static boolean preservesProtectedFacts(String speech, List<String> protectedFacts) {
        for (String protectedFact : protectedFacts) {
            if (!speech.contains(protectedFact)) {
                return false;
            }
        }
        return true;
    }

    private static LatestUtterance latestUserUtterance(EventHistory events) {
        if (events == null) {
            return null;
        }
        List<Event> history = events.toList();
        for (int index = history.size() - 1; index >= 0; index--) {
            Event event = history.get(index);
            if (Event.TYPE_USER_UTTERANCE.equals(event.getType())
                    && Event.ACTOR_USER.equals(event.getActor())
                    && Event.KIND_OBSERVATION.equals(event.getKind())
                    && event.getPayload() != null
                    && !event.getPayload().isBlank()) {
                return new LatestUtterance(index, event.getPayload());
            }
        }
        return null;
    }

    private static boolean isConversationEvent(Event event) {
        return event != null && (Event.TYPE_USER_UTTERANCE.equals(event.getType())
                || Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType()));
    }

    private static BehaviourPlan plan(String speech, String gesture) {
        BehaviourPlan plan = BehaviourPlan.speechOnly(speech);
        JsonObject nonVerbal = new JsonObject();
        nonVerbal.addProperty("gesture", gesture);
        plan.setNonVerbal(nonVerbal);
        return plan;
    }

    private record LatestUtterance(int index, String payload) {
    }

    private record ValidatedAnswer(Entry entry, String speech) {
    }
}
