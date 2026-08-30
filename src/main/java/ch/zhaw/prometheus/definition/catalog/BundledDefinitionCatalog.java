package ch.zhaw.prometheus.definition.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import ch.zhaw.prometheus.definition.compiled.DefinitionCompiler;
import ch.zhaw.prometheus.definition.component.BuiltInComponentCatalog;
import ch.zhaw.prometheus.definition.document.AgentDefinitionDocument;
import ch.zhaw.prometheus.definition.document.AgentDefinitionJson;
import ch.zhaw.prometheus.definition.validation.AgentDefinitionSchemaException;

/** Loads the deterministic, versioned definition catalog bundled on the classpath. */
public final class BundledDefinitionCatalog {
    public static final String MAIN_MANIFEST = "/agent-definitions/catalog/main/manifest.json";

    private static final int MANIFEST_SCHEMA_VERSION = 1;
    private static final Pattern RESOURCE_PATH = Pattern.compile(
            "[a-z0-9_]+(?:/[a-z0-9_]+)*/revision-[1-9][0-9]*\\.json");
    private static final JsonMapper MANIFEST_JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final List<BundledAgentDefinition> definitions;
    private final Map<String, BundledAgentDefinition> byKey;

    private BundledDefinitionCatalog(List<BundledAgentDefinition> definitions) {
        this.definitions = List.copyOf(definitions);
        Map<String, BundledAgentDefinition> indexed = new LinkedHashMap<>();
        definitions.forEach(definition -> indexed.put(definition.document().key(), definition));
        this.byKey = Map.copyOf(indexed);
    }

    public static BundledDefinitionCatalog loadMainCatalog() {
        return load(MAIN_MANIFEST);
    }

    static BundledDefinitionCatalog load(String manifestResource) {
        Manifest manifest = readManifest(manifestResource);
        if (manifest.schemaVersion() != MANIFEST_SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported bundled catalog manifest schema version: "
                    + manifest.schemaVersion());
        }
        if (manifest.entries() == null || manifest.entries().isEmpty()) {
            throw new IllegalStateException("Bundled catalog manifest has no entries: " + manifestResource);
        }

        String base = manifestResource.substring(0, manifestResource.lastIndexOf('/') + 1);
        AgentDefinitionJson definitionJson = new AgentDefinitionJson();
        DefinitionCompiler compiler = new DefinitionCompiler(BuiltInComponentCatalog.createRegistry(), definitionJson);
        List<BundledAgentDefinition> loaded = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        Set<String> resources = new HashSet<>();
        String previousKey = null;
        for (ManifestEntry entry : manifest.entries()) {
            requireManifestEntry(entry, keys, resources, previousKey);
            previousKey = entry.key();
            String classpathResource = base + entry.resource();
            AgentDefinitionDocument document;
            try (InputStream input = BundledDefinitionCatalog.class.getResourceAsStream(classpathResource)) {
                if (input == null) {
                    throw new IllegalStateException("Bundled agent definition is missing: " + classpathResource);
                }
                try {
                    document = definitionJson.parse(input);
                } catch (AgentDefinitionSchemaException exception) {
                    throw new IllegalStateException("Bundled agent definition failed schema validation: "
                            + classpathResource + " " + exception.violations(), exception);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to close bundled agent definition: " + classpathResource,
                        exception);
            }
            if (!entry.key().equals(document.key()) || entry.revision() != document.revision()) {
                throw new IllegalStateException("Bundled catalog entry does not match document identity: "
                        + entry.key() + " revision " + entry.revision());
            }
            loaded.add(new BundledAgentDefinition(entry.resource(), document, compiler.compile(document)));
        }
        return new BundledDefinitionCatalog(loaded);
    }

    public List<BundledAgentDefinition> definitions() {
        return this.definitions;
    }

    public Optional<BundledAgentDefinition> find(String key) {
        return Optional.ofNullable(this.byKey.get(key));
    }

    public BundledAgentDefinition require(String key) {
        return find(key).orElseThrow(() -> new IllegalArgumentException("Unknown bundled definition key: " + key));
    }

    private static Manifest readManifest(String resource) {
        if (resource == null || !resource.startsWith("/") || !resource.endsWith(".json")) {
            throw new IllegalArgumentException("Manifest must be an absolute JSON classpath resource");
        }
        try (InputStream input = BundledDefinitionCatalog.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Bundled catalog manifest is missing: " + resource);
            }
            return MANIFEST_JSON.readValue(input, Manifest.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read bundled catalog manifest: " + resource, exception);
        }
    }

    private static void requireManifestEntry(ManifestEntry entry, Set<String> keys, Set<String> resources,
            String previousKey) {
        if (entry == null || entry.key() == null || entry.key().isBlank() || entry.revision() < 1
                || entry.resource() == null || !RESOURCE_PATH.matcher(entry.resource()).matches()) {
            throw new IllegalStateException("Bundled catalog contains an invalid manifest entry");
        }
        if (previousKey != null && previousKey.compareTo(entry.key()) >= 0) {
            throw new IllegalStateException("Bundled catalog keys must be unique and sorted: " + entry.key());
        }
        if (!keys.add(entry.key()) || !resources.add(entry.resource())) {
            throw new IllegalStateException("Bundled catalog contains a duplicate key or resource: " + entry.key());
        }
        String expectedSuffix = "revision-" + entry.revision() + ".json";
        if (!entry.resource().endsWith(expectedSuffix)) {
            throw new IllegalStateException("Bundled catalog revision does not match resource path: " + entry.key());
        }
    }

    private record Manifest(int schemaVersion, List<ManifestEntry> entries) {
    }

    private record ManifestEntry(String key, int revision, String resource) {
    }
}
