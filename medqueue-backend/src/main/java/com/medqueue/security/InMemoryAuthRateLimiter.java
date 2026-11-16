package com.medqueue.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** A synchronized fixed-window limiter local to this application instance. */
@Component
public class InMemoryAuthRateLimiter {
    private static final int MAX_BUCKETS = 20_000;
    private final Clock clock;
    private final LinkedHashMap<String, Window> windows = new LinkedHashMap<>();

    public InMemoryAuthRateLimiter() {
        this(Clock.systemUTC());
    }

    InMemoryAuthRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized Decision acquire(String key, int limit, Duration duration) {
        if (limit < 1 || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Rate limit and window must be positive");
        }
        Instant now = clock.instant();
        removeExpired(now);
        Window window = windows.get(key);
        if (window == null) {
            if (windows.size() >= MAX_BUCKETS) {
                return new Decision(false, Math.max(1, duration.toSeconds()));
            }
            window = new Window(now, duration);
            windows.put(key, window);
        }
        if (window.count >= limit) {
            Duration remaining = Duration.between(now, window.expiresAt);
            long retryAfter = remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
            return new Decision(false, Math.max(1, retryAfter));
        }
        window.count++;
        return new Decision(true, 0);
    }

    private void removeExpired(Instant now) {
        Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            Window window = iterator.next().getValue();
            if (!now.isBefore(window.expiresAt)) iterator.remove();
        }
    }

    public record Decision(boolean allowed, long retryAfterSeconds) { }

    private static final class Window {
        private final Instant expiresAt;
        private int count;

        private Window(Instant startedAt, Duration duration) {
            this.expiresAt = startedAt.plus(duration);
        }
    }
}
