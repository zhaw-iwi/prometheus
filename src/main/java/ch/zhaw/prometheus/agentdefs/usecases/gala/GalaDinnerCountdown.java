package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.policy.LivePlaybackCue;
import ch.zhaw.prometheus.model.policy.LiveSpeechPart;

/** Only consecutive, complete, fresh countdown prefixes may bridge acoustic segments. */
final class GalaDinnerCountdown {
    static final String PENDING = PREFIX + "rps_countdown_pending";
    private static final long MAX_PAUSE_MS = 5000;
    record Part(String segmentId, LiveSpeechPart speech) {}
    private GalaDinnerCountdown() {}

    static List<Part> append(Storage storage, String segmentId, List<LiveSpeechPart> fresh, Instant boundary) {
        var previous = new ArrayList<Part>();
        if (storage.containsKey(PENDING)) for (var value : storage.get(PENDING).getAsJsonArray()) {
            var part = value.getAsJsonObject();
            previous.add(new Part(part.get("segment").getAsString(), new LiveSpeechPart(part.get("text").getAsString(),
                    Instant.parse(part.get("at").getAsString()), part.get("receipt").getAsString())));
        }
        remove(storage, PENDING);
        if (fresh.isEmpty() || fresh.stream().anyMatch(part -> part.receiptId() == null)) return List.of();
        if (!previous.isEmpty()) {
            var last = previous.getLast().speech().receivedAt();
            var first = fresh.getFirst().receivedAt();
            if (previous.stream().anyMatch(part -> part.speech().receivedAt().isBefore(boundary))
                    || first.isBefore(last) || first.isAfter(last.plusMillis(MAX_PAUSE_MS))) previous.clear();
            // Recording normally deduplicates segments; a duplicate must never advance a prefix.
            else if (previous.stream().anyMatch(part -> part.segmentId().equals(segmentId))) return List.of();
        }
        var combined = new ArrayList<>(previous);
        combined.addAll(fresh.stream().map(part -> new Part(segmentId, part)).toList());
        // Segment boundaries are word boundaries even when native fragments omit spaces/punctuation.
        if (!previous.isEmpty()) {
            int at = previous.size(); var part = combined.get(at);
            combined.set(at, new Part(segmentId, new LiveSpeechPart(" " + part.speech().text(),
                    part.speech().receivedAt(), part.speech().receiptId())));
        }
        if (bounded(combined) && GalaDinnerRps.countdown(join(combined), true)) return List.copyOf(combined);
        if (!prefix(join(combined))) {
            combined.clear(); combined.addAll(fresh.stream().map(part -> new Part(segmentId, part)).toList());
        }
        // Discard an optional lead-in fragment; keep only receipts belonging to a valid prefix.
        while (combined.size() > 1 && prefix(join(combined.subList(1, combined.size())))) combined.removeFirst();
        if (!bounded(combined) || !prefix(join(combined))) return List.of();
        var pending = new JsonArray();
        for (var part : combined) {
            var value = new JsonObject(); value.addProperty("segment", part.segmentId());
            value.addProperty("text", part.speech().text()); value.addProperty("at", part.speech().receivedAt().toString());
            value.addProperty("receipt", part.speech().receiptId()); pending.add(value);
        }
        // Storage entries are bounded at 8192 characters; leave room for serialization overhead.
        if (pending.toString().length() <= 7000) storage.put(PENDING, pending);
        return List.of();
    }
    private static boolean bounded(List<Part> parts) {
        return parts.size() <= LivePlaybackCue.MAX_RECEIPT_IDS
                && parts.stream().map(Part::segmentId).distinct().count() <= LivePlaybackCue.MAX_SEGMENTS
                && parts.stream().map(part -> part.speech().receiptId()).distinct().count() == parts.size()
                && parts.stream().mapToInt(part -> part.speech().text().length()).sum() <= 256;
    }
    private static String join(List<Part> parts) {
        return parts.stream().map(part -> part.speech().text()).collect(java.util.stream.Collectors.joining());
    }
    private static boolean prefix(String text) {
        String words = text.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{S}]+", " ").replaceAll("\\s+", " ").trim();
        return words.matches("(?:(?:get )?ready )?rock(?: paper)?|(?:(?:mach dich )?bereit )?schere(?: stein)?");
    }
}
