package com.uscdip.backend.service;

import com.uscdip.backend.dto.TransitionActionRequest;
import com.uscdip.backend.dto.WorkflowTransitionResult;
import com.uscdip.backend.entity.WorkOrderEntity;
import com.uscdip.backend.model.WorkOrderStatus;
import com.uscdip.backend.repository.WorkOrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class WorkOrderWorkflowService {

    private final WorkOrderRepository workOrderRepository;
    private final StateMachineService stateMachineService;

    public WorkOrderWorkflowService(WorkOrderRepository workOrderRepository, StateMachineService stateMachineService) {
        this.workOrderRepository = workOrderRepository;
        this.stateMachineService = stateMachineService;
    }

    public Optional<WorkflowTransitionResult> applyAction(String workOrderId, TransitionActionRequest request) {
        return workOrderRepository.findById(workOrderId)
                .map(workOrder -> applyTransition(workOrder, request));
    }

    public Optional<Map<String, Object>> getAllowedActionSnapshot(String workOrderId) {
        return workOrderRepository.findById(workOrderId)
                .map(workOrder -> {
                    String currentStatus = normalizeStatus(workOrder.getStatus());
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("machineType", "work_order");
                    payload.put("workOrderId", workOrderId);
                    payload.put("currentStatus", currentStatus);
                    payload.put("allowedActions", stateMachineService.getAllowedActions(StateMachineService.MACHINE_WORK_ORDER, currentStatus));
                    return payload;
                });
    }

    private WorkflowTransitionResult applyTransition(WorkOrderEntity workOrder, TransitionActionRequest request) {
        String currentStatus = normalizeStatus(workOrder.getStatus());
        String normalizedAction = stateMachineService.normalizeAction(request.getAction());
        String nextStatusName = stateMachineService.resolveNextStatus(
                StateMachineService.MACHINE_WORK_ORDER,
                currentStatus,
                normalizedAction
        );

        LocalDateTime now = LocalDateTime.now();
        if (workOrder.getCreatedAt() == null) {
            workOrder.setCreatedAt(now);
        }
        if ("FALSE_REPORT_WRITEBACK".equals(normalizedAction)) {
            workOrder.setFeedbackType(request.getFeedbackType());
            workOrder.setFeedbackReason(request.getFeedbackReason());
        }
        workOrder.setStatus(nextStatusName);
        workOrder.setLastAction(normalizedAction);
        workOrder.setUpdatedAt(now);
        workOrderRepository.save(workOrder);

        return WorkflowTransitionResult.builder()
                .machineType("work_order")
                .entityId(workOrder.getWorkOrderId())
                .previousStatus(currentStatus)
                .action(normalizedAction)
                .currentStatus(nextStatusName)
                .updatedAt(now)
                .allowedActions(stateMachineService.getAllowedActions(StateMachineService.MACHINE_WORK_ORDER, nextStatusName))
                .build();
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return WorkOrderStatus.CREATED.name();
        }
        return status.trim().toUpperCase();
    }
}
