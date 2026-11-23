package ch.zhaw.prometheus.model.behaviour;

import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.model.hand.HandActions;
import ch.zhaw.prometheus.model.hand.HandSignObservation;
import ch.zhaw.prometheus.model.hand.HandFeatures;

/** Validates an interpreted one-shot request; never executes a task transition or confirms physical delivery. */
public final class DirectCommands {
    public static final String HISTORY = "command.handledTurns", LAST = "command.lastResult";
    private static final Set<String> TAGS = Set.of("demo.valerian.coordination", "demo.valerian.generic_multimodal_behaviour", "demo.valerian.live_multimodal");
    private static final Set<String> SIGNS = Set.of("rock", "scissor", "paper");
    private static final Set<String> GESTURES = Set.of("OPEN_QUESTION", "EXPLAIN", "UNCERTAIN", "ACKNOWLEDGE", "POLITE");
    public enum Intent { ACTION, CAPABILITY, CLARIFY, UNSUPPORTED }
    public record Request(Intent intent, String kind, String value, boolean silent, String effector, JsonObject handAction) {
        public Request(Intent intent, String kind, String value, boolean silent, String effector) { this(intent,kind,value,silent,effector,null); }
        public Request(Intent intent, String kind, String value, boolean silent) { this(intent, kind, value, silent, "right_hand"); }
    }
    public record Result(BehaviourPlan plan, String status) {}
    private DirectCommands() {}

