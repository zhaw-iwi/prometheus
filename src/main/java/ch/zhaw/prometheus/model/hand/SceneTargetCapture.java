package ch.zhaw.prometheus.model.hand;

import java.util.UUID;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.persistence.*;
import ch.zhaw.prometheus.model.Agent;

/** One bounded latest capture per agent; it cannot itself authorize behaviour. */
@Entity
public class SceneTargetCapture {
    @Id private UUID agentId;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="agentId",insertable=false,updatable=false)
    @org.hibernate.annotations.OnDelete(action=org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Agent agent;
    @Version private long version;
    private UUID epoch;
    private int generation;
    @Column(columnDefinition="TEXT") private String snapshot;
    protected SceneTargetCapture() {}
    public SceneTargetCapture(UUID agentId) { this.agentId=agentId; }
    public UUID epoch() { return epoch; }
    public int generation() { return generation; }
    public JsonObject snapshot() { return JsonParser.parseString(snapshot).getAsJsonObject(); }
    public void replace(UUID epoch,int generation,JsonObject snapshot) {
        this.epoch=epoch;this.generation=generation;this.snapshot=snapshot.toString();
    }
}
