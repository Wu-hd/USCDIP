package com.uscdip.backend.controller;

import com.uscdip.backend.dto.TransitionActionRequest;
import com.uscdip.backend.dto.WorkflowTransitionResult;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.service.WorkOrderWorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/work-orders")
public class WorkOrderWorkflowController {

    private final WorkOrderWorkflowService workOrderWorkflowService;

    public WorkOrderWorkflowController(WorkOrderWorkflowService workOrderWorkflowService) {
        this.workOrderWorkflowService = workOrderWorkflowService;
    }

    @PutMapping("/{workOrderId}/actions")
    public ResponseEntity<ApiResponse<WorkflowTransitionResult>> applyAction(
            @PathVariable String workOrderId,
            @Valid @RequestBody TransitionActionRequest request
    ) {
        try {
            return workOrderWorkflowService.applyAction(workOrderId, request)
                    .map(result -> ResponseEntity.ok(ApiResponse.success(result)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.failure("WORK_ORDER_NOT_FOUND", "Work order not found: " + workOrderId, null)));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.failure("INVALID_STATE_TRANSITION", ex.getMessage(), null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.failure("INVALID_ACTION", ex.getMessage(), null));
        }
    }

    @GetMapping("/{workOrderId}/allowed-actions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllowedActions(@PathVariable String workOrderId) {
        return workOrderWorkflowService.getAllowedActionSnapshot(workOrderId)
                .map(snapshot -> ResponseEntity.ok(ApiResponse.success(snapshot)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure("WORK_ORDER_NOT_FOUND", "Work order not found: " + workOrderId, null)));
    }
}
