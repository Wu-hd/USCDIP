package com.uscdip.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.config.BackendOidcProperties;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class LocalAccessTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String JWT_ALGORITHM = "HS256";

    private final BackendOidcProperties oidcProperties;
    private final ObjectMapper objectMapper;

    public LocalAccessTokenService(BackendOidcProperties oidcProperties, ObjectMapper objectMapper) {
        this.oidcProperties = oidcProperties;
        this.objectMapper = objectMapper;
    }

    public AccessTokenIssueResult issue(String userId, String username, String sessionId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(oidcProperties.getAccessTokenTtlSeconds());
        String tokenId = UUID.randomUUID().toString();

        Map<String, Object> header = Map.of(
                "alg", JWT_ALGORITHM,
                "typ", "JWT"
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", oidcProperties.getLocalTokenIssuer());
        payload.put("sub", userId);
        payload.put("username", username);
        payload.put("sid", sessionId);
        payload.put("jti", tokenId);
        payload.put("token_use", "access");
        payload.put("iat", issuedAt.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        String token = sign(header, payload);
        return new AccessTokenIssueResult(token, expiresAt, tokenId);
    }

    public AccessTokenPrincipal verify(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new AuthFlowException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Access token is malformed");
        }

        String expectedSignature = signRaw(parts[0] + "." + parts[1]);
        if (!constantTimeEquals(expectedSignature, parts[2])) {
            throw new AuthFlowException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Access token signature is invalid");
        }

        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> claims = objectMapper.readValue(payloadBytes, new TypeReference<>() {
            });
            Instant issuedAt = Instant.ofEpochSecond(asLong(claims.get("iat")));
            Instant expiresAt = Instant.ofEpochSecond(asLong(claims.get("exp")));
            if (expiresAt.isBefore(Instant.now())) {
                throw new AuthFlowException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Access token is expired");
            }
            if (!"access".equals(claims.get("token_use"))) {
                throw new AuthFlowException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Access token type is invalid");
            }
            return new AccessTokenPrincipal(
                    asString(claims.get("sub")),
                    asString(claims.get("username")),
                    asString(claims.get("sid")),
                    asString(claims.get("jti")),
                    issuedAt,
                    expiresAt,
                    claims
            );
        } catch (AuthFlowException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuthFlowException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Access token payload is invalid");
        }
    }

    private String sign(Map<String, Object> header, Map<String, Object> payload) {
        try {
            String encodedHeader = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(header));
            String encodedPayload = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(payload));
            String content = encodedHeader + "." + encodedPayload;
            return content + "." + signRaw(content);
        } catch (Exception ex) {
            throw new AuthFlowException(ErrorCode.TOKEN_ISSUE_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, "Failed to issue access token");
        }
    }

    private String signRaw(String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(oidcProperties.getAccessTokenSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] signature = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception ex) {
            throw new AuthFlowException(ErrorCode.TOKEN_ISSUE_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, "Failed to sign access token");
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null || left.length() != right.length()) {
            return false;
        }
        int result = 0;
        for (int index = 0; index < left.length(); index++) {
            result |= left.charAt(index) ^ right.charAt(index);
        }
        return result == 0;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(asString(value));
    }

    public record AccessTokenIssueResult(String token, Instant expiresAt, String tokenId) {
    }

    public record AccessTokenPrincipal(
            String userId,
            String username,
            String sessionId,
            String tokenId,
            Instant issuedAt,
            Instant expiresAt,
            Map<String, Object> claims
    ) {
    }
}
