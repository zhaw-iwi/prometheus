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
    public record View(UUID requestId,StudyTurn.Status status,Long committedTurn,UUID inputEventId,
            UUID responseEventId,String responseType,String responsePayload,Instant receivedAt,Instant finishedAt,String reason) {}
    public record Entry(String input,View receipt) {}
    public record Conversation(long turnVersion,List<Entry> turns,String phase,JsonNode draft,JsonNode agreement,int actions) {}
    private record Claim(UUID attemptId,UUID agentId,boolean claimed,View view,boolean predicted) {}
    private final StudySessions sessions;
    private final StudyAttemptRepository attempts;
    private final StudyTurnRepository receipts;
    private final AgentApplicationService agents;
    private final AgentRepository agentRepository;
    private final TransactionTemplate transaction;
    private final ObjectMapper json;
    private final StudyRecorder recorder;
    private final StudyProcedure procedure;
    public StudyTurns(StudySessions sessions,StudyAttemptRepository attempts,StudyTurnRepository receipts,
            AgentApplicationService agents,AgentRepository agentRepository,PlatformTransactionManager transactions,ObjectMapper json,StudyRecorder recorder,StudyProcedure procedure) {
        this.sessions=sessions; this.attempts=attempts; this.receipts=receipts; this.agents=agents;
        this.agentRepository=agentRepository; transaction=new TransactionTemplate(transactions);
        this.json=json;
        this.recorder=recorder;
        this.procedure=procedure;
    }
    public View submit(String token,Request request) {
        if (request==null || request.requestId()==null || request.expectedTurn()<0 || request.text()==null
                || request.text().isBlank() || request.text().length()>2000)
            throw failure(HttpStatus.BAD_REQUEST,"A request ID, current turn and text of at most 2000 characters are required");
        Claim claim=transaction.execute(tx -> {
            var attempt=sessions.require(token,true);
            var existing=receipts.findByAttemptIdAndRequestId(attempt.id,request.requestId());
            if (existing.isPresent()) {
                var receipt=existing.get();
                if (!receipt.input.equals(request.text()) || receipt.expectedTurn!=request.expectedTurn())
                    throw failure(HttpStatus.CONFLICT,"Request ID already used for different content");
                reconcile(attempt,receipt);
                return new Claim(attempt.id,attempt.agentId,false,view(receipt),false);
            }
            open(attempt); interaction(attempt);
            if (attempt.pendingRequest!=null) throw failure(HttpStatus.CONFLICT,"Reconcile the pending request first");
            if (request.expectedTurn()!=attempt.turnVersion) throw failure(HttpStatus.CONFLICT,"Refresh the conversation before submitting");
            boolean predicted=procedure.permitsStart(attempt);
            if (!predicted && (ch.zhaw.prometheus.agentdefs.core.CoordinationAcceptance.explicit(request.text())
                    || ch.zhaw.prometheus.agentdefs.core.CoordinationAcceptance.assent(request.text())))
                throw failure(HttpStatus.CONFLICT,"Save your private prediction before starting");
            var receipt=receipts.saveAndFlush(new StudyTurn(attempt,request.requestId(),request.expectedTurn(),request.text(),Instant.now()));
            attempt.pendingRequest=request.requestId(); attempts.saveAndFlush(attempt);
            return new Claim(attempt.id,attempt.agentId,true,view(receipt),predicted);
        });
        if (!claim.claimed()) return claim.view();
        try {
            agents.acknowledge(claim.agentId(),new EventRequest(Event.TYPE_USER_UTTERANCE,Event.ACTOR_USER,
                    Event.KIND_OBSERVATION,request.text()),OutputProfile.FULL_PLAN,(candidate,position,response,persist) ->
                transaction.execute(tx -> {
                    var attempt=attempts.lockById(claim.attemptId()).orElseThrow();
                    var receipt=receipts.findByAttemptIdAndRequestId(attempt.id,request.requestId()).orElseThrow();
                    interaction(attempt);
                    if (attempt.closed() || !request.requestId().equals(attempt.pendingRequest)
                            || receipt.status!=StudyTurn.Status.PENDING || !Instant.now().isBefore(receipt.deadline))
                        throw new IllegalStateException("Study turn authority expired");
                    var candidateStorage=((TaskPolicy)candidate.getCurrentState().ownPolicy()).storage();
                    if (!claim.predicted() && candidateStorage.containsKey(CoordinationMemory.LOG))
                        for (var entry:candidateStorage.get(CoordinationMemory.LOG).getAsJsonArray()) {
                            var item=entry.getAsJsonObject();
                            if ("START".equals(item.get("operation").getAsString()) && item.has("sourcePosition")
                                    && item.get("sourcePosition").getAsLong()>=position) throw new IllegalStateException("Prediction required before acceptance");
                        }
                    var saved=persist.get(); agentRepository.flush();
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
            var agent=agentRepository.findById(attempt.agentId).orElseThrow();
            var storage=((TaskPolicy)agent.getCurrentState().ownPolicy()).storage();
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
                r.responsePayload,r.receivedAt,r.finishedAt,r.reason);
    }
}
