package ch.zhaw.prometheus.agentdefs.usecases.healthcare;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.definition.catalog.BundledDefinitionCatalog;
import ch.zhaw.prometheus.definition.compiled.CompiledAgentDefinition;
import ch.zhaw.prometheus.definition.compiled.CompiledAtomicState;
import ch.zhaw.prometheus.definition.compiled.CompiledCompositeState;
import ch.zhaw.prometheus.definition.component.builtin.ExtractionActionComponent;
import ch.zhaw.prometheus.definition.component.builtin.PromptBehaviourActionComponent;
import ch.zhaw.prometheus.definition.component.builtin.PromptDecisionComponent;
import ch.zhaw.prometheus.definition.component.builtin.PromptPolicyComponent;

class HealthcareUseCasePromptContractTest {
    private static final List<AgentDefinition> DEFINITIONS = List.of(
            new SingleStateGuessingGame(),
            new SingleStateGuessingGameUserGuess(),
            new SingleStateHealthcareConversation(),
            new SingleStateSmartGoalCoaching(),
            new SingleStateTherapyAppointmentReminder(),
            new TwoStateTherapyAppointmentReminder());

    private static final List<String> STALE_PERSONA_TERMS = List.of(
            "GIGI",
            "gigi",
            "TDSR",
            "tdsr",
            "Tour de Suisse",
            "Davos",
            "Hotel Grischa",
            "Summit",
            "Antworte nur auf Deutsch",
            "Jee-jee",
            "Chee-chee",
            "robot",
            "Roboter");

    @Test
    void healthcareOuterPersonasUseValerianDigitalAgentPersona() {
        for (String persona : List.of(HealthcarePrompts.OUTER_STATE, HealthcareGeneralPrompts.OUTER_STATE)) {
            assertTrue(persona.contains("You are Valerian"));
            assertTrue(persona.contains("manifestation of a digital agent"));
            assertTrue(persona.contains("SIRA Lab"));
            assertTrue(persona.contains("PROMETHEUS"));
            assertTrue(persona.contains("rapid prototyping"));
            assertTrue(persona.contains("human-AI collaboration"));
            assertTrue(persona.contains("Answer only in English"));
        }
    }

    @Test
    void healthcareDefinitionsUseHealthcareNamespaceEnglishAndDisplayName() {
        for (AgentDefinition definition : DEFINITIONS) {
            assertTrue(definition.key().startsWith("usecases.healthcare."), definition.key());
            assertTrue(definition.displayName().startsWith("Valerian Use Cases Healthcare"), definition.key());
            assertTrue(definition.description().startsWith("English healthcare"), definition.key());
            assertTrue(AgentDefinition.LANGUAGE_ENGLISH.equals(definition.languageCode()), definition.key());
        }
    }

