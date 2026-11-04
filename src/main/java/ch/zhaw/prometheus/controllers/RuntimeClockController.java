package ch.zhaw.prometheus.controllers;

import java.time.Clock;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public time only: calibration never loads an agent, checks a database, or grants access. */
@RestController
public class RuntimeClockController {
    private final Clock clock;
    public RuntimeClockController() { this(Clock.systemUTC()); }
    RuntimeClockController(Clock clock) { this.clock = clock; }
    public record Sample(long serverTimeMs) {}

    @GetMapping("/runtime/clock")
    public ResponseEntity<Sample> sample() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache").body(new Sample(clock.millis()));
    }
}
