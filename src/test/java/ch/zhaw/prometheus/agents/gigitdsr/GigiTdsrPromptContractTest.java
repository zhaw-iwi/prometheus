package ch.zhaw.prometheus.agents.gigitdsr;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class GigiTdsrPromptContractTest {

    private static final int MAX_PERSISTED_PROMPT_LENGTH = 8000;

    @Test
    void gestureGuessingGameDefinesGermanGigiDemoContract() {
        Agent agent = new ch.zhaw.prometheus.agentdefs.gigitdsr.GuessingGameWithGestures().createAgent();

        assertTrue(agent.getName().contains("GIGI TDSR"));
        assertTrue(agent.getDescription().contains("Deutschsprachiger"));
        assertTrue(agent.getDescription().contains("Gesten"));

        String prompt = agent.getTotalPolicy().getPromptMessages().get(0).getContent();
        assertTrue(prompt.contains("Du bist GIGI"));
        assertTrue(prompt.contains("Antworte immer auf Deutsch"));
        assertTrue(prompt.contains("BehaviourPlan"));
        assertTrue(prompt.contains("Die Interaktion endet nur"));
        assertTrue(prompt.contains("Eine richtige Bestaetigung deines Tipps allein beendet die Interaktion nicht"));
    }

    @Test
    void gestureGuessingGamePersistsStructuredNonverbalPlanPrompt() throws Exception {
        Agent agent = new ch.zhaw.prometheus.agentdefs.gigitdsr.GuessingGameWithGestures().createAgent();
        PromptPolicy policy = interactionPolicy(agent.getCurrentState());

        assertNotNull(policy.getNonVerbalPlanPrompt());
        assertTrue(policy.getNonVerbalPlanPrompt().contains("Produce STRICT JSON only"));
        assertTrue(policy.getNonVerbalPlanPrompt().contains("gesture"));
        assertTrue(policy.getNonVerbalPlanPrompt().contains("OPEN_QUESTION"));
        assertTrue(policy.getNonVerbalGesturePrompt().contains("Allowed labels only"));
    }

    @Test
    void explicitExitDecisionDoesNotTreatCorrectGuessAsFinal() throws Exception {
        String prompt = prompt(ch.zhaw.prometheus.agentdefs.gigitdsr.GuessingGameWithGestures.class,
                "PROMPT_TO_FINAL");

        assertTrue(prompt.contains("hoher Sicherheit"));
        assertTrue(prompt.contains("das gesamte Gespraech jetzt zu beenden"));
        assertTrue(prompt.contains("eine Bestaetigung, dass dein finaler Tipp richtig war"));
        assertFalse(prompt.contains("der finale Tipp bestaetigt wurde"),
                "Correct-guess confirmation alone must not trigger the final state");
    }

    @Test
    void socialContextAgentDefinesGermanGigiSocialEventContract() {
        Agent agent = new ch.zhaw.prometheus.agentdefs.gigitdsr.SocialContextSensitivity().createAgent();

        assertTrue(agent.getName().contains("GIGI TDSR"));
        assertTrue(agent.getDescription().contains("soziale Kontextwechsel"));

        String prompt = agent.getTotalPolicy().getPromptMessages().get(0).getContent();
        assertTrue(prompt.contains("Du bist GIGI"));
        assertTrue(prompt.contains("Antworte immer auf Deutsch"));
        assertTrue(prompt.contains("obs.social.situation_change"));
        assertTrue(prompt.contains("arrival ->"));
        assertTrue(prompt.contains("crowd_detected ->"));
        assertTrue(prompt.contains("Normale Unterhaltung"));
        assertTrue(prompt.contains("Die Interaktion endet nur"));
    }

    @Test
    void socialContextExitDecisionRequiresExplicitStopIntent() throws Exception {
        String prompt = prompt(ch.zhaw.prometheus.agentdefs.gigitdsr.SocialContextSensitivity.class,
                "PROMPT_TO_FINAL");

        assertTrue(prompt.contains("hoher Sicherheit"));
        assertTrue(prompt.contains("das gesamte Gespraech jetzt zu beenden"));
        assertTrue(prompt.contains("soziale Beobachtungen"));
        assertFalse(prompt.contains("arrival"),
                "Social change events must not be listed as final-state triggers");
    }

    @Test
    void rockScissorPaperDefinesGermanGigiMotionContract() {
        Agent agent = new ch.zhaw.prometheus.agentdefs.gigitdsr.RockScissorPaper().createAgent();

        assertTrue(agent.getName().contains("GIGI TDSR"));
        assertTrue(agent.getName().contains("Schere, Stein, Papier"));
        assertTrue(agent.getDescription().contains("motion.handSign"));
        assertTrue(agent.listStates().contains("GIGI TDSR RPS Zeichen zeigen"));
        assertTrue(agent.listStates().contains("GIGI TDSR RPS Rundenergebnis"));

        String prompt = agent.getTotalPolicy().getPromptMessages().get(0).getContent();
        assertTrue(prompt.contains("Du bist GIGI"));
        assertTrue(prompt.contains("Antworte immer auf Deutsch"));
        assertTrue(prompt.contains("BehaviourPlan"));
        assertTrue(prompt.contains("deterministisch"));
        assertTrue(prompt.contains("Die Interaktion endet nur"));
    }

    @Test
    void rockScissorPaperPromptsKeepReadyPlayAgainAndFinalSeparated() throws Exception {
        Class<?> definitionClass = ch.zhaw.prometheus.agentdefs.gigitdsr.RockScissorPaper.class;

        assertTrue(prompt(definitionClass, "PROMPT_READY").contains("bereit"));
        assertTrue(prompt(definitionClass, "PROMPT_READY").contains("Handzeichen-Events"));
        assertTrue(prompt(definitionClass, "PROMPT_PLAY_AGAIN").contains("weitere Runde"));
        assertTrue(prompt(definitionClass, "PROMPT_TO_FINAL").contains("das gesamte Schere-Stein-Papier-Spiel"));
        assertTrue(prompt(definitionClass, "PROMPT_TO_FINAL").contains("Handzeichen-Events"));
    }

    @Test
    void promptsFitPersistedColumns() throws IllegalAccessException {
        for (Class<?> seedClass : List.of(
                ch.zhaw.prometheus.agentdefs.gigitdsr.GuessingGameWithGestures.class,
                ch.zhaw.prometheus.agentdefs.gigitdsr.SocialContextSensitivity.class,
                ch.zhaw.prometheus.agentdefs.gigitdsr.RockScissorPaper.class)) {
            for (Field field : seedClass.getDeclaredFields()) {
                if (!field.getName().startsWith("PROMPT_")) {
                    continue;
                }
                field.setAccessible(true);
                String prompt = (String) field.get(null);
                assertTrue(prompt.length() <= MAX_PERSISTED_PROMPT_LENGTH,
                        seedClass.getSimpleName() + "." + field.getName()
                                + " must fit the persisted prompt column");
            }
        }
    }

    @Test
    void configuredPolicyEmitsStructuredNonverbalPlanOnStart() {
        Agent agent = new ch.zhaw.prometheus.agentdefs.gigitdsr.GuessingGameWithGestures().createAgent();
        EventSequencedGateway gateway = new EventSequencedGateway(List.of(
                "Hallo, ich bin GIGI. Denk an etwas Vertrautes.",
                "{\"gesture\":\"POLITE\",\"facialExpression\":{\"type\":\"welcoming\",\"intensity\":0.7}}"));

        ch.zhaw.prometheus.model.event.Event event = agent.start(
                new PolicyRuntime(new PromptMessageAssembler(), gateway));

        assertNotNull(event);
        BehaviourPlan plan = BehaviourPlan.fromJson(event.getPayload());
        assertNotNull(plan);
        assertNotNull(plan.getNonVerbal());
        assertTrue(plan.getNonVerbal().getAsJsonObject().has("facialExpression"));
        assertTrue(plan.getNonVerbal().getAsJsonObject().get("gesture").getAsString().equals("POLITE"));
    }

    private static PromptPolicy interactionPolicy(State state) throws Exception {
        Field policyField = State.class.getDeclaredField("policy");
        policyField.setAccessible(true);
        Policy policy = (Policy) policyField.get(state);
        return assertInstanceOf(PromptPolicy.class, policy);
    }

    private static String prompt(Class<?> definitionClass, String fieldName) throws Exception {
        Field field = definitionClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(null);
    }

    private static final class EventSequencedGateway implements LanguageModelGateway {
        private final List<String> completions;
        private int completionIndex = 0;

        private EventSequencedGateway(List<String> completions) {
            this.completions = completions;
        }

        @Override
        public String complete(List<PromptMessage> messages) {
            return this.completions.get(this.completionIndex++);
        }

        @Override
        public boolean decide(List<PromptMessage> messages) {
            return false;
        }

        @Override
        public com.google.gson.JsonElement extract(List<PromptMessage> messages) {
            return com.google.gson.JsonNull.INSTANCE;
        }

        @Override
        public com.google.gson.JsonElement summarise(List<PromptMessage> messages) {
            return com.google.gson.JsonNull.INSTANCE;
        }

        @Override
        public String summariseOffline(List<PromptMessage> messages) {
            return "";
        }
    }
}
