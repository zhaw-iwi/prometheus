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
    public record View(UUID id, long version, Mode mode, String scenario, String materialsVersion,
            Stage stage, String consentVersion, Instant consentedAt, Instant createdAt, Instant updatedAt,
            UUID predecessorId, long turnVersion, UUID pendingRequest) {}
    public record Invitation(View session, String token) {}
    private final StudyAttemptRepository attempts;
    private final StudyFormRepository forms;
    private final StudyMaterials materials;
    private final StudyCoordinationAgent definition;
    private final AgentApplicationService agents;
    private final AgentCreationContext creation;
    private final boolean collectionEnabled;
    private final String informationVersion;
    private final Gson json = new Gson();
    public StudySessions(StudyAttemptRepository attempts, StudyFormRepository forms, StudyMaterials materials,
            StudyCoordinationAgent definition, AgentApplicationService agents, PromptMessageAssembler prompts,
            LanguageModelGateway gateway, @Value("${prometheus.study.collection-enabled:false}") boolean enabled,
            @Value("${prometheus.study.information-version:}") String informationVersion) {
        this.attempts=attempts; this.forms=forms; this.materials=materials; this.definition=definition;
        this.agents=agents; creation=new AgentCreationContext(prompts,gateway);
        collectionEnabled=enabled; this.informationVersion=informationVersion;
    }
    @Transactional
    public Invitation invite(Mode mode, String scenario, UUID predecessor) {
        if (mode==null) throw failure(HttpStatus.BAD_REQUEST,"Attempt mode is required");
        var task=materials.scenario(scenario);
        if (mode!=Mode.SYNTHETIC && (!collectionEnabled || informationVersion.isBlank() || !task.ready()))
            throw failure(HttpStatus.CONFLICT,"Human collection is not open for this scenario");
        if (predecessor!=null) {
            var previous=attempts.lockById(predecessor).orElseThrow(() -> failure(HttpStatus.NOT_FOUND,"Unknown attempt"));
            if (previous.stage==Stage.REPLACED) throw failure(HttpStatus.CONFLICT,"Attempt already replaced");
            previous.advance(Stage.REPLACED);
        }
        byte[] secret=new byte[32]; new SecureRandom().nextBytes(secret);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        var attempt=attempts.saveAndFlush(new StudyAttempt(hash(token),mode,scenario,materials.manifest().version(),predecessor));
        return new Invitation(view(attempt),token);
    }
    @Transactional(readOnly=true)
    public View get(String token) { return view(require(token,false)); }
    @Transactional(readOnly=true)
    public View inspect(UUID id) { return view(attempts.findById(id).orElseThrow(() -> failure(HttpStatus.NOT_FOUND,"Unknown attempt"))); }
    @Transactional
    public View consent(String token, String version) {
        var attempt=require(token,true); open(attempt);
        String expected=attempt.mode==Mode.SYNTHETIC ? SYNTHETIC_CONSENT : informationVersion;
        if (!expected.equals(version)) throw failure(HttpStatus.BAD_REQUEST,"Information version does not match");
        if (attempt.consentedAt!=null) return view(attempt); // A repeat confirms the same durable consent.
        stage(attempt,Stage.INFORMATION);
        attempt.consentVersion=version; attempt.consentedAt=Instant.now(); attempt.advance(Stage.PRE);
        return saved(attempt);
    }
    @Transactional
    public View form(String token, String kind, Map<String,String> answers) {
        var attempt=require(token,true); open(attempt);
        Stage expected=switch(kind==null?"":kind) {
            case "pre" -> Stage.PRE; case "intention" -> Stage.INTENTION; case "private" -> Stage.PRIVATE;
            default -> throw failure(HttpStatus.BAD_REQUEST,"Unknown form");
        };
        validateForm(kind,answers);
        String encoded=json.toJson(new TreeMap<>(answers));
        var existing=forms.findByAttemptIdAndKind(attempt.id,kind);
        if (existing.isPresent()) {
            if (!existing.get().answers.equals(encoded)) throw failure(HttpStatus.CONFLICT,"Form already submitted");
            return view(attempt);
        }
        stage(attempt,expected);
        forms.save(new StudyForm(attempt,kind,encoded));
        if (expected==Stage.PRIVATE) {
            // This definition's starter is deterministic: no provider I/O in this transaction.
            // Agent and ownership binding commit together; no demo credential is issued.
            attempt.agentId=agents.persistCreatedAgent(definition.createInstance(creation)).getId();
            attempt.advance(Stage.COORDINATION);
        } else attempt.advance(expected==Stage.PRE?Stage.INTENTION:Stage.PRIVATE);
        return saved(attempt);
    }
    @Transactional
    public View withdraw(String token) {
        var attempt=require(token,true);
        if (attempt.stage==Stage.WITHDRAWN) return view(attempt);
        open(attempt); attempt.advance(Stage.WITHDRAWN); return saved(attempt);
    }
    @Transactional
    public View revoke(UUID id) {
        var attempt=attempts.lockById(id).orElseThrow(() -> failure(HttpStatus.NOT_FOUND,"Unknown attempt"));
        if (!attempt.closed()) attempt.advance(Stage.REVOKED);
        return saved(attempt);
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
    private void validateForm(String kind, Map<String,String> answers) {
        Set<String> keys=kind.equals("pre") ? new HashSet<>(materials.manifest().pre().stream().map(StudyMaterials.Item::id).toList())
                : Set.of(kind);
        if (answers==null || !answers.keySet().equals(keys) || answers.values().stream().anyMatch(v -> v==null || v.isBlank() || v.length()>2000))
            throw failure(HttpStatus.BAD_REQUEST,"Complete the required form fields (maximum 2000 characters each)");
        if (kind.equals("pre")) for (var item:materials.manifest().pre())
            if (item.kind().equals("rating7") && !answers.get(item.id()).matches("[1-7]"))
                throw failure(HttpStatus.BAD_REQUEST,"Choose a rating from 1 to 7");
    }
    View saved(StudyAttempt attempt) { return view(attempts.saveAndFlush(attempt)); }
    static View view(StudyAttempt a) {
        return new View(a.id,a.version,a.mode,a.scenario,a.materialsVersion,a.stage,a.consentVersion,a.consentedAt,
                a.createdAt,a.updatedAt,a.predecessorId,a.turnVersion,a.pendingRequest);
    }
    static String hash(String token) {
        if (token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw failure(HttpStatus.UNAUTHORIZED,"Invalid study credential");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static ResponseStatusException failure(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
