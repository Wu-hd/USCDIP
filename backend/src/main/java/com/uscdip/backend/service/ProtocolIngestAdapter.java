package com.uscdip.backend.service;

import com.uscdip.backend.dto.ProtocolAdaptRequest;
import com.uscdip.backend.dto.UnifiedIngestMetricDto;
import com.uscdip.backend.model.IngestProtocolType;

import java.util.List;

public interface ProtocolIngestAdapter {

    IngestProtocolType protocolType();

    List<UnifiedIngestMetricDto> adapt(ProtocolAdaptRequest request);
}
