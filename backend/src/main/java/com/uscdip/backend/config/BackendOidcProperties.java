package com.uscdip.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "backend.oidc")
public class BackendOidcProperties {

    private boolean enabled = false;
    private String registrationId = "keycloak";
    private String issuerUri = "http://localhost:8081/realms/uscdip";
    private String clientId = "uscdip-backend";
    private String clientSecret = "change-me";
    private String accessTokenSecret = "change-me-local-access-token-secret";
    private long accessTokenTtlSeconds = 1800;
    private long refreshTokenTtlSeconds = 1209600;
    private long stateTtlSeconds = 300;
    private String refreshTokenHashAlgorithm = "SHA-256";
    private String localTokenIssuer = "uscdip-backend";
    private String postLogoutRedirectUri = "http://localhost:8080/swagger-ui/index.html";
    private String defaultRole = "LEADER_READONLY";
    private String defaultRegion = "REGION-HZ";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(String registrationId) {
        this.registrationId = registrationId;
    }

    public String getIssuerUri() {
        return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getAccessTokenSecret() {
        return accessTokenSecret;
    }

    public void setAccessTokenSecret(String accessTokenSecret) {
        this.accessTokenSecret = accessTokenSecret;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public long getStateTtlSeconds() {
        return stateTtlSeconds;
    }

    public void setStateTtlSeconds(long stateTtlSeconds) {
        this.stateTtlSeconds = stateTtlSeconds;
    }

    public String getRefreshTokenHashAlgorithm() {
        return refreshTokenHashAlgorithm;
    }

    public void setRefreshTokenHashAlgorithm(String refreshTokenHashAlgorithm) {
        this.refreshTokenHashAlgorithm = refreshTokenHashAlgorithm;
    }

    public String getLocalTokenIssuer() {
        return localTokenIssuer;
    }

    public void setLocalTokenIssuer(String localTokenIssuer) {
        this.localTokenIssuer = localTokenIssuer;
    }

    public String getPostLogoutRedirectUri() {
        return postLogoutRedirectUri;
    }

    public void setPostLogoutRedirectUri(String postLogoutRedirectUri) {
        this.postLogoutRedirectUri = postLogoutRedirectUri;
    }

    public String getDefaultRole() {
        return defaultRole;
    }

    public void setDefaultRole(String defaultRole) {
        this.defaultRole = defaultRole;
    }

    public String getDefaultRegion() {
        return defaultRegion;
    }

    public void setDefaultRegion(String defaultRegion) {
        this.defaultRegion = defaultRegion;
    }
}
