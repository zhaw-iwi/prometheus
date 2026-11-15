package ch.zhaw.prometheus.application.study;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.study.StudyCoordinationAgent;
import ch.zhaw.prometheus.application.AgentApplicationService;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import static ch.zhaw.prometheus.application.study.StudyAttempt.*;

@Service
public class StudySessions {
    public static final String SYNTHETIC_CONSENT = "synthetic-test-v1";
    public static final String SYNTHETIC_INFORMATION="This is a synthetic software test, not research participation. Use invented information only. No microphone or camera is used. Private preparation answers are stored for testing and are not sent to the assistant.";
    public record View(UUID id, long version, Mode mode, String scenario, String materialsVersion,
            Stage stage, String consentVersion, Instant consentedAt, Instant createdAt, Instant updatedAt,
            UUID predecessorId, long turnVersion, UUID pendingRequest, String finishReason,UUID participantId,String allocationKind,Long allocationOrdinal,Instant interactionStartedAt) {}
    public record Invitation(View session, String token) {
        @Override public String toString() { return "StudyInvitation[session="+session.id()+", credential=REDACTED]"; }
    }
    public record Page(View session,String title,String task,List<StudyMaterials.Item> fields,String information,
            String informationVersion,String formKind,String debrief) {}
    private final StudyAttemptRepository attempts;
    private final StudyFormRepository forms;
    private final StudyMaterials materials;
    private final StudyCoordinationAgent definition;
    private final AgentApplicationService agents;
    private final AgentCreationContext creation;
    private final boolean collectionEnabled;
    private final String informationVersion;
    private final String informationText;
    private final StudyProtocol protocol;
    private final StudyAllocator allocator;
    private final Gson json = new Gson();
    private final StudyRecorder recorder;
    private final StudyTurnRepository receipts;
    private final StudyAgentRegistry ownership;
    private final StudyAdmission admission;
    public StudySessions(StudyAttemptRepository attempts, StudyFormRepository forms, StudyMaterials materials,
            StudyCoordinationAgent definition, AgentApplicationService agents, PromptMessageAssembler prompts,
            LanguageModelGateway gateway, @Value("${prometheus.study.collection-enabled:false}") boolean enabled,
            @Value("${prometheus.study.information-version:}") String informationVersion,
            @Value("${prometheus.study.information-text:}") String informationText,StudyProtocol protocol,StudyAllocator allocator,StudyRecorder recorder,StudyTurnRepository receipts,StudyAgentRegistry ownership,StudyAdmission admission) {
        this.attempts=attempts; this.forms=forms; this.materials=materials; this.definition=definition;
        this.agents=agents; creation=new AgentCreationContext(prompts,gateway);
        collectionEnabled=enabled; this.informationVersion=informationVersion;
        this.informationText=informationText;this.protocol=protocol;
        this.allocator=allocator;
        this.recorder=recorder;
        this.receipts=receipts;
        this.ownership=ownership;
        this.admission=admission;
    }
    @Transactional
    public Invitation invite(Mode mode, String scenario, UUID predecessor) {
        return invite(mode,scenario,predecessor,false);
    }
    @Transactional
    public Invitation invite(Mode mode, String scenario, UUID predecessor,boolean foundation) {
        if (mode==null) throw failure(HttpStatus.BAD_REQUEST,"Attempt mode is required");
        if(foundation && mode!=Mode.SYNTHETIC)throw failure(HttpStatus.BAD_REQUEST,"Foundation fixtures are synthetic only");
        StudyAllocator.Assignment allocation=null;
        if(predecessor!=null && scenario==null)scenario=attempts.findById(predecessor).orElseThrow(()->failure(HttpStatus.NOT_FOUND,"Unknown attempt")).scenario;
        if(scenario==null){allocation=allocator.next(mode);scenario=allocation.scenario();}
        else if(mode!=Mode.SYNTHETIC && predecessor==null)throw failure(HttpStatus.BAD_REQUEST,"Human attempts use balanced random allocation");
        var task=materials.scenario(scenario);
        if (mode!=Mode.SYNTHETIC && (!collectionEnabled || !admission.enabled() || informationVersion.isBlank() || informationText.isBlank() || !task.ready()))
            throw failure(HttpStatus.CONFLICT,"Human collection is not open for this scenario");
        UUID participant=UUID.randomUUID();
        if (predecessor!=null) {
            var previous=attempts.lockById(predecessor).orElseThrow(() -> failure(HttpStatus.NOT_FOUND,"Unknown attempt"));
            if (previous.stage==Stage.REPLACED) throw failure(HttpStatus.CONFLICT,"Attempt already replaced");
            if(previous.mode!=mode || !previous.scenario.equals(scenario))throw failure(HttpStatus.CONFLICT,"A replacement preserves participant mode and scenario");
            participant=previous.participantId==null?previous.id:previous.participantId;
            foundation=previous.foundation;
            previous.advance(Stage.REPLACED);
            attempts.saveAndFlush(previous); recorder.procedure(previous,"replaced");
            release(previous);
        }
        byte[] secret=new byte[32]; new SecureRandom().nextBytes(secret);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        var attempt=new StudyAttempt(hash(token),mode,scenario,materials.manifest().version(),predecessor);
        attempt.foundation=foundation;
        attempt.participantId=participant;attempt.informationVersion=mode==Mode.SYNTHETIC?SYNTHETIC_CONSENT:informationVersion;
        attempt.allocationKind=predecessor!=null?"REPLACEMENT":allocation==null?"ASSIGNED_SYNTHETIC":"BALANCED_RANDOM";
        attempt.allocationOrdinal=allocation==null?null:allocation.ordinal();
        attempt.informationText=mode==Mode.SYNTHETIC?SYNTHETIC_INFORMATION:informationText;
        attempt=attempts.saveAndFlush(attempt);
        recorder.created(attempt); recorder.procedure(attempt,"created");
        return new Invitation(view(attempt),token);
    }
    @Transactional(readOnly=true)
    public View get(String token) { return view(require(token,false)); }
    @Transactional
    public StudyAdmission.Status presence(String token){
        var attempt=require(token,true);open(attempt);
        if(Set.of(Stage.COORDINATION,Stage.REVISION,Stage.TRIAL).contains(attempt.stage))admission.acquire(attempt.id);
        return admission.status();
    }
    private void release(StudyAttempt attempt){admission.releaseAfterCommit(attempt.id);}
    @Transactional(readOnly=true)
    public View inspect(UUID id) { return view(attempts.findById(id).orElseThrow(() -> failure(HttpStatus.NOT_FOUND,"Unknown attempt"))); }
    @Transactional(readOnly=true)
    public Page page(String token) {
        var attempt=require(token,false); var scenario=protocol.scenario(attempt);var frozen=protocol.materials(attempt);
        var fields=switch(attempt.stage) {
            case PRE -> frozen.pre();
            case INTENTION -> List.of(new StudyMaterials.Item("intention","In your own words, what would you like the assistant to do?","text"));
            case PRIVATE -> List.of(new StudyMaterials.Item("private",scenario.privatePrompt(),"text"));
            case ASSESSMENT -> scenario.understanding();
            case POST -> frozen.post();
            default -> List.<StudyMaterials.Item>of();
        };
        String task=Set.of(Stage.INFORMATION,Stage.PRE).contains(attempt.stage)?null:scenario.task();
        String kind=switch(attempt.stage){case PRE->"pre";case INTENTION->"intention";case PRIVATE->"private";case ASSESSMENT->"understanding";case POST->"post";default->null;};
        return new Page(view(attempt),scenario.title(),task,fields,
                attempt.informationText==null?SYNTHETIC_INFORMATION:attempt.informationText,
                attempt.informationVersion==null?SYNTHETIC_CONSENT:attempt.informationVersion,kind,
                Set.of(Stage.DEBRIEF,Stage.COMPLETE).contains(attempt.stage)?frozen.debrief():null);
    }
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(String token,String cursor) {
        var attempt=require(token,false); open(attempt);
        if (attempt.agentId==null) throw failure(HttpStatus.CONFLICT,"Complete preparation first");
        if (cursor!=null && cursor.length()>64) throw failure(HttpStatus.BAD_REQUEST,"Invalid event cursor");
        var emitter=agents.subscribeBehaviour(attempt.agentId,cursor).orElseThrow(() -> failure(HttpStatus.NOT_FOUND,"Study conversation unavailable"));
        // Flush headers immediately even when the replay cursor is already current.
        try { emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().comment("connected")); }
        catch (java.io.IOException disconnected) { emitter.completeWithError(disconnected); }
        return emitter;
    }
    @Transactional
    public View finish(String token,String reason) {
        var attempt=require(token,true); open(attempt);
        if (reason==null || !Set.of("ACTIVITY_ENDED","NO_AGREEMENT","TECHNICAL","TIMEOUT").contains(reason))
            throw failure(HttpStatus.BAD_REQUEST,"Choose an interaction outcome");
        if (Set.of(Stage.ASSESSMENT,Stage.POST,Stage.DEBRIEF).contains(attempt.stage)) {
            if(!reason.equals(attempt.finishReason))throw failure(HttpStatus.CONFLICT,"Interaction ending already recorded");return view(attempt);
        }
        if (!Set.of(Stage.COORDINATION,Stage.REVISION,Stage.TRIAL).contains(attempt.stage))
            throw failure(HttpStatus.CONFLICT,"Complete preparation first");
        if (attempt.pendingRequest!=null) {
            var receipt=receipts.findByAttemptIdAndRequestId(attempt.id,attempt.pendingRequest).orElseThrow();
            if (receipt.status!=StudyTurn.Status.COMMITTED) {
                receipt.status=StudyTurn.Status.REJECTED; receipt.reason="interaction_finished"; receipt.finishedAt=StudyTurn.now();
                receipts.saveAndFlush(receipt);
            }
            attempt.pendingRequest=null; attempt.turnVersion++;
        }
        attempt.finishReason=reason; attempt.advance(Stage.ASSESSMENT);
        var result=saved(attempt); recorder.procedure(attempt,"finish");recorder.assessment(attempt);release(attempt); return result;
    }
    @Transactional
    public View consent(String token, String version) {
        var attempt=require(token,true); open(attempt);
        String expected=attempt.informationVersion==null?SYNTHETIC_CONSENT:attempt.informationVersion;
        if (!expected.equals(version)) throw failure(HttpStatus.BAD_REQUEST,"Information version does not match");
        if (attempt.consentedAt!=null) return view(attempt); // A repeat confirms the same durable consent.
        stage(attempt,Stage.INFORMATION);
        attempt.consentVersion=version; attempt.consentedAt=Instant.now(); attempt.advance(Stage.PRE);
        var result=saved(attempt); recorder.procedure(attempt,"consent"); return result;
    }
    @Transactional
    public View form(String token, String kind, Map<String,String> answers) {
        var attempt=require(token,true);
        Stage expected=switch(kind==null?"":kind) {
            case "pre" -> Stage.PRE; case "intention" -> Stage.INTENTION; case "private" -> Stage.PRIVATE;
            case "understanding" -> Stage.ASSESSMENT;case "post" -> Stage.POST;
            default -> throw failure(HttpStatus.BAD_REQUEST,"Unknown form");
        };
        validateForm(attempt,kind,answers);
        String encoded=json.toJson(new TreeMap<>(answers));
        var existing=forms.findByAttemptIdAndKind(attempt.id,kind);
        if (existing.isPresent()) {
            if (!existing.get().answers.equals(encoded)) throw failure(HttpStatus.CONFLICT,"Form already submitted");
            return view(attempt);
        }
        open(attempt);stage(attempt,expected);
        forms.save(new StudyForm(attempt,kind,encoded));
        if (expected==Stage.PRIVATE) {
            admission.acquire(attempt.id);
            // This definition's starter is deterministic: no provider I/O in this transaction.
            // Agent and ownership binding commit together; no demo credential is issued.
            var agent=agents.persistCreatedAgent(definition.createInstance(creation));
            if(!attempt.foundation)((ch.zhaw.prometheus.model.task.TaskPolicy)agent.getCurrentState().ownPolicy()).storage().put(
                    ch.zhaw.prometheus.agentdefs.study.StudyCoordinationUpdateAction.REQUIRED_ACTIVITY,new com.google.gson.JsonPrimitive(
                            switch(attempt.scenario){case "guess"->"GUESSING";case "context"->"CONTEXT_CHECKIN";case "signs"->"SIGN_ANSWERS";default->throw new IllegalStateException();}));
            attempt.agentId=agent.getId();
            attempt.interactionStartedAt=Instant.now();
            ownership.provision(agent.getId());
            attempt.advance(Stage.COORDINATION);
            attempts.saveAndFlush(attempt); recorder.provisioned(attempt,agent);
        } else attempt.advance(switch(expected){case PRE->Stage.INTENTION;case INTENTION->Stage.PRIVATE;case ASSESSMENT->Stage.POST;case POST->Stage.DEBRIEF;default->throw new IllegalStateException();});
        var result=saved(attempt); recorder.procedure(attempt,"form:"+kind); return result;
    }
    @Transactional
    public View complete(String token){
        var attempt=require(token,true);if(attempt.stage==Stage.COMPLETE)return view(attempt);
        open(attempt);stage(attempt,Stage.DEBRIEF);attempt.advance(Stage.COMPLETE);
        var result=saved(attempt);recorder.procedure(attempt,"complete");return result;
    }
    @Transactional
    public View withdraw(String token) {
        var attempt=require(token,true);
        if (attempt.stage==Stage.WITHDRAWN) return view(attempt);
        open(attempt); attempt.advance(Stage.WITHDRAWN);
        var result=saved(attempt); recorder.procedure(attempt,"withdrawn");release(attempt); return result;
    }
    @Transactional
    public View revoke(UUID id) {
        var attempt=attempts.lockById(id).orElseThrow(() -> failure(HttpStatus.NOT_FOUND,"Unknown attempt"));
        if (attempt.closed()) return view(attempt);
        attempt.advance(Stage.REVOKED);
        var result=saved(attempt); recorder.procedure(attempt,"revoked");release(attempt); return result;
    }
    StudyAttempt require(String token, boolean lock) {
        String digest=hash(token);
        return (lock?attempts.lockByHash(digest):attempts.findByTokenHash(digest))
                .orElseThrow(() -> failure(HttpStatus.UNAUTHORIZED,"Invalid study credential"));
    }
    static void open(StudyAttempt attempt) {
        if (attempt.closed()) throw failure(HttpStatus.CONFLICT,"Attempt is closed");
    }
    static void stage(StudyAttempt attempt, Stage expected) {
        if (attempt.stage!=expected) throw failure(HttpStatus.CONFLICT,"Complete the current study step first");
    }
    private void validateForm(StudyAttempt attempt,String kind, Map<String,String> answers) {
        var frozen=protocol.materials(attempt);
        var items=switch(kind){case "pre"->frozen.pre();case "post"->frozen.post();case "understanding"->protocol.scenario(attempt).understanding();
            default->List.of(new StudyMaterials.Item(kind,kind,"text"));};
        if(answers==null || !answers.keySet().equals(new HashSet<>(items.stream().map(StudyMaterials.Item::id).toList())))
            throw failure(HttpStatus.BAD_REQUEST,"Use the current form fields");
        for(var item:items){String value=answers.get(item.id());
            if(value==null || value.length()>2000 || !item.optional()&&value.isBlank())throw failure(HttpStatus.BAD_REQUEST,"Complete required fields (maximum 2000 characters each)");
            if(item.kind().equals("rating7") && !value.matches("[1-7]") && !(item.optional()&&Set.of("SKIP","NOT_APPLICABLE").contains(value)))
                throw failure(HttpStatus.BAD_REQUEST,"Choose a rating or explicit missing response");
        }
    }
    View saved(StudyAttempt attempt) { return view(attempts.saveAndFlush(attempt)); }
    static View view(StudyAttempt a) {
        return new View(a.id,a.version,a.mode,a.scenario,a.materialsVersion,a.stage,a.consentVersion,a.consentedAt,
                a.createdAt,a.updatedAt,a.predecessorId,a.turnVersion,a.pendingRequest,a.finishReason,a.participantId==null?a.id:a.participantId,a.allocationKind,a.allocationOrdinal,a.interactionStartedAt);
    }
    static String hash(String token) {
        if (token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw failure(HttpStatus.UNAUTHORIZED,"Invalid study credential");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static ResponseStatusException failure(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
