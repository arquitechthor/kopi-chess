package com.arquitechthor.kopi.adapter.relay;

import com.arquitechthor.kopi.application.port.KopiAccount;
import com.arquitechthor.kopi.application.port.KopiSessionStore;
import com.arquitechthor.kopi.application.port.PvpRelay;
import java.net.http.HttpClient;
import java.time.Clock;
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

    /// Closed on shutdown (`close()` is inferred), which stops its thread.
    @Bean
    RelayWebSocket relayNotifications(RelayProperties properties, KopiApi api) {
        return new RelayWebSocket(http, json, properties, api);
    }
}
