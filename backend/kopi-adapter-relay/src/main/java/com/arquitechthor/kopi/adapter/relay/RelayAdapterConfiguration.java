package com.arquitechthor.kopi.adapter.relay;

import com.arquitechthor.kopi.application.port.AccountHistory;
import com.arquitechthor.kopi.application.port.KopiAccount;
import com.arquitechthor.kopi.application.port.KopiSessionStore;
import com.arquitechthor.kopi.application.port.PvpRelay;
import com.arquitechthor.kopi.application.port.RemoteRequestHandler;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/// Spring configuration of the relay adapter, imported explicitly by
/// `kopi-bootstrap`. Nothing connects at start-up: Kopi is called only when
/// the person signs in or plays online, so Kopi Chess starts and plays
/// against the bots without a network.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RelayProperties.class)
public class RelayAdapterConfiguration {

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final JsonMapper json = JsonMapper.builder().build();

    @Bean
    CognitoTokens cognitoTokens(RelayProperties properties) {
        return new CognitoTokens(http, json, properties);
    }

    @Bean
    KopiApi kopiApi(RelayProperties properties, KopiSessionStore sessions, CognitoTokens tokens, Clock clock) {
        return new KopiApi(http, json, properties, sessions, tokens, clock);
    }

    @Bean
    KopiAccount kopiAccount(CognitoTokens tokens, KopiApi api, KopiSessionStore sessions, Clock clock) {
        return new RelayKopiAccount(tokens, api, sessions, clock);
    }

    @Bean
    PvpRelay pvpRelay(KopiApi api) {
        return new RelayPvp(api);
    }

    @Bean
    AccountHistory accountHistory(KopiApi api) {
        return new RelayAccountHistory(api);
    }

    /// Both the notifications and server mode, over one connection. Closed
    /// on shutdown (`close()` is inferred), which stops its threads.
    ///
    /// The remote adapter's handler is looked up on each request, not at
    /// start-up: it needs the use cases, and they may need this adapter.
    /// Without one, every request gets `SERVER_MODE_OFF`.
    @Bean
    RelayWebSocket relayWebSocket(RelayProperties properties, KopiApi api,
            ObjectProvider<RemoteRequestHandler> handler) {
        RemoteRequestHandler lazy = (device, payload) -> {
            var actual = handler.getIfAvailable();
            return actual == null ? RemoteRelayMessages.SERVER_MODE_OFF : actual.handle(device, payload);
        };
        return new RelayWebSocket(http, json, properties, api, new RemoteRelayMessages(json, lazy));
    }
}
