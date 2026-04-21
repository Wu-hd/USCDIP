package com.uscdip.backend.support;

import org.slf4j.MDC;

import java.util.UUID;

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

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
