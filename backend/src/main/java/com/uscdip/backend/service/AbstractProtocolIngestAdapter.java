package com.uscdip.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uscdip.backend.dto.ProtocolAdaptRequest;
import com.uscdip.backend.dto.UnifiedIngestMetricDto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

abstract class AbstractProtocolIngestAdapter implements ProtocolIngestAdapter {

    private final ObjectMapper objectMapper;

    protected AbstractProtocolIngestAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    protected List<JsonNode> payloadItems(ProtocolAdaptRequest request) {
        JsonNode payload = request.payload();
        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException("payload is required");
        }
        if (payload.isArray()) {
            List<JsonNode> items = new ArrayList<>();
            payload.forEach(items::add);
            return items;
        }
        return List.of(payload);
    }

    protected LocalDateTime requiredTime(JsonNode node, String field) {
        String value = requiredText(node, field);
        return LocalDateTime.parse(value);
    }

    protected String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.asText().trim();
    }

    protected JsonNode requiredNode(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    protected JsonNode mergeAttributes(JsonNode payloadNode, Map<String, String> derivedAttributes) {
        ObjectNode merged = objectMapper.createObjectNode();
        JsonNode provided = payloadNode.get("attributes");
        if (provided != null && provided.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = provided.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                merged.set(entry.getKey(), entry.getValue());
            }
        }
        derivedAttributes.forEach((key, value) -> {
            if (value != null && !value.isBlank() && !merged.has(key)) {
                merged.put(key, value);
            }
        });
        return merged.isEmpty() ? null : merged;
    }

    protected UnifiedIngestMetricDto metric(
            String deviceId,
            String metricCode,
            JsonNode value,
            LocalDateTime eventTime,
            LocalDateTime recvTime,
            LocalDateTime deviceTime,
            ProtocolAdaptRequest request,
            JsonNode attributes
    ) {
        return new UnifiedIngestMetricDto(
                deviceId,
                protocolType().name(),
                metricCode,
                value,
                eventTime,
                recvTime,
                deviceTime,
                request.traceId(),
                request.isBackfill(),
                attributes
        );
    }
}
