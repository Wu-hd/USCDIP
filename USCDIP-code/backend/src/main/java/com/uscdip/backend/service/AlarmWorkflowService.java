package com.uscdip.backend.service;

import com.uscdip.backend.dto.TransitionActionRequest;
import com.uscdip.backend.dto.WorkflowTransitionResult;
import com.uscdip.backend.entity.AlarmEntity;
import com.uscdip.backend.model.AlarmStatus;
import com.uscdip.backend.repository.AlarmRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AlarmWorkflowService {

    private final AlarmRepository alarmRepository;
    private final StateMachineService stateMachineService;

    public AlarmWorkflowService(AlarmRepository alarmRepository, StateMachineService stateMachineService) {
        this.alarmRepository = alarmRepository;
        this.stateMachineService = stateMachineService;
    }

    public Optional<WorkflowTransitionResult> applyAction(String alarmId, TransitionActionRequest request) {
        return alarmRepository.findById(alarmId)
                .map(alarm -> applyTransition(alarm, request));
    }

    public Optional<Map<String, Object>> getAllowedActionSnapshot(String alarmId) {
        return alarmRepository.findById(alarmId)
                .map(alarm -> {
                    AlarmStatus currentStatus = alarm.getStatus() == null ? AlarmStatus.TRIGGERED : alarm.getStatus();
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("machineType", "alarm");
                    payload.put("alarmId", alarmId);
                    payload.put("currentStatus", currentStatus.name());
                    payload.put("allowedActions", stateMachineService.getAllowedActions(StateMachineService.MACHINE_ALARM, currentStatus.name()));
                    return payload;
                });
    }

    private WorkflowTransitionResult applyTransition(AlarmEntity alarm, TransitionActionRequest request) {
        AlarmStatus currentStatus = alarm.getStatus() == null ? AlarmStatus.TRIGGERED : alarm.getStatus();
        String normalizedAction = stateMachineService.normalizeAction(request.getAction());
        String nextStatusName = stateMachineService.resolveNextStatus(
                StateMachineService.MACHINE_ALARM,
                currentStatus.name(),
                normalizedAction
        );
        AlarmStatus nextStatus = AlarmStatus.valueOf(nextStatusName);

        LocalDateTime now = LocalDateTime.now();
        if (alarm.getCreatedAt() == null) {
            alarm.setCreatedAt(now);
        }
        alarm.setStatus(nextStatus);
        alarm.setLastAction(normalizedAction);
        alarm.setUpdatedAt(now);
        alarmRepository.save(alarm);

        return WorkflowTransitionResult.builder()
                .machineType("alarm")
                .entityId(alarm.getAlarmId())
                .previousStatus(currentStatus.name())
                .action(normalizedAction)
                .currentStatus(nextStatus.name())
                .updatedAt(now)
                .allowedActions(stateMachineService.getAllowedActions(StateMachineService.MACHINE_ALARM, nextStatus.name()))
                .build();
    }
}