    @Test
    void migratedHealthcareDefinitionsPreserveEveryPromptRole() {
        BundledDefinitionCatalog catalog = BundledDefinitionCatalog.loadMainCatalog();
        assertSinglePrompts(catalog.require(SingleStateGuessingGame.KEY).compiled(),
                SingleStateGuessingGame.PROMPT_STATE, SingleStateGuessingGame.PROMPT_STATE_STARTER,
                SingleStateGuessingGame.PROMPT_TO_FINAL, SingleStateGuessingGame.PROMPT_OUTCOME_EXTRACTION,
                SingleStateGuessingGame.PROMPT_FINAL, false);
        assertSinglePrompts(catalog.require(SingleStateGuessingGameUserGuess.KEY).compiled(),
                SingleStateGuessingGameUserGuess.PROMPT_STATE,
                SingleStateGuessingGameUserGuess.PROMPT_STATE_STARTER,
                SingleStateGuessingGameUserGuess.PROMPT_TO_FINAL,
                SingleStateGuessingGameUserGuess.PROMPT_OUTCOME_EXTRACTION,
                SingleStateGuessingGameUserGuess.PROMPT_FINAL, false);
        assertSinglePrompts(catalog.require(SingleStateHealthcareConversation.KEY).compiled(),
                SingleStateHealthcareConversation.PROMPT_STATE,
                SingleStateHealthcareConversation.PROMPT_STATE_STARTER,
                SingleStateHealthcareConversation.PROMPT_TO_FINAL,
                SingleStateHealthcareConversation.PROMPT_OUTCOME_EXTRACTION,
                SingleStateHealthcareConversation.PROMPT_FINAL, true);
        assertSinglePrompts(catalog.require(SingleStateSmartGoalCoaching.KEY).compiled(),
                SingleStateSmartGoalCoaching.PROMPT_STATE, SingleStateSmartGoalCoaching.PROMPT_STATE_STARTER,
                SingleStateSmartGoalCoaching.PROMPT_TO_FINAL,
                SingleStateSmartGoalCoaching.PROMPT_OUTCOME_EXTRACTION,
                SingleStateSmartGoalCoaching.PROMPT_FINAL, false);
        assertSinglePrompts(catalog.require(SingleStateTherapyAppointmentReminder.KEY).compiled(),
                SingleStateTherapyAppointmentReminder.PROMPT_STATE,
                SingleStateTherapyAppointmentReminder.PROMPT_STATE_STARTER,
                SingleStateTherapyAppointmentReminder.PROMPT_TO_FINAL,
                SingleStateTherapyAppointmentReminder.PROMPT_OUTCOME_EXTRACTION,
                SingleStateTherapyAppointmentReminder.PROMPT_FINAL, false);

        CompiledAgentDefinition intro = catalog.require(TwoStateTherapyAppointmentReminder.KEY).compiled();
        assertPolicy(intro, "context", HealthcarePrompts.OUTER_STATE, "");
        assertPolicy(intro, "introduction", TwoStateTherapyAppointmentReminder.PROMPT_INTRO_STATE,
                TwoStateTherapyAppointmentReminder.PROMPT_INTRO_STATE_STARTER);
        assertPolicy(intro, "therapy_reminder", TwoStateTherapyAppointmentReminder.PROMPT_STATE,
                TwoStateTherapyAppointmentReminder.PROMPT_STATE_STARTER);
        assertDecision(intro, "context_end", HealthcarePrompts.OUTER_STATE_TO_FINAL);
        assertDecision(intro, "introduction_to_therapy",
                TwoStateTherapyAppointmentReminder.PROMPT_INTRO_TO_THERAPY_REMINDER);
        assertDecision(intro, "therapy_end", TwoStateTherapyAppointmentReminder.PROMPT_TO_FINAL);
        assertDecision(intro, "therapy_social_reaction", HealthcarePrompts.SOCIAL_INTERJECTION_OPPORTUNITY);
        assertEndActions(intro, "context_end", TwoStateTherapyAppointmentReminder.PROMPT_OUTCOME_EXTRACTION,
                TwoStateTherapyAppointmentReminder.PROMPT_FINAL, HealthcarePrompts.FINAL_STARTER);
        assertEndActions(intro, "therapy_end", TwoStateTherapyAppointmentReminder.PROMPT_OUTCOME_EXTRACTION,
                TwoStateTherapyAppointmentReminder.PROMPT_FINAL, HealthcarePrompts.FINAL_STARTER);
    }

    @Test
    void healthcareConversationIsEnglishHealthcareDemoOnly() {
        assertTrue(SingleStateHealthcareConversation.PROMPT_STATE.contains("healthcare use-case demonstration"));
        assertTrue(SingleStateHealthcareConversation.PROMPT_STATE.contains("digital agents in healthcare"));
        assertTrue(SingleStateHealthcareConversation.PROMPT_FINAL.contains("Answer only in English"));
        assertFalse(SingleStateHealthcareConversation.PROMPT_STATE.contains("Hotel"));
        assertFalse(SingleStateHealthcareConversation.PROMPT_STATE.contains("Davos"));
    }

