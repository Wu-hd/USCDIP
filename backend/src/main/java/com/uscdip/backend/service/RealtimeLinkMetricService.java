package com.uscdip.backend.service;

import com.uscdip.backend.dto.RealtimeFieldSpecItem;
import com.uscdip.backend.dto.RealtimeLinkMetricView;
import com.uscdip.backend.entity.RealtimeLinkMetricEntity;
import com.uscdip.backend.repository.RealtimeLinkMetricRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RealtimeLinkMetricService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 200;

    private final RealtimeLinkMetricRepository realtimeLinkMetricRepository;

    public RealtimeLinkMetricService(RealtimeLinkMetricRepository realtimeLinkMetricRepository) {
        this.realtimeLinkMetricRepository = realtimeLinkMetricRepository;
    }

    public List<RealtimeFieldSpecItem> getFieldSpec() {
        return List.of(
                new RealtimeFieldSpecItem("trace_id", "string", true, "all", "全链路追踪主键，贯穿设备采样到地图渲染", "tr-20260411-0001"),
                new RealtimeFieldSpecItem("event_time", "datetime", true, "device_sampling", "设备采样时间，用于真实性和顺序判定", "2026-04-11T09:00:00"),
                new RealtimeFieldSpecItem("recv_time", "datetime", true, "edge_or_cloud_recv", "边缘或云端接收时间，用于链路延迟计算", "2026-04-11T09:00:01"),
                new RealtimeFieldSpecItem("is_backfill", "boolean", true, "ingestion", "标记是否为断网补偿数据", "false"),
                new RealtimeFieldSpecItem("last_ack_seq", "long", false, "frontend_ack", "前端最后确认序号，用于断连补发", "105"),
                new RealtimeFieldSpecItem("chain_stage", "string", true, "all", "链路阶段：device_sampling/edge_recv/cloud_ingest/alarm_decide/frontend_recv/map_render", "cloud_ingest")
        );
    }

    public Map<String, Object> listRecent(int limit) {
        int normalizedLimit = normalizeLimit(limit);
        List<RealtimeLinkMetricView> items = realtimeLinkMetricRepository
                .findAll(Sort.by(Sort.Direction.DESC, "eventTime"))
                .stream()
                .limit(normalizedLimit)
                .map(this::toView)
                .toList();

        long backfillCount = items.stream().filter(RealtimeLinkMetricView::getIsBackfill).count();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("limit", normalizedLimit);
        payload.put("total", items.size());
        payload.put("backfillCount", backfillCount);
        payload.put("items", items);
        return payload;
    }

    public Map<String, Object> getTraceTimeline(String traceId) {
        List<RealtimeLinkMetricView> timeline = realtimeLinkMetricRepository.findByTraceIdOrderByEventTimeAsc(traceId)
                .stream()
                .map(this::toView)
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("traceId", traceId);
        payload.put("total", timeline.size());
        payload.put("timeline", timeline);
        return payload;
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private RealtimeLinkMetricView toView(RealtimeLinkMetricEntity entity) {
        return RealtimeLinkMetricView.builder()
                .metricId(entity.getMetricId())
                .traceId(entity.getTraceId())
                .deviceId(entity.getDeviceId())
                .segmentId(entity.getSegmentId())
                .nodeId(entity.getNodeId())
                .chainStage(entity.getChainStage())
                .eventTime(entity.getEventTime())
                .recvTime(entity.getRecvTime())
                .isBackfill(entity.getIsBackfill())
                .lastAckSeq(entity.getLastAckSeq())
                .metricCode(entity.getMetricCode())
                .metricValue(entity.getMetricValue())
                .latencyMs(entity.getLatencyMs())
                .build();
    }
}
