package ch.zhaw.prometheus.logging;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class LogStreamBroadcaster {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogStreamBroadcaster.class);

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
        SseEmitter emitter = new SseEmitter(0L);
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
                emitter.send(SseEmitter.event().name("log").data(event));
            } catch (Throwable failure) {
                long failures = this.sendFailureCount.incrementAndGet();
                if (failures == 1 || failures % 100 == 0) {
                    LOGGER.debug("SSE log send failed; failures={}", failures, failure);
                }
                this.emitters.remove(emitter);
            }
        }
    }
}
