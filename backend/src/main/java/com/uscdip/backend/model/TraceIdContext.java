package com.uscdip.backend.model;

import org.slf4j.MDC;

import java.util.UUID;

public final class TraceIdContext {

    public static final String HEADER_NAME = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    private TraceIdContext() {
    }

    public static String currentTraceId() {
        return MDC.get(MDC_KEY);
    }

    public static String ensureTraceId(String candidate) {
        String traceId = isBlank(candidate) ? generateTraceId() : candidate.trim();
        MDC.put(MDC_KEY, traceId);
        return traceId;
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }

    private static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
