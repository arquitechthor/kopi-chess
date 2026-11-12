package com.arquitechthor.kopi.adapter.relay;

import com.arquitechthor.kopi.application.port.KopiSessionStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/// Cognito and the backend Kopi, faked on a local port with the JDK's own
/// `HttpServer`: each test says what each route answers, and every request
/// is recorded.
final class FakeKopi implements AutoCloseable {

    /// A request as received.
    record Request(String method, String path, Map<String, String> headers, String body, String query) {
        Map<String, String> form() {
            var values = new LinkedHashMap<String, String>();
            for (var pair : body.split("&")) {
                var eq = pair.indexOf('=');
                if (eq > 0) {
                    values.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                            URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
                }
            }
            return values;
        }
    }

    /// An answer: status and JSON body (empty for none).
    record Answer(int status, String body) {}

    private final HttpServer server;
    private final Map<String, Function<Request, Answer>> routes = new LinkedHashMap<>();
    final List<Request> requests = new ArrayList<>();

    FakeKopi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /// What `method path` answers (the path without the query).
    FakeKopi on(String method, String path, Function<Request, Answer> answer) {
        routes.put(method + " " + path, answer);
        return this;
    }

    FakeKopi on(String method, String path, int status, String body) {
        return on(method, path, _ -> new Answer(status, body));
    }

    List<Request> requests(String method, String path) {
        return requests.stream().filter(r -> r.method().equals(method) && r.path().equals(path)).toList();
    }

    private void handle(HttpExchange exchange) throws IOException {
        var headers = new LinkedHashMap<String, String>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.getFirst()));
        var request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers,
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                exchange.getRequestURI().getRawQuery());
        synchronized (requests) {
            requests.add(request);
        }
        var route = routes.get(request.method() + " " + request.path());
        var answer = route == null ? new Answer(404, "{\"message\":\"Not Found\"}") : route.apply(request);
        var bytes = answer.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(answer.status(), answer.status() == 204 ? -1 : bytes.length);
        if (answer.status() != 204) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    /// The properties of an adapter that talks to this fake.
    RelayProperties properties() {
        return new RelayProperties(uri(), "test-client", uri(), URI.create("ws://127.0.0.1:1/prod"),
                URI.create("http://localhost:6970/login/oauth2/code/kopi"), URI.create("http://localhost:6970/"),
                Duration.ofSeconds(5));
    }

    /// An unsigned JWT with these claims, as an ID token.
    static String jwt(String claimsJson) {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(claimsJson.getBytes(StandardCharsets.UTF_8)) + ".sig";
    }

    /// A session store in memory.
    static final class MemorySessions implements KopiSessionStore {
        StoredSession session;

        @Override
        public Optional<StoredSession> load() {
            return Optional.ofNullable(session);
        }

        @Override
        public void save(StoredSession stored) {
            session = stored;
        }

        @Override
        public void clear() {
            session = null;
        }
    }
}
