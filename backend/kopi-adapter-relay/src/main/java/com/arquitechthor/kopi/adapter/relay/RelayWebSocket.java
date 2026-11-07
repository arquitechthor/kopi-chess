package com.arquitechthor.kopi.adapter.relay;

import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.application.port.RelayNotifications;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.json.JsonMapper;

/// [RelayNotifications] over the relay's WebSocket (`docs/protocolo-rele.md`,
/// §7), with the JDK's WebSocket client.
///
/// - Connects only while there is a Kopi session; checks again every minute.
/// - Sends `{"type": "ping"}` every 5 minutes: API Gateway closes a silent
///   connection after 10.
/// - Reconnects when the connection closes (API Gateway closes every
///   connection after 2 hours), waiting longer after each failure, up to 5
///   minutes; and calls the listener after reconnecting, since a
///   notification may have been missed in between.
final class RelayWebSocket implements RelayNotifications, AutoCloseable {

    private static final System.Logger log = System.getLogger(RelayWebSocket.class.getName());
    private static final Duration PING_EVERY = Duration.ofMinutes(5);
    private static final Duration CHECK_EVERY = Duration.ofMinutes(1);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    private final HttpClient http;
    private final JsonMapper json;
    private final RelayProperties properties;
    private final KopiApi api;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        var thread = new Thread(runnable, "kopi-relay-websocket");
        thread.setDaemon(true);
        return thread;
    });

    private volatile Runnable onNews;
    private volatile WebSocket socket;
    private volatile boolean stopped;
    private Duration backoff = Duration.ofSeconds(10);

    RelayWebSocket(HttpClient http, JsonMapper json, RelayProperties properties, KopiApi api) {
        this.http = Objects.requireNonNull(http, "http");
        this.json = Objects.requireNonNull(json, "json");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.api = Objects.requireNonNull(api, "api");
    }

    @Override
    public synchronized void listen(Runnable listener) {
        this.onNews = Objects.requireNonNull(listener, "listener");
        stopped = false;
        scheduler.execute(this::connectIfSignedIn);
        scheduler.scheduleWithFixedDelay(this::connectIfSignedIn, CHECK_EVERY.toSeconds(), CHECK_EVERY.toSeconds(),
                TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::ping, PING_EVERY.toSeconds(), PING_EVERY.toSeconds(), TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        stopped = true;
        var current = socket;
        socket = null;
        if (current != null) {
            current.abort();
        }
    }

    @Override
    public void close() {
        stop();
        scheduler.shutdownNow();
    }

    /// The address to connect to: the token goes in the query (§7).
    URI address(String accessToken) {
        return URI.create(properties.webSocket() + "?protocol=" + KopiApi.PROTOCOL + "&token="
                + URLEncoder.encode(accessToken, StandardCharsets.UTF_8));
    }

    private void connectIfSignedIn() {
        if (stopped || socket != null) {
            return;
        }
        String token;
        try {
            token = api.accessToken();
        } catch (KopiOnlineException _) {
            return; // signed out, or Kopi unreachable: try again in a minute
        }
        try {
            socket = http.newWebSocketBuilder()
                    .connectTimeout(properties.timeout())
                    .buildAsync(address(token), new Listener())
                    .get(properties.timeout().toSeconds() + 1, TimeUnit.SECONDS);
            backoff = Duration.ofSeconds(10);
            log.log(System.Logger.Level.INFO, "Connected to the relay's notifications");
            notifyNews(); // anything missed while disconnected
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.log(System.Logger.Level.DEBUG, "Relay notifications unavailable: " + e);
            retryLater();
        }
    }

    private void retryLater() {
        socket = null;
        if (stopped || scheduler.isShutdown()) {
            return;
        }
        var wait = backoff;
        backoff = backoff.multipliedBy(2).compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff.multipliedBy(2);
        scheduler.schedule(this::connectIfSignedIn, wait.toSeconds(), TimeUnit.SECONDS);
    }

    private void ping() {
        var current = socket;
        if (current != null) {
            current.sendText("{\"type\":\"ping\"}", true).exceptionally(_ -> {
                current.abort();
                return null;
            });
        }
    }

    private void notifyNews() {
        var listener = onNews;
        if (listener != null) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                log.log(System.Logger.Level.WARNING, "The relay's notification could not be handled", e);
            }
        }
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder text = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                var message = text.toString();
                text.setLength(0);
                try {
                    if ("game".equals(json.readTree(message).path("type").asString(""))) {
                        scheduler.execute(RelayWebSocket.this::notifyNews);
                    }
                } catch (RuntimeException _) {
                    // not JSON: ignored
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (socket == webSocket) {
                scheduler.execute(RelayWebSocket.this::retryLater);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (socket == webSocket) {
                scheduler.execute(RelayWebSocket.this::retryLater);
            }
        }
    }
}
