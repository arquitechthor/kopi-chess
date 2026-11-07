package com.arquitechthor.kopi.adapter.relay;

import com.arquitechthor.kopi.application.port.KopiAccount;
import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.application.port.KopiOnlineException.Problem;
import com.arquitechthor.kopi.application.port.KopiSessionStore;
import com.arquitechthor.kopi.application.port.KopiSessionStore.StoredSession;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/// [KopiAccount] over Cognito and the backend Kopi (ADR-019).
///
/// Signing in is Authorization Code with PKCE: the verifier waits here, in
/// memory, keyed by the `state` sent to the login page, for at most ten
/// minutes. Kopi Chess is for one person on one computer, so that is enough;
/// a restart in between just means signing in again.
final class RelayKopiAccount implements KopiAccount {

    private static final Duration LOGIN_TIMEOUT = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private record Pending(String verifier, Instant startedAt) {}

    private final CognitoTokens tokens;
    private final KopiApi api;
    private final KopiSessionStore sessions;
    private final Clock clock;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    RelayKopiAccount(CognitoTokens tokens, KopiApi api, KopiSessionStore sessions, Clock clock) {
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.api = Objects.requireNonNull(api, "api");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public URI beginLogin() {
        var now = clock.instant();
        pending.values().removeIf(login -> login.startedAt().plus(LOGIN_TIMEOUT).isBefore(now));
        var verifier = random(48);
        var state = random(16);
        pending.put(state, new Pending(verifier, now));
        return tokens.authorizeUri(state, challenge(verifier));
    }

    @Override
    public void completeLogin(String code, String state) {
        var login = state == null ? null : pending.remove(state);
        if (login == null || login.startedAt().plus(LOGIN_TIMEOUT).isBefore(clock.instant())) {
            throw new KopiOnlineException(Problem.LOGIN_FAILED, "Unknown or expired sign-in");
        }
        var received = tokens.exchange(code, login.verifier());
        if (received.refreshToken() == null || received.idToken() == null) {
            throw new KopiOnlineException(Problem.LOGIN_FAILED, "Cognito gave no refresh or ID token");
        }
        var claims = tokens.claims(received.idToken());
        var sub = CognitoTokens.text(claims, "sub");
        if (sub == null) {
            throw new KopiOnlineException(Problem.LOGIN_FAILED, "The ID token names no account");
        }
        sessions.save(new StoredSession(received.accessToken(), received.refreshToken(),
                clock.instant().plusSeconds(received.expiresInSeconds()), sub, CognitoTokens.text(claims, "email")));
    }

    @Override
    public Optional<Session> session() {
        return sessions.load().map(stored -> new Session(stored.sub(), stored.email()));
    }

    @Override
    public Optional<String> kopiId() {
        try {
            return Optional.ofNullable(CognitoTokens.text(api.call("GET", "/kopi-id", null), "kopiId"));
        } catch (KopiOnlineException e) {
            if (e.problem() == Problem.NO_KOPI_ID) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public String reserveKopiId(String kopiId) {
        return CognitoTokens.text(api.call("PUT", "/kopi-id", Map.of("kopiId", kopiId)), "kopiId");
    }

    @Override
    public Optional<String> findKopiId(String kopiId) {
        try {
            var path = "/kopi-id/" + URLEncoder.encode(kopiId, StandardCharsets.UTF_8);
            return Optional.ofNullable(CognitoTokens.text(api.call("GET", path, null), "kopiId"));
        } catch (KopiOnlineException e) {
            if (e.problem() == Problem.KOPI_ID_NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public URI signOut() {
        sessions.load().ifPresent(stored -> tokens.revoke(stored.refreshToken()));
        sessions.clear();
        return tokens.logoutUri();
    }

    private static String random(int bytes) {
        var buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    /// PKCE's S256 challenge of `verifier`.
    static String challenge(String verifier) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JDK has SHA-256", e);
        }
    }
}
