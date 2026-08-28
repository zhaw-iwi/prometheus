package ch.zhaw.prometheus.agentdefs.tdsr.aisha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class AishaCatalogPolicyUnitTest {

    private final PromptMessageAssembler assembler = new PromptMessageAssembler();

    @Test
    void startUsesFixedArabicGreetingAndPoliteGestureWithoutModelCall() {
        RecordingGateway gateway = new RecordingGateway(null);
        AishaCatalogPolicy policy = new AishaCatalogPolicy(AishaCatalog.loadDefault());

        BehaviourPlan plan = policy.onStart(state(policy), new EventHistory(), this.assembler, gateway);

        assertEquals(AishaCatalogPolicy.GREETING, plan.getSpeech());
        assertEquals("POLITE", gesture(plan));
        assertEquals(0, gateway.extractCalls);
    }

    @Test
    void matchedQuestionUsesOneBoundedStructuredCallAndDeterministicGesture() {
        RecordingGateway gateway = new RecordingGateway(JsonParser.parseString("""
                {"answerId":"invest_qatar_overview","speech":"نحن وكالة ترويج الاستثمار في قطر، ونرافق المستثمرين خلال رحلتهم الاستثمارية."}
                """));
        AishaCatalogPolicy policy = new AishaCatalogPolicy(AishaCatalog.loadDefault());
        EventHistory history = historyWithBoundedContext("ما هي استثمر قطر؟");

        BehaviourPlan plan = policy.onRespond(state(policy), history, this.assembler, gateway);

        assertEquals("نحن وكالة ترويج الاستثمار في قطر، ونرافق المستثمرين خلال رحلتهم الاستثمارية.",
                plan.getSpeech());
        assertEquals("EXPLAIN", gesture(plan));
        assertEquals(1, gateway.extractCalls);
        assertEquals(0, gateway.completeCalls);
        assertTrue(gateway.messages.size() <= AishaCatalogPolicy.MAX_CONTEXT_EVENTS + 2);
        String prompt = gateway.messages.stream().map(PromptMessage::getContent)
                .reduce("", (left, right) -> left + "\n" + right);
        assertFalse(prompt.contains("قديم-لا-يجب-إرساله"));
        assertFalse(prompt.contains("يمكنكم التواصل معنا عبر قنوات استثمر قطر الرسمية"));
        assertTrue(prompt.contains("invest_qatar_overview"));
    }

    @Test
    void missingProtectedFactFallsBackToApprovedCatalogAnswer() {
        RecordingGateway gateway = new RecordingGateway(JsonParser.parseString("""
                {"answerId":"qatar_national_vision","speech":"تدعم الرؤية اقتصاداً متنوعاً ومستداماً."}
                """));
        AishaCatalog catalog = AishaCatalog.loadDefault();
        AishaCatalogPolicy policy = new AishaCatalogPolicy(catalog);
        EventHistory history = new EventHistory();
        history.appendEvent(user("ما هي رؤية قطر الوطنية 2030؟"));

        BehaviourPlan plan = policy.onRespond(state(policy), history, this.assembler, gateway);

        assertEquals(catalog.entry("qatar_national_vision").approvedAnswerAr(), plan.getSpeech());
        assertTrue(plan.getSpeech().contains("2030"));
        assertEquals("EXPLAIN", gesture(plan));
        assertEquals(1, gateway.extractCalls);
    }

    @Test
    void latinScriptInSpeechFallsBackToArabicCatalogAnswer() {
        RecordingGateway gateway = new RecordingGateway(JsonParser.parseString("""
                {"answerId":"invest_qatar_overview","speech":"نحن Invest Qatar ونساعد المستثمرين في رحلتهم الاستثمارية."}
                """));
        AishaCatalog catalog = AishaCatalog.loadDefault();
        AishaCatalogPolicy policy = new AishaCatalogPolicy(catalog);
        EventHistory history = new EventHistory();
        history.appendEvent(user("ما هي استثمر قطر؟"));

        BehaviourPlan plan = policy.onRespond(state(policy), history, this.assembler, gateway);

        assertEquals(catalog.entry("invest_qatar_overview").approvedAnswerAr(), plan.getSpeech());
        assertEquals("EXPLAIN", gesture(plan));
        assertEquals(1, gateway.extractCalls);
    }

    @Test
    void unrelatedQuestionUsesFixedFallbackWithoutCallingModel() {
        RecordingGateway gateway = new RecordingGateway(JsonParser.parseString("{}"));
        AishaCatalogPolicy policy = new AishaCatalogPolicy(AishaCatalog.loadDefault());
        EventHistory history = new EventHistory();
        history.appendEvent(user("هل ستمطر في قطر مساء اليوم؟"));

        BehaviourPlan plan = policy.onRespond(state(policy), history, this.assembler, gateway);

        assertEquals(AishaCatalogPolicy.OUT_OF_SCOPE, plan.getSpeech());
        assertEquals("UNCERTAIN", gesture(plan));
        assertEquals(0, gateway.extractCalls);
    }

    @Test
    void responseWithoutUserUtteranceProducesNothing() {
        AishaCatalogPolicy policy = new AishaCatalogPolicy(AishaCatalog.loadDefault());

        BehaviourPlan plan = policy.onRespond(state(policy), new EventHistory(), this.assembler,
                new RecordingGateway(null));

        assertNull(plan);
    }

    private static State state(AishaCatalogPolicy policy) {
        return new State("Aisha test", policy, List.of());
    }

    private static EventHistory historyWithBoundedContext(String latestQuestion) {
        EventHistory history = new EventHistory();
        history.appendEvent(user("قديم-لا-يجب-إرساله"));
        history.appendEvent(assistant("إجابة قديمة جداً"));
        history.appendEvent(user("السلام عليكم"));
        history.appendEvent(assistant("وعليكم السلام"));
        history.appendEvent(user("ما الذي تقدمه قطر للمستثمرين؟"));
        history.appendEvent(assistant("توجد فرص متنوعة"));
        history.appendEvent(user(latestQuestion));
        return history;
    }

    private static Event user(String text) {
        return Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, text);
    }

    private static Event assistant(String speech) {
        return Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                BehaviourPlan.speechOnly(speech).toJson());
    }

    private static String gesture(BehaviourPlan plan) {
        assertNotNull(plan);
        assertNotNull(plan.getNonVerbal());
        return plan.getNonVerbal().getAsJsonObject().get("gesture").getAsString();
    }

    private static final class RecordingGateway implements LanguageModelGateway {
        private final JsonElement extraction;
        private int extractCalls;
        private int completeCalls;
        private List<PromptMessage> messages = List.of();

        private RecordingGateway(JsonElement extraction) {
            this.extraction = extraction;
        }

        @Override
        public String complete(List<PromptMessage> messages) {
            this.completeCalls++;
            return "";
        }

        @Override
        public boolean decide(List<PromptMessage> messages) {
            return false;
        }

        @Override
        public JsonElement extract(List<PromptMessage> messages) {
            this.extractCalls++;
            this.messages = List.copyOf(messages);
            return this.extraction;
        }

        @Override
        public JsonElement summarise(List<PromptMessage> messages) {
            return null;
        }

        @Override
        public String summariseOffline(List<PromptMessage> messages) {
            return null;
        }
    }
}
