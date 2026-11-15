package ch.zhaw.prometheus.application.study;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.repositories.AgentRepository;
import static ch.zhaw.prometheus.application.study.StudySessions.*;

@Service
public class StudyEvidence {
    public record Display(UUID eventId,String clientTime,String disposition) {
        public Display(UUID eventId,String clientTime){this(eventId,clientTime,null);}
    }
    public record ClientEvent(UUID id,String type,String clientTime,String code) {}
    private final StudySessions sessions;
    private final StudyAttemptRepository attempts;
    private final StudyTurnRepository turns;
    private final StudyFormRepository forms;
    private final StudyRecordRepository records;
    private final StudyRecorder recorder;
    private final AgentRepository agents;
    private final ObjectMapper json;
    public StudyEvidence(StudySessions sessions,StudyAttemptRepository attempts,StudyTurnRepository turns,
            StudyFormRepository forms,StudyRecordRepository records,StudyRecorder recorder,AgentRepository agents,ObjectMapper json) {
        this.sessions=sessions; this.attempts=attempts; this.turns=turns; this.forms=forms;
        this.records=records; this.recorder=recorder; this.agents=agents; this.json=json;
    }
    @Transactional
    public void display(String token,Display display) {
        var attempt=sessions.require(token,true); open(attempt);
        if (display==null || display.eventId()==null) throw failure(HttpStatus.BAD_REQUEST,"Event ID required");
        clientTime(display.clientTime()); String key="display:"+display.eventId();
        if (records.findByAttemptIdAndRecordKey(attempt.id,key).isPresent()) return;
        var evidence=records.findByAttemptIdOrderByRecordedAt(attempt.id);
        var events=events(evidence);
        var event=events.get(display.eventId().toString());
        if (event==null || !Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.path("type").asText()))
            throw failure(HttpStatus.NOT_FOUND,"Unknown generated study response");
        boolean withheld=withheld(attempt,display.eventId().toString(),turns.findByAttemptIdOrderByReceivedAt(attempt.id),evidence);
        String disposition=display.disposition()==null?"DISPLAY":display.disposition();
        if(!Set.of("DISPLAY","WITHHELD").contains(disposition) || disposition.equals("WITHHELD")&&!withheld)
            throw failure(HttpStatus.BAD_REQUEST,"Invalid rendering disposition");
        ObjectNode payload=json.valueToTree(display);payload.put("serverSceneVersion",attempt.sceneVersion);
        recorder.append(attempt.id,key,disposition.equals("WITHHELD")?"WITHHELD":withheld?"LATE_DISPLAY":"DISPLAY",payload);
    }
    @Transactional
    public void client(String token,ClientEvent event) {
        var attempt=sessions.require(token,true); open(attempt);
        if (event==null || event.id()==null || event.type()==null || !Set.of("DISCONNECT","RECONNECT","HELP","ERROR","STAGE_VIEW").contains(event.type())
                || event.code()==null || !event.code().matches("[a-zA-Z0-9_.-]{0,80}"))
            throw failure(HttpStatus.BAD_REQUEST,"Invalid client evidence");
        clientTime(event.clientTime()); String key="client:"+event.id();
        JsonNode payload=json.valueToTree(event);
        var existing=records.findByAttemptIdAndRecordKey(attempt.id,key);
        if (existing.isPresent()) {
            if (!recorder.parse(existing.get().payload).equals(payload)) throw failure(HttpStatus.CONFLICT,"Client event ID reused");
            return;
        }
        recorder.append(attempt.id,key,"CLIENT",payload);
    }
    @Transactional(readOnly=true)
    public List<JsonNode> participantEvents(String token) {
        var attempt=sessions.require(token,false);
        var evidence=records.findByAttemptIdOrderByRecordedAt(attempt.id);var receipts=turns.findByAttemptIdOrderByReceivedAt(attempt.id);
        return events(evidence).values().stream().sorted(Comparator.comparingLong(e -> e.path("position").asLong())).map(event->{
            if(!withheld(attempt,event.path("id").asText(),receipts,evidence))return event;
            ObjectNode projected=event.deepCopy();projected.put("withheld",true);
            projected.put("payload","{\"speech\":\"A response was withheld because the simulated scene changed before confirmed display.\"}");
            return (JsonNode)projected;
        }).toList();
    }
    private boolean withheld(StudyAttempt attempt,String event,List<StudyTurn> receipts,List<StudyRecord> evidence) {
        boolean sensitive=receipts.stream().anyMatch(r->r.responseEventId!=null && r.responseEventId.toString().equals(event)
                && r.sceneSensitive && r.sceneVersion<attempt.sceneVersion);
        return sensitive && evidence.stream().noneMatch(r->r.kind.equals("DISPLAY") && r.recordKey.equals("display:"+event));
    }
    /** Authorized analysis includes private forms; callers must enforce the operator boundary. */
    @Transactional
    public ObjectNode export(UUID id) {
        var attempt=attempts.lockById(id).orElseThrow(() -> failure(HttpStatus.NOT_FOUND,"Unknown attempt"));
        var evidence=records.findByAttemptIdOrderByRecordedAt(id); var receipts=turns.findByAttemptIdOrderByReceivedAt(id);
        var privateForms=forms.findByAttemptIdOrderByCreatedAt(id);
        var result=json.createObjectNode(); result.put("schema",StudyRecorder.SCHEMA); result.put("exportedAt",Instant.now().toString());
        result.set("session",json.valueToTree(view(attempt)));
        var manifest=result.putObject("manifest"); manifest.put("materialsVersion",attempt.materialsVersion);
        var list=result.putArray("records"); var snapshots=result.putArray("agreements");
        var issues=manifest.putArray("issues"); var recordedRequests=new HashSet<String>();
        var acceptedRequests=new HashSet<String>(); var expectedAcceptances=new HashSet<String>();
        var displayed=new HashSet<String>(); var withheldDisplays=new HashSet<String>();var lateDisplays=new HashSet<String>();var recordedForms=new HashSet<String>();
        boolean metadata=false, configurationComplete=false;
        for (var record:evidence) {
            var data=recorder.parse(record.payload); var row=list.addObject(); row.put("id",record.id.toString());
            row.put("kind",record.kind); row.put("serverRecordedAt",record.recordedAt.toString()); row.set("data",data);
            row.put("key",record.recordKey);
            if (record.recordKey.startsWith("procedure:form:")) recordedForms.add(record.recordKey.substring("procedure:form:".length()));
            switch(record.kind) {
                case "METADATA" -> {
                    metadata=true;
                    if ("UNKNOWN".equals(data.path("sourceRevision").asText())) issues.add("source_revision_unknown");
                    boolean configured=true;
                    for (var route:data.path("configuredRoutes")) if (route.path("unconfigured").asBoolean()) configured=false;
                    configurationComplete=configured && !"UNKNOWN".equals(data.path("sourceRevision").asText());
                    if (!configured) issues.add("inference_configuration_incomplete");
                }
                case "TURN" -> {
                    recordedRequests.add(data.path("requestId").asText());
                    for (var entry:data.path("journal")) if ("START".equals(entry.path("operation").asText()))
                        expectedAcceptances.add(data.path("requestId").asText());
                }
                case "ACCEPTANCE" -> { snapshots.add(data); acceptedRequests.add(data.path("requestId").asText()); }
                case "DISPLAY" -> displayed.add(data.path("eventId").asText());
                case "WITHHELD" -> withheldDisplays.add(data.path("eventId").asText());
                case "LATE_DISPLAY" -> lateDisplays.add(data.path("eventId").asText());
                default -> { }
            }
        }
        var requestList=result.putArray("requests"); var committed=new HashSet<String>(); int unresolved=0;
        for (var receipt:receipts) {
            var row=requestList.addObject(); row.put("input",receipt.input); row.set("receipt",json.valueToTree(StudyTurns.view(receipt)));
            if (receipt.status==StudyTurn.Status.COMMITTED) committed.add(receipt.requestId.toString());
            if (Set.of(StudyTurn.Status.PENDING,StudyTurn.Status.UNCERTAIN).contains(receipt.status)) unresolved++;
        }
        var recordedEvents=events(evidence); var actualEvents=new HashSet<String>();
        var canonicalAgent=attempt.agentId==null?Optional.<ch.zhaw.prometheus.model.Agent>empty():agents.findById(attempt.agentId);
        boolean agentMissing=attempt.agentId!=null && canonicalAgent.isEmpty();
        canonicalAgent.ifPresent(agent->agent.getEventHistory().toList().forEach(e->actualEvents.add(e.getId().toString())));
        var formKinds=new HashSet<>(privateForms.stream().map(f -> f.kind).toList());
        boolean formsComplete=recordedForms.equals(formKinds) && (attempt.agentId==null || formKinds.containsAll(Set.of("pre","intention","private")))
                && (attempt.stage!=StudyAttempt.Stage.COMPLETE || formKinds.containsAll(Set.of("understanding","post")));
        boolean assessmentReference=attempt.finishReason==null || evidence.stream().anyMatch(r->r.kind.equals("ASSESSMENT_REFERENCE"));
        boolean complete=metadata && !agentMissing && assessmentReference && committed.equals(recordedRequests) && expectedAcceptances.equals(acceptedRequests)
                && actualEvents.equals(recordedEvents.keySet()) && formsComplete;
        if (!metadata) issues.add("metadata_missing");
        if (agentMissing) issues.add("canonical_agent_missing");
        if (!assessmentReference) issues.add("assessment_reference_missing");
        if (!committed.equals(recordedRequests)) issues.add("turn_record_mismatch");
        if (!expectedAcceptances.equals(acceptedRequests)) issues.add("acceptance_snapshot_mismatch");
        if (!actualEvents.equals(recordedEvents.keySet())) issues.add("canonical_event_mismatch");
        if (!formsComplete) issues.add("private_form_record_mismatch");
        if (unresolved>0) issues.add("unresolved_requests");
        long generated=recordedEvents.values().stream().filter(e -> Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(e.path("type").asText())).count();
        manifest.put("recordingComplete",complete); manifest.put("procedureComplete",attempt.stage==StudyAttempt.Stage.COMPLETE);
        manifest.put("configurationComplete",configurationComplete);
        manifest.put("displayComplete",displayed.size()==generated); manifest.put("generatedResponses",generated);
        manifest.put("displayReceipts",displayed.size()); manifest.put("unresolvedRequests",unresolved);
        manifest.put("withheldRenderReports",withheldDisplays.size());manifest.put("lateDisplayReports",lateDisplays.size());
        manifest.put("deliveryAccounted",displayed.size()+withheldDisplays.size()+lateDisplays.size()==generated);
        manifest.put("records",evidence.size()); manifest.put("turns",receipts.size()); manifest.put("agreements",snapshots.size());
        if (displayed.size()!=generated) issues.add("missing_display_evidence");
        var answers=result.putArray("answers");
        for (var form:privateForms) {
            var values=recorder.parse(form.answers); values.fields().forEachRemaining(entry -> {
                var answer=answers.addObject(); answer.put("form",form.kind); answer.put("item",entry.getKey());
                answer.set("value",entry.getValue()); answer.put("materialsVersion",form.materialsVersion);
                answer.put("serverRecordedAt",form.createdAt.toString());
            });
        }
        manifest.put("answerRows",answers.size());result.set("assessment",StudyAssessment.score(result)); return result;
    }
    private Map<String,JsonNode> events(List<StudyRecord> evidence) {
        var result=new LinkedHashMap<String,JsonNode>();
        for (var record:evidence) if (Set.of("PROVISION","TURN").contains(record.kind))
            for (var event:recorder.parse(record.payload).path("events")) result.put(event.path("id").asText(),event);
        return result;
    }
    private static void clientTime(String value) {
        try { if (value==null || value.length()>40) throw new IllegalArgumentException(); Instant.parse(value); }
        catch (RuntimeException invalid) { throw failure(HttpStatus.BAD_REQUEST,"Use an ISO UTC client timestamp"); }
    }
}
