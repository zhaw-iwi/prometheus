package ch.zhaw.prometheus.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ch.zhaw.prometheus.model.Agent;

public interface AgentRepository extends JpaRepository<Agent, UUID> {

}
