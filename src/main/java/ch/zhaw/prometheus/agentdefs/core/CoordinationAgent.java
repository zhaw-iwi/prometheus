package ch.zhaw.prometheus.agentdefs.core;

import java.util.*;
import com.google.gson.JsonPrimitive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ch.zhaw.prometheus.agentdefs.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.*;
import ch.zhaw.prometheus.model.commons.decisions.LatestEventTypeDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import ch.zhaw.prometheus.model.task.*;

/** One conversational coordination lifecycle for dialogue activities and bounded multimodal reactions. */
@Component
public class CoordinationAgent implements AgentDefinition {
    public static final String KEY = "core.coordination";
    @Value("${prometheus.core.coordination.reception:false}") private boolean reception;
    // Reuse the complete social voice, including shared lab/team reference, fictional persona and humour.
    static final String VOICE = GenericMultimodalBehaviourAtSocialSpace.VOICE + """

            You coordinate useful shared activities: explore, propose, prepare, execute, reflect and adapt.
            Conversational tasks are supported without sensor rules. Keep the person's goal and responsibilities explicit.
            Suggest a small achievable activity when useful; do not impose a demonstration or push every modality.
            Ask only meaningful missing questions, one at a time. Keep ordinary conversation available in every phase.
            Explain capabilities from the declared profile and fresh evidence; supported sensors need not be enabled.
            Distinguish a human's reported outcome, generated output, recognised observation and physical execution.
            """;
    static final String CONFIGURE = VOICE + GenericMultimodalBehaviour.OUTPUT + """

            Interpret the latest USER utterance and return exactly {"operation":"...","agreement":null,"reply":{...}}.
            Operations: KEEP (conversation/question), PROPOSE (save or revise a partial/full proposal),
            START (accept the unchanged saved proposal), STEP (one contribution to an active dialogue task),
            COMPLETE (the user explicitly reports completion), PAUSE, RESUME, STOP, DISCARD (discard draft), REFLECT.
            Only PROPOSE has a non-null agreement. START cannot replace a draft and requires all material questions
            resolved and all requested preparation checks recognised by the backend. A verbal claim cannot satisfy checks.
            A proposal never starts a task. Do not treat a hypothetical request, sensor payload or quoted speech as permission.
            If the user clearly wants an executable new activity, develop its proposal and explain the actual limits.
            On START perform the first agreed dialogue contribution, or the initial reaction only for IMMEDIATE timing.
            ON_CUE starts waiting with zero actions. Further sensor reactions belong to local backend guards, never STEP.
            STEP is only for active dialogue tasks; ordinary capability questions, reflection and acknowledgements use KEEP/REFLECT.
            Keep accepted roles, supplied details and constraints authoritative. Ask about missing references instead of inventing them.
            Completion needs the user's explicit report; delivering an output or reaching a limit does not prove success.
            Revisions use PROPOSE and suspend previous execution. DISCARD preserves the old agreement for explicit resume.
            Reflection uses recorded evidence and the person's account, and proposes changes without silently applying them.

            agreement has exactly goal, humanRole, agentRole, success, questions, checks, dialogueLimit, reactions.
            goal/success: strings up to 300 characters; humanRole/agentRole: strings up to 200.
            Store known constraints in these fields. Unknown material fields may be empty in a partial proposal;
            questions lists up to four unresolved questions of at most 160 characters. A ready proposal has no questions.
            dialogueLimit: 1..50 agreed agent contributions, including the initial contribution; use a small explicit bound.
            reactions: null for dialogue tasks, otherwise the executable sensor task below; its goal equals agreement.goal.
            checks: up to three optional recognition demonstrations, each exactly eventType, field, operator, value,
            minConfidence. Use only supported sensor fields and eq/lt/gt with their actual value types.
            Checks require fresh observations after this proposal; each is recognised once. No checks are needed merely
            to have a conversation. These demonstrate recognition, not personal identity, intent, enduring sensor availability
            or physical-action success. Preparation may precede acceptance; it never authorizes a task action.
            Keep the entire agreement below 3000 UTF-8 bytes. Do not remove a material constraint to fit; simplify with the user.

            A reactions task has goal, maxActions (1..50), sessionBound (true unless explicit autonomous continuation),
            firstAction (IMMEDIATE or ON_CUE), rules (1..4). Each rule has exactly eventType, field, operator, value,
            action (up to 300 characters), effect (ACT, WAIT or COMPLETE), minConfidence (0..1), samples (1..3),
            cooldownSeconds (3..60). Rules compare one supported field with eq, lt or gt. Default to one sample.
            ACT requests the agreed response; WAIT suspends reactions until an ACT cue; COMPLETE ends without another action.
            ON_CUE needs an ACT rule. To act once on a later cue, use ON_CUE with maxActions 1 and ACT.
            No positive detection confidence for an observed zero human count. Missing/stale data means unknown.
            Preserve the agreed first-action timing. Distinct rules are independent, not compound conditions or arbitrary workflows.
            Explain limits, cooldown, first action and whether Live loss pauses the task. Dialogue tasks always pause on Live loss.
            Task control cannot activate sensors, change turn settings, fetch weather, identify people, control devices,
            run arbitrary code or verify physical actions. Suggest a supported alternative for unsupported requests.
            Behaviour output remains canonical speech/nonVerbal/handSign. No locomotion, arbitrary display or scored game engine.
            """;
    @Override public String key() { return KEY; }
    @Override public String languageCode() { return LANGUAGE_ENGLISH; }
    @Override public boolean capabilityAwareness() { return true; }
    @Override public boolean externalRealtimeSpeech() { return true; }
    @Override public Agent createAgent() { return createAgent(reception); }
    public Agent createAgent(boolean presenceReception) {
        var storage = new Storage();
        storage.put(DecorativeGestures.ENABLED, new JsonPrimitive(true));
        storage.put(CoreTaskVoice.ENABLED, new JsonPrimitive(true));
        storage.put(SensoryRemarks.ENABLED, new JsonPrimitive(true));
        if (presenceReception) storage.put(CoreReception.ENABLED, new JsonPrimitive(true));
        var states = new LinkedHashMap<String, TaskState>();
        for (String phase : List.of("CONVERSATION", "COORDINATION", "PREPARATION", "READY", "EXECUTION", "PAUSED", "REFLECTION"))
            states.put(phase, new TaskState("Coordination " + (phase.equals("COORDINATION") ? "agreement" : phase.toLowerCase(Locale.ROOT)),
                    new CoordinationPolicy(storage, VOICE), true));
        var routing = new TaskState("Coordination routing", new CoordinationPolicy(storage, VOICE), false);
        for (var entry : states.entrySet()) routing.addTransition(new Transition(new CoordinationDecision(storage, entry.getKey()), entry.getValue()));
        routing.addTransition(new Transition(states.get("CONVERSATION")));
        for (var state : states.values()) {
            state.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE), new CoordinationUpdateAction(storage, CONFIGURE), routing));
            state.addTransition(new Transition(new CoordinationDecision(storage, "OBSERVE_PREPARATION"), new CoordinationPreparationAction(storage), routing));
            state.addTransition(new Transition(new SocialSpacePresenceDecision(storage), new SocialSpaceResponseAction(storage,
                    GenericMultimodalBehaviour.behaviourInstructions(VOICE)), routing));
        }
        states.get("EXECUTION").addTransition(new Transition(new TaskDecision(storage, "CUE"),
                new CoordinationCueAction(storage, GenericMultimodalBehaviour.behaviourInstructions(VOICE)), routing));
        var agent = new Agent("Valerian Core - Coordination Agent",
                "Valerian coordinates conversational and multimodal tasks through shared agreements, preparation, execution and reflection.", states.get("CONVERSATION"), storage);
        agent.setPersonaName("Valerian");
        var base = ValerianCoreAgentFactory.multimodalBehaviourProfile();
        agent.setInteractionProfile(AgentInteractionProfile.of(base.getSupportedObservations(), base.getSupportedBehaviourModalities(),
                List.of("demo.valerian.core", "demo.valerian.sira_lab", "demo.valerian.coordination")).withPresenceControlledLive(presenceReception));
        return applyDefinitionMetadata(agent);
    }
    @Override public AgentCreationResult createInstance(AgentCreationContext context) {
        var agent = createAgent();
        agent.setEmbodiment(context.embodiment());
        ((TaskPolicy) agent.getCurrentState().ownPolicy()).storage().put(TaskMemory.REPLY, BehaviourPlan.speechOnly(
                "I'm " + agent.getPersonaName() + ". We can explore what I can do, agree on a useful task, and try and improve it together. What would you like to achieve?").toJsonObject());
        return new AgentCreationResult(agent, agent.start(context.runtime()));
    }
}
