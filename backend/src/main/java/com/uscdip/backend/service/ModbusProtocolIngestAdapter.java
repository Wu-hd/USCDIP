package com.uscdip.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.dto.ProtocolAdaptRequest;
import com.uscdip.backend.dto.UnifiedIngestMetricDto;
import com.uscdip.backend.model.IngestProtocolType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ModbusProtocolIngestAdapter extends AbstractProtocolIngestAdapter {

    public ModbusProtocolIngestAdapter(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public IngestProtocolType protocolType() {
        return IngestProtocolType.MODBUS;
    }

    @Override
    public List<UnifiedIngestMetricDto> adapt(ProtocolAdaptRequest request) {
        return payloadItems(request).stream()
                .map(item -> {
                    Map<String, String> derived = new LinkedHashMap<>();
                    derived.put("registerAddress", text(item, "registerAddress"));
                    derived.put("slaveId", text(item, "slaveId"));
                    JsonNode attributes = mergeAttributes(item, derived);
                    String metricCode = fallbackText(item, "metricCode", "registerAddress");
                    JsonNode value = item.hasNonNull("registerValue") ? item.get("registerValue") : requiredNode(item, "value");
                    return metric(
                            requiredText(item, "deviceId"),
                            metricCode,
                            value,
                            parseTime(item, "sampledAt", "eventTime"),
                            parseTime(item, "gatewayReceivedAt", "recvTime"),
                            parseTime(item, "controllerTime", "deviceTime"),
                            request,
                            attributes
                    );
                })
                .toList();
    }

    private String fallbackText(JsonNode node, String primary, String secondary) {
        String value = text(node, primary);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return requiredText(node, secondary);
    }

    private java.time.LocalDateTime parseTime(JsonNode node, String primary, String fallback) {
        String raw = text(node, primary);
        if (raw != null && !raw.isBlank()) {
            return java.time.LocalDateTime.parse(raw);
        }
        return requiredTime(node, fallback);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
