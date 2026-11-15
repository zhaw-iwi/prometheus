package ch.zhaw.prometheus.application.study;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import static ch.zhaw.prometheus.application.study.StudySessions.*;

@Service
public class StudyOperations {
    public record BatchRequest(UUID requestId,StudyAttempt.Mode mode,int count){}
    public record BatchResult(UUID requestId,boolean replay,List<Invitation> invitations){}
    public record Assistance(UUID id,String kind,String note){}
    public record Summary(View session,long agreements,long unresolved,long assistance){}
    public record Listing(int page,int size,long total,List<Summary> attempts){}
    private final StudySessions sessions;private final StudyAttemptRepository attempts;private final StudyBatchRepository batches;
    private final StudyRecordRepository records;private final StudyTurnRepository turns;private final StudyRecorder recorder;
    private final StudyEvidence evidence;private final ObjectMapper json;
    public StudyOperations(StudySessions sessions,StudyAttemptRepository attempts,StudyBatchRepository batches,StudyRecordRepository records,
            StudyTurnRepository turns,StudyRecorder recorder,StudyEvidence evidence,ObjectMapper json){
        this.sessions=sessions;this.attempts=attempts;this.batches=batches;this.records=records;this.turns=turns;this.recorder=recorder;this.evidence=evidence;this.json=json;
    }
    @Transactional
    public BatchResult provision(BatchRequest request){
        if(request==null || request.requestId()==null || request.mode()==null || request.count()<1 || request.count()>50)throw failure(HttpStatus.BAD_REQUEST,"Choose a mode and 1–50 invitations");
        batches.ensure(request.requestId());var batch=batches.lock(request.requestId()).orElseThrow();
        if(batch.mode!=null){
            if(batch.mode!=request.mode() || batch.requested!=request.count())throw failure(HttpStatus.CONFLICT,"Provisioning request ID reused");
            return new BatchResult(batch.id,true,Arrays.stream(batch.attemptIds.split(",")).map(id->new Invitation(sessions.inspect(UUID.fromString(id)),null)).toList());
        }
        var invitations=new ArrayList<Invitation>();for(int i=0;i<request.count();i++)invitations.add(sessions.invite(request.mode(),null,null));
        batch.mode=request.mode();batch.requested=request.count();batch.createdAt=Instant.now();
        batch.attemptIds=String.join(",",invitations.stream().map(i->i.session().id().toString()).toList());batches.saveAndFlush(batch);
        return new BatchResult(batch.id,false,List.copyOf(invitations));
    }
    @Transactional(readOnly=true)
    public Listing list(StudyAttempt.Mode mode,int page,int size){
        if(mode==null || page<0 || size<1 || size>100)throw failure(HttpStatus.BAD_REQUEST,"Choose a mode and valid page size");
        var found=attempts.findByModeOrderByCreatedAtDesc(mode,PageRequest.of(page,size));
        return new Listing(page,size,found.getTotalElements(),found.stream().map(a->new Summary(view(a),records.countByAttemptIdAndKind(a.id,"ACCEPTANCE"),
                turns.findByAttemptIdOrderByReceivedAt(a.id).stream().filter(t->Set.of(StudyTurn.Status.PENDING,StudyTurn.Status.UNCERTAIN).contains(t.status)).count(),records.countByAttemptIdAndKind(a.id,"ASSISTANCE"))).toList());
    }
    @Transactional
    public void assist(UUID attemptId,Assistance note){
        var attempt=attempts.lockById(attemptId).orElseThrow(()->failure(HttpStatus.NOT_FOUND,"Unknown attempt"));
        if(note==null || note.id()==null || !Set.of("TECHNICAL","PROCEDURAL","CONTENT").contains(Objects.toString(note.kind(),""))
                || note.note()==null || note.note().isBlank() || note.note().length()>1000)throw failure(HttpStatus.BAD_REQUEST,"Use an assistance category and a brief note");
        String key="assistance:"+note.id();JsonNode payload=json.valueToTree(note);var existing=records.findByAttemptIdAndRecordKey(attemptId,key);
        if(existing.isPresent()){if(!recorder.parse(existing.get().payload).equals(payload))throw failure(HttpStatus.CONFLICT,"Assistance ID reused");return;}
        recorder.append(attempt.id,key,"ASSISTANCE",payload);
    }
    @Transactional
    public ObjectNode dataset(StudyAttempt.Mode mode){
        if(mode==null)throw failure(HttpStatus.BAD_REQUEST,"Choose a dataset mode");
        var selected=attempts.findByModeOrderByCreatedAt(mode);if(selected.size()>1000)throw failure(HttpStatus.CONFLICT,"Export at most 1000 attempts per dataset");
        var result=json.createObjectNode();result.put("schema","hri-dataset-v1");result.put("mode",mode.name());
        result.put("exportedAt",Instant.now().toString());var data=result.putArray("attempts");var participants=new HashSet<UUID>();var stages=new TreeMap<String,Integer>();var scenarios=new TreeMap<String,Integer>();
        for(var attempt:selected){data.add(evidence.export(attempt.id));participants.add(attempt.participantId==null?attempt.id:attempt.participantId);
            stages.merge(attempt.stage.name(),1,Integer::sum);if(attempt.predecessorId==null)scenarios.merge(attempt.scenario,1,Integer::sum);}
        var counts=result.putObject("counts");counts.put("attempts",selected.size());counts.put("participants",participants.size());
        counts.set("attemptStages",json.valueToTree(stages));counts.set("initialAssignments",json.valueToTree(scenarios));
        result.put("interpretation","All attempts retained. Replacement attempts share participant identity. No automatic fidelity or comprehension judgment.");return result;
    }
    public static String csv(JsonNode dataset){
        var rows=new StringBuilder("participant_id,attempt_id,predecessor_id,mode,scenario,materials_version,stage,finish_reason,accepted_agreements,recording_complete,procedure_complete,umux_lite_raw_0_100,umux_lite_adjusted_2013,item,form,value\r\n");
        for(var attempt:dataset.path("attempts")){
            var session=attempt.path("session");var assessment=attempt.path("assessment");var prefix=new ArrayList<String>();
            for(String key:List.of("participantId","id","predecessorId","mode","scenario","materialsVersion","stage","finishReason"))prefix.add(cell(session.path(key)));
            prefix.add(cell(assessment.path("acceptedAgreements")));prefix.add(cell(attempt.path("manifest").path("recordingComplete")));prefix.add(cell(attempt.path("manifest").path("procedureComplete")));
            prefix.add(cell(assessment.path("umuxLite").path("raw0to100")));prefix.add(cell(assessment.path("umuxLite").path("regressionAdjusted2013")));
            if(attempt.path("answers").isEmpty())rows.append(String.join(",",prefix)).append(",,,\r\n");
            for(var answer:attempt.path("answers"))rows.append(String.join(",",prefix)).append(',').append(cell(answer.path("item"))).append(',').append(cell(answer.path("form"))).append(',').append(cell(answer.path("value"))).append("\r\n");
        }return rows.toString();
    }
    private static String cell(JsonNode value){
        String text=value.isMissingNode()||value.isNull()?"":value.asText();
        // JSON retains original text; this spreadsheet-safe derivative never executes formulas.
        if(text.matches("(?s)^[\\s]*[=+@-].*") || text.startsWith("\t") || text.startsWith("\r") || text.startsWith("\n"))text="'"+text;
        return "\""+text.replace("\"","\"\"")+"\"";
    }
}
