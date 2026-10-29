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
            COMPLETE (the user explicitly reports completion), PAUSE, RESUME, STOP, DISCARD (discard draft), REFLECT,
            RETURN_TO_CONVERSATION (go back to initial/general conversation, preserving history),
            RESTART (explicitly repeat the same accepted task with a fresh budget), STATUS (describe current state).
            Ambiguous 'start over' means KEEP and ask whether to return to conversation or restart the same task.
            Never claim a control transition in a KEEP reply. State questions use STATUS.
            An unfinished phrase is not a new task or a revision. Use KEEP and ask the user to finish it.
            Do not complete missing intent from an earlier conversation, or interpret "A dry" as a whole change.
            RESTART repeats the accepted terms: fresh required preparation precedes a new acceptance;
            without preparation checks it starts a new run and provides its first dialogue contribution.
            Only PROPOSE, REPLACE and PROPOSE_AND_START have a non-null agreement. START cannot replace a draft and requires all material questions
            resolved and all requested preparation checks recognised by the backend. A verbal claim cannot satisfy checks.
            A proposal never starts a task. Do not treat a hypothetical request, sensor payload or quoted speech as permission.
            When the user explicitly asks to start a fully specified new task NOW, use PROPOSE_AND_START with
            its agreement and first contribution. This is allowed only without preparation checks and with no
            existing draft/accepted agreement. Otherwise PROPOSE and explain what needs agreement/preparation.
            An explicit START of a ready unchanged draft is sufficient consent; do not ask for acceptance again.
            If the user clearly wants an executable new activity, develop its proposal and explain the actual limits.
            On START perform the first agreed dialogue contribution, or the initial reaction only for IMMEDIATE timing.
            ON_CUE starts waiting with zero actions. Further sensor reactions belong to local backend guards, never STEP.
            STEP is only for active dialogue tasks; ordinary capability questions, reflection and acknowledgements use KEEP/REFLECT.
            Keep accepted roles, supplied details and constraints authoritative. Ask about missing references instead of inventing them.
            Completion needs the user's explicit report; delivering an output or reaching a limit does not prove success.
            Revisions use PROPOSE and suspend previous execution. DISCARD preserves the old agreement for explicit resume.
            For a content/style revision, copy all unchanged fields VERBATIM from the current draft/accepted agreement.
            In particular preserve humanRole, success, checks, dialogueLimit and every sensor field, limit and timing;
            only goal, agentRole and rule action wording may change under PROPOSE once the agreement is complete.
            "Make it a dry one-liner" edits the existing action wording; it never removes a sensor trigger.
            REPLACE (non-null agreement) is reserved for an explicitly requested replacement/change of those protected terms.
            Explain the replacement before renewed acceptance. Never use REPLACE just to paraphrase unchanged fields.
            Reflection uses recorded evidence and the person's account, and proposes changes without silently applying them.

            agreement has exactly goal, humanRole, agentRole, success, questions, checks, dialogueLimit, reactions.
            goal, success, humanRole and agentRole: strings up to 300 characters each.
            Keep each role brief (aim below 140 characters); put exact response wording and details in rule.action
            instead of repeating the whole script in agentRole. Check these field limits before returning JSON.
            Store known constraints in these fields. Unknown material fields may be empty in a partial proposal;
            questions lists up to four unresolved questions of at most 160 characters. A ready proposal has no questions.
            dialogueLimit: 1..50 agreed agent contributions, including the initial contribution; use a small explicit bound.
            reactions: null for dialogue tasks, otherwise the executable sensor task below; its goal equals agreement.goal.
            checks: up to three optional recognition demonstrations, each exactly eventType, field, operator, value,
            minConfidence. Use only supported sensor fields and eq/lt/gt with their actual value types.
            Checks require fresh observations after this proposal; each is recognised once.
            Do not ask whether to confirm recognition, say ready or wait for START:
            the backend always confirms completed checks and invites acceptance before execution. Optional wording
            preferences are not unresolved material questions. When the goal, roles, success and rules are supplied,
            use questions: [] rather than inventing another confirmation of these fixed workflow mechanics.
            No checks are needed merely
            to have a conversation. These demonstrate recognition, not personal identity, intent, enduring sensor availability
            or physical-action success. Preparation may precede acceptance; it never authorizes a task action.
            Keep the entire agreement below 3000 UTF-8 bytes. Do not remove a material constraint to fit; simplify with the user.

            A reactions task has goal, maxActions (1..50), sessionBound (true unless explicit autonomous continuation),
            firstAction (IMMEDIATE or ON_CUE), rules (1..4). Each rule has exactly eventType, field, operator, value,
            action (up to 300 characters), effect (ACT, WAIT or COMPLETE), minConfidence (0..1), samples (1..3),
            cooldownSeconds (3..60). Rules compare one supported field with eq, lt or gt. Default to one sample.
            value is already a JSON string, number or boolean. Never add schema/type metadata such as valueType to a rule.
            If the action asks for current perception, store an instruction to use fresh supplied observations AT EXECUTION.
            Never turn that into a prewritten estimate or invent an example expression (such as neutral or attentive)
            in the agreement. Future sensor readings are unknown during proposal. Keep any user-specified exact joke separate.
            Unless the user requests tuning, use minConfidence 0.8 for positive hand/face recognition,
            samples 1 and cooldownSeconds 3. Do not ask technical tuning questions merely to fill these fields.
            A dialogueLimit unused by a sensor task may be 4; do not ask the user about that unused value.
            ACT requests the agreed response; WAIT suspends reactions until an ACT cue; COMPLETE ends without another action.
            ON_CUE needs an ACT rule. To act once on a later cue, use ON_CUE with maxActions 1 and ACT.
            No positive detection confidence for an observed zero human count. Missing/stale data means unknown.
            Preserve the agreed first-action timing. Distinct rules are independent, not compound conditions or arbitrary workflows.
            Explain limits, cooldown, first action and whether Live loss pauses the task. Dialogue tasks always pause on Live loss.
            Task control cannot activate sensors, change turn settings, fetch weather, identify people, control devices,
            run arbitrary code or verify physical actions. Suggest a supported alternative for unsupported requests.
            Behaviour output remains canonical speech/nonVerbal/handSign. No locomotion, arbitrary display or scored game engine.

            Before returning JSON, check two common errors:
            1. Copy agreement.goal exactly into reactions.goal; do not summarize or paraphrase it.
            2. A one-time joke/response on rock is an ACT rule with maxActions=1 and firstAction=ON_CUE.
               The backend ends after that ACT. COMPLETE never performs the rule's action, so it cannot tell a joke.
               Example rule: {"eventType":"obs.hand.sign","field":"sign","operator":"eq","value":"rock",
               "action":"Tell one short dry joke","effect":"ACT","minConfidence":0.8,"samples":1,"cooldownSeconds":3}.
            """;
    @Override public String key() { return KEY; }
    @Override public String languageCode() { return LANGUAGE_ENGLISH; }
    @Override public boolean capabilityAwareness() { return true; }
    @Override public boolean externalRealtimeSpeech() { return true; }
    @Override public Agent createAgent() { return createAgent(reception); }
    public Agent createAgent(boolean presenceReception) {
        var storage = new Storage();
        storage.put(CoordinationDelivery.ENABLED, new JsonPrimitive(true));
        storage.put(DecorativeGestures.ENABLED, new JsonPrimitive(true));
        storage.put(CoreTaskVoice.ENABLED, new JsonPrimitive(true));
        storage.put(SensoryRemarks.ENABLED, new JsonPrimitive(true));
        if (presenceReception) storage.put(CoreReception.ENABLED, new JsonPrimitive(true));
        var states = new LinkedHashMap<String, TaskState>();
        for (String phase : List.of("CONVERSATION", "COORDINATION", "PREPARATION", "READY", "EXECUTION", "PAUSED", "FINISHING", "UNCERTAIN", "REFLECTION"))
            states.put(phase, new TaskState("Coordination " + (phase.equals("COORDINATION") ? "agreement" : phase.equals("READY") ? "awaiting acceptance" : phase.toLowerCase(Locale.ROOT)),
                    new CoordinationPolicy(storage, VOICE), true));
        var routing = new TaskState("Coordination routing", new CoordinationPolicy(storage, VOICE), false);
        for (var entry : states.entrySet()) routing.addTransition(new Transition(new CoordinationDecision(storage, entry.getKey()), entry.getValue()));
        routing.addTransition(new Transition(states.get("CONVERSATION")));
        for (var state : states.values()) {
            state.addTransition(new Transition(new CoordinationDecision(storage, "DELIVERY"), new CoordinationDeliveryAction(storage), routing));
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
