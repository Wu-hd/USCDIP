package com.uscdip.backend.model;

import java.util.Arrays;
import java.util.Locale;

public enum IngestProtocolType {
    MQTT,
    MODBUS,
    NB_IOT;

    public static IngestProtocolType from(String rawValue) {
        String normalized = normalize(rawValue);
        return Arrays.stream(values())
                .filter(value -> value.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported ingest protocolType: " + rawValue));
    }

    public static String normalize(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        return rawValue.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }
}
