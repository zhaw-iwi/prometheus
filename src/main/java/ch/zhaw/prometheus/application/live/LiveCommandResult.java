package ch.zhaw.prometheus.application.live;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Predicate;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.behaviour.DirectCommands;
import ch.zhaw.prometheus.model.event.Event;

/** Quiet, bounded facts for a current admitted native turn, never a new speaking occasion. */
public final class LiveCommandResult {
    public record Turn(UUID source, String status, String facts) {}
    private LiveCommandResult() {}
    public static Turn current(Agent agent, Instant now, Predicate<UUID> admittedInSession) {
        var result = agent.getStorage().get(DirectCommands.LAST);
        if (result == null || !DirectCommands.enabled(agent.getInteractionProfile())) return null;
        var user = agent.getEventHistory().toList().stream().filter(e -> Event.TYPE_USER_UTTERANCE.equals(e.getType()))
                .reduce((a, b) -> b).orElse(null);
        if (user == null || user.getId() == null || user.getCreatedDate() == null
                || !now.isBefore(user.getCreatedDate().plusSeconds(30)) || user.getCreatedDate().isAfter(now)
                || !admittedInSession.test(user.getId())) return null;
        var receipt = result.getAsJsonObject();
        if (!java.util.Objects.equals(user.sourcePosition(), receipt.get("sourcePosition").getAsLong())) return null;
        String status = receipt.get("status").getAsString();
        String meaning = switch (status) {
            case "ACCEPTED" -> "Accepted output plan: " + acceptedPlan(receipt) + ". "
                    + ("HAND_SIGN".equals(receipt.get("kind").getAsString()) ? "Direct sign authorized by this request. No task or consent step. " : "")
                    + ("HAND_POSE".equals(receipt.get("kind").getAsString()) ? "Immediate pose; no demonstration needed. " : "")
                    + ("POINT".equals(receipt.get("kind").getAsString()) ? "One point only. A repeat needs its own result. " : "")
                    + (receipt.has("renderedEvent") ? "Digital renderer confirmed this pose." : "Rendering/physical execution unconfirmed.");
            case "CONFLICT" -> "Not performed: pending task output conflicts. Ask whether to resolve or pause it; no task change made.";
            case "UNSUPPORTED" -> "Not performed: unsupported output. Offer a declared alternative.";
            case "CLARIFY" -> receipt.has("clarification") && receipt.get("clarification").getAsString().equals("ok_shape")
                    ? "Not performed. " + DirectCommands.OK_CHOICE
                    : "Not performed: the action or target is unresolved and needs clarification.";
            case "CAPABILITY" -> "Capability question only; no demonstration requested. A person may request a supported sign directly.";
            default -> "";
        };
        return meaning.isEmpty() ? null : new Turn(user.getId(), receipt.has("renderedEvent") ? "RENDERED" : status,
                "COMMAND RESULT for user turn " + user.getId() + ": " + meaning
                + (receipt.get("silent").getAsBoolean() ? " Explicit silence: do not speak." : " Use only for that pending answer; no duplicate acknowledgement.")
                + " No new speaking occasion.");
    }
    private static String acceptedPlan(com.google.gson.JsonObject receipt) {
        String kind = receipt.get("kind").getAsString();
        // Structured hand commands deliberately have value:null. Keep their full
        // canonical action in Storage, without overflowing this quiet context delta.
        return switch (kind) {
            case "HAND_POSE", "POINT", "HAND_SEQUENCE" -> kind;
            default -> kind + " " + receipt.get("value").getAsString();
        };
    }
}
