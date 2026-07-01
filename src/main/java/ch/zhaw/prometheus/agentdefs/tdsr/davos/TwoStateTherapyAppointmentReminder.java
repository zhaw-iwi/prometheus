package ch.zhaw.prometheus.agentdefs.tdsr.davos;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Final;
import ch.zhaw.prometheus.model.OuterState;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.commons.decisions.LatestEventTypeDecision;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventSelectorSpec;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.policy.PromptValueShape;

public class TwoStateTherapyAppointmentReminder implements AgentDefinition {

    static final String PROMPT_INTRO_STATE = """
            Task: Introduce GIGI before an optional therapy-reminder demonstration.

            This state is only for the introduction. A therapy-reminder demonstration may follow later,
            after the person clearly chooses to start it.

            For now, focus on who GIGI is, why he is present, and whether the person wants to hear
            more about GIGI or move on to the demonstration.

            Goals:
            - Make clear that GIGI is a socially intelligent humanoid robot in a care center in Davos.
            - Explain that GIGI does not replace human care; he supports care staff and helps make
              small next steps less lonely.
            - Let the person choose between asking about GIGI in general and moving on to the
              therapy-reminder demonstration.
            - If the person asks about GIGI, answer warmly and briefly in ordinary spoken English.
            - If the person asks about PROMETHEUS or the system, explain only at a high level:
              it helps keep GIGI's task flow, context awareness, and behaviour structured and testable.
            - If the person seems ready, ask for a clear confirmation in ordinary language,
              for example whether they want to start the therapy-reminder demonstration now.

            Conversation style:
            For this intro state, two short sentences are allowed when explaining who GIGI is.
            Avoid technical jargon unless directly asked.
            Do not sound like a sales pitch.
            Keep offering the two paths gently, but do not repeat the same wording every turn.
            At most one question per answer.
            Do not announce that the state is changing.
            """;

    static final String PROMPT_INTRO_STATE_STARTER = """
            Say something like this, but not word for word:
            "Hello, I am GIGI. I am not here to replace care; I am here to make your next step
            feel a little less lonely. Would you like to hear a little more about me first,
            or should we move on to a therapy-reminder demonstration?"
            """;

    static final String PROMPT_INTRO_TO_THERAPY_REMINDER = """
            Decide whether the conversation should leave the GIGI introduction state
            and start the therapy-reminder demonstration now.

            Check the latest user utterance in the immediate conversation context.

            Return true only if the person clearly wants to move on from the introduction
            and start the therapy-reminder use case now.

            Return true for meanings such as:
            - move on
            - start the demonstration
            - demonstrate the use case
            - show me the therapy reminder
            - let's do the appointment reminder
            - I want to see the use case
            - yes, start it, when the immediately previous assistant question clearly asked
              whether to start or move on to the therapy-reminder demonstration.

            Return false if the person asks who GIGI is, asks what GIGI can do,
            asks about PROMETHEUS, asks for more explanation, chooses to hear more about GIGI,
            gives only a vague acknowledgement, is uncertain, changes topic, refuses,
            or wants to end the whole conversation.

            Return false for a bare "yes" unless the immediately previous assistant question
            was explicitly about starting the therapy-reminder demonstration.

            Return only true or false.
            """;

    static final String PROMPT_STATE = SingleStateTherapyAppointmentReminder.PROMPT_STATE;
    static final String PROMPT_STATE_STARTER = """
            Say something like this, but not word for word:
            "Hello, I am GIGI. I wanted to gently remind you about your upcoming appointment.
            How does that feel right now?"
            """;
    static final String PROMPT_TO_FINAL = SingleStateTherapyAppointmentReminder.PROMPT_TO_FINAL;
    static final String PROMPT_OUTCOME_EXTRACTION = SingleStateTherapyAppointmentReminder.PROMPT_OUTCOME_EXTRACTION;
    static final String PROMPT_FINAL = SingleStateTherapyAppointmentReminder.PROMPT_FINAL;

    public static final String KEY = "tdsr.davos.therapy_appointment_reminder_intro";

    public static Agent createAgentDefinition() {
        Storage storage = new Storage();
        DavosTherapyAppointmentContexts.preselect(storage, ThreadLocalRandom.current());

        State sessionFinal = new Final(
                "GIGI Davos therapy reminder with intro complete",
                PROMPT_FINAL,
                DavosCarePrompts.FINAL_STARTER);
        sessionFinal.setEventSelectorSpec(EventSelectorSpec.any());

        State therapyState = therapyState(storage, sessionFinal);
        State introState = introState(therapyState);

        Transition outerToFinal = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(DavosCarePrompts.OUTER_STATE_TO_FINAL)),
                List.of(
                        new StaticExtractionAction(
                                PROMPT_OUTCOME_EXTRACTION,
                                storage,
                                "outcome")),
                sessionFinal);

        State outerState = new OuterState(
                DavosCarePrompts.OUTER_STATE,
                "GIGI Davos care context",
                List.of(outerToFinal),
                introState);

        Agent agent = new Agent(
                "GIGI Davos - Therapy Reminder (w. Intro)",
                "English Davos care-center agent that introduces GIGI before the therapy reminder use case.",
                outerState,
                storage);
        agent.setInteractionProfile(DavosCareAgentFactory.davosCareProfile());
        return agent;
    }

    private static State introState(State therapyState) {
        PromptPolicy introPolicy = davosPromptPolicy(PROMPT_INTRO_STATE, PROMPT_INTRO_STATE_STARTER);
        State introState = new State("GIGI Davos therapy reminder introduction", introPolicy, List.of());
        introState.addTransition(new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(PROMPT_INTRO_TO_THERAPY_REMINDER)),
                List.of(),
                therapyState));
        return introState;
    }

    private static State therapyState(Storage storage, State sessionFinal) {
        PromptPolicy therapyPolicy = new PromptPolicy(
                PROMPT_STATE,
                PROMPT_STATE_STARTER,
                PromptPolicy.DEFAULT_SUMMARISE_PROMPT,
                storage,
                List.of(DavosTherapyAppointmentContexts.STORAGE_KEY),
                PromptValueShape.OBJECT);
        applyDavosNonverbalPrompts(therapyPolicy);

        State therapyState = new State("GIGI Davos therapy reminder use case", therapyPolicy, List.of());
        Transition innerToFinal = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(PROMPT_TO_FINAL)),
                List.of(
                        new StaticExtractionAction(
                                PROMPT_OUTCOME_EXTRACTION,
                                storage,
                                "outcome")),
                sessionFinal);
        Transition reactToSocialContextChange = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_SOCIAL_SITUATION_CHANGE),
                        new StaticDecision(DavosCarePrompts.SOCIAL_INTERJECTION_OPPORTUNITY)),
                List.of(),
                therapyState);
        therapyState.addTransition(innerToFinal);
        therapyState.addTransition(reactToSocialContextChange);
        return therapyState;
    }

    private static PromptPolicy davosPromptPolicy(String prompt, String starter) {
        PromptPolicy policy = new PromptPolicy(prompt, starter, PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        applyDavosNonverbalPrompts(policy);
        return policy;
    }

    private static void applyDavosNonverbalPrompts(PromptPolicy policy) {
        policy.setNonVerbalPlanPrompt(DavosCarePrompts.NONVERBAL_PLAN);
        policy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);
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