    @Test
    void healthcareSourcesDoNotMentionRetiredDavosOrRobotPersona() throws IOException {
        for (Path source : Files.walk(Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/usecases/healthcare"))
                .filter(path -> path.toString().endsWith(".java"))
                .toList()) {
            String text = Files.readString(source);
            for (String staleTerm : STALE_PERSONA_TERMS) {
                assertFalse(text.contains(staleTerm), () -> "stale term " + staleTerm + " in " + source);
            }
        }
    }

    private static void assertSinglePrompts(CompiledAgentDefinition definition, String statePrompt,
            String starterPrompt, String toFinalPrompt, String extractionPrompt, String finalPrompt,
            boolean generalPersona) {
        String outer = generalPersona ? HealthcareGeneralPrompts.OUTER_STATE : HealthcarePrompts.OUTER_STATE;
        String outerToFinal = generalPersona ? HealthcareGeneralPrompts.OUTER_STATE_TO_FINAL
                : HealthcarePrompts.OUTER_STATE_TO_FINAL;
        String social = generalPersona ? HealthcareGeneralPrompts.SOCIAL_INTERJECTION_OPPORTUNITY
                : HealthcarePrompts.SOCIAL_INTERJECTION_OPPORTUNITY;
        String finalStarter = generalPersona ? HealthcareGeneralPrompts.FINAL_STARTER : HealthcarePrompts.FINAL_STARTER;
        assertPolicy(definition, "context", outer, "");
        assertPolicy(definition, "task", statePrompt, starterPrompt);
        assertDecision(definition, "context_end", outerToFinal);
        assertDecision(definition, "task_end", toFinalPrompt);
        assertDecision(definition, "task_react_obs_social_situation_change", social);
        assertEndActions(definition, "context_end", extractionPrompt, finalPrompt, finalStarter);
        assertEndActions(definition, "task_end", extractionPrompt, finalPrompt, finalStarter);
    }

    private static void assertPolicy(CompiledAgentDefinition definition, String stateId, String response,
            String starter) {
        Object state = definition.state(stateId);
        PromptPolicyComponent policy = state instanceof CompiledAtomicState atomic
                ? (PromptPolicyComponent) atomic.policy()
                : (PromptPolicyComponent) ((CompiledCompositeState) state).policy();
        assertEquals(response.strip(), policy.responsePrompt(), definition.key() + " " + stateId + " response");
        assertEquals(starter.strip(), policy.starterPrompt(), definition.key() + " " + stateId + " starter");
    }

    private static void assertDecision(CompiledAgentDefinition definition, String transitionId, String prompt) {
        var transition = definition.transitions().stream()
                .filter(candidate -> transitionId.equals(candidate.id())).findFirst().orElseThrow();
        PromptDecisionComponent decision = (PromptDecisionComponent) transition.decisions().get(1);
        assertEquals(prompt.strip(), decision.decisionPrompt(), definition.key() + " " + transitionId);
    }

    private static void assertEndActions(CompiledAgentDefinition definition, String transitionId, String extraction,
            String completion, String finalStarter) {
        var transition = definition.transitions().stream()
                .filter(candidate -> transitionId.equals(candidate.id())).findFirst().orElseThrow();
        ExtractionActionComponent extract = (ExtractionActionComponent) transition.actions().get(0);
        PromptBehaviourActionComponent goodbye = (PromptBehaviourActionComponent) transition.actions().get(1);
        assertEquals(extraction.strip(), extract.extractionPrompt(), definition.key() + " " + transitionId);
        assertEquals(completion.strip(), goodbye.policy().responsePrompt(), definition.key() + " " + transitionId);
        assertEquals(finalStarter.strip(), goodbye.policy().starterPrompt(), definition.key() + " " + transitionId);
    }
}
