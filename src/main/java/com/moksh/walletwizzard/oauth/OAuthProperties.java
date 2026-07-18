package com.moksh.walletwizzard.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.oauth")
public record OAuthProperties(

        /** Cognito App Client ID (public client — no secret, PKCE only). */
        String clientId,

        /** e.g. walletwizzard.auth.ap-south-1.amazoncognito.com */
        String cognitoDomain,

        /** Public base URL of this server. e.g. https://api.walletwizzard.com */
        String baseUrl
) {}
