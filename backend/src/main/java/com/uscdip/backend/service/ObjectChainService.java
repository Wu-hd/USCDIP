package com.uscdip.backend.service;

import com.uscdip.backend.dto.ObjectChainResponse;
import com.uscdip.backend.dto.ObjectDictionaryItem;
import com.uscdip.backend.entity.FacilityEntity;
import com.uscdip.backend.entity.IncidentEntity;
import com.uscdip.backend.entity.NodeEntity;
import com.uscdip.backend.entity.SegmentEntity;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.repository.DeviceRepository;
import com.uscdip.backend.repository.FacilityRepository;
import com.uscdip.backend.repository.IncidentRepository;
import com.uscdip.backend.repository.ModelResultRepository;
import com.uscdip.backend.repository.NodeRepository;
import com.uscdip.backend.repository.SegmentRepository;
import com.uscdip.backend.repository.WorkOrderRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ObjectChainService {

    private final NodeRepository nodeRepository;
    private final SegmentRepository segmentRepository;
    private final FacilityRepository facilityRepository;
    private final DeviceRepository deviceRepository;
    private final IncidentRepository incidentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final ModelResultRepository modelResultRepository;

    public ObjectChainService(
            NodeRepository nodeRepository,
            SegmentRepository segmentRepository,
            FacilityRepository facilityRepository,
            DeviceRepository deviceRepository,
            IncidentRepository incidentRepository,
            WorkOrderRepository workOrderRepository,
            ModelResultRepository modelResultRepository
    ) {
        this.nodeRepository = nodeRepository;
        this.segmentRepository = segmentRepository;
        this.facilityRepository = facilityRepository;
        this.deviceRepository = deviceRepository;
        this.incidentRepository = incidentRepository;
        this.workOrderRepository = workOrderRepository;
        this.modelResultRepository = modelResultRepository;
    }

    public List<ObjectDictionaryItem> getObjectDictionary() {
        return List.of(
                new ObjectDictionaryItem("node", "node_id", List.of(), "status"),
                new ObjectDictionaryItem("segment", "segment_id", List.of("start_node_id", "end_node_id"), "status"),
                new ObjectDictionaryItem("facility", "facility_id", List.of("segment_id", "node_id"), "status"),
                new ObjectDictionaryItem("device", "device_id", List.of("facility_id", "segment_id", "node_id"), "status"),
                new ObjectDictionaryItem("incident", "incident_id", List.of("segment_id", "node_id"), "status"),
                new ObjectDictionaryItem("work_order", "work_order_id", List.of("incident_id", "segment_id", "node_id"), "status"),
                new ObjectDictionaryItem("model_result", "model_result_id", List.of("segment_id", "node_id"), "status")
        );
    }

    public PageResponse<ObjectDictionaryItem> getObjectDictionaryPage(int page, int pageSize) {
        List<ObjectDictionaryItem> dictionary = getObjectDictionary();
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = (safePage - 1) * safePageSize;

        if (fromIndex >= dictionary.size()) {
            return PageResponse.of(Collections.emptyList(), dictionary.size(), safePage, safePageSize);
        }

        int toIndex = Math.min(fromIndex + safePageSize, dictionary.size());
        return PageResponse.of(dictionary.subList(fromIndex, toIndex), dictionary.size(), safePage, safePageSize);
    }

    public Optional<ObjectChainResponse> getBySegmentId(String segmentId) {
        Optional<SegmentEntity> segment = segmentRepository.findById(segmentId);
        if (segment.isEmpty()) {
            return Optional.empty();
        }

        List<NodeEntity> nodes = nodeRepository.findAllById(List.of(segment.get().getStartNodeId(), segment.get().getEndNodeId()));
        List<FacilityEntity> facilities = facilityRepository.findBySegmentId(segmentId);
        List<String> facilityIds = facilities.stream().map(FacilityEntity::getFacilityId).toList();
        List<IncidentEntity> incidents = incidentRepository.findBySegmentId(segmentId);
        List<String> incidentIds = incidents.stream().map(IncidentEntity::getIncidentId).toList();

        ObjectChainResponse response = ObjectChainResponse.builder()
                .queryType("segment")
                .segmentId(segmentId)
                .nodes(nodes)
                .segments(List.of(segment.get()))
                .facilities(facilities)
                .devices(facilityIds.isEmpty() ? Collections.emptyList() : deviceRepository.findByFacilityIdIn(facilityIds))
                .incidents(incidents)
                .workOrders(incidentIds.isEmpty() ? Collections.emptyList() : workOrderRepository.findByIncidentIdIn(incidentIds))
                .modelResults(modelResultRepository.findBySegmentId(segmentId))
                .build();

        return Optional.of(response);
    }

    public Optional<ObjectChainResponse> getByNodeId(String nodeId) {
        Optional<NodeEntity> node = nodeRepository.findById(nodeId);
        if (node.isEmpty()) {
            return Optional.empty();
        }

        List<SegmentEntity> segments = segmentRepository.findByStartNodeIdOrEndNodeId(nodeId, nodeId);
        List<String> segmentIds = segments.stream().map(SegmentEntity::getSegmentId).collect(Collectors.toList());
        List<FacilityEntity> facilities = facilityRepository.findByNodeId(nodeId);
        if (facilities.isEmpty() && !segmentIds.isEmpty()) {
            facilities = segmentIds.stream()
                    .flatMap(id -> facilityRepository.findBySegmentId(id).stream())
                    .collect(Collectors.toList());
        }
        List<String> facilityIds = facilities.stream().map(FacilityEntity::getFacilityId).toList();

        List<IncidentEntity> incidents = incidentRepository.findByNodeId(nodeId);
        if (incidents.isEmpty() && !segmentIds.isEmpty()) {
            incidents = segmentIds.stream()
                    .flatMap(id -> incidentRepository.findBySegmentId(id).stream())
                    .collect(Collectors.toList());
        }
        List<String> incidentIds = incidents.stream().map(IncidentEntity::getIncidentId).toList();

        ObjectChainResponse response = ObjectChainResponse.builder()
                .queryType("node")
                .nodeId(nodeId)
                .nodes(List.of(node.get()))
                .segments(segments)
                .facilities(facilities)
                .devices(facilityIds.isEmpty() ? Collections.emptyList() : deviceRepository.findByFacilityIdIn(facilityIds))
                .incidents(incidents)
                .workOrders(incidentIds.isEmpty() ? Collections.emptyList() : workOrderRepository.findByIncidentIdIn(incidentIds))
                .modelResults(segmentIds.isEmpty() ? modelResultRepository.findByNodeId(nodeId) : segmentIds.stream().flatMap(id -> modelResultRepository.findBySegmentId(id).stream()).toList())
                .build();

        return Optional.of(response);
    }

    public Map<String, Long> getDictionaryCounts() {
        return Map.of(
                "node", nodeRepository.count(),
                "segment", segmentRepository.count(),
                "facility", facilityRepository.count(),
                "device", deviceRepository.count(),
                "incident", incidentRepository.count(),
                "work_order", workOrderRepository.count(),
                "model_result", modelResultRepository.count()
        );
    }
}
