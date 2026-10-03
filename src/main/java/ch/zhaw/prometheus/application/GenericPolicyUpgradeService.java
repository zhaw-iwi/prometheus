package ch.zhaw.prometheus.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ch.zhaw.prometheus.agentdefs.core.GenericMultimodalBehaviour;
import ch.zhaw.prometheus.logging.AgentMonitorBroadcaster;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.repositories.AgentRepository;

/** Explicit maintenance upgrade; exact historical fingerprints, never semantic prompt rewriting. */
@Service
public class GenericPolicyUpgradeService {
    // Built-in versions introduced in 202/208/209. Legacy text exists only in a regression fixture.
    private static final Map<String, Set<String>> LEGACY = Map.of(
            "voice", Set.of("582df03f37fc317bd45776e9d00a77519614000b25be2d57f6d721e9d6971419"),
            "configure", Set.of("877460f0afd425f241751a156a848955130d80b75cff1bb8b3c19e241cc43812",
                    "f4805d99ded026111a8807148652b57956883d46d0fc81c19f9791d38a69a9d5",
                    "e0ad0673142872b741e5ec43bdb80060f1aa242cb4547b601de3eee821192d1f"),
            "cue", Set.of("f80a5dadff1454d78302232a28becf11609a72205911fd43d1f476af55d97c33"));
    public record Target(UUID policyId, String role, String status, String before, String after) {}
    public record Preview(UUID agentId, String fingerprint, List<Target> targets, boolean ruleReview,
            boolean pauseRequired, boolean liveSessionOpen) {}
    public record Result(int updatedPolicies, boolean paused, Preview current) {}
    private record Change(Target target, Consumer<String> update) {}
    private record Inspection(Preview preview, List<Change> changes, TaskPolicy policy) {}
    private final AgentRepository agents;
    private final AgentApplicationService turns;
    private final ExternalSpeechOwnership ownership;
    private final AgentMonitorBroadcaster monitor;
    private final TransactionTemplate transaction;
    public GenericPolicyUpgradeService(AgentRepository agents, AgentApplicationService turns, ExternalSpeechOwnership ownership,
            AgentMonitorBroadcaster monitor, PlatformTransactionManager transactions) {
        this.agents = agents; this.turns = turns; this.ownership = ownership; this.monitor = monitor;
        transaction = new TransactionTemplate(transactions);
    }
    public Optional<Preview> preview(UUID id) {
        return turns.serialized(id, () -> transaction.execute(status -> agents.findById(id).map(agent -> inspect(agent).preview())));
    }
    public Optional<Result> apply(UUID id, String fingerprint) {
        return turns.serialized(id, () -> transaction.execute(status -> agents.findById(id).map(agent -> {
            var inspection = inspect(agent); var preview = inspection.preview();
            if (preview.liveSessionOpen()) throw new IllegalStateException("Close the Live session before upgrading saved policies");
            int count = (int) preview.targets().stream().filter(target -> target.status().equals("upgrade")).count();
            if (count == 0 && !preview.pauseRequired()) return new Result(0, false, preview);
            if (!preview.fingerprint().equals(fingerprint)) throw new IllegalStateException("Saved configuration changed; preview again");
            for (var change : inspection.changes()) if (change.target().status().equals("upgrade"))
                change.update().accept(GenericMultimodalBehaviour.builtInPrompts().get(change.target().role()));
            if (preview.pauseRequired()) TaskMemory.requireRuleReview(inspection.policy().storage());
            agents.saveAndFlush(agent);
            AfterCommit.run(() -> monitor.publish(agent));
            return new Result(count, preview.pauseRequired(), inspect(agent).preview());
        })));
    }
    private Inspection inspect(Agent agent) {
        if (agent.getCurrentState() == null || !(agent.getCurrentState().ownPolicy() instanceof TaskPolicy task))
            throw new IllegalArgumentException("This is not a Generic task agent");
        var changes = new ArrayList<Change>();
        agent.reachableStates().stream().map(state -> state.ownPolicy()).filter(TaskPolicy.class::isInstance)
                .map(TaskPolicy.class::cast).distinct().forEach(policy -> changes.add(change(policy.getId(), "voice",
                        policy.configuredInstructions(), policy::setConfiguredInstructions)));
        agent.reachableActions().stream().filter(TaskUpdateAction.class::isInstance).map(TaskUpdateAction.class::cast)
                .forEach(action -> {
                    if (action.getPolicy() instanceof PromptPolicy policy) changes.add(change(policy.getId(), action.isTaskCue() ? "cue" : "configure",
                            policy.promptTemplate(), policy::setPromptTemplate));
                });
        var targets = changes.stream().map(Change::target).sorted(Comparator.comparing(target -> target.policyId().toString())).toList();
        boolean invalidSpec = invalid(task, TaskMemory.SPEC), review = invalidSpec || invalid(task, TaskMemory.DRAFT);
        var memory = new TreeMap<String, Object>();
        for (String key : List.of(TaskMemory.SPEC, TaskMemory.DRAFT, TaskMemory.PHASE, TaskMemory.REVISION, TaskMemory.ACTIONS, TaskMemory.SESSION))
            if (task.storage().containsKey(key)) memory.put(key, task.storage().get(key));
        String fingerprint = hash(agent.getId() + new com.google.gson.Gson().toJson(targets) + new com.google.gson.Gson().toJson(memory));
        return new Inspection(new Preview(agent.getId(), fingerprint, targets, review,
                invalidSpec && TaskMemory.active(task.storage()), ownership.hasOwner(agent.getId())), changes, task);
    }
    private static boolean invalid(TaskPolicy policy, String key) {
        if (!policy.storage().containsKey(key)) return false;
        try { TaskSpec.parse(policy.storage().get(key)).executable(); return false; }
        catch (IllegalArgumentException invalid) { return true; }
    }
    private static Change change(UUID id, String role, String text, Consumer<String> update) {
        String before = hash(text), after = hash(GenericMultimodalBehaviour.builtInPrompts().get(role));
        return new Change(new Target(id, role, before.equals(after) ? "current" : LEGACY.get(role).contains(before) ? "upgrade" : "custom",
                before, after), update);
    }
    private static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Objects.toString(text, "").getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
