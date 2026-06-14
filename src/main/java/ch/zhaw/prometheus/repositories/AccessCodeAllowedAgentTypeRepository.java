package ch.zhaw.prometheus.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ch.zhaw.prometheus.model.access.AccessCodeAllowedAgentType;

public interface AccessCodeAllowedAgentTypeRepository extends JpaRepository<AccessCodeAllowedAgentType, UUID> {
    List<AccessCodeAllowedAgentType> findByAccessCodeId(UUID accessCodeId);
}
