package com.uscdip.backend;

import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.dto.WebSocketAckRequest;
import com.uscdip.backend.dto.WebSocketPushPayload;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.WebSocketConnectionStatus;
import com.uscdip.backend.model.WebSocketUserPrincipal;
import com.uscdip.backend.repository.WebSocketAckRepository;
import com.uscdip.backend.repository.WebSocketConnectionRepository;
import com.uscdip.backend.repository.WebSocketPushMessageRepository;
import com.uscdip.backend.repository.WebSocketSubscriptionRepository;
import com.uscdip.backend.service.LocalTokenService;
import com.uscdip.backend.service.WebSocketPushGatewayService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

@SpringBootTest(properties = {
        "backend.outbox.relay-fixed-delay-ms=3600000",
        "backend.notification.retry-fixed-delay-ms=3600000",
        "backend.websocket.idle-scan-fixed-delay-ms=3600000",
        "backend.websocket.notification-bridge-fixed-delay-ms=3600000",
        "backend.websocket.allowed-origins=http://localhost:5173",
        "backend.websocket.idle-timeout-seconds=1"
})
class WebSocketPushGatewayIntegrationTest {

    @Autowired
    private LocalTokenService localTokenService;

    @Autowired
    private WebSocketPushGatewayService gatewayService;

    @Autowired
    private WebSocketConnectionRepository connectionRepository;

    @Autowired
    private WebSocketSubscriptionRepository subscriptionRepository;

    @Autowired
    private WebSocketPushMessageRepository pushMessageRepository;

    @Autowired
    private WebSocketAckRepository ackRepository;

    @Test
    void handshakeRejectsInvalidTokenAndOriginButAcceptsLocalToken() {
        Assertions.assertFalse(gatewayService.isOriginAllowed("https://evil.example"));
        Assertions.assertThrows(AuthFlowException.class, () ->
                gatewayService.authenticateHandshake("bad-token", "127.0.0.1", "JUnit"));

        TokenPairResponse token = localTokenService.issueForUser("U-DISPATCH-001", "127.0.0.1", "JUnit");
        WebSocketUserPrincipal principal = gatewayService.authenticateHandshake(token.accessToken(), "127.0.0.1", "JUnit");

        Assertions.assertEquals("U-DISPATCH-001", principal.userId());
        Assertions.assertEquals(
                WebSocketConnectionStatus.CONNECTED.name(),
                connectionRepository.findById(principal.connectionId()).orElseThrow().getStatus()
        );
    }

    @Test
    void subscriptionAuthorizationAllowsOwnScopeAndRejectsCrossScope() {
        WebSocketUserPrincipal dispatcher = principal("U-DISPATCH-001");
        gatewayService.attachStompSession(dispatcher, "stomp-dispatcher-001");
        gatewayService.subscribe(dispatcher, "stomp-dispatcher-001", "sub-1", "/topic/region.REGION-HZ.workorder.notifications");
        Assertions.assertFalse(subscriptionRepository.findByStompSessionIdAndStatus("stomp-dispatcher-001", "ACTIVE").isEmpty());
        Assertions.assertThrows(AuthFlowException.class, () ->
                gatewayService.subscribe(dispatcher, "stomp-dispatcher-001", "sub-2", "/topic/region.REGION-SH.workorder.notifications"));

        WebSocketUserPrincipal inspector = principal("U-INSPECT-001");
        gatewayService.attachStompSession(inspector, "stomp-inspector-001");
        gatewayService.subscribe(inspector, "stomp-inspector-001", "sub-3", "/user/queue/workorder.notifications");
        Assertions.assertThrows(AuthFlowException.class, () ->
                gatewayService.subscribe(inspector, "stomp-inspector-001", "sub-4", "/topic/user.U-DISPATCH-001.workorder.notifications"));
    }

    @Test
    void notificationBridgeAckAndReplayUseLastAckSeq() {
        gatewayService.bridgeNotificationsToPushMessages();

        String topic = "user.U-INSPECT-001.workorder.notifications";
        List<WebSocketPushPayload> initialReplay = gatewayService.replayPreview("U-INSPECT-001", topic);
        Assertions.assertFalse(initialReplay.isEmpty());
        Assertions.assertTrue(initialReplay.stream().allMatch(payload -> payload.traceId() != null && !payload.traceId().isBlank()));

        WebSocketUserPrincipal inspector = principal("U-INSPECT-001");
        long firstSeq = initialReplay.get(0).seqNo();
        gatewayService.ack(inspector, new WebSocketAckRequest(topic, firstSeq));
        Assertions.assertEquals(firstSeq, ackRepository.findByUserIdAndTopic("U-INSPECT-001", topic).orElseThrow().getLastAckSeq());

        List<WebSocketPushPayload> replayAfterAck = gatewayService.replayPreview("U-INSPECT-001", topic);
        Assertions.assertTrue(replayAfterAck.stream().allMatch(payload -> payload.seqNo() > firstSeq));
        Assertions.assertTrue(replayAfterAck.stream().allMatch(payload -> payload.traceId() != null && !payload.traceId().isBlank()));
        Assertions.assertTrue(pushMessageRepository.findByTopicAndSeqNoGreaterThanOrderBySeqNoAsc(
                topic,
                0L,
                org.springframework.data.domain.PageRequest.of(0, 50)
        ).size() >= initialReplay.size());
    }

    @Test
    void idleScanClosesConnectionsAndSubscriptions() {
        WebSocketUserPrincipal principal = principal("U-DISPATCH-001");
        gatewayService.attachStompSession(principal, "stomp-idle-001");
        gatewayService.subscribe(principal, "stomp-idle-001", "sub-idle", "/topic/region.REGION-HZ.workorder.notifications");

        connectionRepository.findById(principal.connectionId()).ifPresent(connection -> {
            connection.setLastSeenAt(LocalDateTime.now().minusSeconds(5));
            connectionRepository.save(connection);
        });

        int closed = gatewayService.closeIdleConnections();
        Assertions.assertTrue(closed >= 1);
        Assertions.assertEquals(
                WebSocketConnectionStatus.DISCONNECTED.name(),
                connectionRepository.findById(principal.connectionId()).orElseThrow().getStatus()
        );
        Assertions.assertTrue(subscriptionRepository.findByStompSessionIdAndStatus("stomp-idle-001", "ACTIVE").isEmpty());
    }

    private WebSocketUserPrincipal principal(String userId) {
        TokenPairResponse token = localTokenService.issueForUser(userId, "127.0.0.1", "JUnit");
        return gatewayService.authenticateHandshake(token.accessToken(), "127.0.0.1", "JUnit");
    }
}
