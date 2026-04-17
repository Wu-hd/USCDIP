package com.uscdip.backend.controller;

import com.uscdip.backend.dto.TransitionActionRequest;
import com.uscdip.backend.dto.WorkflowTransitionResult;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.service.AlarmWorkflowService;
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
@RequestMapping("/api/alarms")
public class AlarmWorkflowController {

    private final AlarmWorkflowService alarmWorkflowService;

    public AlarmWorkflowController(AlarmWorkflowService alarmWorkflowService) {
        this.alarmWorkflowService = alarmWorkflowService;
    }

    @PutMapping("/{alarmId}/actions")
    public ResponseEntity<ApiResponse<WorkflowTransitionResult>> applyAction(
            @PathVariable String alarmId,
            @Valid @RequestBody TransitionActionRequest request
    ) {
        try {
            return alarmWorkflowService.applyAction(alarmId, request)
                    .map(result -> ResponseEntity.ok(ApiResponse.success(result)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.failure("ALARM_NOT_FOUND", "Alarm not found: " + alarmId, null)));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.failure("INVALID_STATE_TRANSITION", ex.getMessage(), null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.failure("INVALID_ACTION", ex.getMessage(), null));
        }
    }

    @GetMapping("/{alarmId}/allowed-actions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllowedActions(@PathVariable String alarmId) {
        return alarmWorkflowService.getAllowedActionSnapshot(alarmId)
                .map(snapshot -> ResponseEntity.ok(ApiResponse.success(snapshot)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure("ALARM_NOT_FOUND", "Alarm not found: " + alarmId, null)));
    }
}
