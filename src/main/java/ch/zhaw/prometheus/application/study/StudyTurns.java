package ch.zhaw.prometheus.application.study;

import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ch.zhaw.prometheus.application.AgentApplicationService;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.task.TaskPolicy;
import ch.zhaw.prometheus.model.task.TaskMemory;
import com.fasterxml.jackson.databind.*;
import static ch.zhaw.prometheus.application.study.StudySessions.*;

/** Durable ingress. Pending provider work owns no transaction and can commit only through its receipt. */
@Service
public class StudyTurns {
    public record Request(UUID requestId,long expectedTurn,String text) {}
    public record SceneRequest(UUID requestId,long expectedScene,Integer humanCount,String observedAt,Integer probeStep) {
        public SceneRequest(UUID requestId,long expectedScene,Integer humanCount,String observedAt){this(requestId,expectedScene,humanCount,observedAt,null);}
    }
    public record SignRequest(UUID requestId,long expectedTurn,UUID observationId,String sign,String mode,
            String revision,UUID run,UUID question,String observedAt) {}
    public record View(UUID requestId,StudyTurn.Status status,Long committedTurn,UUID inputEventId,
            UUID responseEventId,String responseType,String responsePayload,Instant receivedAt,Instant finishedAt,String reason,
            String inputType,long expectedTurn,long sceneVersion,boolean sceneSensitive) {}
    public record Entry(String input,View receipt) {}
    public record Conversation(long turnVersion,List<Entry> turns,String phase,JsonNode draft,JsonNode agreement,int actions) {}
    private record Claim(UUID attemptId,UUID agentId,boolean claimed,View view,boolean predicted,long sceneVersion) {}
    private final StudySessions sessions;
    private final StudyAttemptRepository attempts;
    private final StudyTurnRepository receipts;
    private final AgentApplicationService agents;
    private final AgentRepository agentRepository;
    private final TransactionTemplate transaction;
    private final ObjectMapper json;
    private final StudyRecorder recorder;
    private final StudyProcedure procedure;
    private final StudyAdmission admission;
    private final StudyLimits limits;
    public StudyTurns(StudySessions sessions,StudyAttemptRepository attempts,StudyTurnRepository receipts,
            AgentApplicationService agents,AgentRepository agentRepository,PlatformTransactionManager transactions,ObjectMapper json,StudyRecorder recorder,StudyProcedure procedure,StudyAdmission admission,StudyLimits limits) {
        this.sessions=sessions; this.attempts=attempts; this.receipts=receipts; this.agents=agents;
        this.agentRepository=agentRepository; transaction=new TransactionTemplate(transactions);
        this.json=json;
        this.recorder=recorder;
        this.procedure=procedure;
        this.admission=admission;
        this.limits=limits;
    }
    public View submit(String token,Request request) {
        return submit(token,request,Event.TYPE_USER_UTTERANCE);
    }
    public View scene(String token,SceneRequest request) {
        if(request==null || request.requestId()==null || request.expectedScene()<0 || request.expectedScene()>1_000_000
                || request.humanCount()!=null && (request.humanCount()<0 || request.humanCount()>3) || request.observedAt()==null || request.observedAt().length()>40)
            throw failure(HttpStatus.BAD_REQUEST,"Invalid simulated scene");
        var payload=new com.google.gson.JsonObject();payload.addProperty("simulation",true);payload.addProperty("source","controlled_study");
        payload.addProperty("sequence",request.expectedScene()+1);payload.addProperty("humanCount",request.humanCount());payload.addProperty("ts",request.observedAt());
        if(request.probeStep()!=null)payload.addProperty("probeStep",request.probeStep());
        return submit(token,new Request(request.requestId(),request.expectedScene(),payload.toString()),Event.TYPE_HUMAN_PRESENCE);
    }
    public View sign(String token,SignRequest request) {
        if(request==null || request.observationId()==null || request.sign()==null || request.sign().length()>24
                || request.mode()==null || !Set.of("DEMONSTRATE","ANSWER").contains(request.mode()) || request.revision()==null
                || !request.revision().matches("[0-9]{1,9}") || request.observedAt()==null || request.observedAt().length()>40)
            throw failure(HttpStatus.BAD_REQUEST,"Invalid simulated sign");
        var payload=new com.google.gson.JsonObject();payload.addProperty("simulation",true);payload.addProperty("source","controlled_study");
        payload.addProperty("observationId",request.observationId().toString());payload.addProperty("sign",request.sign());payload.addProperty("mode",request.mode());
        payload.addProperty("revision",request.revision());payload.addProperty("run",request.run()==null?null:request.run().toString());
        payload.addProperty("question",request.question()==null?null:request.question().toString());payload.addProperty("ts",request.observedAt());
        return submit(token,new Request(request.requestId(),request.expectedTurn(),payload.toString()),ch.zhaw.prometheus.agentdefs.study.StudySigns.EVENT);
    }
    private View submit(String token,Request request,String type) {
        boolean scene=Event.TYPE_HUMAN_PRESENCE.equals(type);
        boolean sign=ch.zhaw.prometheus.agentdefs.study.StudySigns.EVENT.equals(type);
        if (request==null || request.requestId()==null || request.expectedTurn()<0 || request.text()==null
                || request.text().isBlank() || request.text().length()>2000)
            throw failure(HttpStatus.BAD_REQUEST,"A request ID, current turn and text of at most 2000 characters are required");
        Claim claim=transaction.execute(tx -> {
            var attempt=sessions.require(token,true);
            var existing=receipts.findByAttemptIdAndRequestId(attempt.id,request.requestId());
            if (existing.isPresent()) {
                var receipt=existing.get();
                if (!receipt.input.equals(request.text()) || receipt.expectedTurn!=request.expectedTurn()
                        || !type.equals(receipt.inputType==null?Event.TYPE_USER_UTTERANCE:receipt.inputType))
                    throw failure(HttpStatus.CONFLICT,"Request ID already used for different content");
                reconcile(attempt,receipt);
                return new Claim(attempt.id,attempt.agentId,false,view(receipt),false,attempt.sceneVersion);
            }
            open(attempt); interaction(attempt);
            limits.check(attempt);
            admission.acquire(attempt.id);
            if(scene) {
                if(!"context".equals(attempt.scenario) || request.expectedTurn()!=attempt.sceneVersion) throw failure(HttpStatus.CONFLICT,"Refresh the simulated scene first");
                var payload=com.google.gson.JsonParser.parseString(request.text()).getAsJsonObject();
                if(!ch.zhaw.prometheus.model.event.ObservationEvidence.fresh(ch.zhaw.prometheus.model.event.ObservationEvidence.observed(payload),Instant.now(),15,2))
                    throw failure(HttpStatus.BAD_REQUEST,"The simulated scene command is no longer current");
                procedure.validateSceneProbe(attempt,payload);
                // Claim the changed environment before waiting on the agent's serial
                // turn lock. The old inference can no longer commit or publish.
                if(attempt.pendingRequest!=null) {
                    var old=receipts.findByAttemptIdAndRequestId(attempt.id,attempt.pendingRequest).orElseThrow();
                    old.status=StudyTurn.Status.REJECTED;old.reason="scene_changed";old.finishedAt=StudyTurn.now();
                    receipts.saveAndFlush(old);attempt.pendingRequest=null;attempt.turnVersion++;
                }
                attempt.sceneVersion++;attempt.scenePayload=request.text();
                recorder.append(attempt.id,"scene:"+request.requestId(),"SCENE_REQUEST",json.valueToTree(request));
            }
            if (attempt.pendingRequest!=null) throw failure(HttpStatus.CONFLICT,"Reconcile the pending request first");
            if (!scene && request.expectedTurn()!=attempt.turnVersion) throw failure(HttpStatus.CONFLICT,"Refresh the conversation before submitting");
            if(sign)procedure.validateSign(attempt,com.google.gson.JsonParser.parseString(request.text()).getAsJsonObject());
            if(!scene && !procedure.sceneApplied(attempt))throw failure(HttpStatus.CONFLICT,"Apply a current simulated scene before continuing");
            boolean predicted=scene || procedure.permitsStart(attempt);
            if (!predicted && (ch.zhaw.prometheus.agentdefs.core.CoordinationAcceptance.explicit(request.text())
                    || ch.zhaw.prometheus.agentdefs.core.CoordinationAcceptance.assent(request.text())))
                throw failure(HttpStatus.CONFLICT,"Save your private prediction before starting");
            var receipt=receipts.saveAndFlush(new StudyTurn(attempt,request.requestId(),request.expectedTurn(),request.text(),Instant.now()));
            receipt.inputType=type;receipt.sceneVersion=attempt.sceneVersion;receipts.saveAndFlush(receipt);
            attempt.pendingRequest=request.requestId(); attempts.saveAndFlush(attempt);
            return new Claim(attempt.id,attempt.agentId,true,view(receipt),predicted,attempt.sceneVersion);
        });
        if (!claim.claimed()) return claim.view();
        try {
            agents.acknowledge(claim.agentId(),new EventRequest(type,scene||sign?"study-simulation":Event.ACTOR_USER,
                    Event.KIND_OBSERVATION,request.text()),OutputProfile.FULL_PLAN,(candidate,position,response,persist) ->
                transaction.execute(tx -> {
                    var attempt=attempts.lockById(claim.attemptId()).orElseThrow();
                    var receipt=receipts.findByAttemptIdAndRequestId(attempt.id,request.requestId()).orElseThrow();
                    interaction(attempt);
                    if (attempt.closed() || attempt.sceneVersion!=claim.sceneVersion() || !request.requestId().equals(attempt.pendingRequest)
                            || receipt.status!=StudyTurn.Status.PENDING || !Instant.now().isBefore(receipt.deadline))
                        throw new IllegalStateException("Study turn authority expired");
                    var candidateStorage=((TaskPolicy)candidate.getCurrentState().ownPolicy()).storage();
                    receipt.sceneSensitive=ch.zhaw.prometheus.agentdefs.study.StudyContext.task(CoordinationMemory.agreement(candidateStorage,CoordinationMemory.AGREEMENT));
                    if (!claim.predicted() && candidateStorage.containsKey(CoordinationMemory.LOG))
                        for (var entry:candidateStorage.get(CoordinationMemory.LOG).getAsJsonArray()) {
                            var item=entry.getAsJsonObject();
                            if ("START".equals(item.get("operation").getAsString()) && item.has("sourcePosition")
                                    && item.get("sourcePosition").getAsLong()>=position) throw new IllegalStateException("Prediction required before acceptance");
                        }
                    var saved=persist.get(); agentRepository.flush();
                    if(scene) {
                        var payload=com.google.gson.JsonParser.parseString(request.text()).getAsJsonObject();
                        if(payload.has("probeStep"))attempt.sceneProbeStep=payload.get("probeStep").getAsInt()+1;
                    }
                    var events=saved.getEventHistory().toList();
                    receipt.inputEventId=events.stream().filter(e -> Long.valueOf(position).equals(e.sourcePosition()))
                            .findFirst().orElseThrow().getId();
                    if (response!=null) {
                        var canonical=events.stream().filter(e -> e.sourcePosition()!=null && e.sourcePosition()>position
                                && Objects.equals(response.getType(),e.getType()) && Objects.equals(response.getPayload(),e.getPayload()))
                                .reduce((a,b)->b).orElseThrow();
                        receipt.responseEventId=canonical.getId(); receipt.responseType=canonical.getType();
                        receipt.responsePayload=canonical.getPayload();
                    }
                    receipt.status=StudyTurn.Status.COMMITTED; receipt.finishedAt=StudyTurn.now();
                    receipt.committedTurn=++attempt.turnVersion; attempt.pendingRequest=null; attempt.updatedAt=Instant.now();
                    recorder.turn(attempt,receipt,saved,position);
                    receipts.saveAndFlush(receipt); attempts.saveAndFlush(attempt);
                    return saved;
                })).orElseThrow(() -> new IllegalStateException("Study agent unavailable"));
        } catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(StudyTurns.class).warn("Study turn interrupted; requestId={}, category={}",
                    request.requestId(),failure.getClass().getSimpleName());
            // Atomic commit may already have succeeded even when a later transport/publication fails.
            // Read durable truth; never retry inference here.
            transaction.executeWithoutResult(tx -> {
                var attempt=attempts.lockById(claim.attemptId()).orElseThrow();
                var receipt=receipts.findByAttemptIdAndRequestId(attempt.id,request.requestId()).orElseThrow();
                if (receipt.status==StudyTurn.Status.PENDING) {
                    receipt.status=attempt.closed()?StudyTurn.Status.REJECTED:StudyTurn.Status.UNCERTAIN;
                    receipt.reason=attempt.closed()?"attempt_closed":"execution_interrupted";
                    receipt.finishedAt=StudyTurn.now(); receipts.saveAndFlush(receipt);
                }
            });
        }
        return lookup(token,request.requestId());
    }
    public Conversation conversation(String token) {
        return transaction.execute(tx -> {
            var attempt=sessions.require(token,true);
            var entries=receipts.findByAttemptIdOrderByReceivedAt(attempt.id).stream()
                    .map(r -> new Entry(r.input,view(r))).toList();
            if (attempt.agentId==null) return new Conversation(attempt.turnVersion,entries,null,null,null,0);
            var storage=agentRepository.findStorageById(attempt.agentId).orElseThrow();
            try {
                JsonNode draft=storage.containsKey(CoordinationMemory.DRAFT)?json.readTree(storage.get(CoordinationMemory.DRAFT).toString()):null;
                JsonNode agreement=storage.containsKey(CoordinationMemory.AGREEMENT)?json.readTree(storage.get(CoordinationMemory.AGREEMENT).toString()):null;
                return new Conversation(attempt.turnVersion,entries,CoordinationMemory.stage(storage),draft,agreement,
                        Integer.parseInt(TaskMemory.text(storage,TaskMemory.ACTIONS,"0")));
            } catch (java.io.IOException invalid) { throw new IllegalStateException("Invalid persisted study agreement",invalid); }
        });
    }
    public View lookup(String token,UUID requestId) {
        return transaction.execute(tx -> {
            var attempt=sessions.require(token,true);
            var receipt=receipts.findByAttemptIdAndRequestId(attempt.id,requestId)
                    .orElseThrow(() -> failure(HttpStatus.NOT_FOUND,"Unknown study request"));
            reconcile(attempt,receipt); return view(receipt);
        });
    }
    /** Explicit participant reconciliation closes the old authority; it never replays its input. */
    public View cancel(String token,UUID requestId) {
        return transaction.execute(tx -> {
            var attempt=sessions.require(token,true); open(attempt);
            var existing=receipts.findByAttemptIdAndRequestId(attempt.id,requestId);
            if (existing.isEmpty()) {
                interaction(attempt);
                // A delayed original POST must not execute after the client discards
                // a request whose receipt has not reached this server yet.
                var cancelled=new StudyTurn(attempt,requestId,attempt.turnVersion,"",Instant.now());
                cancelled.status=StudyTurn.Status.REJECTED; cancelled.reason="cancelled_before_receipt"; cancelled.finishedAt=StudyTurn.now();
                receipts.saveAndFlush(cancelled);
                if (attempt.pendingRequest==null) { attempt.turnVersion++; attempts.saveAndFlush(attempt); }
                recorder.procedure(attempt,"cancel:"+requestId); return view(cancelled);
            }
            var receipt=existing.get();
            if (receipt.status==StudyTurn.Status.COMMITTED) return view(receipt);
            if (requestId.equals(attempt.pendingRequest)) {
                receipt.status=StudyTurn.Status.REJECTED; receipt.reason="participant_cancelled";
                receipt.finishedAt=StudyTurn.now(); attempt.pendingRequest=null; attempt.turnVersion++;
                attempts.saveAndFlush(attempt); receipts.saveAndFlush(receipt);
                recorder.procedure(attempt,"cancel:"+requestId);
            }
            return view(receipt);
        });
    }
    private void reconcile(StudyAttempt attempt,StudyTurn receipt) {
        if (receipt.status==StudyTurn.Status.PENDING && (attempt.closed() || !Instant.now().isBefore(receipt.deadline))) {
            receipt.status=attempt.closed()?StudyTurn.Status.REJECTED:StudyTurn.Status.UNCERTAIN;
            receipt.reason=attempt.closed()?"attempt_closed":"request_deadline_elapsed";
            receipt.finishedAt=StudyTurn.now(); receipts.saveAndFlush(receipt);
        }
    }
    private static void interaction(StudyAttempt attempt) {
        if (attempt.agentId==null || !Set.of(StudyAttempt.Stage.COORDINATION,StudyAttempt.Stage.REVISION,StudyAttempt.Stage.TRIAL).contains(attempt.stage))
            throw failure(HttpStatus.CONFLICT,"Complete the study preparation first");
    }
    static View view(StudyTurn r) {
        return new View(r.requestId,r.status,r.committedTurn,r.inputEventId,r.responseEventId,r.responseType,
                r.responsePayload,r.receivedAt,r.finishedAt,r.reason,r.inputType==null?Event.TYPE_USER_UTTERANCE:r.inputType,r.expectedTurn,r.sceneVersion,r.sceneSensitive);
    }
}
