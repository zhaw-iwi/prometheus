package ch.zhaw.prometheus.agentdefs.tdsr.aisha;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.Gson;

public final class AishaCatalog {
    public static final String DEFAULT_RESOURCE =
            "/ch/zhaw/prometheus/agentdefs/tdsr/aisha/aisha-catalog-v1.json";
    public static final int SCHEMA_VERSION = 1;

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9]+(?:_[a-z0-9]+)*");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_TOKEN = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Set<String> ALLOWED_GESTURES = Set.of(
            "OPEN_QUESTION", "EXPLAIN", "UNCERTAIN", "ACKNOWLEDGE", "POLITE", "NONE");
    private static final Set<String> ARABIC_STOP_WORDS = Set.of(
            "ما", "ماذا", "من", "هي", "هو", "هل", "كيف", "عن", "في", "على", "الى", "ان",
            "يمكن", "يمكنني", "لديكم", "لدينا", "هذه", "هذا", "التي", "الذي", "مع", "قطر");
    private static final double MINIMUM_CANDIDATE_SCORE = 0.34;

    private final String catalogVersion;
    private final String reviewStatus;
    private final List<Entry> entries;
    private final Map<String, Entry> entriesById;

    public AishaCatalog(CatalogDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Aisha catalog document is required");
        }
        if (document.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported Aisha catalog schema version: "
                    + document.schemaVersion());
        }
        this.catalogVersion = requireText(document.catalogVersion(), "catalogVersion");
        this.reviewStatus = requireText(document.reviewStatus(), "reviewStatus");
        if (document.entries() == null || document.entries().isEmpty()) {
            throw new IllegalArgumentException("Aisha catalog must contain at least one entry");
        }
        Map<String, Entry> indexed = new LinkedHashMap<>();
        for (Entry rawEntry : document.entries()) {
            Entry entry = validateEntry(rawEntry);
            if (indexed.putIfAbsent(entry.id(), entry) != null) {
                throw new IllegalArgumentException("duplicate Aisha catalog entry id: " + entry.id());
            }
        }
        this.entriesById = Map.copyOf(indexed);
        this.entries = List.copyOf(indexed.values());
    }

    public static AishaCatalog loadDefault() {
        return load(AishaCatalog.class.getResourceAsStream(DEFAULT_RESOURCE), DEFAULT_RESOURCE);
    }

    public static AishaCatalog load(InputStream input, String sourceName) {
        if (input == null) {
            throw new IllegalStateException("Aisha catalog resource is missing: " + sourceName);
        }
        try (InputStream stream = input;
                InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            CatalogDocument document = new Gson().fromJson(reader, CatalogDocument.class);
            return new AishaCatalog(document);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("cannot load Aisha catalog: " + sourceName, exception);
        }
    }

    public String catalogVersion() {
        return this.catalogVersion;
    }

    public String reviewStatus() {
        return this.reviewStatus;
    }

    public List<Entry> entries() {
        return this.entries;
    }

    public Entry entry(String id) {
        return this.entriesById.get(id);
    }

    public List<Candidate> candidates(String question, int maximumCandidates) {
        if (maximumCandidates < 1 || question == null || question.isBlank()) {
            return List.of();
        }
        String normalizedQuestion = normalize(question);
        Set<String> questionTokens = tokens(normalizedQuestion);
        if (questionTokens.isEmpty()) {
            return List.of();
        }
        List<Candidate> scored = new ArrayList<>();
        for (Entry entry : this.entries) {
            double score = 0.0;
            for (String alias : entry.questionAliasesAr()) {
                score = Math.max(score, similarity(normalizedQuestion, questionTokens, alias));
            }
            if (score >= MINIMUM_CANDIDATE_SCORE) {
                scored.add(new Candidate(entry, score));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(Candidate::score).reversed()
                        .thenComparing(candidate -> candidate.entry().id()))
                .limit(maximumCandidates)
                .toList();
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT);
        normalized = COMBINING_MARKS.matcher(normalized).replaceAll("");
        normalized = normalized
                .replace('أ', 'ا')
                .replace('إ', 'ا')
                .replace('آ', 'ا')
                .replace('ى', 'ي')
                .replace('ؤ', 'و')
                .replace('ئ', 'ي')
                .replace('ة', 'ه');
        return NON_TOKEN.matcher(normalized).replaceAll(" ").trim().replaceAll("\\s+", " ");
    }

    private static double similarity(String normalizedQuestion, Set<String> questionTokens, String alias) {
        String normalizedAlias = normalize(alias);
        if (normalizedAlias.isEmpty()) {
            return 0.0;
        }
        if (normalizedQuestion.equals(normalizedAlias)) {
            return 1.0;
        }
        if (normalizedQuestion.contains(normalizedAlias) || normalizedAlias.contains(normalizedQuestion)) {
            return 0.9;
        }
        Set<String> aliasTokens = tokens(normalizedAlias);
        if (aliasTokens.isEmpty()) {
            return 0.0;
        }
        int overlap = 0;
        for (String token : questionTokens) {
            if (aliasTokens.contains(token)) {
                overlap++;
            }
        }
        if (overlap == 0) {
            return 0.0;
        }
        return (2.0 * overlap) / (questionTokens.size() + aliasTokens.size());
    }

    private static Set<String> tokens(String normalized) {
        Set<String> result = new LinkedHashSet<>();
        for (String token : normalized.split(" ")) {
            if (token.length() < 2 || ARABIC_STOP_WORDS.contains(token)) {
                continue;
            }
            result.add(token);
        }
        return result;
    }

    private static Entry validateEntry(Entry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Aisha catalog entries must not be null");
        }
        String id = requireText(entry.id(), "entry.id");
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("invalid Aisha catalog entry id: " + id);
        }
        List<String> aliases = normalizedTextList(entry.questionAliasesAr(), id + ".questionAliasesAr");
        if (aliases.isEmpty()) {
            throw new IllegalArgumentException("Aisha catalog entry requires an Arabic question alias: " + id);
        }
        String answer = requireText(entry.approvedAnswerAr(), id + ".approvedAnswerAr");
        if (!containsArabic(answer)) {
            throw new IllegalArgumentException("Aisha approved answer must contain Arabic text: " + id);
        }
        String gesture = requireText(entry.gesture(), id + ".gesture").toUpperCase(Locale.ROOT);
        if (!ALLOWED_GESTURES.contains(gesture)) {
            throw new IllegalArgumentException("unsupported Aisha gesture for " + id + ": " + gesture);
        }
        List<String> protectedFacts = normalizedTextList(entry.protectedFacts(), id + ".protectedFacts");
        List<Integer> sourceRows = entry.sourceRows() == null ? List.of() : List.copyOf(entry.sourceRows());
        Set<Integer> uniqueRows = new HashSet<>();
        for (Integer row : sourceRows) {
            if (row == null || row < 2 || !uniqueRows.add(row)) {
                throw new IllegalArgumentException("invalid or duplicate source row for Aisha entry: " + id);
            }
        }
        return new Entry(id, aliases, answer, protectedFacts, gesture, sourceRows, entry.timeSensitive());
    }

    private static List<String> normalizedTextList(List<String> values, String field) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            normalized.add(requireText(value, field));
        }
        return List.copyOf(normalized);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static boolean containsArabic(String value) {
        return value.codePoints().anyMatch(codePoint -> codePoint >= 0x0600 && codePoint <= 0x06ff);
    }

    public record CatalogDocument(
            int schemaVersion,
            String catalogVersion,
            String reviewStatus,
            List<Entry> entries) {
    }

    public record Entry(
            String id,
            List<String> questionAliasesAr,
            String approvedAnswerAr,
            List<String> protectedFacts,
            String gesture,
            List<Integer> sourceRows,
            boolean timeSensitive) {
    }

    public record Candidate(Entry entry, double score) {
    }
}
