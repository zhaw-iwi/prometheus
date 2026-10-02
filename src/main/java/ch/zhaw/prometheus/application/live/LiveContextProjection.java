package ch.zhaw.prometheus.application.live;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.google.gson.JsonParser;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.NonverbalSummaryPromptContextAugmenter;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.application.live.LiveContextSnapshot.Item;

@Component
public class LiveContextProjection {
    private final PromptMessageAssembler assembler;
    private final LiveVoicePolicyAdapter policies;
    private final Clock clock;
    private static final Map<String, Long> TTL = Map.of(
            Event.TYPE_FACE_EMOTION, 15L, Event.TYPE_HUMAN_PRESENCE, 15L, Event.TYPE_SOCIAL_GROUPING, 15L,
            Event.TYPE_SOCIAL_CONTEXT, 15L, Event.TYPE_SOCIAL_SITUATION_CHANGE, 30L, Event.TYPE_HAND_SIGN, 15L,
            Event.TYPE_WEATHER_CURRENT, 600L, Event.TYPE_WEATHER_FORECAST, 21600L);
    @Autowired public LiveContextProjection(PromptMessageAssembler assembler, LiveVoicePolicyAdapter policies) {
        this(assembler, policies, Clock.systemUTC());
    }
    public LiveContextProjection(PromptMessageAssembler assembler, LiveVoicePolicyAdapter policies, Clock clock) {
        this.assembler = assembler; this.policies = policies; this.clock = clock;
    }
    public boolean supports(Agent agent) { return policies.supports(agent); }
    public LiveContextSnapshot project(Agent agent) {
        var guidance = policies.sections(agent);
        String instructions = String.join("\n", guidance.values());
        var leaf = LiveVoicePolicyAdapter.chain(agent.getCurrentState()).getLast();
        // selectList retains source identity/time; EventHistory.select intentionally creates working copies.
        List<Event> selected = agent.getEventHistory().selectList(leaf.getEventSelector());
        List<Item> candidates = new ArrayList<>();
        List<Event> aggregateEvents = new ArrayList<>();
        List<UUID> aggregateSources = new ArrayList<>();
        Instant aggregateTime = null, aggregateExpiry = null;
        Instant now = clock.instant();
        for (int index = 0; index < selected.size(); index++) {
            Event event = selected.get(index);
            boolean dialogue = Event.TYPE_USER_UTTERANCE.equals(event.getType()) || Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType());
            if (!dialogue && !TTL.containsKey(event.getType())) continue;
            Instant observed = observedAt(event);
            Instant expiry = !dialogue && observed != null ? observed.plusSeconds(TTL.get(event.getType())) : null;
            String freshness = dialogue ? "history" : observed == null || observed.isAfter(now) ? "unknown"
                    : !now.isBefore(expiry) ? "expired" : "fresh";
            String text = freshness.equals("expired") ? "Current value unknown; previous observation expired."
                    : assembler.toPromptMessage(event).getContent();
            if (text.isBlank()) continue;
            String key = event.getId() == null ? "unpersisted-" + index : event.getId().toString();
            var ids = event.getId() == null ? List.<UUID>of() : List.of(event.getId());
            Item item = new Item(key, event.getType(), dialogue ? assembler.mapRole(event) : "developer",
                    ids, event.getCreatedDate(), observed, expiry, freshness, bounded(text, 1000));
            // Coalesce current sensors by type. Face summaries keep only selected, fresh evidence.
            if (!dialogue) candidates.removeIf(old -> old.type().equals(event.getType()));
            candidates.add(item);
            if (event.getType().equals(Event.TYPE_FACE_EMOTION) && freshness.equals("fresh")) {
                aggregateEvents.add(event);
                if (aggregateEvents.size() > 8) aggregateEvents.removeFirst();
                aggregateTime = observed;
                if (aggregateExpiry == null || expiry.isBefore(aggregateExpiry)) aggregateExpiry = expiry;
            }
        }
        EventHistory aggregateHistory = new EventHistory();
        for (Event event : aggregateEvents) {
            aggregateHistory.appendEvent(event);
            if (event.getId() != null) aggregateSources.add(event.getId());
        }
        var summaries = new NonverbalSummaryPromptContextAugmenter().augment(aggregateHistory);
        if (!summaries.isEmpty()) candidates.add(new Item("face-summary", "summary.face", "developer", aggregateSources,
                null, aggregateTime, aggregateExpiry, "fresh", bounded(summaries.getFirst().getContent(), 1000)));
        int omitted = selected.size() - candidates.size();
        // Keep the most recent evidence and dialogue within a conservative byte budget, including JSON overhead.
        while (!candidates.isEmpty() && (candidates.size() > 40 || snapshot(agent, "", now, instructions, candidates, 0, guidance)
                .startupInput().toString().getBytes(StandardCharsets.UTF_8).length > 7000)) {
            candidates.removeFirst(); omitted++;
        }
        StringBuilder basis = new StringBuilder(instructions).append(leaf.getEventSelectorSpec() == null
                ? leaf.getName() : leaf.getEventSelectorSpec().toJson());
        for (Item item : candidates) basis.append(item.key()).append(item.type()).append(item.role())
                .append(item.sourceIds()).append(item.observedAt()).append(item.expiresAt()).append(item.freshness()).append(item.text());
        return snapshot(agent, hash(basis.toString()), now, instructions, candidates, Math.max(0, omitted), guidance);
    }
    private static LiveContextSnapshot snapshot(Agent agent, String revision, Instant now, String instructions, List<Item> items, int omitted, Map<String, String> guidance) {
        return new LiveContextSnapshot(agent.getId(), agent.executionEpoch(), revision, now,
                agent.getCurrentState().getActiveStatePath(), instructions, items, omitted,
                guidance);
    }
    private static Instant observedAt(Event event) {
        if (TTL.containsKey(event.getType())) {
            try {
                var payload = JsonParser.parseString(event.getPayload());
                if (payload.isJsonObject()) {
                    for (String field : List.of("observed_at", "ts")) {
                        if (payload.getAsJsonObject().has(field))
                            return Instant.parse(payload.getAsJsonObject().get(field).getAsString());
                    }
                }
            } catch (RuntimeException invalid) { return null; }
        }
        return event.getCreatedDate();
    }
    static String bounded(String text, int maxBytes) {
        if (text.getBytes(StandardCharsets.UTF_8).length <= maxBytes) return text;
        int end = Math.min(text.length(), maxBytes / 4);
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end) + " [truncated]";
    }
    private static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
