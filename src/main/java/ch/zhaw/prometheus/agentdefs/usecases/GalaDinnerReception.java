package ch.zhaw.prometheus.agentdefs.usecases;

import java.util.ArrayList;
import java.util.List;

import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerAction;
import ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerDecision;
import ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerPolicy;
import ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerState;
import ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerTaskAction;
import ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerTaskPolicy;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.commons.decisions.LatestEventTypeDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import ch.zhaw.prometheus.model.task.TaskDecision;

/** Event-owned reception, games and co-created tasks; every activity returns to reception. */
public class GalaDinnerReception implements AgentDefinition {
    public static final String KEY = "usecases.gala_dinner_reception";

    @Override public String key() { return KEY; }
    @Override public String languageCode() { return LANGUAGE_GERMAN; }
    @Override public boolean capabilityAwareness() { return true; }
    @Override public boolean externalRealtimeSpeech() { return true; }

    @Override public Agent createAgent() {
        return createAgent(false);
    }

    protected Agent createAgent(boolean prepareRps) {
        Storage storage = new Storage();
        if (prepareRps) ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerRounds.enable(storage);
        var reception = new GalaDinnerState("Gala reception", storage, true);
        var rps = new GalaDinnerState("Gala rock-scissor-paper", storage, true);
        var guessing = new GalaDinnerState("Gala guess the person", storage, true);
        var task = new GalaDinnerState("Gala co-created task", new GalaDinnerTaskPolicy(storage), true);
        var routing = new GalaDinnerState("Gala routing", storage, false);
        task.addTransition(new Transition(new LatestEventTypeDecision(GalaDinnerTaskPolicy.RESPONSE_UNCERTAIN),
                new GalaDinnerAction(storage, "FAIL_TASK"), routing));
        var activities = new ArrayList<>(List.of(reception, rps, guessing, task));
        if (prepareRps) {
            var rpsSetup = new GalaDinnerState("Gala rock-scissor-paper preparation", storage, true);
            routing.addTransition(new Transition(new GalaDinnerDecision(storage, "RPS_SETUP"), rpsSetup));
            rpsSetup.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_HAND_SIGN),
                    new GalaDinnerAction(storage, "HAND"), routing));
            activities.add(rpsSetup);
            var introduction = new GalaDinnerState("Gala rock-scissor-paper countdown", storage, true);
            routing.addTransition(new Transition(new GalaDinnerDecision(storage, "RPS_INTRO"), introduction));
            introduction.addTransition(new Transition(new LatestEventTypeDecision(ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerRounds.SPEECH_CUE),
                    new GalaDinnerAction(storage, "SPEECH_CUE"), routing));
            introduction.addTransition(new Transition(new GalaDinnerDecision(storage, "INTRO_EXPIRED"),
                    new GalaDinnerAction(storage, "CANCEL_INTRO"), routing));
            activities.add(introduction);
        }
        routing.addTransition(new Transition(new GalaDinnerDecision(storage, "RPS"), rps));
        routing.addTransition(new Transition(new GalaDinnerDecision(storage, "GUESS"), guessing));
        routing.addTransition(new Transition(new GalaDinnerDecision(storage, "TASK"), task));
        routing.addTransition(new Transition(reception));
        for (var state : activities) {
            state.addTransition(new Transition(new LatestEventTypeDecision(GalaDinnerPolicy.LIVE_ENDED),
                    new GalaDinnerAction(storage, "EXPIRE"), routing));
            state.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                    new GalaDinnerAction(storage, prepareRps ? "TURN_WITH_RPS_PREPARATION" : "TURN"), routing));
            state.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_SOCIAL_GROUPING),
                    new GalaDinnerAction(storage, "PRESENCE"), routing));
            state.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_FACE_EMOTION),
                    new GalaDinnerAction(storage, "FACE"), routing));
            state.addTransition(new Transition(new GalaDinnerDecision(storage, "EXPIRED"),
                    new GalaDinnerAction(storage, "EXPIRE"), routing));
        }
        rps.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_HAND_SIGN),
                new GalaDinnerAction(storage, "HAND"), routing));
        for (String type : List.of(Event.TYPE_WEATHER_CURRENT, Event.TYPE_HAND_SIGN))
            reception.addTransition(new Transition(new LatestEventTypeDecision(type),
                    new GalaDinnerAction(storage, "AMBIENT"), routing));
        // The browser can submit human presence before grouping for the same camera sample.
        task.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_HUMAN_PRESENCE),
                new GalaDinnerAction(storage, "PRESENCE"), routing));
        task.addTransition(new Transition(new TaskDecision(storage, "CUE"),
                new GalaDinnerTaskAction(storage, true), routing));
        var agent = new Agent(prepareRps ? "GIGI - Gala Dinner Reception with RPS preparation" : "GIGI - Gala Dinner Reception",
                "German GIGI reception for the Home of Innovation gala on 24 October 2026, with RPS, person guessing and co-created multimodal tasks."
                        + (prepareRps ? " RPS checks all three hand signs once per Live conversation." : ""),
                reception, storage);
        agent.setPersonaName("GIGI");
        agent.setInteractionProfile(AgentInteractionProfile.of(
                List.of(AgentInteractionProfile.OBS_USER_UTTERANCE, AgentInteractionProfile.OBS_FACE_EMOTION,
                        AgentInteractionProfile.OBS_HUMAN_PRESENCE, AgentInteractionProfile.OBS_SOCIAL_GROUPING,
                        AgentInteractionProfile.OBS_SOCIAL_CONTEXT, AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE,
                        AgentInteractionProfile.OBS_HAND_SIGN, AgentInteractionProfile.OBS_WEATHER_CURRENT,
                        AgentInteractionProfile.OBS_WEATHER_FORECAST),
                List.of(AgentInteractionProfile.MODALITY_SPEECH, AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE,
                        AgentInteractionProfile.MODALITY_NONVERBAL_FACIAL_EXPRESSION,
                        AgentInteractionProfile.MODALITY_NONVERBAL_GAZE, AgentInteractionProfile.MODALITY_NONVERBAL_MOTION,
                        AgentInteractionProfile.MODALITY_MOTION_HAND_SIGN, AgentInteractionProfile.MODALITY_DISPLAY),
                List.of("demo.gigi.gala_dinner", "demo.gigi.sira_lab")).withPresenceControlledLive(true));
        return applyDefinitionMetadata(agent);
    }

    @Override public AgentCreationResult createInstance(AgentCreationContext context) {
        var agent = createAgent();
        agent.setEmbodiment(context.embodiment());
        return AgentCreationResult.created(agent);
    }
}
