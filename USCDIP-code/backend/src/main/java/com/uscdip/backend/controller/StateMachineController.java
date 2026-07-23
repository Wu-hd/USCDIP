package com.uscdip.backend.controller;

import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.StateTransitionRule;
import com.uscdip.backend.service.StateMachineService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/state-machines")
public class StateMachineController {

    private final StateMachineService stateMachineService;

    public StateMachineController(StateMachineService stateMachineService) {
        this.stateMachineService = stateMachineService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> getAllDefinitions() {
        return ApiResponse.success(stateMachineService.getDefinitions());
    }

    @GetMapping("/{machineType}/allowed/{status}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllowedTransitions(
            @PathVariable String machineType,
            @PathVariable String status
    ) {
        try {
            List<StateTransitionRule> rules = stateMachineService.getAllowedTransitions(machineType, status);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("machineType", machineType);
            payload.put("currentStatus", status);
            payload.put("allowedTransitions", rules);
            payload.put("allowedActions", rules.stream().map(StateTransitionRule::getAction).distinct().toList());
            return ResponseEntity.ok(ApiResponse.success(payload));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.failure("INVALID_STATE_MACHINE", ex.getMessage(), null));
        }
    }
}
