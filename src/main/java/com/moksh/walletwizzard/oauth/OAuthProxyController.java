package com.moksh.walletwizzard.oauth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OAuth 2.0 authorization proxy.
 *
 * Problem: MCP clients use http://localhost:<random-port>/callback as the redirect_uri.
 * Cognito requires exact redirect_uri matches, so random ports can't be pre-registered.
 *
 * Solution: This controller acts as an authorization server that relays to Cognito:
 *   1. MCP client → GET /oauth2/authorize  (random-port redirect_uri)
 *   2. We → GET Cognito/authorize          (our fixed redirect_uri + our PKCE pair)
 *   3. Cognito → GET /oauth2/callback      (Cognito code)
 *   4. We exchange Cognito code → access_token, issue our own auth code
 *   5. We → MCP client callback            (our auth code)
 *   6. MCP client → POST /oauth2/token     (exchange our auth code)
 *   7. We return the Cognito access_token  (Spring resource server validates it normally)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Profile("!local")
public class OAuthProxyController {

    private final OAuthProperties props;
    private final RestClient restClient = RestClient.create();

    // ourState → context of the in-flight MCP client authorization request
    private final ConcurrentHashMap<String, PendingFlow> pendingFlows = new ConcurrentHashMap<>();

    // our auth code → Cognito tokens + PKCE challenge from original MCP client request
    private final ConcurrentHashMap<String, IssuedCode> issuedCodes = new ConcurrentHashMap<>();

    @GetMapping("/oauth2/authorize")
    public ResponseEntity<Void> authorize(
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("code_challenge") String codeChallenge,
            @RequestParam(value = "code_challenge_method", defaultValue = "S256") String codeChallengeMethod,
            @RequestParam("state") String state,
            @RequestParam(value = "scope", defaultValue = "openid email profile") String scope) {

        String ourCodeVerifier = randomBase64(32);
        String ourCodeChallenge = sha256Base64(ourCodeVerifier);
        String ourState = UUID.randomUUID().toString();

        pendingFlows.put(ourState, new PendingFlow(
                redirectUri, state, codeChallenge, codeChallengeMethod,
                ourCodeVerifier, Instant.now().plusSeconds(600)));

        URI cognitoUri = UriComponentsBuilder
                .fromUriString("https://" + props.cognitoDomain() + "/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", props.clientId())
                .queryParam("redirect_uri", props.baseUrl() + "/oauth2/callback")
                .queryParam("scope", scope)
                .queryParam("state", ourState)
                .queryParam("code_challenge", ourCodeChallenge)
                .queryParam("code_challenge_method", "S256")
                .build().toUri();

        return ResponseEntity.status(HttpStatus.FOUND).location(cognitoUri).build();
    }

    @GetMapping("/oauth2/callback")
    public ResponseEntity<Void> callback(
            @RequestParam("code") String cognitoCode,
            @RequestParam("state") String ourState) {

        PendingFlow flow = pendingFlows.remove(ourState);
        if (flow == null || flow.expiresAt().isBefore(Instant.now())) {
            log.warn("OAuth callback: unknown or expired state {}", ourState);
            return ResponseEntity.badRequest().build();
        }

        // Exchange Cognito auth code for tokens using our PKCE verifier
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", cognitoCode);
        body.add("client_id", props.clientId());
        body.add("redirect_uri", props.baseUrl() + "/oauth2/callback");
        body.add("code_verifier", flow.ourCodeVerifier());

        @SuppressWarnings("unchecked")
        Map<String, Object> tokenResponse = restClient.post()
                .uri("https://" + props.cognitoDomain() + "/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(Map.class);

        // Issue our own auth code backed by the real Cognito access token
        String ourAuthCode = randomBase64(32);
        issuedCodes.put(ourAuthCode, new IssuedCode(
                (String) tokenResponse.get("access_token"),
                (String) tokenResponse.get("token_type"),
                (Integer) tokenResponse.get("expires_in"),
                flow.originalCodeChallenge(),
                flow.originalCodeChallengeMethod(),
                Instant.now().plusSeconds(300)));

        URI clientCallback = UriComponentsBuilder.fromUriString(flow.originalRedirectUri())
                .queryParam("code", ourAuthCode)
                .queryParam("state", flow.originalState())
                .build().toUri();

        return ResponseEntity.status(HttpStatus.FOUND).location(clientCallback).build();
    }

    @PostMapping(value = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam("grant_type") String grantType,
            @RequestParam("code") String code,
            @RequestParam(value = "code_verifier", required = false) String codeVerifier) {

        IssuedCode issued = issuedCodes.remove(code);
        if (issued == null || issued.expiresAt().isBefore(Instant.now())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "invalid_grant"));
        }

        // Validate PKCE: S256(code_verifier) must equal the code_challenge stored at /authorize time
        if (codeVerifier != null) {
            String computed = sha256Base64(codeVerifier);
            if (!computed.equals(issued.codeChallenge())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "invalid_grant", "error_description", "PKCE verification failed"));
            }
        }

        return ResponseEntity.ok(Map.of(
                "access_token", issued.accessToken(),
                "token_type", issued.tokenType() != null ? issued.tokenType() : "Bearer",
                "expires_in", issued.expiresIn() != null ? issued.expiresIn() : 3600,
                "scope", "openid email profile"
        ));
    }

    private String randomBase64(int bytes) {
        byte[] buf = new byte[bytes];
        new SecureRandom().nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private String sha256Base64(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    record PendingFlow(String originalRedirectUri, String originalState,
                       String originalCodeChallenge, String originalCodeChallengeMethod,
                       String ourCodeVerifier, Instant expiresAt) {}

    record IssuedCode(String accessToken, String tokenType, Integer expiresIn,
                      String codeChallenge, String codeChallengeMethod, Instant expiresAt) {}
}
