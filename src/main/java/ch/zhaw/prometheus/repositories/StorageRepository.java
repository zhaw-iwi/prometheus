package ch.zhaw.prometheus.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ch.zhaw.prometheus.model.Storage;

public interface StorageRepository extends JpaRepository<Storage, UUID> {

}
