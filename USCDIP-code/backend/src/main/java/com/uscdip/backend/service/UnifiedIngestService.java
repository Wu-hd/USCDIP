package com.uscdip.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.uscdip.backend.dto.ProtocolAdaptRequest;
import com.uscdip.backend.dto.ProtocolAdaptResponse;
import com.uscdip.backend.dto.BackfillIngestRequest;
import com.uscdip.backend.dto.DataQualityBatchSummary;
import com.uscdip.backend.dto.TsdbWriteSummary;
import com.uscdip.backend.dto.UnifiedIngestBatchRequest;
import com.uscdip.backend.dto.UnifiedIngestBatchResponse;
import com.uscdip.backend.dto.UnifiedIngestMetricDto;
import com.uscdip.backend.entity.DeviceEntity;
import com.uscdip.backend.entity.IngestBatchEntity;
import com.uscdip.backend.entity.IngestRecordEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.IngestProtocolType;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.repository.DeviceRepository;
import com.uscdip.backend.repository.IngestBatchRepository;
import com.uscdip.backend.repository.IngestRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UnifiedIngestService {

    private static final String MENU_ASSET_READ = "MENU:ASSET:READ";
    private static final String STATUS_RECEIVED = "RECEIVED";
    private static final String STATUS_ADAPTED = "ADAPTED";
    private static final String STATUS_PENDING = "PENDING";
    private static final int RAW_EXCERPT_MAX = 2000;

    private final IngestBatchRepository ingestBatchRepository;
    private final IngestRecordRepository ingestRecordRepository;
    private final DeviceRepository deviceRepository;
    private final ObjectScopeService objectScopeService;
    private final ProtocolAdapterRegistry protocolAdapterRegistry;
    private final TsdbWriteService tsdbWriteService;
    private final DataQualityScoringService dataQualityScoringService;
    private final ObjectMapper objectMapper;

    public UnifiedIngestService(
            IngestBatchRepository ingestBatchRepository,
            IngestRecordRepository ingestRecordRepository,
            DeviceRepository deviceRepository,
            ObjectScopeService objectScopeService,
            ProtocolAdapterRegistry protocolAdapterRegistry,
            TsdbWriteService tsdbWriteService,
            DataQualityScoringService dataQualityScoringService,
            ObjectMapper objectMapper
    ) {
        this.ingestBatchRepository = ingestBatchRepository;
        this.ingestRecordRepository = ingestRecordRepository;
        this.deviceRepository = deviceRepository;
        this.objectScopeService = objectScopeService;
        this.protocolAdapterRegistry = protocolAdapterRegistry;
        this.tsdbWriteService = tsdbWriteService;
        this.dataQualityScoringService = dataQualityScoringService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UnifiedIngestBatchResponse ingestMetrics(UnifiedIngestBatchRequest request) {
        IngestProtocolType protocolType = parseProtocol(request.protocolType());
        List<UnifiedIngestMetricDto> normalizedMetrics = normalizeMetrics(
                protocolType,
                request.traceId(),
                request.isBackfill(),
                request.metrics()
        );
        validateDevices(normalizedMetrics, protocolType);
        PersistedBatch persistedBatch = persistBatch(
                protocolType,
                request.sourceType(),
                request.sourceKey(),
                request.traceId(),
                request.isBackfill(),
                STATUS_RECEIVED,
                normalizedMetrics,
                buildDirectRawExcerpt(request),
                null,
                null,
                null
        );
        TsdbWriteSummary summary = tsdbWriteService.writeBatch(persistedBatch.batch().getBatchId());
        return toResponse(
                persistedBatch.batch(),
                persistedBatch.records(),
                true,
                summary,
                dataQualityScoringService.summarizeBatch(persistedBatch.batch().getBatchId())
        );
    }

    @Transactional
    public ProtocolAdaptResponse adaptPayload(String protocolTypeValue, ProtocolAdaptRequest request) {
        IngestProtocolType protocolType = parseProtocol(protocolTypeValue);
        List<UnifiedIngestMetricDto> adaptedMetrics;
        try {
            adaptedMetrics = protocolAdapterRegistry.resolve(protocolType).adapt(request);
        } catch (AuthFlowException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw invalidPayload(ex.getMessage());
        }
        List<UnifiedIngestMetricDto> normalizedMetrics = normalizeMetrics(
                protocolType,
                request.traceId(),
                request.isBackfill(),
                adaptedMetrics
        );
        validateDevices(normalizedMetrics, protocolType);
        PersistedBatch persistedBatch = persistBatch(
                protocolType,
                request.sourceType(),
                request.sourceKey(),
                request.traceId(),
                request.isBackfill(),
                STATUS_ADAPTED,
                normalizedMetrics,
                safeExcerpt(request.payload()),
                null,
                null,
                null
        );
        TsdbWriteSummary summary = tsdbWriteService.writeBatch(persistedBatch.batch().getBatchId());
        UnifiedIngestBatchResponse batch = toResponse(
                persistedBatch.batch(),
                persistedBatch.records(),
                true,
                summary,
                dataQualityScoringService.summarizeBatch(persistedBatch.batch().getBatchId())
        );
        return new ProtocolAdaptResponse(protocolType.name(), normalizedMetrics.size(), normalizedMetrics, batch);
    }

    @Transactional
    public UnifiedIngestBatchResponse ingestBackfill(BackfillIngestRequest request) {
        if (!Boolean.TRUE.equals(request.isBackfill())) {
            throw new AuthFlowException(
                    ErrorCode.BACKFILL_PAYLOAD_INVALID,
                    HttpStatus.BAD_REQUEST,
                    "isBackfill must be true"
            );
        }
        IngestProtocolType protocolType = parseProtocol(request.protocolType());
        List<UnifiedIngestMetricDto> normalizedMetrics = normalizeMetrics(
                protocolType,
                request.traceId(),
                true,
                request.metrics()
        );
        validateDevices(normalizedMetrics, protocolType);
        validateBackfillKeys(request.batchNo(), request.seqNo(), normalizedMetrics);
        PersistedBatch persistedBatch = persistBatch(
                protocolType,
                request.sourceType(),
                request.sourceKey(),
                request.traceId(),
                true,
                STATUS_RECEIVED,
                normalizedMetrics,
                buildBackfillRawExcerpt(request),
                request.batchNo().trim(),
                request.seqNo(),
                request.originalSampleTime()
        );
        TsdbWriteSummary summary = tsdbWriteService.writeBatch(persistedBatch.batch().getBatchId());
        return toResponse(
                persistedBatch.batch(),
                persistedBatch.records(),
                true,
                summary,
                dataQualityScoringService.summarizeBatch(persistedBatch.batch().getBatchId())
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<UnifiedIngestBatchResponse> listBatches(AuthorizationContext context, int page, int pageSize) {
        List<IngestBatchEntity> batches = ingestBatchRepository.findAllByOrderByReceivedAtDesc();
        Map<String, List<IngestRecordEntity>> visibleRecords = loadVisibleRecords(context, batches.stream()
                .map(IngestBatchEntity::getBatchId)
                .toList());

        List<UnifiedIngestBatchResponse> items = batches.stream()
                .filter(batch -> !visibleRecords.getOrDefault(batch.getBatchId(), List.of()).isEmpty())
                .map(batch -> toResponse(
                        batch,
                        visibleRecords.getOrDefault(batch.getBatchId(), List.of()),
                        false,
                        tsdbWriteService.getSummaryForBatch(batch),
                        dataQualityScoringService.summarizeBatch(batch.getBatchId())
                ))
                .toList();

        return paginate(items, page, pageSize);
    }

    @Transactional(readOnly = true)
    public UnifiedIngestBatchResponse getBatchDetail(AuthorizationContext context, String batchId) {
        IngestBatchEntity batch = ingestBatchRepository.findById(batchId)
                .orElseThrow(() -> new AuthFlowException(
                        ErrorCode.INGEST_BATCH_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Ingest batch not found: " + batchId
                ));
        List<IngestRecordEntity> visibleRecords = loadVisibleRecords(context, List.of(batchId))
                .getOrDefault(batchId, List.of());
        if (visibleRecords.isEmpty()) {
            throw new AuthFlowException(ErrorCode.DATA_SCOPE_DENIED, HttpStatus.FORBIDDEN, ErrorCode.DATA_SCOPE_DENIED.defaultMessage());
        }
        return toResponse(
                batch,
                visibleRecords,
                true,
                tsdbWriteService.getSummaryForBatch(batch),
                dataQualityScoringService.summarizeBatch(batch.getBatchId())
        );
    }

    private PersistedBatch persistBatch(
            IngestProtocolType protocolType,
            String sourceType,
            String sourceKey,
            String traceId,
            boolean isBackfill,
            String status,
            List<UnifiedIngestMetricDto> metrics,
            String rawPayloadExcerpt,
            String batchNo,
            Long seqNo,
            LocalDateTime originalSampleTime
    ) {
        LocalDateTime now = LocalDateTime.now();
        IngestBatchEntity batch = ingestBatchRepository.save(new IngestBatchEntity(
                "INGB-" + UUID.randomUUID(),
                protocolType.name(),
                sourceType.trim(),
                sourceKey.trim(),
                traceId.trim(),
                metrics.size(),
                status,
                isBackfill,
                now,
                STATUS_PENDING,
                null,
                null,
                batchNo,
                seqNo,
                originalSampleTime
        ));

        List<IngestRecordEntity> records = new ArrayList<>();
        for (UnifiedIngestMetricDto metric : metrics) {
            records.add(new IngestRecordEntity(
                    "INGR-" + UUID.randomUUID(),
                    batch.getBatchId(),
                    metric.deviceId().trim(),
                    metric.metricCode().trim().toUpperCase(Locale.ROOT),
                    serialize(metric.value()),
                    metric.eventTime(),
                    metric.recvTime(),
                    metric.deviceTime(),
                    metric.traceId() == null ? traceId.trim() : metric.traceId().trim(),
                    Boolean.TRUE.equals(metric.isBackfill()),
                    protocolType.name(),
                    serialize(metric.attributes()),
                    rawPayloadExcerpt,
                    now
            ));
        }
        ingestRecordRepository.saveAll(records);
        return new PersistedBatch(batch, records);
    }

    private List<UnifiedIngestMetricDto> normalizeMetrics(
            IngestProtocolType protocolType,
            String traceId,
            boolean isBackfill,
            List<UnifiedIngestMetricDto> metrics
    ) {
        if (metrics == null || metrics.isEmpty()) {
            throw invalidPayload("metrics must not be empty");
        }
        return metrics.stream()
                .map(metric -> normalizeMetric(metric, protocolType, traceId, isBackfill))
                .toList();
    }

    private UnifiedIngestMetricDto normalizeMetric(
            UnifiedIngestMetricDto metric,
            IngestProtocolType protocolType,
            String traceId,
            boolean isBackfill
    ) {
        String metricProtocol = metric.protocolType();
        if (metricProtocol != null && !metricProtocol.isBlank()
                && !IngestProtocolType.normalize(metricProtocol).equals(protocolType.name())) {
            throw invalidPayload("metric protocolType does not match batch protocolType");
        }
        if (metric.recvTime().isBefore(metric.eventTime())) {
            throw invalidPayload("recvTime must be greater than or equal to eventTime");
        }
        return new UnifiedIngestMetricDto(
                metric.deviceId().trim(),
                protocolType.name(),
                metric.metricCode().trim().toUpperCase(Locale.ROOT),
                metric.value() == null ? NullNode.getInstance() : metric.value(),
                metric.eventTime(),
                metric.recvTime(),
                metric.deviceTime(),
                traceId.trim(),
                isBackfill,
                metric.attributes()
        );
    }

    private void validateDevices(List<UnifiedIngestMetricDto> metrics, IngestProtocolType protocolType) {
        Set<String> deviceIds = metrics.stream()
                .map(UnifiedIngestMetricDto::deviceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, DeviceEntity> devices = deviceRepository.findAllById(deviceIds).stream()
                .collect(Collectors.toMap(DeviceEntity::getDeviceId, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        List<String> missing = deviceIds.stream()
                .filter(deviceId -> !devices.containsKey(deviceId))
                .toList();
        if (!missing.isEmpty()) {
            throw new AuthFlowException(
                    ErrorCode.INGEST_DEVICE_NOT_FOUND,
                    HttpStatus.BAD_REQUEST,
                    "Ingest devices not found: " + String.join(",", missing)
            );
        }

        for (UnifiedIngestMetricDto metric : metrics) {
            DeviceEntity device = devices.get(metric.deviceId());
            String deviceProtocol = IngestProtocolType.normalize(device.getProtocolType());
            if (!deviceProtocol.isBlank() && !deviceProtocol.equals(protocolType.name())) {
                throw invalidPayload("device protocolType does not match ingest protocolType for " + metric.deviceId());
            }
        }
    }

    private Map<String, List<IngestRecordEntity>> loadVisibleRecords(AuthorizationContext context, Collection<String> batchIds) {
        if (batchIds == null || batchIds.isEmpty()) {
            return Map.of();
        }
        List<IngestRecordEntity> records = ingestRecordRepository.findByBatchIdIn(batchIds);
        if (records.isEmpty()) {
            return Map.of();
        }

        Set<String> accessibleDeviceIds = objectScopeService.filterAccessibleIds(
                context,
                ObjectScopeService.OBJECT_DEVICE,
                records.stream().map(IngestRecordEntity::getDeviceId).collect(Collectors.toCollection(LinkedHashSet::new)),
                MENU_ASSET_READ
        );

        return records.stream()
                .filter(record -> accessibleDeviceIds.contains(record.getDeviceId()))
                .sorted(Comparator.comparing(IngestRecordEntity::getEventTime).thenComparing(IngestRecordEntity::getRecordId))
                .collect(Collectors.groupingBy(IngestRecordEntity::getBatchId, LinkedHashMap::new, Collectors.toList()));
    }

    private UnifiedIngestBatchResponse toResponse(
            IngestBatchEntity batch,
            List<IngestRecordEntity> records,
            boolean includeRecords,
            TsdbWriteSummary tsdbWrite,
            DataQualityBatchSummary dqScore
    ) {
        List<UnifiedIngestMetricDto> metrics = includeRecords
                ? records.stream().map(this::toMetric).toList()
                : List.of();
        return new UnifiedIngestBatchResponse(
                batch.getBatchId(),
                batch.getProtocolType(),
                batch.getSourceType(),
                batch.getSourceKey(),
                batch.getTraceId(),
                batch.isBackfill(),
                batch.getStatus(),
                batch.getRecordCount() == null ? records.size() : batch.getRecordCount(),
                records.size(),
                batch.getReceivedAt(),
                metrics,
                tsdbWrite,
                dqScore
        );
    }

    private UnifiedIngestMetricDto toMetric(IngestRecordEntity entity) {
        return new UnifiedIngestMetricDto(
                entity.getDeviceId(),
                entity.getAdapterType(),
                entity.getMetricCode(),
                deserialize(entity.getMetricValue()),
                entity.getEventTime(),
                entity.getRecvTime(),
                entity.getDeviceTime(),
                entity.getTraceId(),
                entity.isBackfill(),
                deserialize(entity.getAttributesJson())
        );
    }

    private PageResponse<UnifiedIngestBatchResponse> paginate(List<UnifiedIngestBatchResponse> items, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = (safePage - 1) * safePageSize;
        if (fromIndex >= items.size()) {
            return PageResponse.of(List.of(), items.size(), safePage, safePageSize);
        }
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return PageResponse.of(items.subList(fromIndex, toIndex), items.size(), safePage, safePageSize);
    }

    private IngestProtocolType parseProtocol(String rawValue) {
        try {
            return IngestProtocolType.from(rawValue);
        } catch (IllegalArgumentException ex) {
            throw new AuthFlowException(
                    ErrorCode.INGEST_PROTOCOL_UNSUPPORTED,
                    HttpStatus.BAD_REQUEST,
                    "Unsupported ingest protocolType: " + rawValue
            );
        }
    }

    private AuthFlowException invalidPayload(String message) {
        return new AuthFlowException(ErrorCode.INGEST_PAYLOAD_INVALID, HttpStatus.BAD_REQUEST, message);
    }

    private String serialize(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize ingest payload fragment", ex);
        }
    }

    private JsonNode deserialize(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(rawValue);
        } catch (IOException ex) {
            return objectMapper.getNodeFactory().textNode(rawValue);
        }
    }

    private String safeExcerpt(JsonNode payload) {
        String serialized = serialize(payload);
        if (serialized == null) {
            return null;
        }
        return serialized.length() <= RAW_EXCERPT_MAX ? serialized : serialized.substring(0, RAW_EXCERPT_MAX);
    }

    private String buildDirectRawExcerpt(UnifiedIngestBatchRequest request) {
        try {
            return safeExcerpt(objectMapper.valueToTree(Map.of(
                    "sourceType", request.sourceType(),
                    "sourceKey", request.sourceKey(),
                    "traceId", request.traceId(),
                    "metricCount", request.metrics().size()
            )));
        } catch (IllegalArgumentException ex) {
            return safeExcerpt(objectMapper.createObjectNode().put("traceId", request.traceId()));
        }
    }

    private String buildBackfillRawExcerpt(BackfillIngestRequest request) {
        try {
            return safeExcerpt(objectMapper.valueToTree(Map.of(
                    "sourceType", request.sourceType(),
                    "sourceKey", request.sourceKey(),
                    "traceId", request.traceId(),
                    "batchNo", request.batchNo(),
                    "seqNo", request.seqNo(),
                    "originalSampleTime", request.originalSampleTime(),
                    "metricCount", request.metrics().size()
            )));
        } catch (IllegalArgumentException ex) {
            return safeExcerpt(objectMapper.createObjectNode().put("traceId", request.traceId()));
        }
    }

    private void validateBackfillKeys(String batchNo, Long seqNo, List<UnifiedIngestMetricDto> metrics) {
        Set<String> dedupeKeys = new LinkedHashSet<>();
        for (UnifiedIngestMetricDto metric : metrics) {
            String dedupeKey = metric.deviceId() + "|" + metric.metricCode() + "|" + batchNo.trim() + "|" + seqNo;
            if (!dedupeKeys.add(dedupeKey)) {
                throw new AuthFlowException(
                        ErrorCode.BACKFILL_DUPLICATE,
                        HttpStatus.CONFLICT,
                        "Duplicate backfill metric within request for " + metric.deviceId() + ":" + metric.metricCode()
                );
            }
        }
    }

    private record PersistedBatch(IngestBatchEntity batch, List<IngestRecordEntity> records) {
    }
}
