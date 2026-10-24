package ch.zhaw.prometheus.application.live;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.google.gson.JsonParser;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.event.ObservationEvidence;
import ch.zhaw.prometheus.model.policy.NonverbalSummaryPromptContextAugmenter;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.application.live.LiveContextSnapshot.Item;

@Component
public class LiveContextProjection {
    private final PromptMessageAssembler assembler;
    private final LiveVoicePolicyAdapter policies;
    private final Clock clock;
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
        Instant now = clock.instant();
        // selectList retains source identity/time; EventHistory.select intentionally creates working copies.
        List<Event> selected = agent.getEventHistory().selectList(leaf.getEventSelector());
        if (leaf.ownPolicy() instanceof ch.zhaw.prometheus.model.policy.ExternalVoicePolicy external) {
            var all = agent.getEventHistory().toList();
            int start = Math.max(0, Math.min(all.size(), external.liveHistoryStartIndex()));
            var encounter = new java.util.HashSet<>(all.subList(start, all.size()));
            // Weather belongs to the environment, not the previous visitor. Retain only
            // selected, fresh source evidence; never renew its age at encounter startup.
            selected = selected.stream().filter(event -> encounter.contains(event) || freshWeather(event, now)).toList();
        }
        List<Item> candidates = new ArrayList<>();
        List<Event> aggregateEvents = new ArrayList<>();
        List<UUID> aggregateSources = new ArrayList<>();
        Instant aggregateTime = null, aggregateExpiry = null;
        var latestTimes = new HashMap<String, Instant>();
        for (int index = 0; index < selected.size(); index++) {
            Event event = selected.get(index);
            boolean dialogue = Event.TYPE_USER_UTTERANCE.equals(event.getType()) || Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType());
            if (!dialogue && !ch.zhaw.prometheus.model.event.ObservationEvidence.supported(event.getType())) continue;
            Instant observed = observedAt(event);
            if (!dialogue && latestTimes.containsKey(event.getType())
                    && (observed == null || observed.isAfter(now) || !observed.isAfter(latestTimes.get(event.getType())))) continue;
            if (!dialogue && observed != null && !observed.isAfter(now)) latestTimes.put(event.getType(), observed);
            Instant expiry = !dialogue && observed != null ? observed.plusSeconds(ch.zhaw.prometheus.model.event.ObservationEvidence.ttl(event.getType())) : null;
            String freshness = dialogue ? "history" : observed == null || observed.isAfter(now) ? "unknown"
                    : !now.isBefore(expiry) ? "expired" : "fresh";
            if (Event.TYPE_FACE_EMOTION.equals(event.getType()) && freshness.equals("fresh")
                    && !ch.zhaw.prometheus.model.event.ObservationEvidence.usableFace(JsonParser.parseString(event.getPayload()).getAsJsonObject(), 0))
                freshness = "unknown";
            String text = freshness.equals("expired") ? "Current value unknown; previous observation expired."
                    : freshness.equals("unknown") ? "Current value unknown; missing/invalid source age or unreliable detection."
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
                aggregateExpiry = aggregateEvents.stream().map(LiveContextProjection::observedAt).min(Instant::compareTo).orElseThrow()
                        .plusSeconds(ch.zhaw.prometheus.model.event.ObservationEvidence.ttl(Event.TYPE_FACE_EMOTION));
            } else if (event.getType().equals(Event.TYPE_FACE_EMOTION)) aggregateEvents.clear();
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
        if (leaf.ownPolicy() instanceof ch.zhaw.prometheus.model.policy.ExternalVoicePolicy external
                && external.liveActivityContext() != null) {
            var activity = external.liveActivityContext();
            basis.append(activity.facts());
            if (activity.continuation() != null) basis.append(activity.continuation().id())
                    .append(activity.continuation().occasion()).append(activity.continuation().facts())
                    .append(activity.continuation().requiredSpeech());
        }
        return snapshot(agent, hash(basis.toString()), now, instructions, candidates, Math.max(0, omitted), guidance);
    }
    private static LiveContextSnapshot snapshot(Agent agent, String revision, Instant now, String instructions, List<Item> items, int omitted, Map<String, String> guidance) {
        var leaf = LiveVoicePolicyAdapter.chain(agent.getCurrentState()).getLast();
        long userSilence = leaf.ownPolicy() instanceof ch.zhaw.prometheus.model.policy.ExternalVoicePolicy external
                ? external.liveUserSilenceMs() : LiveTranscriptSegmenter.SILENCE_MS;
        return new LiveContextSnapshot(agent.getId(), agent.executionEpoch(), revision, now,
                agent.getCurrentState().getActiveStatePath(), instructions, items, omitted,
                guidance, userSilence, leaf.ownPolicy() instanceof ch.zhaw.prometheus.model.policy.ExternalVoicePolicy external ? external.liveActivityContext() : null,
                leaf.ownPolicy() instanceof ch.zhaw.prometheus.model.policy.ExternalVoicePolicy external && external.liveFilterIsolatedInputActivity());
    }
    private static Instant observedAt(Event event) {
        if (ch.zhaw.prometheus.model.event.ObservationEvidence.supported(event.getType()))
            return ch.zhaw.prometheus.model.event.ObservationEvidence.observed(event);
        return event.getCreatedDate();
    }
    private static boolean freshWeather(Event event, Instant now) {
        return (Event.TYPE_WEATHER_CURRENT.equals(event.getType()) || Event.TYPE_WEATHER_FORECAST.equals(event.getType()))
                && ObservationEvidence.fresh(ObservationEvidence.observed(event), now, ObservationEvidence.ttl(event.getType()), 0);
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
