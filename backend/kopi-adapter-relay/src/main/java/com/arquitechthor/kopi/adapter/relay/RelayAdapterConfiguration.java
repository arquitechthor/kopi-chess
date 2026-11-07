package com.arquitechthor.kopi.adapter.relay;

import com.arquitechthor.kopi.application.port.KopiAccount;
import com.arquitechthor.kopi.application.port.KopiSessionStore;
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
    KopiAccount kopiAccount(RelayProperties properties, KopiSessionStore sessions, Clock clock) {
        var tokens = new CognitoTokens(http, json, properties);
        return new RelayKopiAccount(tokens, new KopiApi(http, json, properties, sessions, tokens, clock), sessions,
                clock);
    }
}
