package ch.zhaw.prometheus.definition.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentDefinitionIdentityJpaRepository extends JpaRepository<AgentDefinitionIdentityEntity, Long> {
    Optional<AgentDefinitionIdentityEntity> findByDefinitionKey(String definitionKey);

    List<AgentDefinitionIdentityEntity> findAllByOrderByDefinitionKey();
}
