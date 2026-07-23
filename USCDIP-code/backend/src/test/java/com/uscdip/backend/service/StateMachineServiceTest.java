package com.uscdip.backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateMachineServiceTest {

    private final StateMachineService stateMachineService = new StateMachineService();

    @Test
    void shouldResolveIncidentDispatchTransition() {
        String next = stateMachineService.resolveNextStatus("incident", "OPEN", "dispatch");
        assertEquals("DISPATCH_PENDING", next);
    }

    @Test
    void shouldRejectInvalidIncidentTransitionFromClosed() {
        assertThrows(
                IllegalStateException.class,
                () -> stateMachineService.resolveNextStatus("incident", "CLOSED", "dispatch")
        );
    }

    @Test
    void shouldExposeFalseReportWritebackActionForAcceptedWorkOrder() {
        List<String> actions = stateMachineService.getAllowedActions("work_order", "ACCEPTED");
        assertTrue(actions.contains("FALSE_REPORT_WRITEBACK"));
        assertTrue(actions.contains("TRANSFER"));
        assertTrue(actions.contains("COMPLETE"));
    }
}
