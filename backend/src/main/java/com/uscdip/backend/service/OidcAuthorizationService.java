package com.uscdip.backend.service;

import com.uscdip.backend.config.BackendOidcProperties;
import com.uscdip.backend.dto.AuthCallbackRequest;
import com.uscdip.backend.dto.AuthLoginDescriptor;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.entity.OidcLoginStateEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.repository.OidcLoginStateRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class OidcAuthorizationService {

    private static final String STATE_STATUS_ACTIVE = "ACTIVE";
    private static final String STATE_STATUS_CONSUMED = "CONSUMED";
    private static final String STATE_STATUS_EXPIRED = "EXPIRED";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final BackendOidcProperties oidcProperties;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider;
    private final ObjectProvider<JwtDecoder> oidcJwtDecoderProvider;
    private final OidcLoginStateRepository oidcLoginStateRepository;
    private final OidcUserSyncService oidcUserSyncService;
    private final LocalTokenService localTokenService;
    private final SecurityAuditService securityAuditService;
    private final RestTemplate restTemplate = new RestTemplate();

    public OidcAuthorizationService(
            BackendOidcProperties oidcProperties,
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
            ObjectProvider<JwtDecoder> oidcJwtDecoderProvider,
            OidcLoginStateRepository oidcLoginStateRepository,
            OidcUserSyncService oidcUserSyncService,
            LocalTokenService localTokenService,
            SecurityAuditService securityAuditService
    ) {
        this.oidcProperties = oidcProperties;
        this.clientRegistrationRepositoryProvider = clientRegistrationRepositoryProvider;
        this.oidcJwtDecoderProvider = oidcJwtDecoderProvider;
        this.oidcLoginStateRepository = oidcLoginStateRepository;
        this.oidcUserSyncService = oidcUserSyncService;
        this.localTokenService = localTokenService;
        this.securityAuditService = securityAuditService;
    }

    @Transactional
    public AuthLoginDescriptor createLoginDescriptor(String redirectUri, String clientIp, String userAgent) {
        if (!oidcProperties.isEnabled()) {
            return new AuthLoginDescriptor(false, oidcProperties.getRegistrationId(), "", null, null);
        }

        expireStates();
        ClientRegistration registration = getRegistration();
        String state = randomToken();
        String codeVerifier = randomToken();
        String codeChallenge = sha256Base64Url(codeVerifier);
        Instant stateExpiresAt = Instant.now().plusSeconds(oidcProperties.getStateTtlSeconds());

        oidcLoginStateRepository.save(new OidcLoginStateEntity(
                state,
                codeVerifier,
                blankToNull(redirectUri),
                STATE_STATUS_ACTIVE,
                LocalDateTime.ofInstant(stateExpiresAt, ZoneId.systemDefault()),
                LocalDateTime.now(),
                null,
                clientIp,
                truncate(userAgent, 512)
        ));

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(registration.getProviderDetails().getAuthorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", registration.getClientId())
                .queryParam("scope", String.join("+", registration.getScopes()))
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256");
        if (!isBlank(redirectUri)) {
            builder.queryParam("redirect_uri", redirectUri);
        }

        return new AuthLoginDescriptor(true, oidcProperties.getRegistrationId(), builder.build(false).toUriString(), state, stateExpiresAt);
    }

    @Transactional
    public TokenPairResponse handleCallback(AuthCallbackRequest request, String clientIp, String userAgent) {
        if (!oidcProperties.isEnabled()) {
            throw new AuthFlowException(ErrorCode.OIDC_DISABLED, HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.OIDC_DISABLED.defaultMessage());
        }

        OidcLoginStateEntity stateEntity = validateState(request, clientIp, userAgent);
        Map<String, Object> providerTokens = exchangeCodeForProviderTokens(request, stateEntity);
        Map<String, Object> claims = mergeClaims(providerTokens);
        String userId = oidcUserSyncService.syncJwtClaims(claims);

        stateEntity.setStatus(STATE_STATUS_CONSUMED);
        stateEntity.setConsumedAt(LocalDateTime.now());
        oidcLoginStateRepository.save(stateEntity);

        securityAuditService.log(
                SecurityAuditService.EVENT_OIDC_CALLBACK_SUCCESS,
                userId,
                null,
                null,
                SecurityAuditService.OUTCOME_SUCCESS,
                "OIDC callback exchanged successfully",
                clientIp,
                userAgent
        );
        return localTokenService.issueForUser(userId, clientIp, userAgent);
    }

    private OidcLoginStateEntity validateState(AuthCallbackRequest request, String clientIp, String userAgent) {
        Optional<OidcLoginStateEntity> stateOptional = oidcLoginStateRepository.findById(request.state());
        if (stateOptional.isEmpty()) {
            logInvalidState(request.state(), "State record not found", clientIp, userAgent);
            throw invalidStateException();
        }

        OidcLoginStateEntity stateEntity = stateOptional.get();
        if (!STATE_STATUS_ACTIVE.equals(stateEntity.getStatus())) {
            logInvalidState(request.state(), "State status is " + stateEntity.getStatus(), clientIp, userAgent);
            throw invalidStateException();
        }
        if (stateEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            stateEntity.setStatus(STATE_STATUS_EXPIRED);
            oidcLoginStateRepository.save(stateEntity);
            logInvalidState(request.state(), "State expired", clientIp, userAgent);
            throw invalidStateException();
        }
        if (!isBlank(stateEntity.getRedirectUri()) && !stateEntity.getRedirectUri().equals(request.redirectUri())) {
            logInvalidState(request.state(), "State redirectUri mismatch", clientIp, userAgent);
            throw invalidStateException();
        }
        return stateEntity;
    }

    private Map<String, Object> exchangeCodeForProviderTokens(AuthCallbackRequest request, OidcLoginStateEntity stateEntity) {
        ClientRegistration registration = getRegistration();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(registration.getClientId(), registration.getClientSecret());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", request.code());
        form.add("redirect_uri", request.redirectUri());
        form.add("code_verifier", stateEntity.getCodeVerifier());

        Map<String, Object> response = restTemplate.exchange(
                registration.getProviderDetails().getTokenUri(),
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }
        ).getBody();

        if (response == null || isBlank(asString(response.get("id_token")))) {
            throw new AuthFlowException(ErrorCode.TOKEN_ISSUE_FAILED, HttpStatus.BAD_GATEWAY, "OIDC provider token response is incomplete");
        }
        return response;
    }

    private Map<String, Object> mergeClaims(Map<String, Object> providerTokens) {
        JwtDecoder oidcJwtDecoder = getOidcJwtDecoder();
        String idToken = asString(providerTokens.get("id_token"));
        Jwt idJwt = oidcJwtDecoder.decode(idToken);
        Map<String, Object> claims = new LinkedHashMap<>(idJwt.getClaims());

        Object accessTokenValue = providerTokens.get("access_token");
        if (accessTokenValue instanceof String accessToken && accessToken.chars().filter(ch -> ch == '.').count() == 2) {
            try {
                Jwt accessJwt = oidcJwtDecoder.decode(accessToken);
                mergeIfPresent(claims, accessJwt.getClaims(), "realm_access");
                mergeIfPresent(claims, accessJwt.getClaims(), "resource_access");
                mergeIfPresent(claims, accessJwt.getClaims(), "roles");
                mergeIfPresent(claims, accessJwt.getClaims(), "preferred_username");
                mergeIfPresent(claims, accessJwt.getClaims(), "email");
                mergeIfPresent(claims, accessJwt.getClaims(), "name");
            } catch (Exception ignored) {
                // Access token may not be JWT; id_token claims are enough for fallback sync.
            }
        }
        return claims;
    }

    private void mergeIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source.containsKey(key) && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private ClientRegistration getRegistration() {
        ClientRegistrationRepository clientRegistrationRepository = clientRegistrationRepositoryProvider.getIfAvailable();
        ClientRegistration registration = clientRegistrationRepository == null
                ? null
                : clientRegistrationRepository.findByRegistrationId(oidcProperties.getRegistrationId());
        if (registration == null) {
            throw new AuthFlowException(ErrorCode.OIDC_DISABLED, HttpStatus.SERVICE_UNAVAILABLE, "OIDC client registration is unavailable");
        }
        return registration;
    }

    private JwtDecoder getOidcJwtDecoder() {
        JwtDecoder jwtDecoder = oidcJwtDecoderProvider.getIfAvailable();
        if (jwtDecoder == null) {
            throw new AuthFlowException(ErrorCode.OIDC_DISABLED, HttpStatus.SERVICE_UNAVAILABLE, "OIDC JWT decoder is unavailable");
        }
        return jwtDecoder;
    }

    private void expireStates() {
        List<OidcLoginStateEntity> activeStates = oidcLoginStateRepository.findByStatus(STATE_STATUS_ACTIVE);
        LocalDateTime now = LocalDateTime.now();
        for (OidcLoginStateEntity state : activeStates) {
            if (state.getExpiresAt().isBefore(now)) {
                state.setStatus(STATE_STATUS_EXPIRED);
                oidcLoginStateRepository.save(state);
            }
        }
    }

    private String sha256Base64Url(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (Exception ex) {
            throw new AuthFlowException(ErrorCode.TOKEN_ISSUE_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate PKCE challenge");
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void logInvalidState(String state, String detail, String clientIp, String userAgent) {
        securityAuditService.log(
                SecurityAuditService.EVENT_OIDC_STATE_INVALID,
                null,
                state,
                null,
                SecurityAuditService.OUTCOME_DENY,
                detail,
                clientIp,
                userAgent
        );
    }

    private AuthFlowException invalidStateException() {
        return new AuthFlowException(ErrorCode.OIDC_STATE_INVALID, HttpStatus.UNAUTHORIZED, ErrorCode.OIDC_STATE_INVALID.defaultMessage());
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
