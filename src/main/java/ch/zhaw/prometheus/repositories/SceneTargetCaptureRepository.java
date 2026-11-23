package ch.zhaw.prometheus.repositories;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ch.zhaw.prometheus.model.hand.SceneTargetCapture;

public interface SceneTargetCaptureRepository extends JpaRepository<SceneTargetCapture,UUID> {
    interface View { UUID getEpoch(); int getGeneration(); String getSnapshot(); }
    // Scalar projection bypasses an enclosing turn's first-level entity cache.
    @org.springframework.data.jpa.repository.Query("select c.epoch as epoch,c.generation as generation,c.snapshot as snapshot from SceneTargetCapture c where c.agentId=:id")
    java.util.Optional<View> latest(UUID id);
}
