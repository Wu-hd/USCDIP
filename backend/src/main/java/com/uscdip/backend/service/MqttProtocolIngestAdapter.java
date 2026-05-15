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
public class MqttProtocolIngestAdapter extends AbstractProtocolIngestAdapter {

    public MqttProtocolIngestAdapter(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public IngestProtocolType protocolType() {
        return IngestProtocolType.MQTT;
    }

    @Override
    public List<UnifiedIngestMetricDto> adapt(ProtocolAdaptRequest request) {
        return payloadItems(request).stream()
                .map(item -> {
                    Map<String, String> derived = new LinkedHashMap<>();
                    derived.put("topic", item.path("topic").asText(null));
                    if (item.has("qos")) {
                        derived.put("qos", item.get("qos").asText());
                    }
                    JsonNode attributes = mergeAttributes(item, derived);
                    return metric(
                            requiredText(item, "deviceId"),
                            requiredText(item, "metricCode"),
                            requiredNode(item, "value"),
                            requiredTime(item, "eventTime"),
                            requiredTime(item, "recvTime"),
                            requiredTime(item, "deviceTime"),
                            request,
                            attributes
                    );
                })
                .toList();
    }
}
