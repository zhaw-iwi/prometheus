package ch.zhaw.prometheus.application.live;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pure routing/deduplication policy. A batch is attempted once; ACK uncertainty is not retried. */
public final class LiveContextDelivery {
    public record Narration(UUID sourceId, String text) {}
    public record Command(String type, String content, String delegationId, UUID sourceId, String revision) {
        @Override public String toString() { return "LiveCommand[type=" + type + ",source=" + sourceId + ",revision=" + revision + "]"; }
    }
    public record Batch(LiveContextSnapshot context, List<Command> commands) { public Batch { commands = List.copyOf(commands); } }
    private LiveContextSnapshot delivered;
    private final Set<UUID> attemptedNarrations = new HashSet<>();
    private boolean ready;
    public LiveContextDelivery(LiveContextSnapshot initial) { delivered = initial; }
    public void ready() { ready = true; }
    public Batch plan(LiveContextSnapshot current, List<Narration> narrations) {
        if (!ready) return new Batch(delivered, List.of());
        if (!delivered.epoch().equals(current.epoch())) throw new IllegalStateException("Obsolete Live context");
        List<Command> commands = new ArrayList<>();
        String revision = current.revision();
        if (!delivered.instructions().equals(current.instructions())) {
            add(commands, "instructions", "CURRENT STATE guidance replaces earlier guidance. Revision " + revision + ".\n" + current.instructions(), null, null, revision);
        }
        var old = new HashMap<String, LiveContextSnapshot.Item>(); delivered.items().forEach(item -> old.put(item.key(), item));
        List<String> removed = current.removedSince(delivered);
        if (!removed.isEmpty()) add(commands, "thinking", "Selected evidence removed; no longer current: " + String.join(", ", removed), null, null, revision);
        for (var item : current.items()) if (item.role().equals("developer") && !item.equals(old.get(item.key())))
            add(commands, "thinking", LiveContextSnapshot.evidenceText(item), null, null, revision);
        if (!revision.equals(delivered.revision())) add(commands, "thinking",
                "CURRENT CONTEXT revision " + revision + "; state " + String.join(" / ", current.statePath())
                + ". New selected facts supersede previous values. No action is implied by this update.", null, null, revision);
        for (Narration narration : narrations) {
            if (attemptedNarrations.contains(narration.sourceId())) continue;
            if (attemptedNarrations.size() >= 4096) throw new IllegalStateException("Narration identity capacity reached");
            attemptedNarrations.add(narration.sourceId());
            String label = "Confirmed backend announcement " + narration.sourceId() + ": ";
            if (bytes(label + narration.text()) <= 360) add(commands, "commentary", label + narration.text(), null, narration.sourceId(), revision);
            else {
                add(commands, "thinking", label + narration.text(), null, narration.sourceId(), revision);
                add(commands, "commentary", "Announce the confirmed backend announcement " + narration.sourceId()
                        + " just supplied in full. Preserve its meaning; do not read metadata aloud.", null, narration.sourceId(), revision);
            }
        }
        if (commands.size() > 128) throw new IllegalStateException("Context batch capacity reached");
        return new Batch(current, commands);
    }
    public void acknowledged(Batch batch) { delivered = batch.context(); }
    public String revision() { return delivered.revision(); }

    public static List<Command> contextReply(String text, String delegation, String revision) {
        List<Command> commands = new ArrayList<>(); add(commands, "thinking", text, delegation, null, revision); return List.copyOf(commands);
    }
    public static Command clarification(UUID segment, String revision) {
        return new Command("session.commentary.append", "The previous short reply has not been applied because its spoken context is uncertain. Ask the user to restate the intended choice in a complete sentence.", null, segment, revision);
    }
    private static void add(List<Command> result, String kind, String text, String delegation, UUID source, String revision) {
        // UTF-8 byte count conservatively bounds tokens, including multilingual text.
        List<String> chunks = new ArrayList<>(); StringBuilder chunk = new StringBuilder(); int count = 0;
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset); String value = new String(Character.toChars(cp)); int size = bytes(value);
            if (count + size > 360) { chunks.add(chunk.toString()); chunk.setLength(0); count = 0; }
            chunk.append(value); count += size; offset += Character.charCount(cp);
        }
        if (!chunk.isEmpty()) chunks.add(chunk.toString());
        for (int index = 0; index < chunks.size(); index++) {
            String prefix = chunks.size() == 1 ? "" : "Contiguous context part " + (index + 1) + "/" + chunks.size() + ":\n";
            result.add(new Command("session." + kind + ".append", prefix + chunks.get(index), delegation, source, revision));
        }
    }
    private static int bytes(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }
}
