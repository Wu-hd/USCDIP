package com.uscdip.backend.service;

import com.uscdip.backend.dto.ObjectChainResponse;
import com.uscdip.backend.dto.ObjectDictionaryItem;
import com.uscdip.backend.entity.FacilityEntity;
import com.uscdip.backend.entity.IncidentEntity;
import com.uscdip.backend.entity.NodeEntity;
import com.uscdip.backend.entity.SegmentEntity;
import com.uscdip.backend.entity.WorkOrderEntity;
import com.uscdip.backend.model.AuthorizationContext;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final ObjectScopeService objectScopeService;
    private final ObjectChainTraversalService objectChainTraversalService;

    public ObjectChainService(
            NodeRepository nodeRepository,
            SegmentRepository segmentRepository,
            FacilityRepository facilityRepository,
            DeviceRepository deviceRepository,
            IncidentRepository incidentRepository,
            WorkOrderRepository workOrderRepository,
            ModelResultRepository modelResultRepository,
            ObjectScopeService objectScopeService,
            ObjectChainTraversalService objectChainTraversalService
    ) {
        this.nodeRepository = nodeRepository;
        this.segmentRepository = segmentRepository;
        this.facilityRepository = facilityRepository;
        this.deviceRepository = deviceRepository;
        this.incidentRepository = incidentRepository;
        this.workOrderRepository = workOrderRepository;
        this.modelResultRepository = modelResultRepository;
        this.objectScopeService = objectScopeService;
        this.objectChainTraversalService = objectChainTraversalService;
    }

    public List<ObjectDictionaryItem> getObjectDictionary(AuthorizationContext authorizationContext) {
        List<ObjectDictionaryItem> allDefinitions = List.of(
                new ObjectDictionaryItem("node", "node_id", List.of(), "status"),
                new ObjectDictionaryItem("segment", "segment_id", List.of("start_node_id", "end_node_id"), "status"),
                new ObjectDictionaryItem("facility", "facility_id", List.of("segment_id", "node_id"), "status"),
                new ObjectDictionaryItem("device", "device_id", List.of("facility_id", "segment_id", "node_id"), "status"),
                new ObjectDictionaryItem("station", "station_id", List.of(), "status"),
                new ObjectDictionaryItem("incident", "incident_id", List.of("segment_id", "node_id"), "status"),
                new ObjectDictionaryItem("work_order", "work_order_id", List.of("incident_id", "segment_id", "node_id"), "status"),
                new ObjectDictionaryItem("model_result", "model_result_id", List.of("segment_id", "node_id"), "status")
        );
        Map<String, Long> accessibleCounts = objectScopeService.computeAccessibleCounts(authorizationContext);
        return allDefinitions.stream()
                .filter(item -> accessibleCounts.getOrDefault(item.getObjectType(), 0L) > 0)
                .toList();
    }

    public PageResponse<ObjectDictionaryItem> getObjectDictionaryPage(AuthorizationContext authorizationContext, int page, int pageSize) {
        List<ObjectDictionaryItem> dictionary = getObjectDictionary(authorizationContext);
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = (safePage - 1) * safePageSize;

        if (fromIndex >= dictionary.size()) {
            return PageResponse.of(Collections.emptyList(), dictionary.size(), safePage, safePageSize);
        }

        int toIndex = Math.min(fromIndex + safePageSize, dictionary.size());
        return PageResponse.of(dictionary.subList(fromIndex, toIndex), dictionary.size(), safePage, safePageSize);
    }

    public Optional<ObjectChainResponse> getBySegmentId(String segmentId, AuthorizationContext authorizationContext) {
        Optional<SegmentEntity> segment = segmentRepository.findById(segmentId);
        if (segment.isEmpty()) {
            return Optional.empty();
        }
        objectScopeService.requireAccess(authorizationContext, ObjectScopeService.OBJECT_SEGMENT, segmentId, "MENU:ASSET:READ");

        List<String> nodeIds = objectChainTraversalService.findNodeIdsForSegment(segmentId);
        if (nodeIds.isEmpty()) {
            nodeIds = List.of(segment.get().getStartNodeId(), segment.get().getEndNodeId());
        }
        List<NodeEntity> nodes = nodeRepository.findAllById(nodeIds);

        List<String> facilityIdsFromRelations = objectChainTraversalService.findFacilityIdsForSegment(segmentId);
        List<FacilityEntity> facilities = facilityIdsFromRelations.isEmpty()
                ? facilityRepository.findBySegmentId(segmentId)
                : facilityRepository.findAllById(facilityIdsFromRelations);
        List<String> facilityIds = facilities.stream().map(FacilityEntity::getFacilityId).toList();

        List<IncidentEntity> incidents = incidentRepository.findBySegmentId(segmentId);
        List<String> incidentIds = incidents.stream().map(IncidentEntity::getIncidentId).toList();
        List<WorkOrderEntity> workOrders = incidentIds.isEmpty() ? Collections.emptyList() : workOrderRepository.findByIncidentIdIn(incidentIds);
        List<String> deviceIds = objectChainTraversalService.findDeviceIdsForFacilityIds(facilityIds);

        ObjectChainResponse response = ObjectChainResponse.builder()
                .queryType("segment")
                .segmentId(segmentId)
                .nodes(filterNodes(authorizationContext, nodes))
                .segments(List.of(segment.get()))
                .facilities(filterFacilities(authorizationContext, facilities))
                .devices(filterDevices(
                        authorizationContext,
                        facilityIds.isEmpty()
                                ? Collections.emptyList()
                                : deviceIds.isEmpty()
                                ? deviceRepository.findByFacilityIdIn(facilityIds)
                                : deviceRepository.findAllById(deviceIds)
                ))
                .incidents(filterIncidents(authorizationContext, incidents))
                .workOrders(filterWorkOrders(authorizationContext, workOrders))
                .modelResults(filterModelResults(authorizationContext, modelResultRepository.findBySegmentId(segmentId)))
                .build();

        return Optional.of(response);
    }

    public Optional<ObjectChainResponse> getByNodeId(String nodeId, AuthorizationContext authorizationContext) {
        Optional<NodeEntity> node = nodeRepository.findById(nodeId);
        if (node.isEmpty()) {
            return Optional.empty();
        }
        objectScopeService.requireAccess(authorizationContext, ObjectScopeService.OBJECT_NODE, nodeId, "MENU:ASSET:READ");

        List<String> segmentIdsFromRelations = objectChainTraversalService.findSegmentIdsForNode(nodeId);
        List<SegmentEntity> segments = segmentIdsFromRelations.isEmpty()
                ? segmentRepository.findByStartNodeIdOrEndNodeId(nodeId, nodeId)
                : segmentRepository.findAllById(segmentIdsFromRelations);
        List<String> segmentIds = segments.stream().map(SegmentEntity::getSegmentId).collect(Collectors.toList());

        List<String> facilityIdsFromRelations = objectChainTraversalService.findFacilityIdsForNode(nodeId);
        List<FacilityEntity> facilities = facilityIdsFromRelations.isEmpty()
                ? facilityRepository.findByNodeId(nodeId)
                : facilityRepository.findAllById(facilityIdsFromRelations);
        if (facilities.isEmpty() && !segmentIds.isEmpty()) {
            Set<String> derivedFacilityIds = new LinkedHashSet<>();
            for (String segmentId : segmentIds) {
                derivedFacilityIds.addAll(objectChainTraversalService.findFacilityIdsForSegment(segmentId));
            }
            facilities = derivedFacilityIds.isEmpty()
                    ? segmentIds.stream()
                    .flatMap(id -> facilityRepository.findBySegmentId(id).stream())
                    .collect(Collectors.toList())
                    : facilityRepository.findAllById(derivedFacilityIds);
        }
        List<String> facilityIds = facilities.stream().map(FacilityEntity::getFacilityId).toList();
        List<String> deviceIds = objectChainTraversalService.findDeviceIdsForFacilityIds(facilityIds);

        List<IncidentEntity> incidents = incidentRepository.findByNodeId(nodeId);
        if (incidents.isEmpty() && !segmentIds.isEmpty()) {
            incidents = segmentIds.stream()
                    .flatMap(id -> incidentRepository.findBySegmentId(id).stream())
                    .collect(Collectors.toList());
        }
        List<String> incidentIds = incidents.stream().map(IncidentEntity::getIncidentId).toList();

        List<WorkOrderEntity> workOrders = incidentIds.isEmpty() ? Collections.emptyList() : workOrderRepository.findByIncidentIdIn(incidentIds);

        ObjectChainResponse response = ObjectChainResponse.builder()
                .queryType("node")
                .nodeId(nodeId)
                .nodes(List.of(node.get()))
                .segments(filterSegments(authorizationContext, segments))
                .facilities(filterFacilities(authorizationContext, facilities))
                .devices(filterDevices(
                        authorizationContext,
                        facilityIds.isEmpty()
                                ? Collections.emptyList()
                                : deviceIds.isEmpty()
                                ? deviceRepository.findByFacilityIdIn(facilityIds)
                                : deviceRepository.findAllById(deviceIds)
                ))
                .incidents(filterIncidents(authorizationContext, incidents))
                .workOrders(filterWorkOrders(authorizationContext, workOrders))
                .modelResults(filterModelResults(authorizationContext, segmentIds.isEmpty() ? modelResultRepository.findByNodeId(nodeId) : segmentIds.stream().flatMap(id -> modelResultRepository.findBySegmentId(id).stream()).toList()))
                .build();

        return Optional.of(response);
    }

    public Map<String, Long> getDictionaryCounts(AuthorizationContext authorizationContext) {
        return objectScopeService.computeAccessibleCounts(authorizationContext);
    }

    private List<NodeEntity> filterNodes(AuthorizationContext authorizationContext, List<NodeEntity> nodes) {
        Set<String> accessibleIds = objectScopeService.filterAccessibleIds(
                authorizationContext,
                ObjectScopeService.OBJECT_NODE,
                nodes.stream().map(NodeEntity::getNodeId).toList(),
                "MENU:ASSET:READ"
        );
        return nodes.stream().filter(node -> accessibleIds.contains(node.getNodeId())).toList();
    }

    private List<SegmentEntity> filterSegments(AuthorizationContext authorizationContext, List<SegmentEntity> segments) {
        Set<String> accessibleIds = objectScopeService.filterAccessibleIds(
                authorizationContext,
                ObjectScopeService.OBJECT_SEGMENT,
                segments.stream().map(SegmentEntity::getSegmentId).toList(),
                "MENU:ASSET:READ"
        );
        return segments.stream().filter(segment -> accessibleIds.contains(segment.getSegmentId())).toList();
    }

    private List<FacilityEntity> filterFacilities(AuthorizationContext authorizationContext, List<FacilityEntity> facilities) {
        Set<String> accessibleIds = objectScopeService.filterAccessibleIds(
                authorizationContext,
                ObjectScopeService.OBJECT_FACILITY,
                facilities.stream().map(FacilityEntity::getFacilityId).toList(),
                "MENU:ASSET:READ"
        );
        return facilities.stream().filter(facility -> accessibleIds.contains(facility.getFacilityId())).toList();
    }

    private List<com.uscdip.backend.entity.DeviceEntity> filterDevices(
            AuthorizationContext authorizationContext,
            List<com.uscdip.backend.entity.DeviceEntity> devices
    ) {
        Set<String> accessibleIds = objectScopeService.filterAccessibleIds(
                authorizationContext,
                ObjectScopeService.OBJECT_DEVICE,
                devices.stream().map(com.uscdip.backend.entity.DeviceEntity::getDeviceId).toList(),
                "MENU:ASSET:READ"
        );
        return devices.stream().filter(device -> accessibleIds.contains(device.getDeviceId())).toList();
    }

    private List<IncidentEntity> filterIncidents(AuthorizationContext authorizationContext, List<IncidentEntity> incidents) {
        Set<String> accessibleIds = objectScopeService.filterAccessibleIds(
                authorizationContext,
                ObjectScopeService.OBJECT_INCIDENT,
                incidents.stream().map(IncidentEntity::getIncidentId).toList(),
                "MENU:WORKORDER:READ"
        );
        return incidents.stream().filter(incident -> accessibleIds.contains(incident.getIncidentId())).toList();
    }

    private List<WorkOrderEntity> filterWorkOrders(AuthorizationContext authorizationContext, List<WorkOrderEntity> workOrders) {
        Set<String> accessibleIds = objectScopeService.filterAccessibleIds(
                authorizationContext,
                ObjectScopeService.OBJECT_WORK_ORDER,
                workOrders.stream().map(WorkOrderEntity::getWorkOrderId).toList(),
                "MENU:WORKORDER:READ"
        );
        return workOrders.stream().filter(workOrder -> accessibleIds.contains(workOrder.getWorkOrderId())).toList();
    }

    private List<com.uscdip.backend.entity.ModelResultEntity> filterModelResults(
            AuthorizationContext authorizationContext,
            List<com.uscdip.backend.entity.ModelResultEntity> modelResults
    ) {
        Set<String> accessibleIds = objectScopeService.filterAccessibleIds(
                authorizationContext,
                ObjectScopeService.OBJECT_MODEL_RESULT,
                modelResults.stream().map(com.uscdip.backend.entity.ModelResultEntity::getModelResultId).toList(),
                "MENU:MODEL:READ"
        );
        return modelResults.stream().filter(modelResult -> accessibleIds.contains(modelResult.getModelResultId())).toList();
    }
}
