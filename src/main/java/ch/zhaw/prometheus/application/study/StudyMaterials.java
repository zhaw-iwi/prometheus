package ch.zhaw.prometheus.application.study;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;
import com.google.gson.Gson;

/** Versioned research materials, kept separate from model instructions and participant responses. */
@Component
public class StudyMaterials {
    public record Item(String id, String prompt, String kind) {}
    public record Scenario(String id, String title, boolean ready, String task, String privatePrompt,
            Item revision, Item probe, List<Item> understanding, List<String> rubric,List<Integer> sceneProbe) {
        public Scenario { understanding = List.copyOf(understanding); rubric = List.copyOf(rubric);sceneProbe=sceneProbe==null?List.of():List.copyOf(sceneProbe); }
    }
    public record Manifest(String version, String language, List<Scenario> scenarios,
            List<Item> pre, List<Item> post) {
        public Manifest { scenarios = List.copyOf(scenarios); pre = List.copyOf(pre); post = List.copyOf(post); }
    }
    private static final Set<String> SCENARIOS = Set.of("guess", "context", "signs");
    // Executors alone do not qualify a scenario for the complete research procedure.
    private static final Set<String> QUALIFIED = Set.of();
    private final Manifest manifest;
    public StudyMaterials() {
        try (var input = StudyMaterials.class.getResourceAsStream("/hri-study/materials-v1.json")) {
            manifest = parse(new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8));
        } catch (java.io.IOException failure) { throw new IllegalStateException("Study materials unavailable", failure); }
    }
    public Manifest manifest() { return manifest; }
    public Scenario scenario(String id) {
        return manifest.scenarios().stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown study scenario"));
    }
    public static Manifest parse(String json) {
        try {
            Manifest value = new Gson().fromJson(json, Manifest.class);
            require(value.version() != null && value.version().matches("hri-text-v[0-9]+-draft") && "en".equals(value.language()));
            var ids = new HashSet<String>(); var scenarios = new HashSet<String>();
            for (Scenario scenario : value.scenarios()) {
                require(SCENARIOS.contains(scenario.id()) && scenarios.add(scenario.id()));
                require(!scenario.ready() || QUALIFIED.contains(scenario.id()));
                require(scenario.id().equals("context")?scenario.sceneProbe().equals(List.of(1,2,1)):scenario.sceneProbe().isEmpty());
                require(nonempty(scenario.title()) && nonempty(scenario.task()) && nonempty(scenario.privatePrompt()));
                item(scenario.revision(), ids); item(scenario.probe(), ids);
                require(!scenario.understanding().isEmpty() && !scenario.rubric().isEmpty());
                scenario.understanding().forEach(i -> item(i, ids));
                scenario.rubric().forEach(r -> require(nonempty(r)));
            }
            require(scenarios.equals(SCENARIOS) && !value.pre().isEmpty() && value.post().size() == 8);
            value.pre().forEach(i -> item(i, ids)); value.post().forEach(i -> item(i, ids));
            return value;
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid study materials", invalid); }
    }
    private static void item(Item item, Set<String> ids) {
        require(item != null && item.id() != null && item.id().matches("[a-z][a-z0-9_.-]{1,79}") && ids.add(item.id()));
        require(nonempty(item.prompt()) && Set.of("text", "rating7", "instruction").contains(item.kind()));
    }
    private static boolean nonempty(String value) { return value != null && !value.isBlank() && value.length() <= 2000; }
    private static void require(boolean condition) { if (!condition) throw new IllegalArgumentException(); }
}
