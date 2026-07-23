package com.uscdip.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "backend.websocket")
public class WebSocketPushProperties {

    private List<String> allowedOrigins = List.of("http://localhost:3000", "http://localhost:5173", "http://localhost:8080");
    private int maxConnections = 200;
    private int maxConnectionsPerUser = 5;
    private int frameSizeLimitBytes = 65536;
    private int sendBufferSizeLimitBytes = 524288;
    private int sendTimeLimitMs = 15000;
    private long idleTimeoutSeconds = 120;
    private long heartbeatIntervalMs = 30000;
    private int replayBatchSize = 50;
    private long idleScanFixedDelayMs = 30000;
    private long notificationBridgeFixedDelayMs = 30000;

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public int getMaxConnectionsPerUser() {
        return maxConnectionsPerUser;
    }

    public void setMaxConnectionsPerUser(int maxConnectionsPerUser) {
        this.maxConnectionsPerUser = maxConnectionsPerUser;
    }

    public int getFrameSizeLimitBytes() {
        return frameSizeLimitBytes;
    }

    public void setFrameSizeLimitBytes(int frameSizeLimitBytes) {
        this.frameSizeLimitBytes = frameSizeLimitBytes;
    }

    public int getSendBufferSizeLimitBytes() {
        return sendBufferSizeLimitBytes;
    }

    public void setSendBufferSizeLimitBytes(int sendBufferSizeLimitBytes) {
        this.sendBufferSizeLimitBytes = sendBufferSizeLimitBytes;
    }

    public int getSendTimeLimitMs() {
        return sendTimeLimitMs;
    }

    public void setSendTimeLimitMs(int sendTimeLimitMs) {
        this.sendTimeLimitMs = sendTimeLimitMs;
    }

    public long getIdleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    public void setIdleTimeoutSeconds(long idleTimeoutSeconds) {
        this.idleTimeoutSeconds = idleTimeoutSeconds;
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public int getReplayBatchSize() {
        return replayBatchSize;
    }

    public void setReplayBatchSize(int replayBatchSize) {
        this.replayBatchSize = replayBatchSize;
    }

    public long getIdleScanFixedDelayMs() {
        return idleScanFixedDelayMs;
    }

    public void setIdleScanFixedDelayMs(long idleScanFixedDelayMs) {
        this.idleScanFixedDelayMs = idleScanFixedDelayMs;
    }

    public long getNotificationBridgeFixedDelayMs() {
        return notificationBridgeFixedDelayMs;
    }

    public void setNotificationBridgeFixedDelayMs(long notificationBridgeFixedDelayMs) {
        this.notificationBridgeFixedDelayMs = notificationBridgeFixedDelayMs;
    }
}
