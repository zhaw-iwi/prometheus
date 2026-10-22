package ch.zhaw.prometheus.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ch.zhaw.prometheus.model.Agent;

public interface AgentRepository extends JpaRepository<Agent, UUID> {
    boolean existsByIdAndExecutionEpoch(UUID id, UUID executionEpoch);
    @org.springframework.data.jpa.repository.Query("select a.eventHistory from Agent a where a.id = :id")
    java.util.Optional<ch.zhaw.prometheus.model.event.EventHistory> findHistoryById(UUID id);
    @org.springframework.data.jpa.repository.Query("select a.id from Agent a")
    java.util.List<UUID> findAllIds();
}
