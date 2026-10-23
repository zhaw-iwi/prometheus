package ch.zhaw.prometheus.agentdefs.usecases;

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
        Storage storage = new Storage();
        var reception = new GalaDinnerState("Gala reception", storage, true);
        var rps = new GalaDinnerState("Gala rock-scissor-paper", storage, true);
        var guessing = new GalaDinnerState("Gala guess the person", storage, true);
        var task = new GalaDinnerState("Gala co-created task", new GalaDinnerTaskPolicy(storage), true);
        var routing = new GalaDinnerState("Gala routing", storage, false);
        task.addTransition(new Transition(new LatestEventTypeDecision(GalaDinnerTaskPolicy.RESPONSE_UNCERTAIN),
                new GalaDinnerAction(storage, "FAIL_TASK"), routing));
        routing.addTransition(new Transition(new GalaDinnerDecision(storage, "RPS"), rps));
        routing.addTransition(new Transition(new GalaDinnerDecision(storage, "GUESS"), guessing));
        routing.addTransition(new Transition(new GalaDinnerDecision(storage, "TASK"), task));
        routing.addTransition(new Transition(reception));
        addReceptionTransitions(List.of(reception, rps, guessing, task), routing, storage, "TURN");
        rps.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_HAND_SIGN),
                new GalaDinnerAction(storage, "HAND"), routing));
        rps.addTransition(new Transition(new LatestEventTypeDecision(GalaDinnerPolicy.COUNTDOWN_PLAYED),
                new GalaDinnerAction(storage, "COUNTDOWN_PLAYED"), routing));
        rps.addTransition(new Transition(new GalaDinnerDecision(storage, "RPS_PREP_TIMEOUT"),
                new GalaDinnerAction(storage, "RPS_PREP_TIMEOUT"), routing));
        addAmbientTransitions(reception, routing, storage);
        // The browser can submit human presence before grouping for the same camera sample.
        task.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_HUMAN_PRESENCE),
                new GalaDinnerAction(storage, "PRESENCE"), routing));
        task.addTransition(new Transition(new TaskDecision(storage, "CUE"),
                new GalaDinnerTaskAction(storage, true), routing));
        return completeAgent(reception, storage, true);
    }

    protected static void addReceptionTransitions(List<GalaDinnerState> states, GalaDinnerState target,
            Storage storage, String turnOperation) {
        for (var state : states) {
            state.addTransition(new Transition(new LatestEventTypeDecision(GalaDinnerPolicy.LIVE_ENDED),
                    new GalaDinnerAction(storage, "EXPIRE"), target));
            state.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                    new GalaDinnerAction(storage, turnOperation), target));
            state.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_SOCIAL_GROUPING),
                    new GalaDinnerAction(storage, "PRESENCE"), target));
            state.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_FACE_EMOTION),
                    new GalaDinnerAction(storage, "FACE"), target));
            state.addTransition(new Transition(new GalaDinnerDecision(storage, "EXPIRED"),
                    new GalaDinnerAction(storage, "EXPIRE"), target));
        }
    }

    protected static void addAmbientTransitions(GalaDinnerState reception, GalaDinnerState target, Storage storage) {
        for (String type : List.of(Event.TYPE_WEATHER_CURRENT, Event.TYPE_HAND_SIGN))
            reception.addTransition(new Transition(new LatestEventTypeDecision(type),
                    new GalaDinnerAction(storage, "AMBIENT"), target));
    }

    protected Agent completeAgent(GalaDinnerState reception, Storage storage, boolean activities) {
        var agent = new Agent(activities ? "GIGI - Gala Dinner Reception" : "GIGI - Gala Dinner Reception (Reception only)",
                activities ? "German GIGI reception for the Home of Innovation gala on 24 October 2026, with RPS, person guessing and co-created multimodal tasks."
                        : "German GIGI gala reception with conversation, information and multimodal reactions; no games or co-created tasks.",
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
                        AgentInteractionProfile.MODALITY_MOTION_HAND_SIGN, AgentInteractionProfile.MODALITY_DISPLAY).stream()
                        .filter(modality -> activities || !List.of(AgentInteractionProfile.MODALITY_MOTION_HAND_SIGN,
                                AgentInteractionProfile.MODALITY_DISPLAY).contains(modality)).toList(),
                List.of("demo.gigi.gala_dinner", "demo.gigi.sira_lab")).withPresenceControlledLive(true));
        return applyDefinitionMetadata(agent);
    }

    @Override public AgentCreationResult createInstance(AgentCreationContext context) {
        var agent = createAgent();
        agent.setEmbodiment(context.embodiment());
        return AgentCreationResult.created(agent);
    }
}
