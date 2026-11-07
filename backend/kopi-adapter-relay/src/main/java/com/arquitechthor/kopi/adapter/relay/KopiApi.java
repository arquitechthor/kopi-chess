package com.arquitechthor.kopi.adapter.relay;

import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.application.port.KopiOnlineException.Problem;
import com.arquitechthor.kopi.application.port.KopiSessionStore;
import com.arquitechthor.kopi.application.port.KopiSessionStore.StoredSession;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// Calls to the backend Kopi with the stored session (`docs/protocolo-rele.md`,
/// §1 and §2): the bearer token, the protocol version header, and every
/// error turned into a [KopiOnlineException] with the relay's `code`.
///
/// The access token is renewed a minute before it expires, and once more if
/// Kopi answers `401` anyway. If the refresh token no longer works either,
/// the session is forgotten: `NOT_SIGNED_IN`.
final class KopiApi {

    /// The version of the relay's protocol this Kopi Chess speaks.
    static final String PROTOCOL = "1";

    private static final Duration RENEW_BEFORE = Duration.ofMinutes(1);

    private final HttpClient http;
    private final JsonMapper json;
    private final RelayProperties properties;
    private final KopiSessionStore sessions;
    private final CognitoTokens tokens;
    private final Clock clock;

    KopiApi(HttpClient http, JsonMapper json, RelayProperties properties, KopiSessionStore sessions,
            CognitoTokens tokens, Clock clock) {
        this.http = Objects.requireNonNull(http, "http");
        this.json = Objects.requireNonNull(json, "json");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /// The answer's JSON, or `null` for `204 No Content`.
    ///
    /// @param body a record or map to send as JSON, or `null`
    /// @throws KopiOnlineException with the relay's code, `NOT_SIGNED_IN`
    ///                             or `UNREACHABLE`
    JsonNode call(String method, String path, Object body) {
        return answer(exchange(method, path, body));
    }

    /// Like [#call], but an answer `409` with `code` = `conflictCode` comes
    /// back as such instead of as an exception: the history's
    /// `REV_CONFLICT` carries the current entry (protocol §9.4).
    Answer callAllowingConflict(String method, String path, Object body, String conflictCode) {
        var response = exchange(method, path, body);
        if (response.statusCode() == 409) {
            JsonNode node;
            try {
                node = json.readTree(response.body());
            } catch (RuntimeException e) {
                node = null;
            }
            if (node != null && conflictCode.equals(CognitoTokens.text(node, "code"))) {
                return new Answer(409, node);
            }
        }
        return new Answer(response.statusCode(), answer(response));
    }

    /// An answer's status and JSON (`null` for `204`).
    record Answer(int status, JsonNode body) {}

    private HttpResponse<String> exchange(String method, String path, Object body) {
        var session = sessions.load()
                .orElseThrow(() -> new KopiOnlineException(Problem.NOT_SIGNED_IN, "No Kopi session"));
        if (session.expiresAt().minus(RENEW_BEFORE).isBefore(clock.instant())) {
            session = renew(session);
        }
        var response = send(method, path, body, session.accessToken());
        if (response.statusCode() == 401) {
            response = send(method, path, body, renew(session).accessToken());
            if (response.statusCode() == 401) {
                sessions.clear();
                throw new KopiOnlineException(Problem.NOT_SIGNED_IN, "Kopi does not accept this session");
            }
        }
        return response;
    }

    /// A valid access token for the stored session (renewed if it is about
    /// to expire), for the WebSocket, which cannot renew it on a `401`.
    ///
    /// @throws KopiOnlineException `NOT_SIGNED_IN`, `UNREACHABLE`
    String accessToken() {
        var session = sessions.load()
                .orElseThrow(() -> new KopiOnlineException(Problem.NOT_SIGNED_IN, "No Kopi session"));
        if (session.expiresAt().minus(RENEW_BEFORE).isBefore(clock.instant())) {
            session = renew(session);
        }
        return session.accessToken();
    }

    /// A new access token for `session`, stored; forgets the session if
    /// the refresh token no longer works.
    private StoredSession renew(StoredSession session) {
        try {
            var renewed = tokens.refresh(session.refreshToken());
            var stored = new StoredSession(renewed.accessToken(),
                    renewed.refreshToken() != null ? renewed.refreshToken() : session.refreshToken(),
                    clock.instant().plusSeconds(renewed.expiresInSeconds()), session.sub(), session.email());
            sessions.save(stored);
            return stored;
        } catch (KopiOnlineException e) {
            if (e.problem() == Problem.NOT_SIGNED_IN) {
                sessions.clear();
            }
            throw e;
        }
    }

    private HttpResponse<String> send(String method, String path, Object body, String accessToken) {
        var builder = HttpRequest.newBuilder(URI.create(properties.api() + path))
                .timeout(properties.timeout())
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Kopi-Chess-Protocol", PROTOCOL)
                .header("Accept", "application/json");
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        }
        try {
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new KopiOnlineException(Problem.UNREACHABLE, "Cannot reach Kopi", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KopiOnlineException(Problem.UNREACHABLE, "Interrupted", e);
        }
    }

    private JsonNode answer(HttpResponse<String> response) {
        var status = response.statusCode();
        if (status == 204) {
            return null;
        }
        JsonNode node;
        try {
            node = response.body() == null || response.body().isBlank() ? json.createObjectNode()
                    : json.readTree(response.body());
        } catch (RuntimeException e) {
            throw new KopiOnlineException(Problem.OTHER, "Kopi answered " + status + " with no JSON", e);
        }
        if (status >= 200 && status < 300) {
            return node;
        }
        var code = CognitoTokens.text(node, "code");
        var detail = CognitoTokens.text(node, "detail");
        throw new KopiOnlineException(problem(status, code), "Kopi answered " + status + " " + code
                + (detail == null ? "" : ": " + detail));
    }

    /// The problem for an error answer: the relay's code if this Kopi Chess
    /// knows it, else one from the status.
    static Problem problem(int status, String code) {
        if (code != null) {
            try {
                return Problem.valueOf(code);
            } catch (IllegalArgumentException _) {
                // a code from a newer relay
            }
        }
        return switch (status) {
            case 403 -> Problem.PENDING_APPROVAL;
            case 404 -> Problem.GAME_NOT_FOUND;
            case 426 -> Problem.PROTOCOL_MISMATCH;
            case 429 -> Problem.TOO_MANY_REQUESTS;
            case 502, 503, 504 -> Problem.UNREACHABLE;
            default -> Problem.OTHER;
        };
    }
}
