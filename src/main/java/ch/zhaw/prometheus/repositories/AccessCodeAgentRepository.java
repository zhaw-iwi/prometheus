package ch.zhaw.prometheus.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ch.zhaw.prometheus.model.access.AccessCodeAgent;

public interface AccessCodeAgentRepository extends JpaRepository<AccessCodeAgent, UUID> {
    List<AccessCodeAgent> findByAccessCodeId(UUID accessCodeId);
}
