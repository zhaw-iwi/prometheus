package ch.zhaw.prometheus.definition.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeclarativeAgentInstanceJpaRepository
        extends JpaRepository<DeclarativeAgentInstanceEntity, UUID> {
    boolean existsByDefinitionRevision_Id(long revisionId);
}
