package ch.zhaw.prometheus.model.hand;

import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;

/** Versioned output contract, separate from camera evidence and legacy RPS. */
public final class HandActions {
    public static final String MODALITY = "motion.handAction";
    public static final Set<String> EFFECTORS = Set.of("left_hand", "right_hand", "both_hands");
    private HandActions() {}
    public static boolean enabled(AgentInteractionProfile profile) {
        return profile != null && profile.getSupportedBehaviourModalities().contains(MODALITY);
    }
    public static String sign(String value) {
        String label = value == null ? "" : value.strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (label) {
            case "halt" -> "open_palm";
            case "attention" -> "index_up";
            case "hang_loose" -> "shaka";
            case "rock_n_roll", "rock_n'_roll", "rock_and_roll" -> "horns";
            case "divers_ok", "diver's_ok", "ok_circle" -> "ok_ring";
            default -> label;
        };
    }
    public static JsonObject named(String sign, String effector) {
        var action = new JsonObject(); action.addProperty("kind", "sign");
        action.addProperty("sign", sign); action.addProperty("effector", effector);
        var envelope = new JsonObject(); envelope.addProperty("schemaVersion", 1); envelope.add("action", action);
        validate(envelope); return envelope;
    }
    public static JsonObject pose(JsonObject action) {
        var envelope=new JsonObject();envelope.addProperty("schemaVersion",1);envelope.add("action",action.deepCopy());
        validate(envelope);return envelope;
    }
    public static void validate(JsonElement raw) {
        try {
            var envelope = raw.getAsJsonObject();
            if (!envelope.keySet().equals(Set.of("schemaVersion", "action"))) throw new IllegalArgumentException();
            var version = envelope.getAsJsonPrimitive("schemaVersion");
            if (!version.isNumber() || version.getAsDouble() != 1) throw new IllegalArgumentException();
            var action = envelope.getAsJsonObject("action");
            if("sequence".equals(text(action,"kind"))){
                if(!action.keySet().equals(Set.of("kind","steps"))||raw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>3500)throw new IllegalArgumentException();
                var steps=action.getAsJsonArray("steps");if(steps.isEmpty()||steps.size()>12)throw new IllegalArgumentException();
                long duration=0;
                for(var value:steps){var step=value.getAsJsonObject();if(!step.keySet().equals(Set.of("action","holdMs")))throw new IllegalArgumentException();
                    HandSignObservation.integer(step.get("holdMs"),100,5000);duration+=step.get("holdMs").getAsInt()+700;
                    if("sequence".equals(text(step.getAsJsonObject("action"),"kind")))throw new IllegalArgumentException("Nested sequence");
                    pose(step.getAsJsonObject("action"));
                }
                if(duration>60000)throw new IllegalArgumentException("Sequence exceeds one minute including transitions");
            }else if("point".equals(text(action,"kind"))) { PointingAction.validate(action);
            } else if ("pose".equals(text(action,"kind"))) {
                if(!action.keySet().equals(Set.of("kind","hands")))throw new IllegalArgumentException();
                var hands=action.getAsJsonArray("hands");if(hands.isEmpty()||hands.size()>2)throw new IllegalArgumentException();
                var sides=new HashSet<String>();
                for(var item:hands) {
                    var hand=item.getAsJsonObject();
                    if(!hand.keySet().equals(hand.has("relations")?Set.of("side","fingers","relations"):Set.of("side","fingers"))||!Set.of("left","right").contains(text(hand,"side"))||!sides.add(text(hand,"side")))throw new IllegalArgumentException();
                    var fingers=hand.getAsJsonObject("fingers");if(!fingers.keySet().equals(HandPoseObservation.FINGERS))throw new IllegalArgumentException();
                    fingers.asMap().values().forEach(HandSignObservation::number);
                    if(hand.has("relations"))HandRelation.requireOutput(HandRelation.parse(hand.get("relations")),fingers);
                }
            } else if (!action.keySet().equals(Set.of("kind", "sign", "effector")) || !"sign".equals(text(action, "kind"))
                    || !HandSignObservation.SIGNS.contains(text(action, "sign")) || !EFFECTORS.contains(text(action, "effector")))
                throw new IllegalArgumentException();
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("Unsupported hand action", invalid); }
    }
    public static void requireCapability(BehaviourPlan plan, AgentInteractionProfile profile) {
        if (plan.getMotion() == null || !plan.getMotion().isJsonObject() || !plan.getMotion().getAsJsonObject().has("handAction")) return;
        if(!enabled(profile))throw new IllegalArgumentException("Named hand output is not declared for this instance");
        var action=plan.getMotion().getAsJsonObject().getAsJsonObject("handAction").getAsJsonObject("action");
        if("sequence".equals(text(action,"kind"))){
            if(!HandFeatures.outputSequences(profile))throw new IllegalArgumentException("Output sequences not declared");
            for(var step:action.getAsJsonArray("steps")){var motion=new JsonObject();motion.add("handAction",pose(step.getAsJsonObject().getAsJsonObject("action")));requireCapability(new BehaviourPlan(null,null,motion,null),profile);}
            return;
        }
        if("point".equals(text(action,"kind"))) {
            if(!HandFeatures.pointing(profile))throw new IllegalArgumentException("Pointing output is not declared");
            if(action.has("target")&&!HandFeatures.targets(profile))throw new IllegalArgumentException("Target output is not declared");
            return;
        }
        if("pose".equals(text(action,"kind")) ? !HandFeatures.poses(profile) : !HandFeatures.signAllowed(text(action,"sign"),HandFeatures.poses(profile),HandFeatures.geometry(profile)))
            throw new IllegalArgumentException("This instance does not declare pose/derived output");
        if(requiresGeometry(action)&&!HandFeatures.geometry(profile))throw new IllegalArgumentException("This instance does not declare relational output");
    }
    public static boolean requiresGeometry(JsonObject action) {
        if("sequence".equals(text(action,"kind")))return action.getAsJsonArray("steps").asList().stream().anyMatch(s->requiresGeometry(s.getAsJsonObject().getAsJsonObject("action")));
        return "sign".equals(text(action,"kind"))?"ok_ring".equals(text(action,"sign"))
                :"pose".equals(text(action,"kind"))&&action.getAsJsonArray("hands").asList().stream().anyMatch(h->h.getAsJsonObject().has("relations")&&!h.getAsJsonObject().getAsJsonArray("relations").isEmpty());
    }
    private static String text(JsonObject object, String name) {
        var value = object.getAsJsonPrimitive(name);
        if (!value.isString()) throw new IllegalArgumentException();
        return value.getAsString();
    }
    public static String guidance(AgentInteractionProfile profile) {
        String input = HandFeatures.geometry(profile) && profile.getSupportedObservations().contains("obs.hand.pose") ? geometryInputGuidance() : "";
        if (!enabled(profile)) return input;
        return input + (HandFeatures.outputSequences(profile)?sequenceGuidance():"") + (HandFeatures.pointing(profile)?PointingAction.guidance():"") + "\nNamed hand output overrides legacy-only motion guidance: motion is exactly {\"handAction\":{\"schemaVersion\":1,"
                + "\"action\":{\"kind\":\"sign\",\"sign\":\"fist|peace|open_palm|thumbs_up|thumbs_down|index_up\","
                + "\"effector\":\"left_hand|right_hand|both_hands\"}}}. Default right_hand. Hold 500 ms then release. "
                + "Halt means open_palm; attention means index_up. Clarify ambiguous OK (thumbs up or diver ring). "
                + "Use this same output for immediate demonstrations and agreed task actions, preserving requested silence. "
                + "Plans describe digital output, never proof of physical execution. Do not mix handAction with legacy handSign.\n"
                + (HandFeatures.poses(profile) ? "Also supported: shaka (hang loose), horns (rock n roll), middle_finger, and custom finger poses. "
                    + "For custom poses, handAction.action is {\"kind\":\"pose\",\"hands\":[{\"side\":\"left|right\",\"fingers\":{\"thumb\":0.5,\"index\":0,\"middle\":0.5,\"ring\":0.5,\"little\":0.5}}]}. "
                    + "Use 1..2 distinct sides and ALL five numeric bends per output hand: 0=stretched, 1=bent, 0.5=neutral/default for unspecified fingers. "
                    + "Only requested hands move.\n" : "")
                + (HandFeatures.geometry(profile) ? geometryGuidance() : "Ring and relational hand geometry are unavailable for this instance.\n");
    }
    public static boolean sequence(BehaviourPlan plan){return plan!=null&&plan.getMotion()!=null&&plan.getMotion().isJsonObject()&&plan.getMotion().getAsJsonObject().has("handAction")&&"sequence".equals(plan.getMotion().getAsJsonObject().getAsJsonObject("handAction").getAsJsonObject("action").get("kind").getAsString());}
    public static String sequenceGuidance(){return "\nFinite output sequence overrides single-action limits. A sequence action is {kind:sequence,steps:[{action:<one supported sign/pose/point action>,holdMs:500}]}. "
            +"In a behaviour/task plan, emit {\"motion\":{\"handAction\":{\"schemaVersion\":1,\"action\":<sequence action>}}}. Never put command kind/value fields inside motion. "
            +"Only direct-command extraction uses command kind HAND_SEQUENCE and value <sequence action>. One command/contribution, no task negotiation for an immediate finite sequence. 1..12 steps, hold 100..5000 ms, sum(hold+700 ms transition) <=60000 ms, envelope <=3500 UTF-8 bytes; no nesting/repetition. "
            +"Count on explicitly chosen hands with poses: 0 stretched,1 bent; one through five extends thumb,index,middle,ring,little cumulatively on the right hand by default. Six through ten keeps right five and extends the left in the same order. Every finger explicit. "
            +"Silently means no speech at all. Clarify ambiguous ranges or indefinite loops. Target freshness is rechecked at each step. Stop/new user turn, supersession, hidden view or disconnection interrupts; reload shows static history, never resumes. "
            +"Published is not executed; only scoped client receipts establish digital playback, never physical actuation.\n";}
    public static String geometryInputGuidance() {
        return "Hand geometry v1: ok_ring means diver's OK, distinct from thumbs_up. "
                + "Versioned pose masks may add relations (max 6): {kind:tip_proximity|spread,fingers:[a,b],operator:lt|gt,value:number} "
                + "or {kind:orientation,axis:palm_normal|index_axis|thumb_axis,direction:left|right|up|down|up_left|up_right|down_left|down_right,toleranceDegrees:5..45}. "
                + "A versioned input hand mask may use all:[] only when relations is nonempty; never invent a bend constraint for a relation-only request. "
                + "Input tip proximity is projected gap divided by max palm width/length, thresholds 0..2; not contact/depth. Spread is proximal angle / 180 degrees (0..1). "
                + "Input axes use unmirrored camera x-right/y-down; face-on palm normal can be unknown. Unavailable geometry never matches. "
                + "Use named handSign ok_ring for the named cue; proximity hysteresis is enter<.18, hold<.22 plus 300ms stability.\n";
    }
    public static String geometryGuidance() {
        return "HAND_SIGN accepts ok_ring and selects the supported ring preset. "
                + "Output relations (max 6) have shape {kind:tip_proximity|spread,fingers:[a,b],operator:lt|gt,value:number} or "
                + "{kind:orientation,axis:index_axis,direction:left|right|up|down|up_left|up_right|down_left|down_right,toleranceDegrees:5..45}. "
                + "Only these combinations: index_axis orientation with index bend 0; "
                + "index/middle spread with both bends 0 (strict upper supported spread 0.3); thumb/index tip_proximity lt at least 0.05 with thumb=.7,index=.735. "
                + "Other fingers remain explicit. Output up is upward in the robot view, right is view-right. Reject unsupported axes/pairs, conflicting bounds or bends; never silently drop terms. "
                + "A relation does not prove physical actuation or fingertip contact.\n";
    }
}
