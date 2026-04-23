package com.uscdip.backend.service;

import com.uscdip.backend.dto.WorkOrderCloseRequest;
import com.uscdip.backend.dto.WorkOrderCompleteRequest;
import com.uscdip.backend.dto.WorkOrderCreateRequest;
import com.uscdip.backend.dto.WorkOrderDispatchRequest;
import com.uscdip.backend.dto.WorkOrderResponse;
import com.uscdip.backend.dto.WorkOrderWritebackRequest;
import com.uscdip.backend.entity.IncidentEntity;
import com.uscdip.backend.entity.ObjectScopeBindingEntity;
import com.uscdip.backend.entity.UserAccountEntity;
import com.uscdip.backend.entity.WorkOrderEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.model.WorkOrderStatus;
import com.uscdip.backend.repository.IncidentRepository;
import com.uscdip.backend.repository.ObjectScopeBindingRepository;
import com.uscdip.backend.repository.UserAccountRepository;
import com.uscdip.backend.repository.WorkOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkOrderService {

    private static final String MENU_WORKORDER_READ = "MENU:WORKORDER:READ";
    private static final String MENU_WORKORDER_DISPATCH = "MENU:WORKORDER:DISPATCH";
    private static final String DEFAULT_TYPE = "INCIDENT_RESPONSE";
    private static final String DEFAULT_PRIORITY = "MEDIUM";
    private static final Set<String> MANUAL_CLOSE_ALLOWED = Set.of(
            WorkOrderStatus.CREATED.name(),
            WorkOrderStatus.DISPATCHED.name(),
            WorkOrderStatus.ACCEPTED.name(),
            WorkOrderStatus.COMPLETED.name()
    );

    private final WorkOrderRepository workOrderRepository;
    private final IncidentRepository incidentRepository;
    private final ObjectScopeBindingRepository objectScopeBindingRepository;
    private final ObjectScopeService objectScopeService;
    private final AuthorizationService authorizationService;
    private final UserAccountRepository userAccountRepository;
    private final OutboxService outboxService;

    public WorkOrderService(
            WorkOrderRepository workOrderRepository,
            IncidentRepository incidentRepository,
            ObjectScopeBindingRepository objectScopeBindingRepository,
            ObjectScopeService objectScopeService,
            AuthorizationService authorizationService,
            UserAccountRepository userAccountRepository,
            OutboxService outboxService
    ) {
        this.workOrderRepository = workOrderRepository;
        this.incidentRepository = incidentRepository;
        this.objectScopeBindingRepository = objectScopeBindingRepository;
        this.objectScopeService = objectScopeService;
        this.authorizationService = authorizationService;
        this.userAccountRepository = userAccountRepository;
        this.outboxService = outboxService;
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkOrderResponse> listWorkOrders(
            AuthorizationContext context,
            int page,
            int pageSize,
            String status,
            String incidentId,
            String assignee,
            String workOrderType
    ) {
        List<WorkOrderEntity> visible = filterVisible(context, workOrderRepository.findAllByOrderByUpdatedAtDescWorkOrderIdDesc()).stream()
                .filter(workOrder -> matches(workOrder.getStatus(), status))
                .filter(workOrder -> matches(workOrder.getIncidentId(), incidentId))
                .filter(workOrder -> matches(workOrder.getAssignee(), assignee) || matches(workOrder.getAssigneeUserId(), assignee))
                .filter(workOrder -> matches(workOrder.getWorkOrderType(), workOrderType))
                .toList();
        return paginate(visible.stream().map(this::toResponse).toList(), page, pageSize);
    }

    @Transactional(readOnly = true)
    public WorkOrderResponse getWorkOrderDetail(AuthorizationContext context, String workOrderId) {
        WorkOrderEntity workOrder = requireWorkOrder(workOrderId);
        objectScopeService.requireAccess(context, ObjectScopeService.OBJECT_WORK_ORDER, workOrder.getWorkOrderId(), MENU_WORKORDER_READ);
        return toResponse(workOrder);
    }

    @Transactional
    public WorkOrderResponse createWorkOrder(AuthorizationContext context, WorkOrderCreateRequest request) {
        IncidentEntity incident = requireIncident(request.incidentId());
        objectScopeService.requireAccess(context, ObjectScopeService.OBJECT_INCIDENT, incident.getIncidentId(), MENU_WORKORDER_DISPATCH);
        ObjectScopeBindingEntity incidentBinding = requireBinding(ObjectScopeService.OBJECT_INCIDENT, incident.getIncidentId());
        LocalDateTime now = LocalDateTime.now();

        AssigneeSnapshot assignee = resolveAssignee(request.assigneeUserId(), request.assignee());
        WorkOrderEntity workOrder = new WorkOrderEntity();
        workOrder.setWorkOrderId("WO-" + UUID.randomUUID());
        workOrder.setIncidentId(incident.getIncidentId());
        workOrder.setSegmentId(incident.getSegmentId());
        workOrder.setNodeId(incident.getNodeId());
        workOrder.setWorkOrderType(defaultString(normalize(request.workOrderType()), DEFAULT_TYPE));
        workOrder.setPriority(defaultString(normalize(request.priority()), DEFAULT_PRIORITY));
        workOrder.setDescription(blankToNull(request.description()));
        workOrder.setAssigneeUserId(assignee.userId());
        workOrder.setAssignee(assignee.username());
        workOrder.setSlaDueAt(request.slaDueAt());
        workOrder.setStatus(WorkOrderStatus.CREATED.name());
        workOrder.setCreatedBy(context.userId());
        workOrder.setCreatedAt(now);
        workOrder.setUpdatedAt(now);
        workOrder.setVersionNo(1L);
        workOrderRepository.save(workOrder);
        syncWorkOrderScope(workOrder, incidentBinding, now);
        outboxService.publishWorkOrderEvent(workOrder, OutboxService.EVENT_WORK_ORDER_CREATED, now);
        return toResponse(workOrder);
    }

    @Transactional
    public WorkOrderResponse dispatchWorkOrder(AuthorizationContext context, String workOrderId, WorkOrderDispatchRequest request) {
        WorkOrderEntity workOrder = requireAccessibleForDispatch(context, workOrderId);
        requireStatus(workOrder, WorkOrderStatus.CREATED.name());
        LocalDateTime now = LocalDateTime.now();
        applyDispatch(workOrder, request, context.userId(), now);
        workOrderRepository.save(workOrder);
        syncScopeFromAssignee(workOrder, now);
        outboxService.publishWorkOrderEvent(workOrder, OutboxService.EVENT_WORK_ORDER_DISPATCHED, now);
        return toResponse(workOrder);
    }

    @Transactional
    public WorkOrderResponse acceptWorkOrder(AuthorizationContext context, String workOrderId) {
        WorkOrderEntity workOrder = requireWorkOrder(workOrderId);
        objectScopeService.requireAccess(context, ObjectScopeService.OBJECT_WORK_ORDER, workOrder.getWorkOrderId(), MENU_WORKORDER_READ);
        requireAssigneeOrDispatchPermission(context, workOrder);
        requireStatus(workOrder, WorkOrderStatus.DISPATCHED.name());
        LocalDateTime now = LocalDateTime.now();
        workOrder.setStatus(WorkOrderStatus.ACCEPTED.name());
        workOrder.setAcceptedBy(context.userId());
        workOrder.setAcceptedAt(now);
        touch(workOrder, now);
        workOrderRepository.save(workOrder);
        outboxService.publishWorkOrderEvent(workOrder, OutboxService.EVENT_WORK_ORDER_ACCEPTED, now);
        return toResponse(workOrder);
    }

    @Transactional
    public WorkOrderResponse transferWorkOrder(AuthorizationContext context, String workOrderId, WorkOrderDispatchRequest request) {
        WorkOrderEntity workOrder = requireAccessibleForDispatch(context, workOrderId);
        if (!WorkOrderStatus.DISPATCHED.name().equals(normalize(workOrder.getStatus()))
                && !WorkOrderStatus.ACCEPTED.name().equals(normalize(workOrder.getStatus()))) {
            throw invalidState("Only DISPATCHED or ACCEPTED work orders can be transferred");
        }
        LocalDateTime now = LocalDateTime.now();
        applyDispatch(workOrder, request, context.userId(), now);
        workOrder.setAcceptedBy(null);
        workOrder.setAcceptedAt(null);
        workOrder.setCompletedBy(null);
        workOrder.setCompletedAt(null);
        workOrder.setCompletionSummary(null);
        workOrderRepository.save(workOrder);
        syncScopeFromAssignee(workOrder, now);
        outboxService.publishWorkOrderEvent(workOrder, OutboxService.EVENT_WORK_ORDER_TRANSFERRED, now);
        return toResponse(workOrder);
    }

    @Transactional
    public WorkOrderResponse completeWorkOrder(AuthorizationContext context, String workOrderId, WorkOrderCompleteRequest request) {
        WorkOrderEntity workOrder = requireWorkOrder(workOrderId);
        objectScopeService.requireAccess(context, ObjectScopeService.OBJECT_WORK_ORDER, workOrder.getWorkOrderId(), MENU_WORKORDER_READ);
        requireAssigneeOrDispatchPermission(context, workOrder);
        requireStatus(workOrder, WorkOrderStatus.ACCEPTED.name());
        LocalDateTime now = LocalDateTime.now();
        workOrder.setStatus(WorkOrderStatus.COMPLETED.name());
        workOrder.setCompletedBy(context.userId());
        workOrder.setCompletedAt(now);
        workOrder.setCompletionSummary(request.completionSummary().trim());
        touch(workOrder, now);
        workOrderRepository.save(workOrder);
        outboxService.publishWorkOrderEvent(workOrder, OutboxService.EVENT_WORK_ORDER_COMPLETED, now);
        return toResponse(workOrder);
    }

    @Transactional
    public WorkOrderResponse closeWorkOrder(AuthorizationContext context, String workOrderId, WorkOrderCloseRequest request) {
        WorkOrderEntity workOrder = requireAccessibleForDispatch(context, workOrderId);
        if (!MANUAL_CLOSE_ALLOWED.contains(normalize(workOrder.getStatus()))) {
            throw invalidState("Work order cannot be closed from status: " + workOrder.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        workOrder.setStatus(WorkOrderStatus.CLOSED.name());
        workOrder.setClosedBy(context.userId());
        workOrder.setClosedAt(now);
        workOrder.setCloseReason(request.closeReason().trim());
        touch(workOrder, now);
        workOrderRepository.save(workOrder);
        outboxService.publishWorkOrderEvent(workOrder, OutboxService.EVENT_WORK_ORDER_CLOSED, now);
        return toResponse(workOrder);
    }

    @Transactional
    public WorkOrderResponse writebackWorkOrder(AuthorizationContext context, String workOrderId, WorkOrderWritebackRequest request) {
        WorkOrderEntity workOrder = requireAccessibleForDispatch(context, workOrderId);
        LocalDateTime now = LocalDateTime.now();
        workOrder.setWritebackType(normalize(request.writebackType()));
        workOrder.setWritebackReason(request.writebackReason().trim());
        workOrder.setWritebackAt(now);
        touch(workOrder, now);
        workOrderRepository.save(workOrder);
        outboxService.publishWorkOrderEvent(workOrder, OutboxService.EVENT_WORK_ORDER_WRITEBACK_RECORDED, now);
        return toResponse(workOrder);
    }

    private WorkOrderEntity requireAccessibleForDispatch(AuthorizationContext context, String workOrderId) {
        WorkOrderEntity workOrder = requireWorkOrder(workOrderId);
        objectScopeService.requireAccess(context, ObjectScopeService.OBJECT_WORK_ORDER, workOrder.getWorkOrderId(), MENU_WORKORDER_DISPATCH);
        return workOrder;
    }

    private WorkOrderEntity requireWorkOrder(String workOrderId) {
        return workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new AuthFlowException(
                        ErrorCode.WORK_ORDER_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Work order not found: " + workOrderId
                ));
    }

    private IncidentEntity requireIncident(String incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new AuthFlowException(
                        ErrorCode.INCIDENT_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Incident not found: " + incidentId
                ));
    }

    private ObjectScopeBindingEntity requireBinding(String objectType, String objectId) {
        return objectScopeBindingRepository.findByObjectTypeAndObjectId(objectType, objectId)
                .orElseThrow(() -> new AuthFlowException(
                        ErrorCode.DATA_SCOPE_DENIED,
                        HttpStatus.FORBIDDEN,
                        "Object scope binding is missing for " + objectType + ":" + objectId
                ));
    }

    private void applyDispatch(WorkOrderEntity workOrder, WorkOrderDispatchRequest request, String operatorId, LocalDateTime now) {
        AssigneeSnapshot assignee = resolveAssignee(request.assigneeUserId(), request.assignee());
        workOrder.setAssigneeUserId(assignee.userId());
        workOrder.setAssignee(assignee.username());
        workOrder.setSlaDueAt(request.slaDueAt());
        workOrder.setStatus(WorkOrderStatus.DISPATCHED.name());
        workOrder.setDispatchedBy(operatorId);
        workOrder.setDispatchedAt(now);
        touch(workOrder, now);
    }

    private void syncWorkOrderScope(WorkOrderEntity workOrder, ObjectScopeBindingEntity sourceBinding, LocalDateTime now) {
        ObjectScopeBindingEntity binding = objectScopeBindingRepository.findByObjectTypeAndObjectId(
                        ObjectScopeService.OBJECT_WORK_ORDER,
                        workOrder.getWorkOrderId()
                )
                .orElseGet(() -> new ObjectScopeBindingEntity(
                        "OSB-WO-" + UUID.randomUUID(),
                        ObjectScopeService.OBJECT_WORK_ORDER,
                        workOrder.getWorkOrderId(),
                        null,
                        null,
                        null,
                        ObjectScopeService.LEVEL_DETAIL,
                        now,
                        now
                ));
        binding.setRegionId(sourceBinding.getRegionId());
        binding.setOwnerUserId(defaultString(workOrder.getAssigneeUserId(), sourceBinding.getOwnerUserId()));
        binding.setOwnerUsername(defaultString(workOrder.getAssignee(), sourceBinding.getOwnerUsername()));
        binding.setScopeLevel(sourceBinding.getScopeLevel());
        binding.setUpdatedAt(now);
        objectScopeBindingRepository.save(binding);
    }

    private void syncScopeFromAssignee(WorkOrderEntity workOrder, LocalDateTime now) {
        ObjectScopeBindingEntity current = requireBinding(ObjectScopeService.OBJECT_WORK_ORDER, workOrder.getWorkOrderId());
        current.setOwnerUserId(blankToNull(workOrder.getAssigneeUserId()));
        current.setOwnerUsername(blankToNull(workOrder.getAssignee()));
        current.setUpdatedAt(now);
        objectScopeBindingRepository.save(current);
    }

    private void requireAssigneeOrDispatchPermission(AuthorizationContext context, WorkOrderEntity workOrder) {
        if (isCurrentAssignee(context, workOrder) || authorizationService.hasPermission(context, MENU_WORKORDER_DISPATCH)) {
            return;
        }
        throw new AuthFlowException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Current user is not the work order assignee");
    }

    private boolean isCurrentAssignee(AuthorizationContext context, WorkOrderEntity workOrder) {
        return equalsIgnoreCase(context.userId(), workOrder.getAssigneeUserId())
                || equalsIgnoreCase(context.username(), workOrder.getAssignee());
    }

    private void requireStatus(WorkOrderEntity workOrder, String expectedStatus) {
        if (!expectedStatus.equals(normalize(workOrder.getStatus()))) {
            throw invalidState("Expected " + expectedStatus + " but was " + workOrder.getStatus());
        }
    }

    private void touch(WorkOrderEntity workOrder, LocalDateTime now) {
        workOrder.setUpdatedAt(now);
        workOrder.setVersionNo(safeVersion(workOrder.getVersionNo()) + 1L);
    }

    private List<WorkOrderEntity> filterVisible(AuthorizationContext context, List<WorkOrderEntity> workOrders) {
        if (workOrders.isEmpty()) {
            return List.of();
        }
        Set<String> accessibleIds = objectScopeService.filterAccessibleIds(
                context,
                ObjectScopeService.OBJECT_WORK_ORDER,
                workOrders.stream().map(WorkOrderEntity::getWorkOrderId).collect(Collectors.toCollection(LinkedHashSet::new)),
                MENU_WORKORDER_READ
        );
        return workOrders.stream().filter(workOrder -> accessibleIds.contains(workOrder.getWorkOrderId())).toList();
    }

    private AssigneeSnapshot resolveAssignee(String assigneeUserId, String assignee) {
        String userId = blankToNull(assigneeUserId);
        String username = blankToNull(assignee);
        if (userId != null) {
            UserAccountEntity user = userAccountRepository.findById(userId)
                    .orElseThrow(() -> new AuthFlowException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found: " + userId));
            return new AssigneeSnapshot(user.getUserId(), defaultString(username, user.getUsername()));
        }
        return new AssigneeSnapshot(null, username);
    }

    private PageResponse<WorkOrderResponse> paginate(List<WorkOrderResponse> items, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = (safePage - 1) * safePageSize;
        if (fromIndex >= items.size()) {
            return PageResponse.of(List.of(), items.size(), safePage, safePageSize);
        }
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return PageResponse.of(items.subList(fromIndex, toIndex), items.size(), safePage, safePageSize);
    }

    private WorkOrderResponse toResponse(WorkOrderEntity workOrder) {
        return new WorkOrderResponse(
                workOrder.getWorkOrderId(),
                workOrder.getIncidentId(),
                workOrder.getSegmentId(),
                workOrder.getNodeId(),
                workOrder.getWorkOrderType(),
                workOrder.getPriority(),
                workOrder.getDescription(),
                workOrder.getAssigneeUserId(),
                workOrder.getAssignee(),
                workOrder.getStatus(),
                workOrder.getSlaDueAt(),
                workOrder.getCreatedBy(),
                workOrder.getDispatchedBy(),
                workOrder.getAcceptedBy(),
                workOrder.getCompletedBy(),
                workOrder.getClosedBy(),
                workOrder.getDispatchedAt(),
                workOrder.getAcceptedAt(),
                workOrder.getCompletedAt(),
                workOrder.getClosedAt(),
                workOrder.getCompletionSummary(),
                workOrder.getCloseReason(),
                workOrder.getWritebackType(),
                workOrder.getWritebackReason(),
                workOrder.getWritebackAt(),
                workOrder.getCreatedAt(),
                workOrder.getUpdatedAt(),
                workOrder.getVersionNo()
        );
    }

    private boolean matches(String actual, String expected) {
        return expected == null || expected.isBlank() || (actual != null && actual.equalsIgnoreCase(expected.trim()));
    }

    private long safeVersion(Long versionNo) {
        return versionNo == null ? 0L : versionNo;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private AuthFlowException invalidState(String message) {
        return new AuthFlowException(ErrorCode.WORK_ORDER_INVALID_STATE, HttpStatus.CONFLICT, message);
    }

    private record AssigneeSnapshot(String userId, String username) {
    }
}
