package com.uscdip.backend.service;

import com.uscdip.backend.repository.AlertRuleRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RuleFallbackService {

    private final AlertRuleRepository alertRuleRepository;

    public RuleFallbackService(AlertRuleRepository alertRuleRepository) {
        this.alertRuleRepository = alertRuleRepository;
    }

    public FallbackDecision evaluate(String modelCode, String segmentId, String nodeId, String reason, Map<String, Object> features) {
        int enabledRuleCount = alertRuleRepository.findByEnabledTrueOrderByRuleCodeAsc().size();
        String summary = "ruleChain=ALERT_RULE_FALLBACK"
                + ", modelCode=" + modelCode
                + ", reason=" + reason
                + ", enabledRules=" + enabledRuleCount
                + ", target=" + (segmentId == null ? nodeId : segmentId)
                + ", featureKeys=" + (features == null ? 0 : features.size());
        return new FallbackDecision("RULE_FALLBACK", "SUCCESS", summary);
    }

    public record FallbackDecision(String resultSource, String status, String outputSummary) {
    }
}
