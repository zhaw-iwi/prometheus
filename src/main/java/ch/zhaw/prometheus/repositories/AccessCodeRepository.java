package ch.zhaw.prometheus.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ch.zhaw.prometheus.model.access.AccessCode;

public interface AccessCodeRepository extends JpaRepository<AccessCode, UUID> {
    Optional<AccessCode> findByCode(String code);
}
