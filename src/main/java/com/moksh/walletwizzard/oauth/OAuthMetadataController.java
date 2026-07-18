package com.moksh.walletwizzard.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Profile("!local")
public class OAuthMetadataController {

    private final OAuthProperties props;

    @GetMapping("/.well-known/oauth-authorization-server")
    public Map<String, Object> metadata() {
        String cognitoBase = "https://" + props.cognitoDomain();
        return Map.of(
                "issuer",                                props.baseUrl(),
                "authorization_endpoint",               cognitoBase + "/oauth2/authorize",
                "token_endpoint",                       cognitoBase + "/oauth2/token",
                "registration_endpoint",                props.baseUrl() + "/oauth2/register",
                "scopes_supported",                     List.of("openid", "email", "profile"),
                "response_types_supported",             List.of("code"),
                "grant_types_supported",                List.of("authorization_code", "refresh_token"),
                "token_endpoint_auth_methods_supported", List.of("none"),
                "code_challenge_methods_supported",     List.of("S256")
        );
    }

    /**
     * Dynamic Client Registration — always returns the pre-configured Cognito public client.
     * The redirect_uris in the request body are ignored here; they must be pre-registered
     * in the Cognito App Client (add http://localhost for Claude Code, and any hosted
     * redirect URI for Claude Desktop / claude.ai).
     */
    @PostMapping("/oauth2/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@RequestBody Map<String, Object> request) {
        return Map.of(
                "client_id",                    props.clientId(),
                "token_endpoint_auth_method",   "none",
                "grant_types",                  List.of("authorization_code", "refresh_token"),
                "response_types",               List.of("code"),
                "scopes",                       "openid email profile"
        );
    }
}
