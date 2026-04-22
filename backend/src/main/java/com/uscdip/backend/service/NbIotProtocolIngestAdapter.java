package com.uscdip.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.dto.ProtocolAdaptRequest;
import com.uscdip.backend.dto.UnifiedIngestMetricDto;
import com.uscdip.backend.model.IngestProtocolType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class NbIotProtocolIngestAdapter extends AbstractProtocolIngestAdapter {

    public NbIotProtocolIngestAdapter(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public IngestProtocolType protocolType() {
        return IngestProtocolType.NB_IOT;
    }

    @Override
    public List<UnifiedIngestMetricDto> adapt(ProtocolAdaptRequest request) {
        return payloadItems(request).stream()
                .map(item -> {
                    Map<String, String> derived = new LinkedHashMap<>();
                    derived.put("imei", text(item, "imei"));
                    derived.put("cellId", text(item, "cellId"));
                    JsonNode attributes = mergeAttributes(item, derived);
                    return metric(
                            fallbackText(item, "deviceId", "terminalId"),
                            fallbackText(item, "metricCode", "metric"),
                            item.hasNonNull("reading") ? item.get("reading") : requiredNode(item, "value"),
                            requiredTime(item, "eventTime"),
                            parseTime(item, "cloudReceiveTime", "recvTime"),
                            parseTime(item, "deviceReportedAt", "deviceTime"),
                            request,
                            attributes
                    );
                })
                .toList();
    }

    private LocalDateTime parseTime(JsonNode node, String primary, String fallback) {
        String raw = text(node, primary);
        if (raw != null && !raw.isBlank()) {
            return LocalDateTime.parse(raw);
        }
        return requiredTime(node, fallback);
    }

    private String fallbackText(JsonNode node, String primary, String secondary) {
        String value = text(node, primary);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return requiredText(node, secondary);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