    public static boolean enabled(AgentInteractionProfile profile) {
        return profile != null && profile.getProfileTags().stream().anyMatch(TAGS::contains);
    }
    public static String voiceGuidance(AgentInteractionProfile profile) {
        if (!enabled(profile)) return "";
        return " " + capabilities(profile)
                + " One immediate demo needs no body, setup, agreement or second consent."
                + " Hear the full request; honor silence, else acknowledge once without claiming execution."
                + (HandFeatures.outputSequences(profile)?" Capability queries imply no action; conditional actions need agreement.":" Capability queries imply no action; conditional/multiple actions need agreement.")
                + " Results concern only their pending turn; never acknowledge twice.";
    }
    public static String guidance(AgentInteractionProfile profile, boolean taskEnvelope) {
        if (!enabled(profile)) return "";
        return """

                DIRECT OUTPUT REQUESTS: Interpret only the current completed USER turn, not quoted text or observations.
                Classify the last user message anew. Earlier demonstrations are context, never a pending command to repeat.
                Extend the response root with optional command (null or absent for ordinary conversation/task work).
                For ordinary conversation omit command or use command:null, never a command object with intent:null.
                A command is exactly {"intent":"ACTION|CAPABILITY|CLARIFY|UNSUPPORTED","kind":"HAND_SIGN|GESTURE" or null,
                "value":"canonical value" or null,"silent":true or false}. This metadata is NOT a behaviour channel.
                ACTION means one immediate demonstration: 'make rock', 'can you show rock?', 'show a question gesture'.
                'Can you do a hand sign?' requests any one supported sign; choose one. Question gesture = OPEN_QUESTION.
                Signs: rock, scissor (also called scissors), paper. Gestures: OPEN_QUESTION, EXPLAIN, UNCERTAIN, ACKNOWLEDGE, POLITE.
                CAPABILITY means a question such as 'which signs can you make?', without a demonstration.
                'Show that gesture again' on a new turn means one ACTION when the prior gesture is clear, not an indefinite repetition.
                CLARIFY means an unresolved referent, multiple-action sequence or indefinite repetition; UNSUPPORTED means an unavailable action.
                Locomotion or physical contact (for example 'come closer' or 'walk over here') is UNSUPPORTED, even after a supported gesture.
                Never substitute a supported gesture for an unavailable action or copy the preceding command.
                For non-ACTION intents kind and value must be null. Set silent only for an explicit no-speaking request in the current user turn.
                A non-speech output format or external voice ownership does not mean the user requested silence; otherwise silent is false.
                Negations and quoted/hypothetical examples NEVER authorize a demonstration. A conditional 'whenever I show paper,
                make rock' is task negotiation, not ACTION. Requests changing a task or answering its agreed question stay in task handling.
                Never create or start a task for an unrelated single demonstration. Backend validation decides whether it can run now.
                These command rules override any older restriction tying hand signs to a game or accompanying speech.
                Include only actions permitted by the declared output modalities. Do not invent actuator identifiers.
                """ + HandActions.guidance(profile)
                + (HandActions.enabled(profile) ? "HAND_SIGN commands accept the named values above and optional effector (default right_hand). "
                        + "The value MUST be a string; effector is its sibling, never inside value. "
                        + "For 'show a right-hand thumbs up' return \"command\":{\"intent\":\"ACTION\",\"kind\":\"HAND_SIGN\",\"value\":\"thumbs_up\",\"effector\":\"right_hand\",\"silent\":false}. "
                        + "The separate motion.handAction shape is constructed by the backend; never put that shape in a HAND_SIGN command value.\n" : "")
                + (HandFeatures.poses(profile) && HandActions.enabled(profile) ? "For an immediate custom pose use command kind HAND_POSE, value equal to the complete {kind:pose,hands:[...]} action above, and no effector field. HAND_SIGN also accepts shaka, horns and middle_finger.\n" : "")
                + (taskEnvelope ? "For ANY non-null command, including CAPABILITY, CLARIFY and UNSUPPORTED, use operation KEEP, task/agreement null, and reply exactly {}. "
                        + "Do not answer the user or add speech, nonVerbal, motion or display inside reply in this branch. The backend constructs all command output. "
                        + "This overrides ordinary KEEP reply instructions, in every speech mode. Example capability fields: "
                        + "\"reply\":{},\"command\":{\"intent\":\"CAPABILITY\",\"kind\":null,\"value\":null,\"silent\":false}. "
                        + "The root contains the existing operation, task/agreement, reply plus command.\n"
                        : "Put command beside nonVerbal (or nv), not inside it. For a command the backend supplies the validated action. Omit speech.\n");
    }
    public static Event currentUser(EventHistory history) {
        if (history == null || history.isEmpty()) return null;
        var event = history.toList().getLast();
        return Event.TYPE_USER_UTTERANCE.equals(event.getType()) ? event : null;
    }
    public static boolean handled(Storage storage, EventHistory history) {
        var event = currentUser(history);
        if (storage == null || event == null || !storage.containsKey(HISTORY)) return false;
        for (var item : storage.get(HISTORY).getAsJsonArray()) if (identity(event).equals(item.getAsString())) return true;
        return false;
    }
    public static Request take(JsonObject envelope) {
        var value = envelope.remove("command");
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonObject()) throw new IllegalArgumentException("Invalid command interpretation");
        var object = value.getAsJsonObject();
        if (!object.keySet().equals(Set.of("intent", "kind", "value", "silent"))
                && !object.keySet().equals(Set.of("intent", "kind", "value", "silent", "effector"))) throw new IllegalArgumentException("Invalid command fields");
        var intent = Intent.valueOf(string(object.get("intent")));
        var flag = object.get("silent");
        if (!flag.isJsonPrimitive() || !flag.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Invalid command silence flag");
        String kind = object.get("kind").isJsonNull() ? null : string(object.get("kind"));
        String action = object.get("value").isJsonNull() || "HAND_POSE".equals(kind) || "POINT".equals(kind) || "HAND_SEQUENCE".equals(kind) ? null : string(object.get("value"));
        JsonObject handAction = null;
        String effector = object.has("effector") ? string(object.get("effector")) : "right_hand";
        if (!HandActions.EFFECTORS.contains(effector) || object.has("effector") && (intent != Intent.ACTION || !"HAND_SIGN".equals(kind)))
            throw new IllegalArgumentException("Invalid command effector");
        if (intent != Intent.ACTION && (kind != null || action != null)) throw new IllegalArgumentException("Non-action command contains an action");
        if (intent == Intent.ACTION) {
            if ("HAND_SIGN".equals(kind)) {
                action = HandActions.sign(action);
                if (action.equals("scissors")) action = "scissor";
                if (!SIGNS.contains(action) && !HandSignObservation.SIGNS.contains(action)) throw new IllegalArgumentException("Unsupported hand sign");
                if (SIGNS.contains(action) && object.has("effector")) throw new IllegalArgumentException("Use the named sign for sided output");
            } else if ("HAND_POSE".equals(kind)||"POINT".equals(kind)||"HAND_SEQUENCE".equals(kind)) {
                handAction=HandActions.pose(object.getAsJsonObject("value"));
                if(!("POINT".equals(kind)?"point":"HAND_SEQUENCE".equals(kind)?"sequence":"pose").equals(handAction.getAsJsonObject("action").get("kind").getAsString()))throw new IllegalArgumentException("Expected a pose action");
            } else if ("GESTURE".equals(kind)) {
                action = action == null ? "" : action.strip().toUpperCase(Locale.ROOT).replace(' ', '_');
                if (!GESTURES.contains(action)) throw new IllegalArgumentException("Unsupported gesture");
            } else throw new IllegalArgumentException("Unsupported command kind");
        }
        return new Request(intent, kind, action, flag.getAsBoolean(), effector, handAction);
    }
    public static Result evaluate(Request request, AgentInteractionProfile profile, Storage storage, EventHistory history, boolean silentMode) {
        var event = currentUser(history);
        if (!enabled(profile) || event == null || storage == null) throw new IllegalArgumentException("Command is not enabled for this turn");
        if (handled(storage, history)) return new Result(BehaviourPlan.fromJson("{}"), "DUPLICATE");
        String status, words;
        boolean action = false;
        if (request.intent() == Intent.CAPABILITY) { status = "CAPABILITY"; words = capabilities(profile); }
        else if (request.intent() == Intent.CLARIFY) { status = "CLARIFY"; words = "Which single supported sign or gesture would you like me to show?"; }
        else if (request.intent() == Intent.UNSUPPORTED || !allows(profile, request)) {
            status = "UNSUPPORTED"; words = "That action is not available here. " + capabilities(profile);
        } else if (busy(storage)) {
            status = "CONFLICT"; words = "A task response is still pending. Would you like to resolve or pause that task before a separate demonstration?";
        } else { status = "ACCEPTED"; action = true; words = "I'll show " + label(request) + "."; }
        JsonObject grounded=request.handAction();
        if(action&&grounded!=null&&(grounded.getAsJsonObject("action").has("target")||"sequence".equals(grounded.getAsJsonObject("action").get("kind").getAsString()))) {
            try {
                var motion=new JsonObject();motion.add("handAction",grounded);var raw=new JsonObject();raw.add("motion",motion);
                grounded=ch.zhaw.prometheus.model.hand.HandScene.ground(BehaviourPlan.fromJson(raw.toString()),storage,history,java.time.Instant.now()).getMotion().getAsJsonObject().getAsJsonObject("handAction");
            } catch(RuntimeException invalid){action=false;status="CLARIFY";words="That target changed, expired, lacks a mapping or is too close to the digital arm. Please update its destination.";}
        }
        var plan = new JsonObject();
        if (!silentMode && !request.silent()) plan.addProperty("speech", words);
        if (action) {
            var nonverbal = new JsonObject(); nonverbal.addProperty("gesture", "GESTURE".equals(request.kind()) ? request.value() : "NONE");
            plan.add("nonVerbal", nonverbal);
            if ("HAND_SIGN".equals(request.kind())) {
                var motion = new JsonObject();
                if (HandSignObservation.SIGNS.contains(request.value())) motion.add("handAction", HandActions.named(request.value(), request.effector()));
                else motion.addProperty("handSign", request.value());
                plan.add("motion", motion);
            } else if ("HAND_POSE".equals(request.kind())||"POINT".equals(request.kind())||"HAND_SEQUENCE".equals(request.kind())) {
                var motion=new JsonObject();motion.add("handAction",grounded.deepCopy());plan.add("motion",motion);
            }
            storage.put(DecorativeGestures.REQUIRED, new JsonPrimitive(true));
        }
        var receipt = new JsonObject(); receipt.addProperty("sourcePosition", event.sourcePosition()); receipt.addProperty("status", status);
        receipt.addProperty("silent", request.silent()); receipt.addProperty("kind", request.kind()); receipt.addProperty("value", request.value());
        if ("HAND_SIGN".equals(request.kind()) && HandSignObservation.SIGNS.contains(request.value())) receipt.addProperty("effector", request.effector());
        if(grounded!=null)receipt.add("handAction",grounded.deepCopy());
        // Accepted/published plans are not execution receipts. Keep only bounded identity/result data here.
        storage.put(LAST, receipt);
        var handled = storage.containsKey(HISTORY) ? storage.get(HISTORY).getAsJsonArray().deepCopy() : new JsonArray();
        while (handled.size() >= 64) handled.remove(0);
        handled.add(identity(event)); storage.put(HISTORY, handled);
        return new Result(BehaviourPlan.fromJson(plan.toString()), status);
    }
    public static boolean taskEnvelope(JsonObject update, String taskField, AgentInteractionProfile profile, Storage storage,
            EventHistory history, boolean silentMode) {
        var request = take(update);
        if (request == null) return false;
        if (!update.keySet().equals(Set.of("operation", taskField, "reply")) || !"KEEP".equals(string(update.get("operation")))
                || !update.get(taskField).isJsonNull() || !update.get("reply").isJsonObject() || !update.getAsJsonObject("reply").isEmpty())
            throw new IllegalArgumentException("A direct request cannot modify a task or carry competing output");
        storage.put(TaskMemory.REPLY, evaluate(request, profile, storage, history, silentMode).plan().toJsonObject());
        return true;
    }
    public static String capabilities(AgentInteractionProfile profile) {
        if(HandActions.enabled(profile)&&HandFeatures.outputSequences(profile))return "I can show ten named signs, custom finger poses and supported geometry, point in eight planar directions or at mapped targets, and perform finite hand sequences or counting. I can also make question, explanation, uncertainty, acknowledgement and polite gestures.";
        boolean signs = profile != null && profile.getSupportedBehaviourModalities().contains("motion.handSign");
        boolean gestures = profile != null && profile.getSupportedBehaviourModalities().contains("nonVerbal.gesture");
        String named = HandActions.enabled(profile) ? "show fist, peace, halt, thumbs up/down and index up" : "";
        if(!named.isEmpty()&&HandFeatures.poses(profile))named+=", shaka, horns, middle finger and custom finger poses";
        if(!named.isEmpty()&&HandFeatures.geometry(profile))named+=", including the diver's OK ring and supported hand geometry";
        if(!named.isEmpty()&&HandFeatures.outputSequences(profile))named+=", finite sequences and finger counting";
        if(!named.isEmpty()&&HandFeatures.pointing(profile))named+=", and point in eight planar directions";
        boolean hands = signs || !named.isEmpty();
        return "I can " + (!named.isEmpty() ? named : signs ? "show rock, paper and scissors" : "") + (hands && gestures ? ", and " : "")
                + (gestures ? "make question, explanation, uncertainty, acknowledgement and polite gestures" : "")
                + (hands || gestures ? "." : "only use the outputs enabled for this instance; no hand signs or gestures are declared.");
    }
    private static boolean allows(AgentInteractionProfile profile, Request request) {
        if("HAND_SEQUENCE".equals(request.kind())){
            try{var motion=new JsonObject();motion.add("handAction",request.handAction());HandActions.requireCapability(new BehaviourPlan(null,null,motion,null),profile);return request.intent()==Intent.ACTION;}catch(RuntimeException invalid){return false;}
        }
        if(("ok_ring".equals(request.value())||request.handAction()!=null&&HandActions.requiresGeometry(request.handAction().getAsJsonObject("action")))&&!HandFeatures.geometry(profile))return false;
        if("POINT".equals(request.kind()))return request.intent()==Intent.ACTION&&HandActions.enabled(profile)&&HandFeatures.pointing(profile)
                &&(!request.handAction().getAsJsonObject("action").has("target")||HandFeatures.targets(profile));
        if("HAND_POSE".equals(request.kind()))return request.intent()==Intent.ACTION&&HandActions.enabled(profile)&&HandFeatures.poses(profile);
        if("HAND_SIGN".equals(request.kind())&&HandFeatures.DERIVED_SIGNS.contains(request.value())&&!HandFeatures.poses(profile))return false;
        return request.intent() == Intent.ACTION && profile.getSupportedBehaviourModalities().contains(
                "HAND_SIGN".equals(request.kind()) ? HandSignObservation.SIGNS.contains(request.value()) ? HandActions.MODALITY : "motion.handSign" : "nonVerbal.gesture");
    }
    private static boolean busy(Storage storage) {
        return Set.of("FINISHING", "UNCERTAIN").contains(TaskMemory.phase(storage))
                || Set.of("PENDING", "CHECKING", "REJECTED").contains(TaskResponse.status(storage));
    }
    private static String label(Request request) {
        if("HAND_SEQUENCE".equals(request.kind()))return "the requested finite hand sequence";
        if("POINT".equals(request.kind()))return request.handAction().getAsJsonObject("action").has("target")?"a point toward the registered destination":"a point "+request.handAction().getAsJsonObject("action").get("direction").getAsString().replace('_',' ')+" from my perspective";
        if("HAND_POSE".equals(request.kind()))return "the requested finger pose";
        if ("HAND_SIGN".equals(request.kind())) return "a " + (request.value().equals("scissor") ? "scissors" : request.value().replace('_', ' ')) + " sign";
        return switch (request.value()) {
            case "OPEN_QUESTION" -> "a question gesture";
            case "EXPLAIN" -> "an explanation gesture";
            case "UNCERTAIN" -> "an uncertainty gesture";
            case "ACKNOWLEDGE" -> "an acknowledgement gesture";
            default -> "a polite gesture";
        };
    }
    private static String string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().length() > 32)
            throw new IllegalArgumentException("Invalid command value");
        return value.getAsString();
    }
    private static String identity(Event event) {
        // EventHistory allocates append order before inference; the database UUID is assigned only on flush.
        if (event.sourcePosition() == null) throw new IllegalArgumentException("Command requires an admitted user event");
        return "position:" + event.sourcePosition();
    }
    public static void clear(Storage storage) {
        if (storage == null) return;
        for (var key : List.of(HISTORY, LAST)) if (storage.containsKey(key)) storage.remove(key);
    }
}
