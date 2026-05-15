package com.uscdip.backend.service;

import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.IngestProtocolType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ProtocolAdapterRegistry {

    private final Map<IngestProtocolType, ProtocolIngestAdapter> adapters;

    public ProtocolAdapterRegistry(List<ProtocolIngestAdapter> adapters) {
        Map<IngestProtocolType, ProtocolIngestAdapter> registry = new EnumMap<>(IngestProtocolType.class);
        adapters.forEach(adapter -> registry.put(adapter.protocolType(), adapter));
        this.adapters = Map.copyOf(registry);
    }

    public ProtocolIngestAdapter resolve(IngestProtocolType protocolType) {
        ProtocolIngestAdapter adapter = adapters.get(protocolType);
        if (adapter == null) {
            throw new AuthFlowException(
                    ErrorCode.INGEST_PROTOCOL_UNSUPPORTED,
                    HttpStatus.BAD_REQUEST,
                    "Protocol adapter is not available for " + protocolType.name()
            );
        }
        return adapter;
    }
}
