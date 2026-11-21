package com.arquitechthor.kopi.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.adapter.relay.RelayProperties;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/// The addresses Kopi's login page sends the browser back to follow the port the
/// browser uses: only 6970 and 8080 are registered (docs/protocolo-rele.md, §1).
class OnlineAddressesTest {

    /// `application.yml` with these variables, and nothing from this machine's environment.
    private static RelayProperties online(Map<String, Object> variables) throws IOException {
        var environment = new StandardEnvironment();
        var sources = environment.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.addFirst(new MapPropertySource("variables", variables));
        for (var yaml : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))) {
            sources.addLast(yaml);
        }
        return Binder.get(environment).bind("kopi.online", RelayProperties.class).get();
    }

    @Test
    void byDefaultPort6970() throws IOException {
        var online = online(Map.of());

        assertThat(online.callback()).hasToString("http://localhost:6970/login/oauth2/code/kopi");
        assertThat(online.logoutReturn()).hasToString("http://localhost:6970/");
    }

    @Test
    void kopiPortMovesTheServerAndTheAddresses() throws IOException {
        var online = online(Map.of("KOPI_PORT", "8080"));

        assertThat(online.callback()).hasToString("http://localhost:8080/login/oauth2/code/kopi");
        assertThat(online.logoutReturn()).hasToString("http://localhost:8080/");
    }

    @Test
    void serverPortFromTheCommandLineToo() throws IOException {
        var online = online(Map.of("server.port", "8080"));

        assertThat(online.callback()).hasToString("http://localhost:8080/login/oauth2/code/kopi");
    }

    @Test
    void inComposeThePublishedPortWins() throws IOException {
        // The container listens on 6970; the browser reaches it on the host's KOPI_PORT.
        var online = online(Map.of("KOPI_PUBLIC_PORT", "8080"));

        assertThat(online.callback()).hasToString("http://localhost:8080/login/oauth2/code/kopi");
        assertThat(online.logoutReturn()).hasToString("http://localhost:8080/");
    }
}
