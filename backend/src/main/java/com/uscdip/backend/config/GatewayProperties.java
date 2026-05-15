package com.uscdip.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "backend.gateway")
public class GatewayProperties {

    private int bodySizeLimitBytes = 4096;
    private int pageSizeMax = 200;
    private int defaultWindowSeconds = 60;
    private int defaultCapacity = 10;

    public int getBodySizeLimitBytes() {
        return bodySizeLimitBytes;
    }

    public void setBodySizeLimitBytes(int bodySizeLimitBytes) {
        this.bodySizeLimitBytes = bodySizeLimitBytes;
    }

    public int getPageSizeMax() {
        return pageSizeMax;
    }

    public void setPageSizeMax(int pageSizeMax) {
        this.pageSizeMax = pageSizeMax;
    }

    public int getDefaultWindowSeconds() {
        return defaultWindowSeconds;
    }

    public void setDefaultWindowSeconds(int defaultWindowSeconds) {
        this.defaultWindowSeconds = defaultWindowSeconds;
    }

    public int getDefaultCapacity() {
        return defaultCapacity;
    }

    public void setDefaultCapacity(int defaultCapacity) {
        this.defaultCapacity = defaultCapacity;
    }
}
