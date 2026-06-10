package ch.zhaw.prometheus.logging;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class LogStreamBroadcaster {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogStreamBroadcaster.class);
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private static LogStreamBroadcaster instance;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AtomicLong sendFailureCount = new AtomicLong(0L);

    public LogStreamBroadcaster() {
        LogStreamBroadcaster.instance = this;
    }

    public static LogStreamBroadcaster getInstance() {
        return LogStreamBroadcaster.instance;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = this.createEmitter();
        this.emitters.add(emitter);
        emitter.onCompletion(() -> this.emitters.remove(emitter));
        emitter.onTimeout(() -> this.emitters.remove(emitter));
        emitter.onError((ex) -> this.emitters.remove(emitter));
        return emitter;
    }

    public void publish(LogEvent event) {
        if (event == null) {
            return;
        }
        for (SseEmitter emitter : this.emitters) {
            try {
                emitter.send(SseEmitter.event().id(String.valueOf(event.getTimestamp())).name("log").data(event));
            } catch (Throwable failure) {
                long failures = this.sendFailureCount.incrementAndGet();
                if (failures == 1 || failures % 100 == 0) {
                    LOGGER.debug("SSE log send failed; failures={}", failures, failure);
                }
                this.emitters.remove(emitter);
                safeComplete(emitter);
            }
        }
    }

    @Scheduled(fixedDelayString = "${prometheus.sse.heartbeat.delay-ms:25000}")
    public void heartbeat() {
        for (SseEmitter emitter : this.emitters) {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (Throwable failure) {
                long failures = this.sendFailureCount.incrementAndGet();
                if (failures == 1 || failures % 100 == 0) {
                    LOGGER.debug("SSE log heartbeat failed; failures={}", failures, failure);
                }
                this.emitters.remove(emitter);
                safeComplete(emitter);
            }
        }
    }

    protected SseEmitter createEmitter() {
        return new SseEmitter(EMITTER_TIMEOUT_MS);
    }

    private static void safeComplete(SseEmitter emitter) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.complete();
        } catch (Throwable ignored) {
        }
    }
}
