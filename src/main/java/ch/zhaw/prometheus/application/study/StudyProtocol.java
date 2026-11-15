package ch.zhaw.prometheus.application.study;

import org.springframework.stereotype.Component;
import com.google.gson.JsonParser;

/** Resume against the exact material snapshot issued to this attempt, not a mutable current file. */
@Component
public class StudyProtocol {
    private final StudyRecordRepository records;
    public StudyProtocol(StudyRecordRepository records){this.records=records;}
    public StudyMaterials.Manifest materials(StudyAttempt attempt){
        var record=records.findByAttemptIdAndRecordKey(attempt.id,"metadata").orElseThrow(()->new IllegalStateException("Missing frozen study materials"));
        var snapshot=StudyMaterials.parse(JsonParser.parseString(record.payload).getAsJsonObject().get("materials").toString());
        if(!attempt.materialsVersion.equals(snapshot.version()))throw new IllegalStateException("Study material version mismatch");
        return snapshot;
    }
    public StudyMaterials.Scenario scenario(StudyAttempt attempt){return materials(attempt).scenarios().stream().filter(s->s.id().equals(attempt.scenario)).findFirst().orElseThrow();}
}
