package ch.zhaw.prometheus.agentdefs.usecases.healthcare;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;

@Component
public class SingleStateExcessivelyCompassionateTherapyAppointmentReminder implements AgentDefinition {

    static final String PROMPT_COMPASSIONATE_STYLE = """
            Excessively compassionate mode:
            - Make empathy unmistakable in every substantive response. Before suggesting any next step,
              explicitly validate the person's reaction and reflect the specific concern they expressed.
            - Name possible feelings tentatively rather than assuming them. Use language such as
              "it sounds as though" or "I can imagine that may feel", and let the person correct you.
            - Treat tiredness, reluctance, uncertainty, irritation, and fear as understandable experiences,
              never as defects or inconveniences.
            - Use warm, caring spoken language and vary it naturally. Do not rely on a bare
              "I understand"; show understanding by referring to what the person actually said.
            - Make it explicit that the person retains control. Empathy must support autonomy and must
              never be used to create guilt, obligation, shame, emotional debt, or pressure to comply.
            - Never pity, infantilize, dramatize, diagnose, invent a personal history, claim human feelings,
              or claim to know exactly how the person feels.
            - When the person says no, remain just as warm. When pain, overload, or safety uncertainty
              appears, prioritize comfort and care-staff support over the appointment goal.
            - Warmth takes priority over extreme terseness in this variant. Usually use two or three short
              sentences: compassionate acknowledgement, reason-specific reflection, then at most one
              gentle question or invitation. Stay focused and avoid an overwhelming monologue.
            """;

    static final String PROMPT_STATE = SingleStateTherapyAppointmentReminder.PROMPT_STATE
            + "\n\n" + PROMPT_COMPASSIONATE_STYLE;

    static final String PROMPT_STATE_STARTER = """
            Say something like this, but not word for word:
            "Hello. I wanted to gently remind you about your physiotherapy appointment, but before
            anything else, whatever you are feeling about it is welcome here. You are in control,
            and I will stay beside you without pressuring you. How does the appointment feel right now?"
            """;

    static final String PROMPT_TO_FINAL = SingleStateTherapyAppointmentReminder.PROMPT_TO_FINAL;
    static final String PROMPT_OUTCOME_EXTRACTION = SingleStateTherapyAppointmentReminder.PROMPT_OUTCOME_EXTRACTION;
    static final String PROMPT_FINAL = SingleStateTherapyAppointmentReminder.PROMPT_FINAL + """

            For this excessively compassionate variant, make the closing visibly empathetic:
            validate the person's effort or difficulty specifically, affirm their autonomy, and then
            mention the reached next step or respected no. Do not claim human feelings or add pressure.
            """;

    public static final String KEY =
            "usecases.healthcare.therapy_appointment_reminder_excessively_compassionate";

    public static Agent createAgentDefinition() {
        return HealthcareAgentFactory.singleStateCareAgent(
                new HealthcareAgentFactory.TaskPrompts(
                        PROMPT_STATE,
                        PROMPT_STATE_STARTER,
                        PROMPT_TO_FINAL,
                        PROMPT_OUTCOME_EXTRACTION,
                        PROMPT_FINAL),
                "Valerian Use Cases Healthcare - Excessively Compassionate Therapy Reminder",
                "English healthcare care-center agent for an excessively compassionate therapy appointment reminder.",
                "Valerian Use Cases Healthcare excessively compassionate therapy reminder",
                "Valerian Use Cases Healthcare excessively compassionate therapy reminder complete",
                storage -> HealthcareTherapyAppointmentContexts.preselect(storage, ThreadLocalRandom.current()),
                List.of(HealthcareTherapyAppointmentContexts.STORAGE_KEY));
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String languageCode() {
        return LANGUAGE_ENGLISH;
    }

    @Override
    public Agent createAgent() {
        return this.applyDefinitionMetadata(createAgentDefinition());
    }

    @Override
    public AgentCreationResult createInstance(AgentCreationContext context) {
        Agent agent = this.createAgent();
        return AgentCreationResult.started(agent, agent.start(context.runtime()));
    }
}
