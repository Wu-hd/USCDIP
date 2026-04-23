package com.uscdip.backend.support;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.function.Supplier;

public final class TraceIdContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_KEY = "traceId";

    private TraceIdContext() {
    }

    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    public static String currentOrGenerate() {
        String current = getTraceId();
        if (isBlank(current)) {
            return UUID.randomUUID().toString();
        }
        return current;
    }

    public static void runWithTraceId(String traceId, Runnable action) {
        withTraceId(traceId, () -> {
            action.run();
            return null;
        });
    }

    public static <T> T withTraceId(String traceId, Supplier<T> supplier) {
        String previous = getTraceId();
        String resolved = isBlank(traceId) ? currentOrGenerate() : traceId.trim();
        MDC.put(TRACE_ID_KEY, resolved);
        try {
            return supplier.get();
        } finally {
            if (isBlank(previous)) {
                MDC.remove(TRACE_ID_KEY);
            } else {
                MDC.put(TRACE_ID_KEY, previous);
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
