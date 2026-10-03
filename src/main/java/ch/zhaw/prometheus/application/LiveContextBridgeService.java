package ch.zhaw.prometheus.application;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.google.gson.JsonObject;
import ch.zhaw.prometheus.application.live.*;
import ch.zhaw.prometheus.application.live.LiveContextDelivery.Command;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import jakarta.annotation.PreDestroy;

/** After-commit invalidation plus bounded refresh workers. No provider wait holds an agent mutation lock. */
@Service
public class LiveContextBridgeService {
    public record Trace(long serverMs, String phase, String type, String revision, UUID sourceId,
            long sequence, int contentBytes, List<UUID> sourceIds) {}
    public record Status(String state, String revision, List<Trace> recent, long dropped, int pendingClarifications) {}
    private final LiveAgentContextService contexts;
    private final Clock clock;
    private AgentActivityService activities;
    @Autowired void configureActivities(AgentActivityService value) { activities = value; }
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), work -> { var thread = new Thread(work, "live-context"); thread.setDaemon(true); return thread; },
            new ThreadPoolExecutor.AbortPolicy());
    @Autowired public LiveContextBridgeService(LiveAgentContextService contexts) { this(contexts, Clock.systemUTC()); }
    LiveContextBridgeService(LiveAgentContextService contexts, Clock clock) { this.contexts = contexts; this.clock = clock; }

    public Session open(LiveContextSnapshot initial, ExternalSpeech owner, Consumer<Command> send,
            Consumer<LiveContextSnapshot> applied, Consumer<String> failure) {
        var session = new Session(initial, owner, send, applied, failure);
        if (sessions.putIfAbsent(owner.sessionId(), session) != null) throw new IllegalStateException("Context session already open");
        return session;
    }
    @EventListener public void changed(AgentCommitted event) {
        sessions.values().stream().filter(session -> session.agent.equals(event.agentId())).forEach(session -> {
            if (!session.owner.epoch().equals(event.epoch())) session.fail("agent_epoch_changed");
            else if (event.narrationId() != null) session.announcement(event.narrationId());
            else session.request();
        });
    }
    @EventListener public void captured(LiveTranscriptCaptureService.Committed event) {
        Session session = sessions.get(event.owner().sessionId());
        if (session == null || !session.owner.equals(event.owner()) || !session.agent.equals(event.agentId())) return;
        session.delegations.committed(event.outcome());
        if ("CLARIFICATION".equals(event.outcome().status())) {
            if (!session.clarifications.offer(event.outcome().segmentId())) session.fail("clarification_queue_full");
        }
        session.request();
    }
    // Tick in memory; coalesce commits, but honor sensory/delegation deadlines and bounded fallback.
    // Scope revocation/liveness remain independently checked by the scoped session lease.
    @Scheduled(fixedDelay = 1000) public void refresh() { sessions.values().forEach(Session::refreshIfDue); }
    @PreDestroy public void shutdown() { sessions.values().forEach(Session::close); workers.shutdownNow(); }

    public final class Session {
        private final UUID agent;
        private final ExternalSpeech owner;
        private final LiveContextDelivery delivery;
        private final LiveDelegation delegations = new LiveDelegation();
        private final Consumer<Command> send;
        private final Consumer<LiveContextSnapshot> applied;
        private final Consumer<String> failure;
        private final AtomicBoolean running = new AtomicBoolean(), dirty = new AtomicBoolean(), closed = new AtomicBoolean();
        private final AtomicBoolean urgent = new AtomicBoolean();
        private final AtomicReference<UUID> lastAnnouncement = new AtomicReference<>();
        private final ArrayBlockingQueue<UUID> clarifications = new ArrayBlockingQueue<>(32);
        private final ArrayDeque<Trace> traces = new ArrayDeque<>();
        private volatile boolean ready;
        private volatile String state = "starting", revision;
        private volatile long nextRefreshAt = Long.MAX_VALUE, nextCommitReadAt;
        private long dropped;
        Session(LiveContextSnapshot initial, ExternalSpeech owner, Consumer<Command> send,
                Consumer<LiveContextSnapshot> applied, Consumer<String> failure) {
            agent = initial.agentId(); this.owner = owner; delivery = new LiveContextDelivery(initial);
            revision = initial.revision(); this.send = send; this.applied = applied; this.failure = failure;
        }
        public void ready() { delivery.ready(); ready = true; state = "ready"; request(); }
        public void receive(JsonObject event) {
            if (closed.get() || !event.has("type") || !"session.delegation.created".equals(event.get("type").getAsString())) return;
            try {
                var delegation = event.getAsJsonObject("delegation");
                if (!"client".equals(delegation.get("target").getAsString())) throw new IllegalArgumentException();
                Long offset = event.has("offset_ms") && !event.get("offset_ms").isJsonNull() ? event.get("offset_ms").getAsLong() : null;
                if (offset != null && (offset < 0 || offset > 3600000)) offset = null;
                delegations.request(delegation.get("id").getAsString(), offset, revision, clock.millis());
                trace("delegation_received", null); request();
            } catch (RuntimeException invalid) { fail("delegation_invalid_or_full"); }
        }
        public void request() {
            if (closed.get()) return; dirty.set(true);
            refreshIfDue();
        }
        private void announcement(UUID source) {
            if (source.equals(lastAnnouncement.getAndSet(source))) return;
            urgent.set(true); trace("announcement_committed", null, List.of(source)); request();
        }
        private void refreshIfDue() {
            // Claim before sampling deadlines: a concurrent completed read must not leave
            // this caller scheduling from an obsolete, already-due deadline.
            if (closed.get() || !ready || !running.compareAndSet(false, true)) return;
            long now = clock.millis();
            boolean deadline = now >= Math.min(nextRefreshAt, delegations.nextDeadline());
            if (!urgent.get() && !deadline && (!dirty.get() || now < nextCommitReadAt)) { running.set(false); return; }
            try { workers.execute(this::run); }
            catch (RejectedExecutionException full) { running.set(false); fail("context_workers_full"); }
        }
        private void scheduleRefresh(LiveContextSnapshot context) {
            long now = clock.millis(), next = now + 30000;
            for (var item : context.items()) {
                // Future source timestamps can become current; fresh observations can expire.
                for (var boundary : new java.time.Instant[] {item.observedAt(), item.expiresAt()})
                    if (boundary != null && boundary.isAfter(context.builtAt())) next = Math.min(next, boundary.toEpochMilli());
            }
            nextRefreshAt = next;
        }
        private void run() {
            if (activities == null) runContext();
            else activities.call(agent, "live_context", urgent.get(), () -> {
                ch.zhaw.prometheus.logging.ActivityTrace.bind(owner.epoch(), owner.sessionId(), lastAnnouncement.get());
                runContext(); return null;
            });
        }
        private void runContext() {
            try {
                dirty.set(false);
                urgent.set(false);
                if (closed.get()) return;
                // Faster append ACKs must not turn each fragment/sensor commit into a graph read.
                // Deferred work remains dirty; no worker sleeps while waiting for this window.
                nextCommitReadAt = clock.millis() + 5000;
                nextRefreshAt = clock.millis() + 30000;
                var fresh = contexts.refresh(agent, owner).orElseThrow(() -> new IllegalStateException("Obsolete context owner"));
                if (closed.get()) return;
                scheduleRefresh(fresh.context());
                var batch = delivery.plan(fresh.context(), fresh.narrations());
                transmitAll(batch.commands());
                if (closed.get()) return;
                delivery.acknowledged(batch); revision = fresh.context().revision(); applied.accept(fresh.context());
                for (var reply : delegations.resolve(fresh.context(), clock.millis()))
                    for (Command command : LiveContextDelivery.contextReply(reply.text(), reply.id(), revision)) transmit(command);
                UUID segment;
                while ((segment = clarifications.poll()) != null) transmit(LiveContextDelivery.clarification(segment, revision));
                state = "ready";
            } catch (RuntimeException invalid) {
                ch.zhaw.prometheus.logging.ActivityTrace.failed("context_delivery_unconfirmed");
                fail("context_delivery_unconfirmed");
            }
            finally { running.set(false); refreshIfDue(); }
        }
        private void transmit(Command command) {
            transmit(command, command.sourceId() == null ? List.of() : List.of(command.sourceId()));
        }
        private void transmitAll(List<Command> commands) {
            var queue = new ArrayDeque<>(commands); var expired = new HashSet<String>();
            while (!queue.isEmpty()) {
                Command command = queue.removeFirst();
                if (command.expiredAt(java.time.Instant.ofEpochMilli(clock.millis()))) {
                    if (!expired.add(command.evidenceType())) continue;
                    trace("expired_before_send", command); command = command.expired();
                }
                var sources = new LinkedHashSet<UUID>();
                if (command.sourceId() != null) sources.add(command.sourceId());
                // Pack compatible factual updates just before transmission. Never carry a
                // freshness decision across an ACK wait or merge announcements/instructions.
                while (!queue.isEmpty() && command.type().equals("session.thinking.append")) {
                    var next = queue.getFirst();
                    String content = command.content() + "\n" + next.content();
                    if (!next.type().equals(command.type()) || !Objects.equals(next.delegationId(), command.delegationId())
                            || !next.revision().equals(command.revision()) || next.expiredAt(java.time.Instant.ofEpochMilli(clock.millis()))
                            || content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 480) break;
                    queue.removeFirst(); if (next.sourceId() != null) sources.add(next.sourceId());
                    command = new Command(command.type(), content, command.delegationId(),
                            sources.size() == 1 ? sources.iterator().next() : null, command.revision());
                }
                transmit(command, List.copyOf(sources));
            }
        }
        private void transmit(Command command, List<UUID> sources) {
            if (closed.get()) throw new IllegalStateException("Context session closed");
            state = "awaiting_ack"; trace("send", command, sources);
            ch.zhaw.prometheus.logging.ActivityTrace.measure("preparing_speech", () -> {
                send.accept(command); return null;
            }); // ACK is context delivery, never proof of playback.
            if (!closed.get()) trace("ack", command, sources);
        }
        private synchronized void trace(String phase, Command command) {
            trace(phase, command, command == null || command.sourceId() == null ? List.of() : List.of(command.sourceId()));
        }
        private synchronized void trace(String phase, Command command, List<UUID> sources) {
            long sequence = dropped + traces.size() + 1;
            if (traces.size() == 64) { traces.removeFirst(); dropped++; }
            traces.addLast(new Trace(clock.millis(), phase, command == null ? null : command.type(),
                    command == null ? revision : command.revision(), command == null
                            ? (sources.size() == 1 ? sources.getFirst() : null) : command.sourceId(), sequence,
                    command == null ? 0 : command.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length, sources));
        }
        void fail(String code) {
            if (!closed.compareAndSet(false, true)) return;
            state = code; trace("failed", null); sessions.remove(owner.sessionId(), this); clarifications.clear(); failure.accept(code);
        }
        public void close() { closed.set(true); sessions.remove(owner.sessionId(), this); clarifications.clear(); if (!state.contains("unconfirmed")) state = "closed"; }
        public synchronized Status status() { return new Status(state, revision, List.copyOf(traces), dropped, clarifications.size()); }
    }
}
