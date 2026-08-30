package ch.zhaw.prometheus.definition.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.zhaw.prometheus.definition.catalog.BundledAgentDefinition;
import ch.zhaw.prometheus.definition.catalog.BundledDefinitionCatalog;
import ch.zhaw.prometheus.definition.document.AgentDefinitionJson;
import ch.zhaw.prometheus.definition.repository.DefinitionProvenance;
import ch.zhaw.prometheus.definition.repository.DefinitionRepository;
import ch.zhaw.prometheus.definition.repository.DefinitionStatus;
import ch.zhaw.prometheus.definition.repository.NewDefinitionRevision;
import ch.zhaw.prometheus.definition.repository.StoredDefinition;
import ch.zhaw.prometheus.definition.repository.StoredDefinitionRevision;

@Service
@Transactional
public class BundledDefinitionImporter {
    private final DefinitionRepository repository;
    private final AgentDefinitionJson definitionJson;

    public BundledDefinitionImporter(DefinitionRepository repository, AgentDefinitionJson definitionJson) {
        this.repository = repository;
        this.definitionJson = definitionJson;
    }

    public BundledImportResult importMainCatalog() {
        return importDefinitions(BundledDefinitionCatalog.loadMainCatalog().definitions());
    }

    public BundledImportResult importDefinitions(List<BundledAgentDefinition> definitions) {
        if (definitions == null || definitions.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Bundled definitions must not be null");
        }
        List<BundledAgentDefinition> ordered = definitions.stream()
                .sorted(Comparator.comparing((BundledAgentDefinition bundled) -> bundled.document().key())
                        .thenComparingInt(bundled -> bundled.document().revision()))
                .toList();
        preflight(ordered);

        int createdDefinitions = 0;
        int createdRevisions = 0;
        int unchangedRevisions = 0;
        Map<String, List<StoredDefinitionRevision>> importedByKey = new LinkedHashMap<>();
        for (BundledAgentDefinition bundled : ordered) {
            String key = bundled.document().key();
            StoredDefinition identity = this.repository.findDefinition(key).orElse(null);
            if (identity == null) {
                identity = this.repository.createDefinition(key);
                createdDefinitions++;
            }
            StoredDefinitionRevision revision = this.repository.findRevision(key, bundled.document().revision())
                    .orElse(null);
            if (revision == null) {
                revision = this.repository.createRevision(new NewDefinitionRevision(identity.id(),
                        bundled.document().revision(), bundled.document().schemaVersion(), DefinitionStatus.PUBLISHED,
                        this.definitionJson.canonicalJson(bundled.document()), bundled.compiled().contentHash(),
                        DefinitionProvenance.BUNDLED, bundled.resource()));
                createdRevisions++;
            } else {
                unchangedRevisions++;
            }
            importedByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(revision);
        }

        int activated = 0;
        for (Map.Entry<String, List<StoredDefinitionRevision>> imported : importedByKey.entrySet()) {
            StoredDefinition identity = this.repository.findDefinition(imported.getKey()).orElseThrow();
            if (identity.activeRevisionId() != null) {
                continue;
            }
            StoredDefinitionRevision latest = imported.getValue().stream()
                    .filter(revision -> revision.status() == DefinitionStatus.PUBLISHED
                            && revision.provenance() == DefinitionProvenance.BUNDLED)
                    .max(Comparator.comparingInt(StoredDefinitionRevision::revisionNumber)).orElse(null);
            if (latest != null) {
                this.repository.activate(identity.id(), latest.id(), identity.optimisticVersion());
                activated++;
            }
        }
        return new BundledImportResult(createdDefinitions, createdRevisions, activated, unchangedRevisions);
    }

    private void preflight(List<BundledAgentDefinition> definitions) {
        Map<String, String> inputIdentities = new LinkedHashMap<>();
        for (BundledAgentDefinition bundled : definitions) {
            String identity = bundled.document().key() + "#" + bundled.document().revision();
            String canonicalHash = this.definitionJson.contentHash(bundled.document());
            if (!canonicalHash.equals(bundled.compiled().contentHash())) {
                throw new BundledDefinitionConflictException("Bundled compiled hash does not match document: "
                        + identity);
            }
            String previous = inputIdentities.putIfAbsent(identity, bundled.compiled().contentHash());
            if (previous != null && !previous.equals(bundled.compiled().contentHash())) {
                throw new BundledDefinitionConflictException("Bundled input contains conflicting revision "
                        + identity);
            }
            this.repository.findRevision(bundled.document().key(), bundled.document().revision()).ifPresent(existing -> {
                if (!existing.contentHash().equals(bundled.compiled().contentHash())) {
                    throw new BundledDefinitionConflictException("Stored revision has a different bundled hash: "
                            + identity);
                }
            });
        }
    }
}
