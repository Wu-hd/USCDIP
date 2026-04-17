package com.uscdip.backend.service;

import com.uscdip.backend.dto.TransitionActionRequest;
import com.uscdip.backend.dto.WorkflowTransitionResult;
import com.uscdip.backend.entity.IncidentEntity;
import com.uscdip.backend.model.IncidentStatus;
import com.uscdip.backend.repository.IncidentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class IncidentWorkflowService {

    private final IncidentRepository incidentRepository;
    private final StateMachineService stateMachineService;

    public IncidentWorkflowService(IncidentRepository incidentRepository, StateMachineService stateMachineService) {
        this.incidentRepository = incidentRepository;
        this.stateMachineService = stateMachineService;
    }

    public Optional<WorkflowTransitionResult> applyAction(String incidentId, TransitionActionRequest request) {
        return incidentRepository.findById(incidentId)
                .map(incident -> applyTransition(incident, request));
    }

    public Optional<Map<String, Object>> getAllowedActionSnapshot(String incidentId) {
        return incidentRepository.findById(incidentId)
                .map(incident -> {
                    IncidentStatus currentStatus = incident.getStatus() == null ? IncidentStatus.OPEN : incident.getStatus();
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("machineType", "incident");
                    payload.put("incidentId", incidentId);
                    payload.put("currentStatus", currentStatus.name());
                    payload.put("allowedActions", stateMachineService.getAllowedActions(StateMachineService.MACHINE_INCIDENT, currentStatus.name()));
                    return payload;
                });
    }

    private WorkflowTransitionResult applyTransition(IncidentEntity incident, TransitionActionRequest request) {
        IncidentStatus currentStatus = incident.getStatus() == null ? IncidentStatus.OPEN : incident.getStatus();
        String normalizedAction = stateMachineService.normalizeAction(request.getAction());
        String nextStatusName = stateMachineService.resolveNextStatus(
                StateMachineService.MACHINE_INCIDENT,
                currentStatus.name(),
                normalizedAction
        );
        IncidentStatus nextStatus = IncidentStatus.valueOf(nextStatusName);

        LocalDateTime now = LocalDateTime.now();
        if (incident.getCreatedAt() == null) {
            incident.setCreatedAt(now);
        }
        incident.setStatus(nextStatus);
        incident.setLastAction(normalizedAction);
        incident.setUpdatedAt(now);
        incidentRepository.save(incident);

        return WorkflowTransitionResult.builder()
                .machineType("incident")
                .entityId(incident.getIncidentId())
                .previousStatus(currentStatus.name())
                .action(normalizedAction)
                .currentStatus(nextStatus.name())
                .updatedAt(now)
                .allowedActions(stateMachineService.getAllowedActions(StateMachineService.MACHINE_INCIDENT, nextStatus.name()))
                .build();
    }
}
