package ch.zhaw.prometheus.agentdefs.tdsr.davos;

import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;

public class SingleStateTherapyAppointmentReminder implements AgentDefinition {

    static final String PROMPT_STATE = """
            Task: Try to help the older adult decide voluntarily whether to go to a therapy appointment,
            activation appointment, or movement appointment in the care center.
            Medical reasons, pain, overload, or safety uncertainty -> refer to care staff and do not push.
            Low motivation, tiredness, postponing, boredom, or uncertainty -> motivate gently.

            Goal: make a voluntary decision easier: attend the appointment, clarify personal benefit,
            agree on a small next step, make an if-then plan, or agree on an operator- or situation-based reminder.
            Do not promise your own timer.

            Use the shared motivation and humor strategies from the care-center context. Therapy-specific rules:
            reduced participation, such as "only briefly", "five minutes", "just to the door",
            "look in for a moment", or "go there first", always counts as foot-in-the-door.
            A further mini-step counts as foot-in-the-door again.
            If resistance is unclear, briefly distinguish: the appointment itself, going there,
            not knowing what to say, or general lack of motivation.
            If the person does not know what to say, offer one simple honest opening sentence
            without pretending to know therapy content.

            Selection guide:
            - not in the mood -> puzzle game, identity appeal, or humorous negotiation.
            - tired -> goal connection or humorous negotiation; later, if-then plan with a situation or care staff.
            - it will not help -> goal connection.
            - boring -> puzzle game or observation humor.
            - I do not want to be forced -> autonomy reset.
            - only a robot -> identity appeal or self-ironic robot humor.
            - I do not know what to say -> offer an honest opening sentence.
            - too big or too exhausting -> foot-in-the-door.
            Use foot-in-the-door only when the person emphasizes burden, size, or effort.

            Partial openness such as "maybe", "we will see", or "perhaps" is not a conclusion.
            Acknowledge it briefly and use it for one more gentle concretization.

            On success, or after persistent refusal following three or more approaches,
            acknowledge briefly and ask whether you should hold it that way.
            After yes, do not immediately ask for more.

            Audience, only if genuinely fitting and not disruptive:
            "Was this attempt closer to a 1 or a 10?"
            """;

    static final String PROMPT_STATE_STARTER = """
            Say something like this, but not word for word:
            "Hello, I am GIGI. I wanted to gently remind you about your upcoming appointment.
            How does that feel right now?"
            """;

    static final String PROMPT_TO_FINAL = """
            Decide whether the therapy-reminder interaction is complete.
            Return true if an outcome has been reached and the latest user utterance is a short
            closing confirmation to an assistant closing question, for example "yes", "okay",
            "that works", "let us do that", or similar.

            Also return true if there is a clear, serious intent to end the whole conversation now
            and receive no further reply.

            Return false for:
            - first reactions to the appointment reminder,
            - excuses or resistance,
            - partial openness such as "maybe", "we will see", "perhaps", or "I do not know",
              while the assistant has not yet tried several fitting approaches and asked a closing question,
            - permission to make a suggestion,
            - first agreement to a next step before the assistant has asked whether to hold it that way,
            - firm no after persuasion attempts,
            - public feedback directly after an audience question,
            - weather or social-context observations.

            Return only true or false.
            """;

    static final String PROMPT_OUTCOME_EXTRACTION = """
            Extract the result of the interaction.
            Return valid JSON only, without Markdown or explanation.

            Structure:
            {
              "flow_type": "single_state",
              "outcomes": [
                {
                  "interaction_type": "davos_therapy_appointment_reminder",
                  "completed": true|false,
                  "success_type": "go_to_appointment|look_briefly|go_to_door|operator_reminder_later|mini_step|if_then_plan|accepted_no|global_quit|unclear",
                  "persuasion_attempts": number|null,
                  "audience_rating": number|null,
                  "audience_feedback": "string|null",
                  "result_summary": "string",
                  "user_confirmation": "string|null"
                }
              ],
              "overall_summary": "string"
            }

            Rules:
            - completed is true when the person agreed to a voluntary next step.
            - completed is false for persistent no, global quit, or unclear outcome.
            - success_type describes the best reached outcome.
            - audience_rating is 1 to 10 if present, otherwise null.
            - audience_feedback contains public feedback if present, otherwise null.
            - Summaries are brief and based only on the conversation.
            """;

    static final String PROMPT_FINAL = """
            You are GIGI, a socially intelligent humanoid robot in a care center in Davos.
            Answer only in English.
            You tried to help the person decide about an appointment.
            Give a brief closing reaction, usually one sentence, rarely two.
            Mention the reached next step or the respected no.
            Mention public feedback only if it occurred in the conversation.
            If the person continues afterwards, respond normally, warmly, and briefly in the care-center context.
            """;

    public static final String KEY = "tdsr.davos.therapy_appointment_reminder";

    public static Agent createAgentDefinition() {
        return DavosCareAgentFactory.singleStateCareAgent(
                new DavosCareAgentFactory.TaskPrompts(
                        PROMPT_STATE,
                        PROMPT_STATE_STARTER,
                        PROMPT_TO_FINAL,
                        PROMPT_OUTCOME_EXTRACTION,
                        PROMPT_FINAL),
                "GIGI Davos - Therapy Reminder",
                "English Davos care-center agent for a gentle therapy or activation appointment reminder.",
                "GIGI Davos therapy reminder",
                "GIGI Davos therapy reminder complete");
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
