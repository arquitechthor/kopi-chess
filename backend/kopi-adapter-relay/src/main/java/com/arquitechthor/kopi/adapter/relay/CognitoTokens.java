package com.arquitechthor.kopi.adapter.relay;

import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.application.port.KopiOnlineException.Problem;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// Cognito's OAuth endpoints for the `kopi-chess-client` app client: the
/// login page's address, and getting, renewing and revoking tokens. No
/// secret: a public client with PKCE (ADR-019).
final class CognitoTokens {

    /// What the token endpoint gives.
    ///
    /// @param refreshToken `null` when renewing (the old one stays valid)
    /// @param idToken      `null` when renewing
    record Tokens(String accessToken, String refreshToken, long expiresInSeconds, String idToken) {}

    private final HttpClient http;
    private final JsonMapper json;
    private final RelayProperties properties;

    CognitoTokens(HttpClient http, JsonMapper json, RelayProperties properties) {
        this.http = Objects.requireNonNull(http, "http");
        this.json = Objects.requireNonNull(json, "json");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /// Kopi's login page, asking for a code for `challenge` (S256).
    URI authorizeUri(String state, String challenge) {
        var query = new LinkedHashMap<String, String>();
        query.put("response_type", "code");
        query.put("client_id", properties.clientId());
        query.put("redirect_uri", properties.callback().toString());
        query.put("scope", "openid email");
        query.put("state", state);
        query.put("code_challenge", challenge);
        query.put("code_challenge_method", "S256");
        return URI.create(properties.loginDomain() + "/oauth2/authorize?" + form(query));
    }

    /// Where to send the browser to end the login page's session too.
    URI logoutUri() {
        var query = new LinkedHashMap<String, String>();
        query.put("client_id", properties.clientId());
        query.put("logout_uri", properties.logoutReturn().toString());
        return URI.create(properties.loginDomain() + "/logout?" + form(query));
    }

    /// The tokens for the code the login page sent back.
    ///
    /// @throws KopiOnlineException `LOGIN_FAILED` if Cognito refuses it,
    ///                             `UNREACHABLE` without a connection
    Tokens exchange(String code, String verifier) {
        var body = new LinkedHashMap<String, String>();
        body.put("grant_type", "authorization_code");
        body.put("client_id", properties.clientId());
        body.put("code", code);
        body.put("redirect_uri", properties.callback().toString());
        body.put("code_verifier", verifier);
        return tokens(post("/oauth2/token", body), Problem.LOGIN_FAILED);
    }

    /// A new access token.
    ///
    /// @throws KopiOnlineException `NOT_SIGNED_IN` if the refresh token is
    ///                             no longer valid (expired or revoked)
    Tokens refresh(String refreshToken) {
        var body = new LinkedHashMap<String, String>();
        body.put("grant_type", "refresh_token");
        body.put("client_id", properties.clientId());
        body.put("refresh_token", refreshToken);
        return tokens(post("/oauth2/token", body), Problem.NOT_SIGNED_IN);
    }

    /// Revokes the refresh token, best effort: signing out never fails.
    void revoke(String refreshToken) {
        try {
            var body = new LinkedHashMap<String, String>();
            body.put("token", refreshToken);
            body.put("client_id", properties.clientId());
            post("/oauth2/revoke", body);
        } catch (KopiOnlineException _) {
            // Forgotten here anyway; it expires on its own.
        }
    }

    /// The claims of an ID token received straight from the token endpoint
    /// over TLS: no signature to check, it did not travel through anyone.
    JsonNode claims(String idToken) {
        var parts = idToken == null ? new String[0] : idToken.split("[.]");
        if (parts.length != 3) {
            throw new KopiOnlineException(Problem.LOGIN_FAILED, "The ID token is not a JWT");
        }
        return json.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
    }

    private Tokens tokens(HttpResponse<String> response, Problem refused) {
        if (response.statusCode() != 200) {
            throw new KopiOnlineException(refused, "Cognito answered " + response.statusCode());
        }
        var node = json.readTree(response.body());
        var access = text(node, "access_token");
        if (access == null) {
            throw new KopiOnlineException(refused, "Cognito gave no access token");
        }
        return new Tokens(access, text(node, "refresh_token"), node.path("expires_in").asLong(3600),
                text(node, "id_token"));
    }

    /// A text field, or `null` if it is missing.
    static String text(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private HttpResponse<String> post(String path, Map<String, String> body) {
        var request = HttpRequest.newBuilder(URI.create(properties.loginDomain() + path))
                .timeout(properties.timeout())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(body)))
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new KopiOnlineException(Problem.UNREACHABLE, "Cannot reach Kopi's login service", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KopiOnlineException(Problem.UNREACHABLE, "Interrupted", e);
        }
    }

    static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
