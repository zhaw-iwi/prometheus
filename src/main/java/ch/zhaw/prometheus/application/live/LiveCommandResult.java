package ch.zhaw.prometheus.application.live;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Predicate;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.behaviour.DirectCommands;
import ch.zhaw.prometheus.model.event.Event;

/** Quiet, bounded facts for a current admitted native turn, never a new speaking occasion. */
public final class LiveCommandResult {
    private LiveCommandResult() {}
    public static String facts(Agent agent, Instant now, Predicate<UUID> admittedInSession) {
        var result = agent.getStorage().get(DirectCommands.LAST);
        if (result == null || !DirectCommands.enabled(agent.getInteractionProfile())) return "";
        var user = agent.getEventHistory().toList().stream().filter(e -> Event.TYPE_USER_UTTERANCE.equals(e.getType()))
                .reduce((a, b) -> b).orElse(null);
        if (user == null || user.getId() == null || user.getCreatedDate() == null
                || !now.isBefore(user.getCreatedDate().plusSeconds(30)) || user.getCreatedDate().isAfter(now)
                || !admittedInSession.test(user.getId())) return "";
        var receipt = result.getAsJsonObject();
        if (!java.util.Objects.equals(user.sourcePosition(), receipt.get("sourcePosition").getAsLong())) return "";
        String status = receipt.get("status").getAsString();
        String meaning = switch (status) {
            case "ACCEPTED" -> "Accepted output plan: " + receipt.get("kind").getAsString() + " " + receipt.get("value").getAsString() + ". Rendering/physical execution unconfirmed.";
            case "CONFLICT" -> "Not performed: pending task output conflicts. Ask whether to resolve or pause it; no task change made.";
            case "UNSUPPORTED" -> "Not performed: unsupported output. Offer a declared alternative.";
            case "CLARIFY" -> "Not performed: clarify one supported sign/gesture.";
            case "CAPABILITY" -> "Capability question only; no demonstration requested.";
            default -> "";
        };
        return meaning.isEmpty() ? "" : "COMMAND RESULT for user turn " + user.getId() + ": " + meaning
                + (receipt.get("silent").getAsBoolean() ? " Explicit silence: do not speak." : " Use only for that pending answer; no duplicate acknowledgement.")
                + " No new speaking occasion.";
    }
}
