package com.uscdip.backend.support;

import org.slf4j.MDC;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;
import java.util.function.Supplier;

public final class TraceIdContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String TRACE_ID_KEY = "traceId";
    public static final String TRACE_ID_REQUEST_ATTRIBUTE = TraceIdContext.class.getName() + ".traceId";

    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");
    private static final Pattern TRACEPARENT_PATTERN = Pattern.compile("^[\\da-fA-F]{2}-([\\da-fA-F]{32})-([\\da-fA-F]{16})-[\\da-fA-F]{2}$");

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

    public static boolean isValidTraceId(String value) {
        return value != null && TRACE_ID_PATTERN.matcher(value.trim()).matches();
    }

    public static String resolveIncomingTraceId(String traceIdHeader, String traceparentHeader) {
        if (isValidTraceId(traceIdHeader)) {
            return traceIdHeader.trim();
        }
        String traceparentTraceId = fromTraceparent(traceparentHeader);
        if (!isBlank(traceparentTraceId)) {
            return traceparentTraceId;
        }
        return UUID.randomUUID().toString();
    }

    public static void setTraceId(String traceId) {
        MDC.put(TRACE_ID_KEY, isBlank(traceId) ? currentOrGenerate() : traceId.trim());
    }

    public static void clearTraceId() {
        MDC.remove(TRACE_ID_KEY);
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
        setTraceId(resolved);
        try {
            return supplier.get();
        } finally {
            if (isBlank(previous)) {
                clearTraceId();
            } else {
                setTraceId(previous);
            }
        }
    }

    private static String fromTraceparent(String value) {
        if (isBlank(value)) {
            return null;
        }
        Matcher matcher = TRACEPARENT_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1).toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
