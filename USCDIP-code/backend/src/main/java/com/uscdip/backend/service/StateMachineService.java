package com.uscdip.backend.service;

import com.uscdip.backend.model.AlarmStatus;
import com.uscdip.backend.model.IncidentStatus;
import com.uscdip.backend.model.StateTransitionRule;
import com.uscdip.backend.model.WorkOrderStatus;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StateMachineService {

    public static final String MACHINE_ALARM = "ALARM";
    public static final String MACHINE_INCIDENT = "INCIDENT";
    public static final String MACHINE_WORK_ORDER = "WORK_ORDER";

    private final Map<String, List<StateTransitionRule>> ruleRegistry;

    public StateMachineService() {
        this.ruleRegistry = Map.of(
                MACHINE_ALARM,
                List.of(
                        rule("TRIGGERED", "DEDUPE", "DEDUPED", "同一对象同一规则同一时间窗内告警去重"),
                        rule("TRIGGERED", "SUPPRESS", "SUPPRESSED", "命中抑制窗口的告警进入抑制态"),
                        rule("TRIGGERED", "ESCALATE", "ESCALATED", "高风险告警直接升级"),
                        rule("DEDUPED", "ESCALATE", "ESCALATED", "去重后仍可人工升级"),
                        rule("SUPPRESSED", "ESCALATE", "ESCALATED", "抑制态告警可人工升级"),
                        rule("DEDUPED", "CLOSE", "CLOSED", "去重告警可直接关闭"),
                        rule("SUPPRESSED", "CLOSE", "CLOSED", "抑制告警可直接关闭"),
                        rule("ESCALATED", "CLOSE", "CLOSED", "已升级告警完成处置后关闭")
                ),
                MACHINE_INCIDENT,
                List.of(
                        rule("OPEN", "DISPATCH", "DISPATCH_PENDING", "事件进入派单准备"),
                        rule("OPEN", "CLOSE", "CLOSED", "无需派单时可直接关闭事件"),
                        rule("DISPATCH_PENDING", "START_HANDLE", "IN_PROGRESS", "工单受理后开始处置"),
                        rule("IN_PROGRESS", "RESOLVE", "RESOLVED", "处置完成，等待结案"),
                        rule("IN_PROGRESS", "FALSE_REPORT_WRITEBACK", "FALSE_REPORT_WRITTEN", "误报回写独立建模，不等同关闭"),
                        rule("RESOLVED", "CLOSE", "CLOSED", "复核通过后关闭事件"),
                        rule("FALSE_REPORT_WRITTEN", "CLOSE", "CLOSED", "误报回写完成后关闭事件")
                ),
                MACHINE_WORK_ORDER,
                List.of(
                        rule("CREATED", "DISPATCH", "DISPATCHED", "创建后进入已派单"),
                        rule("DISPATCHED", "ACCEPT", "ACCEPTED", "受理后进入处理中"),
                        rule("DISPATCHED", "TRANSFER", "DISPATCHED", "转派保持已派单状态"),
                        rule("ACCEPTED", "TRANSFER", "DISPATCHED", "处理中转派回已派单"),
                        rule("ACCEPTED", "COMPLETE", "COMPLETED", "处理完成进入待关闭"),
                        rule("DISPATCHED", "FALSE_REPORT_WRITEBACK", "FALSE_REPORT_WRITTEN", "派单后可误报回写"),
                        rule("ACCEPTED", "FALSE_REPORT_WRITEBACK", "FALSE_REPORT_WRITTEN", "处理过程中可误报回写"),
                        rule("COMPLETED", "CLOSE", "CLOSED", "验收后关闭工单"),
                        rule("FALSE_REPORT_WRITTEN", "CLOSE", "CLOSED", "回写完成后关闭工单")
                )
        );
    }

    public Map<String, Object> getDefinitions() {
        Map<String, Object> definitions = new LinkedHashMap<>();
        definitions.put("alarm", toMachineDefinition(MACHINE_ALARM));
        definitions.put("incident", toMachineDefinition(MACHINE_INCIDENT));
        definitions.put("work_order", toMachineDefinition(MACHINE_WORK_ORDER));
        return definitions;
    }

    public List<StateTransitionRule> getAllowedTransitions(String machineType, String currentStatus) {
        String normalizedMachine = normalizeMachineType(machineType);
        String normalizedStatus = normalizeStatus(currentStatus);
        return ruleRegistry.get(normalizedMachine)
                .stream()
                .filter(rule -> rule.getFrom().equals(normalizedStatus))
                .toList();
    }

    public List<String> getAllowedActions(String machineType, String currentStatus) {
        return getAllowedTransitions(machineType, currentStatus)
                .stream()
                .map(StateTransitionRule::getAction)
                .distinct()
                .toList();
    }

    public String resolveNextStatus(String machineType, String currentStatus, String action) {
        String normalizedAction = normalizeAction(action);
        return getAllowedTransitions(machineType, currentStatus)
                .stream()
                .filter(rule -> rule.getAction().equals(normalizedAction))
                .findFirst()
                .map(StateTransitionRule::getTo)
                .orElseThrow(() -> new IllegalStateException("Invalid transition, no rule for current status and action"));
    }

    public String normalizeMachineType(String machineType) {
        String normalized = normalizeToken(machineType);
        if ("WORKORDER".equals(normalized)) {
            return MACHINE_WORK_ORDER;
        }
        if (MACHINE_ALARM.equals(normalized) || MACHINE_INCIDENT.equals(normalized) || MACHINE_WORK_ORDER.equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Unsupported state machine type: " + machineType);
    }

    public String normalizeAction(String action) {
        return normalizeToken(action);
    }

    private String normalizeStatus(String status) {
        return normalizeToken(status);
    }

    private Map<String, Object> toMachineDefinition(String machineType) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("machineType", machineType.toLowerCase(Locale.ROOT));
        definition.put("states", statesFor(machineType));
        definition.put("rules", ruleRegistry.get(machineType));
        return definition;
    }

    private List<String> statesFor(String machineType) {
        return switch (machineType) {
            case MACHINE_ALARM -> Arrays.stream(AlarmStatus.values()).map(Enum::name).toList();
            case MACHINE_INCIDENT -> Arrays.stream(IncidentStatus.values()).map(Enum::name).toList();
            case MACHINE_WORK_ORDER -> Arrays.stream(WorkOrderStatus.values()).map(Enum::name).toList();
            default -> throw new IllegalArgumentException("Unsupported state machine type: " + machineType);
        };
    }

    private String normalizeToken(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Value cannot be blank");
        }
        return raw.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }

    private StateTransitionRule rule(String from, String action, String to, String description) {
        return new StateTransitionRule(from, action, to, description);
    }
}
