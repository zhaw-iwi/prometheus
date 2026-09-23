package ch.zhaw.prometheus.model.rps;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;

import ch.zhaw.prometheus.model.Action;
import ch.zhaw.prometheus.model.PreparedAction;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.snapshot.ObservationSnapshot;
import jakarta.persistence.Entity;

@Entity
public class RpsSetTargetWinsAction extends Action {
    private static final Pattern INTEGER = Pattern.compile("(?<![\\p{L}\\p{N}])-?\\d+(?![\\p{L}\\p{N}])");
    private static final Map<String, Integer> SMALL_NUMBER_WORDS = Map.ofEntries(
            Map.entry("one", 1),
            Map.entry("two", 2),
            Map.entry("three", 3),
            Map.entry("four", 4),
            Map.entry("five", 5),
            Map.entry("six", 6),
            Map.entry("seven", 7),
            Map.entry("eight", 8),
            Map.entry("nine", 9),
            Map.entry("ten", 10),
            Map.entry("eleven", 11),
            Map.entry("twelve", 12),
            Map.entry("thirteen", 13),
            Map.entry("fourteen", 14),
            Map.entry("fifteen", 15),
            Map.entry("sixteen", 16),
            Map.entry("seventeen", 17),
            Map.entry("eighteen", 18),
            Map.entry("nineteen", 19));
    private static final Map<String, Integer> TENS_NUMBER_WORDS = Map.ofEntries(
            Map.entry("twenty", 20),
            Map.entry("thirty", 30),
            Map.entry("forty", 40),
            Map.entry("fifty", 50),
            Map.entry("sixty", 60),
            Map.entry("seventy", 70),
            Map.entry("eighty", 80),
            Map.entry("ninety", 90));
    private static final Map<String, Integer> SPOKEN_NUMBER_ALIASES = Map.ofEntries(
            Map.entry("won", 1),
            Map.entry("to", 2),
            Map.entry("too", 2),
            Map.entry("tree", 3),
            Map.entry("free", 3),
            Map.entry("for", 4),
            Map.entry("fore", 4),
            Map.entry("ate", 8));

    protected RpsSetTargetWinsAction() {
    }

    public RpsSetTargetWinsAction(Storage storage) {
        super(new NoOpPolicy(), storage, RpsStorageKeys.TARGET_WINS);
        blocking();
    }

    @Override
    public void execute(EventHistory eventHistory, PolicyRuntime runtime) {
        int targetWins = requireTargetWins(eventHistory);
        getStorage().put(RpsStorageKeys.TARGET_WINS, new JsonPrimitive(targetWins));
        getStorage().put(RpsStorageKeys.ROUNDS, new JsonArray());
    }

    @Override
    public PreparedAction prepare(EventHistory events, ObservationSnapshot snapshot, PolicyRuntime runtime) {
        int targetWins = requireTargetWins(events);
        return prepared(java.util.Set.of(RpsStorageKeys.TARGET_WINS, RpsStorageKeys.ROUNDS),
                gateway -> Map.of(
                        RpsStorageKeys.TARGET_WINS, Integer.toString(targetWins),
                        RpsStorageKeys.ROUNDS, "[]"));
    }

    static OptionalInt parseTargetWins(String utterance) {
        if (utterance == null || utterance.isBlank()) {
            return OptionalInt.empty();
        }
        String normalized = utterance.toLowerCase(Locale.ROOT);
        if (normalized.matches(".*\\b(?:minus|negative)\\b.*")) {
            return OptionalInt.empty();
        }
        if (normalized.matches(".*\\b(?:hundred|thousand|million|billion)\\b.*")) {
            return OptionalInt.empty();
        }
        String singleToken = normalized.replaceAll("^[^\\p{L}]+|[^\\p{L}]+$", "");
        Integer spokenAlias = SPOKEN_NUMBER_ALIASES.get(singleToken);
        if (spokenAlias != null) {
            return OptionalInt.of(spokenAlias);
        }

        List<Integer> candidates = new ArrayList<>();
        Matcher integerMatcher = INTEGER.matcher(normalized);
        while (integerMatcher.find()) {
            try {
                candidates.add(Integer.parseInt(integerMatcher.group()));
            } catch (NumberFormatException exception) {
                return OptionalInt.empty();
            }
        }
        String[] tokens = normalized.split("[^\\p{L}]+");
        for (int index = 0; index < tokens.length; index++) {
            Integer tens = TENS_NUMBER_WORDS.get(tokens[index]);
            if (tens != null) {
                int value = tens;
                if (index + 1 < tokens.length) {
                    Integer unit = SMALL_NUMBER_WORDS.get(tokens[index + 1]);
                    if (unit != null && unit < 10) {
                        value += unit;
                        index++;
                    }
                }
                candidates.add(value);
                continue;
            }
            Integer value = SMALL_NUMBER_WORDS.get(tokens[index]);
            if (value != null) {
                candidates.add(value);
                continue;
            }
        }
        List<Integer> distinctCandidates = candidates.stream().distinct().toList();
        if (distinctCandidates.size() != 1 || distinctCandidates.get(0) < 1) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(distinctCandidates.get(0));
    }

    private static int requireTargetWins(EventHistory events) {
        String utterance = latestUserUtterance(events);
        return parseTargetWins(utterance)
                .orElseThrow(() -> new IllegalArgumentException("a single positive target-win count is required"));
    }

    static String latestUserUtterance(EventHistory events) {
        if (events == null || events.isEmpty()) {
            return null;
        }
        List<Event> source = events.toList();
        for (int index = source.size() - 1; index >= 0; index--) {
            Event event = source.get(index);
            if (event != null && Event.TYPE_USER_UTTERANCE.equals(event.getType())) {
                return event.getPayload();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "RpsSetTargetWinsAction";
    }
}
