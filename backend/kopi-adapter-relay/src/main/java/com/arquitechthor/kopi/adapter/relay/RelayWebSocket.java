package com.arquitechthor.kopi.adapter.relay;

import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.application.port.RelayNotifications;
import com.arquitechthor.kopi.application.port.ServerConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// The relay's WebSocket (`docs/protocolo-rele.md`, §7 and §10), with the
/// JDK's WebSocket client: [RelayNotifications] always, and
/// [ServerConnection] while server mode is on (ADR-022). One connection
/// does both: `role=notify` (the default) or `role=server`.
///
/// - Connects only while there is a Kopi session; checks again every minute.
/// - Sends `{"type": "ping"}` every 5 minutes: API Gateway closes a silent
///   connection after 10.
/// - Reconnects when the connection closes (API Gateway closes every
///   connection after 2 hours), waiting longer after each failure, up to 5
///   minutes; and calls the listener after reconnecting, since a
///   notification may have been missed in between.
/// - Turning server mode on or off, or renaming, reconnects at once with
///   the new role.
/// - Answers each request from the app on a virtual thread (a bot's move
///   takes a while), never on the WebSocket's or the scheduler's thread.
///   Sends go one after another, as the JDK's WebSocket requires.
final class RelayWebSocket implements RelayNotifications, ServerConnection, AutoCloseable {

    private static final System.Logger log = System.getLogger(RelayWebSocket.class.getName());
    private static final Duration PING_EVERY = Duration.ofMinutes(5);
    private static final Duration CHECK_EVERY = Duration.ofMinutes(1);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    /// The longest `name` the relay keeps (§10.1).
    static final int MAX_NAME = 40;

    private final HttpClient http;
    private final JsonMapper json;
    private final RelayProperties properties;
    private final KopiApi api;
    private final RemoteRelayMessages remote;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        var thread = new Thread(runnable, "kopi-relay-websocket");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService requests = Executors.newVirtualThreadPerTaskExecutor();

    private volatile Runnable onNews;
    private volatile WebSocket socket;
    private volatile boolean stopped;
    /// The name while serving; `null` while not.
    private volatile String serverName;
    private boolean running;
    private Duration backoff = Duration.ofSeconds(10);
    private CompletableFuture<?> lastSend = CompletableFuture.completedFuture(null);

    RelayWebSocket(HttpClient http, JsonMapper json, RelayProperties properties, KopiApi api,
            RemoteRelayMessages remote) {
        this.http = Objects.requireNonNull(http, "http");
        this.json = Objects.requireNonNull(json, "json");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.api = Objects.requireNonNull(api, "api");
        this.remote = Objects.requireNonNull(remote, "remote");
    }

    @Override
    public synchronized void listen(Runnable listener) {
        this.onNews = Objects.requireNonNull(listener, "listener");
        ensureRunning();
        scheduler.execute(this::connectIfSignedIn);
    }

    @Override
    public synchronized void serve(String name) {
        var trimmed = name == null || name.isBlank() ? "Kopi Chess" : name.strip();
        trimmed = trimmed.length() > MAX_NAME ? trimmed.substring(0, MAX_NAME) : trimmed;
        if (trimmed.equals(serverName)) {
            ensureRunning();
            scheduler.execute(this::connectIfSignedIn);
            return;
        }
        serverName = trimmed;
        log.log(System.Logger.Level.INFO, "Server mode on");
        reconnect();
    }

    @Override
    public synchronized void stopServing() {
        if (serverName == null) {
            return;
        }
        serverName = null;
        log.log(System.Logger.Level.INFO, "Server mode off");
        reconnect();
    }

    @Override
    public boolean serving() {
        return serverName != null;
    }

    @Override
    public synchronized void stop() {
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
        requests.shutdownNow();
    }

    /// The address to connect to: the token goes in the query (§7), and
    /// the role and name while serving (§10.1).
    URI address(String accessToken) {
        var address = new StringBuilder(properties.webSocket().toString())
                .append("?protocol=").append(KopiApi.PROTOCOL)
                .append("&token=").append(URLEncoder.encode(accessToken, StandardCharsets.UTF_8));
        var name = serverName;
        if (name != null) {
            address.append("&role=server&name=").append(URLEncoder.encode(name, StandardCharsets.UTF_8));
        }
        return URI.create(address.toString());
    }

    /// Starts the periodic checks and pings, once. Connecting now is up to
    /// the caller.
    private void ensureRunning() {
        stopped = false;
        if (running) {
            return;
        }
        running = true;
        scheduler.scheduleWithFixedDelay(this::connectIfSignedIn, CHECK_EVERY.toSeconds(), CHECK_EVERY.toSeconds(),
                TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::ping, PING_EVERY.toSeconds(), PING_EVERY.toSeconds(), TimeUnit.SECONDS);
    }

    /// Drops the connection, if any, and connects again with the current
    /// role. On the scheduler's thread, like connecting: a connection still
    /// being opened is dropped once it is open, never kept with the old
    /// role.
    private void reconnect() {
        ensureRunning();
        scheduler.execute(() -> {
            var current = socket;
            socket = null;
            if (current != null) {
                current.abort();
            }
            backoff = Duration.ofSeconds(10);
            connectIfSignedIn();
        });
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
            log.log(System.Logger.Level.INFO, serving() ? "Connected to the relay as a server"
                    : "Connected to the relay's notifications");
            notifyNews(); // anything missed while disconnected
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.log(System.Logger.Level.DEBUG, "Relay unavailable: " + e);
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
            send(current, "{\"type\":\"ping\"}");
        }
    }

    /// Sends after the previous send has finished; a failed send drops the
    /// connection, which reconnects.
    private synchronized void send(WebSocket to, String text) {
        lastSend = lastSend.handle((_, _) -> null)
                .thenCompose(_ -> to.sendText(text, true))
                .exceptionally(error -> {
                    log.log(System.Logger.Level.DEBUG, "Relay send failed: " + error);
                    to.abort();
                    return null;
                });
    }

    /// Answers a request from the app, on its own virtual thread.
    private void answer(WebSocket from, JsonNode message) {
        try {
            requests.execute(() -> {
                try {
                    remote.answer(message, serving()).ifPresent(reply -> send(from, reply));
                } catch (RuntimeException e) {
                    log.log(System.Logger.Level.WARNING, "A remote request could not be answered", e);
                }
            });
        } catch (RejectedExecutionException _) {
            // shutting down
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
                    var node = json.readTree(message);
                    if ("game".equals(node.path("type").asString(""))) {
                        scheduler.execute(RelayWebSocket.this::notifyNews);
                    } else if (RemoteRelayMessages.isRequest(node)) {
                        answer(webSocket, node);
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
