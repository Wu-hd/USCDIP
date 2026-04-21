package com.uscdip.backend.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class GatewayRateLimitService {

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public void clearAll() {
        counters.clear();
    }

    public boolean tryAcquire(String key, int capacity, int windowSeconds) {
        if (key == null || key.isBlank() || capacity <= 0 || windowSeconds <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter(now, new AtomicInteger(0)));
        synchronized (counter) {
            if (now - counter.windowStartMs >= windowSeconds * 1000L) {
                counter.windowStartMs = now;
                counter.count.set(0);
            }
            if (counter.count.get() >= capacity) {
                return false;
            }
            counter.count.incrementAndGet();
            return true;
        }
    }

    private static final class WindowCounter {
        private long windowStartMs;
        private final AtomicInteger count;

        private WindowCounter(long windowStartMs, AtomicInteger count) {
            this.windowStartMs = windowStartMs;
            this.count = count;
        }
    }
}
