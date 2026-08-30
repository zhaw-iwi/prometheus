package ch.zhaw.prometheus.definition.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AgentDefinitionRevisionJpaRepository extends JpaRepository<AgentDefinitionRevisionEntity, Long> {
    Optional<AgentDefinitionRevisionEntity> findByDefinition_DefinitionKeyAndRevisionNumber(
            String definitionKey, int revisionNumber);

    List<AgentDefinitionRevisionEntity> findByDefinition_DefinitionKeyOrderByRevisionNumber(String definitionKey);

    @Query("select revision from AgentDefinitionIdentityEntity definition "
            + "join definition.activeRevision revision order by definition.definitionKey")
    List<AgentDefinitionRevisionEntity> findActiveRevisions();
}
