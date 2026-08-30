package ch.zhaw.prometheus.definition.application;

import java.util.List;

import ch.zhaw.prometheus.definition.compiled.DefinitionRevisionLoader;
import ch.zhaw.prometheus.definition.compiled.DefinitionRevisionSource;
import ch.zhaw.prometheus.definition.document.AgentDefinitionDocument;
import ch.zhaw.prometheus.definition.document.AgentDefinitionJson;
import ch.zhaw.prometheus.definition.repository.DefinitionLifecycleException;
import ch.zhaw.prometheus.definition.repository.DefinitionNotFoundException;
import ch.zhaw.prometheus.definition.repository.DefinitionRepository;
import ch.zhaw.prometheus.definition.repository.StoredDefinitionRevision;

public final class DefinitionRevisionSources implements DefinitionRevisionLoader {
    private final DefinitionRepository repository;
    private final AgentDefinitionJson definitionJson;

    public DefinitionRevisionSources(DefinitionRepository repository, AgentDefinitionJson definitionJson) {
        this.repository = repository;
        this.definitionJson = definitionJson;
    }

    @Override
    public DefinitionRevisionSource load(long revisionId) {
        return source(this.repository.findRevision(revisionId)
                .orElseThrow(() -> new DefinitionNotFoundException("Definition revision not found: " + revisionId)));
    }

    public List<DefinitionRevisionSource> active() {
        return this.repository.findActiveRevisions().stream().map(this::source).toList();
    }

    public DefinitionRevisionSource source(StoredDefinitionRevision revision) {
        AgentDefinitionDocument document = this.definitionJson.parse(revision.canonicalJson());
        String actualHash = this.definitionJson.contentHash(document);
        if (!revision.definitionKey().equals(document.key()) || revision.revisionNumber() != document.revision()
                || revision.schemaVersion() != document.schemaVersion()) {
            throw new DefinitionLifecycleException("Persisted revision identity does not match specification JSON: "
                    + revision.id());
        }
        if (!revision.contentHash().equals(actualHash)) {
            throw new DefinitionLifecycleException("Persisted revision hash does not match canonical JSON: "
                    + revision.id());
        }
        return new DefinitionRevisionSource(revision.id(), revision.contentHash(), document);
    }
}
