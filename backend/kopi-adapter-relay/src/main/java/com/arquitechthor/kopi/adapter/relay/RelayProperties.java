package com.arquitechthor.kopi.adapter.relay;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// `kopi.online.*` in `application.yml`: where the Kopi account and the
/// relay are (`docs/protocolo-rele.md`, §1). None of it is a secret: the
/// app client is public and uses PKCE.
///
/// @param loginDomain  Cognito's login page and token endpoint
/// @param clientId     the `kopi-chess-client` app client
/// @param api          the backend Kopi's HTTP API
/// @param webSocket    the relay's notifications
/// @param callback     where Kopi's login page sends the browser back; it
///                     must be registered in the app client exactly so
/// @param logoutReturn where the browser lands after signing out
/// @param timeout      how long a call to Kopi may take
@ConfigurationProperties("kopi.online")
public record RelayProperties(
        URI loginDomain,
        String clientId,
        URI api,
        URI webSocket,
        @DefaultValue("http://localhost:6970/login/oauth2/code/kopi") URI callback,
        @DefaultValue("http://localhost:6970/") URI logoutReturn,
        @DefaultValue("10s") Duration timeout) {

    public RelayProperties {
        Objects.requireNonNull(loginDomain, "loginDomain");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(api, "api");
        Objects.requireNonNull(webSocket, "webSocket");
        Objects.requireNonNull(callback, "callback");
        Objects.requireNonNull(logoutReturn, "logoutReturn");
        Objects.requireNonNull(timeout, "timeout");
    }
}
