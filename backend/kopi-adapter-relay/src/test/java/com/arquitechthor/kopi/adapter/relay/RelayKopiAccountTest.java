package com.arquitechthor.kopi.adapter.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.application.port.KopiAccount;
import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.application.port.KopiOnlineException.Problem;
import com.arquitechthor.kopi.application.port.KopiSessionStore.StoredSession;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/// The Kopi account against a fake Cognito and backend Kopi.
class RelayKopiAccountTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final String TOKENS = """
            {"access_token": "access-1", "refresh_token": "refresh-1", "expires_in": 3600,
             "id_token": "%s", "token_type": "Bearer"}"""
            .formatted(FakeKopi.jwt("{\"sub\": \"sub-1\", \"email\": \"ana@example.com\"}"));

    private FakeKopi kopi;
    private final FakeKopi.MemorySessions sessions = new FakeKopi.MemorySessions();
    private KopiAccount account;

    @BeforeEach
    void start() throws IOException {
        kopi = new FakeKopi();
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var http = HttpClient.newHttpClient();
        var json = JsonMapper.builder().build();
        var tokens = new CognitoTokens(http, json, kopi.properties());
        account = new RelayKopiAccount(tokens, new KopiApi(http, json, kopi.properties(), sessions, tokens, clock),
                sessions, clock);
    }

    @AfterEach
    void stop() {
        kopi.close();
    }

    private void signedIn(Instant expiresAt) {
        sessions.save(new StoredSession("access-1", "refresh-1", expiresAt, "sub-1", "ana@example.com"));
    }

    private static Map<String, String> query(URI uri) {
        return Arrays.stream(uri.getRawQuery().split("&")).map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(p -> p[0], p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8)));
    }

    @Test
    void signingInGoesToKopisLoginPageWithPkce() {
        var uri = account.beginLogin();

        assertThat(uri.getPath()).isEqualTo("/oauth2/authorize");
        assertThat(query(uri)).containsEntry("client_id", "test-client")
                .containsEntry("response_type", "code")
                .containsEntry("code_challenge_method", "S256")
                .containsEntry("redirect_uri", "http://localhost:8080/login/oauth2/code/kopi")
                .containsKeys("state", "code_challenge");
    }

    @Test
    void theCodeFromTheLoginPageGivesTheSession() {
        kopi.on("POST", "/oauth2/token", 200, TOKENS);
        var state = query(account.beginLogin()).get("state");
        var challenge = query(account.beginLogin()).get("code_challenge");

        account.completeLogin("the-code", state);

        assertThat(account.session()).hasValue(new KopiAccount.Session("sub-1", "ana@example.com"));
        assertThat(sessions.session.expiresAt()).isEqualTo(NOW.plusSeconds(3600));
        var form = kopi.requests("POST", "/oauth2/token").getFirst().form();
        assertThat(form).containsEntry("grant_type", "authorization_code").containsEntry("code", "the-code")
                .containsEntry("client_id", "test-client");
        // The verifier sent is the one whose challenge went to the login page (not the second login's).
        assertThat(RelayKopiAccount.challenge(form.get("code_verifier"))).isNotEqualTo(challenge);
    }

    @Test
    void anUnknownStateIsAFailedLoginAndKopiIsNotAsked() {
        assertThatThrownBy(() -> account.completeLogin("the-code", "made-up"))
                .isInstanceOfSatisfying(KopiOnlineException.class,
                        e -> assertThat(e.problem()).isEqualTo(Problem.LOGIN_FAILED));
        assertThat(kopi.requests).isEmpty();
    }

    @Test
    void aStateServesOnlyOnce() {
        kopi.on("POST", "/oauth2/token", 200, TOKENS);
        var state = query(account.beginLogin()).get("state");
        account.completeLogin("the-code", state);

        assertThatThrownBy(() -> account.completeLogin("the-code", state)).isInstanceOf(KopiOnlineException.class);
    }

    @Test
    void theKopiIdIsAskedWithTheTokenAndTheProtocolVersion() {
        signedIn(NOW.plusSeconds(3600));
        kopi.on("GET", "/kopi-id", 200, "{\"kopiId\": \"Ana_92\"}");

        assertThat(account.kopiId()).hasValue("Ana_92");
        var request = kopi.requests("GET", "/kopi-id").getFirst();
        assertThat(request.headers()).containsEntry("authorization", "Bearer access-1")
                .containsEntry("x-kopi-chess-protocol", "1");
    }

    @Test
    void noKopiIdYetIsEmpty() {
        signedIn(NOW.plusSeconds(3600));
        kopi.on("GET", "/kopi-id", 404, "{\"status\":404,\"code\":\"NO_KOPI_ID\"}");

        assertThat(account.kopiId()).isEmpty();
    }

    @Test
    void anAccessTokenAboutToExpireIsRenewedFirst() {
        signedIn(NOW.plusSeconds(30));
        kopi.on("POST", "/oauth2/token", 200, "{\"access_token\": \"access-2\", \"expires_in\": 3600}");
        kopi.on("GET", "/kopi-id", 200, "{\"kopiId\": \"Ana_92\"}");

        account.kopiId();

        assertThat(kopi.requests("POST", "/oauth2/token").getFirst().form())
                .containsEntry("grant_type", "refresh_token").containsEntry("refresh_token", "refresh-1");
        assertThat(kopi.requests("GET", "/kopi-id").getFirst().headers())
                .containsEntry("authorization", "Bearer access-2");
        assertThat(sessions.session.refreshToken()).isEqualTo("refresh-1"); // kept: Cognito sent no new one
    }

    @Test
    void aRefusedTokenIsRenewedOnceAndTheCallRepeated() {
        signedIn(NOW.plusSeconds(3600));
        kopi.on("POST", "/oauth2/token", 200, "{\"access_token\": \"access-2\", \"expires_in\": 3600}");
        kopi.on("GET", "/kopi-id", request -> request.headers().get("authorization").endsWith("access-2")
                ? new FakeKopi.Answer(200, "{\"kopiId\": \"Ana_92\"}")
                : new FakeKopi.Answer(401, "{\"message\":\"Unauthorized\"}"));

        assertThat(account.kopiId()).hasValue("Ana_92");
    }

    @Test
    void aRevokedSessionIsForgotten() {
        signedIn(NOW.plusSeconds(3600));
        kopi.on("POST", "/oauth2/token", 400, "{\"error\": \"invalid_grant\"}");
        kopi.on("GET", "/kopi-id", 401, "{\"message\":\"Unauthorized\"}");

        assertThatThrownBy(() -> account.kopiId()).isInstanceOfSatisfying(KopiOnlineException.class,
                e -> assertThat(e.problem()).isEqualTo(Problem.NOT_SIGNED_IN));
        assertThat(account.session()).isEmpty();
    }

    @Test
    void withoutASessionNothingIsCalled() {
        assertThatThrownBy(() -> account.kopiId()).isInstanceOfSatisfying(KopiOnlineException.class,
                e -> assertThat(e.problem()).isEqualTo(Problem.NOT_SIGNED_IN));
        assertThat(kopi.requests).isEmpty();
    }

    @Test
    void theRelaysCodesBecomeProblems() {
        signedIn(NOW.plusSeconds(3600));
        kopi.on("PUT", "/kopi-id", 409, "{\"status\":409,\"code\":\"KOPI_ID_TAKEN\",\"detail\":\"x\"}");
        kopi.on("GET", "/kopi-id", 403, "{\"status\":403,\"code\":\"PENDING_APPROVAL\"}");

        assertThatThrownBy(() -> account.reserveKopiId("Ana_92")).isInstanceOfSatisfying(KopiOnlineException.class,
                e -> assertThat(e.problem()).isEqualTo(Problem.KOPI_ID_TAKEN));
        assertThat(kopi.requests("PUT", "/kopi-id").getFirst().body()).isEqualTo("{\"kopiId\":\"Ana_92\"}");
        assertThatThrownBy(() -> account.kopiId()).isInstanceOfSatisfying(KopiOnlineException.class,
                e -> assertThat(e.problem()).isEqualTo(Problem.PENDING_APPROVAL));
    }

    @Test
    void anotherIdIsFoundOrNot() {
        signedIn(NOW.plusSeconds(3600));
        kopi.on("GET", "/kopi-id/luis", 200, "{\"kopiId\": \"Luis\"}");
        kopi.on("GET", "/kopi-id/nadie", 404, "{\"status\":404,\"code\":\"KOPI_ID_NOT_FOUND\"}");

        assertThat(account.findKopiId("luis")).hasValue("Luis");
        assertThat(account.findKopiId("nadie")).isEmpty();
    }

    @Test
    void anUnreachableKopiIsSaidSo() {
        signedIn(NOW.plusSeconds(3600));
        kopi.close();

        assertThatThrownBy(() -> account.kopiId()).isInstanceOfSatisfying(KopiOnlineException.class,
                e -> assertThat(e.problem()).isEqualTo(Problem.UNREACHABLE));
        assertThat(account.session()).isPresent(); // kept: it may work again later
    }

    @Test
    void signingOutRevokesForgetsAndEndsTheLoginPageSession() {
        signedIn(NOW.plusSeconds(3600));
        kopi.on("POST", "/oauth2/revoke", 200, "");

        var logout = account.signOut();

        assertThat(account.session()).isEmpty();
        assertThat(kopi.requests("POST", "/oauth2/revoke").getFirst().form()).containsEntry("token", "refresh-1");
        assertThat(logout.getPath()).isEqualTo("/logout");
        assertThat(query(logout)).containsEntry("logout_uri", "http://localhost:8080/");
    }
}
