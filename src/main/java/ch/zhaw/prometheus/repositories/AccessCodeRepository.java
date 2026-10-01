package ch.zhaw.prometheus.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ch.zhaw.prometheus.model.access.AccessCode;

public interface AccessCodeRepository extends JpaRepository<AccessCode, UUID> {
    interface Scope {
        UUID getId();
        boolean getEnabled();
        boolean getLinked();
    }

    // Preserve invalid-code versus invisible-agent errors without loading either graph.
    @org.springframework.data.jpa.repository.Query("""
            select c.id as id, c.enabled as enabled,
                   case when l.id is null then false else true end as linked
            from AccessCode c left join AccessCodeAgent l on l.accessCode = c and l.agent.id = :agentId
            where c.code = :code
            """)
    Optional<Scope> findScope(@org.springframework.data.repository.query.Param("code") String code,
            @org.springframework.data.repository.query.Param("agentId") UUID agentId);

    boolean existsByIdAndEnabledTrue(UUID id);
    Optional<AccessCode> findByCode(String code);
}
