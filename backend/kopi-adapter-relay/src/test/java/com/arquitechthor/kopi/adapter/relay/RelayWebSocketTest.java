package com.arquitechthor.kopi.adapter.relay;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.application.port.KopiSessionStore.StoredSession;
import com.arquitechthor.kopi.application.port.RemoteRequestHandler;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// The relay's WebSocket against a fake relay: notifications, server mode
/// and the app's requests (`docs/protocolo-rele.md` §7, §10).
class RelayWebSocketTest {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<String> handled = new CopyOnWriteArrayList<>();
    private volatile RemoteRequestHandler handler = (device, payload) -> {
        handled.add(device + " " + payload);
        return "{\"v\":1,\"ok\":true}";
    };
    private FakeKopi kopi;
    private FakeRelay relay;
    private RelayWebSocket webSocket;
    private final Semaphore news = new Semaphore(0);

    @BeforeEach
    void start() throws IOException {
        kopi = new FakeKopi();
        relay = new FakeRelay();
        var fake = kopi.properties();
        var properties = new RelayProperties(fake.loginDomain(), fake.clientId(), fake.api(), relay.uri(),
                fake.callback(), fake.logoutReturn(), Duration.ofSeconds(5));
        var sessions = new FakeKopi.MemorySessions();
        sessions.save(new StoredSession("access-1", "refresh-1", NOW.plusSeconds(3600), "sub-1", "ana@example.com"));
        var http = HttpClient.newHttpClient();
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var api = new KopiApi(http, json, properties, sessions, new CognitoTokens(http, json, properties), clock);
        webSocket = new RelayWebSocket(http, json, properties, api,
                new RemoteRelayMessages(json, (device, payload) -> handler.handle(device, payload)));
    }

    @AfterEach
    void stop() throws IOException {
        webSocket.close();
        relay.close();
        kopi.close();
    }

    private static Map<String, String> query(FakeRelay.Connection connection) {
        return Arrays.stream(connection.query.split("&")).map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(p -> p[0], p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8)));
    }

    private JsonNode tree(String text) {
        return json.readTree(text);
    }

    private static String request(String id, String op) {
        return """
                {"type": "relay", "from": "dev-1", "fromRole": "device", "fromName": "Móvil", "id": "%s",
                 "payload": {"v": 1, "op": "%s"}, "protocol": 1}""".formatted(id, op);
    }

    @Test
    void notificationsConnectWithoutARole() throws Exception {
        webSocket.listen(news::release);

        var connection = relay.nextConnection();

        assertThat(query(connection)).containsEntry("protocol", "1").containsEntry("token", "access-1")
                .doesNotContainKeys("role", "name");
        assertThat(webSocket.serving()).isFalse();
    }

    @Test
    void serverModeConnectsAsAServerWithItsName() throws Exception {
        webSocket.serve("PC de casa");

        var connection = relay.nextConnection();

        assertThat(query(connection)).containsEntry("role", "server").containsEntry("name", "PC de casa")
                .containsEntry("token", "access-1");
        assertThat(webSocket.serving()).isTrue();
    }

    @Test
    void turningServerModeOnReconnectsAsAServerAndOffReconnectsWithoutARole() throws Exception {
        webSocket.listen(news::release);
        var notify = relay.nextConnection();

        webSocket.serve("PC de casa");
        var server = relay.nextConnection();
        webSocket.stopServing();
        var back = relay.nextConnection();

        assertThat(query(notify)).doesNotContainKey("role");
        assertThat(query(server)).containsEntry("role", "server");
        assertThat(query(back)).doesNotContainKey("role");
        assertThat(webSocket.serving()).isFalse();
    }

    @Test
    void aLongNameIsCutToFortyCharacters() throws Exception {
        webSocket.serve("x".repeat(50));

        assertThat(query(relay.nextConnection()).get("name")).hasSize(RelayWebSocket.MAX_NAME);
    }

    @Test
    void theAppsRequestIsAnsweredToItsSenderWithTheSameId() throws Exception {
        webSocket.serve("PC de casa");
        var connection = relay.nextConnection();

        connection.send(request("q7", "hello"));
        var reply = tree(connection.next());

        assertThat(reply.path("type").stringValue()).isEqualTo("relay");
        assertThat(reply.path("to").stringValue()).isEqualTo("dev-1");
        assertThat(reply.path("id").stringValue()).isEqualTo("q7");
        assertThat(reply.path("payload")).isEqualTo(tree("{\"v\":1,\"ok\":true}"));
        assertThat(handled).containsExactly("dev-1 {\"v\":1,\"op\":\"hello\"}");
    }

    @Test
    void aRequestOnANotificationsConnectionGetsServerModeOff() throws Exception {
        webSocket.listen(news::release);
        var connection = relay.nextConnection();

        connection.send(request("q1", "bots"));
        var reply = tree(connection.next());

        assertThat(reply.path("payload").path("error").stringValue()).isEqualTo("SERVER_MODE_OFF");
        assertThat(handled).isEmpty();
    }

    @Test
    void aSlowRequestDoesNotHoldUpTheOthers() throws Exception {
        var release = new CountDownLatch(1);
        handler = (device, payload) -> {
            if (payload.contains("move")) {
                try {
                    release.await(5, TimeUnit.SECONDS); // a bot thinking
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return "{\"v\":1,\"ok\":true}";
        };
        webSocket.serve("PC de casa");
        var connection = relay.nextConnection();

        connection.send(request("slow", "move"));
        connection.send(request("fast", "bots"));
        var first = tree(connection.next());
        release.countDown();
        var second = tree(connection.next());

        assertThat(first.path("id").stringValue()).isEqualTo("fast");
        assertThat(second.path("id").stringValue()).isEqualTo("slow");
    }

    @Test
    void gameNotificationsStillArriveWhileServing() throws Exception {
        webSocket.listen(news::release);
        relay.nextConnection();
        webSocket.serve("PC de casa");
        var server = relay.nextConnection();
        news.drainPermits();

        server.send("{\"type\": \"game\", \"protocol\": 1}");

        assertThat(news.tryAcquire(5, TimeUnit.SECONDS)).isTrue();
    }
}
