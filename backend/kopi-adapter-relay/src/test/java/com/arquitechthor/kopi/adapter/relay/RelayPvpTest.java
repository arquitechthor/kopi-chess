package com.arquitechthor.kopi.adapter.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.application.port.KopiOnlineException.Problem;
import com.arquitechthor.kopi.application.port.KopiSessionStore.StoredSession;
import com.arquitechthor.kopi.application.port.PvpRelay;
import com.arquitechthor.kopi.application.port.PvpRelay.Status;
import com.arquitechthor.kopi.domain.chess.Color;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/// The relay's protocol (`docs/protocolo-rele.md`) against a fake backend.
class RelayPvpTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final String GAME = """
            {"gameId": "g-1", "status": "ACTIVE", "white": {"kopiId": "Ana_92"}, "black": {"kopiId": "luis"},
             "myColor": "white", "invitedBy": "white", "initialFen": null, "firstMover": "white", "lastPly": 2,
             "lastMoveAt": "2026-11-07T11:59:00.000Z", "drawOfferBy": "black", "result": null, "termination": null,
             "finishedBy": null, "createdAt": "2026-11-07T11:00:00.000Z", "expiresAt": "2026-12-07T11:59:00.000Z",
             "moves": [{"ply": 2, "uci": "e7e5", "zobrist": "0123456789abcdef", "at": "2026-11-07T11:59:00.000Z"}]}""";

    private FakeKopi kopi;
    private PvpRelay relay;

    @BeforeEach
    void start() throws IOException {
        kopi = new FakeKopi();
        var sessions = new FakeKopi.MemorySessions();
        sessions.save(new StoredSession("access-1", "refresh-1", NOW.plusSeconds(3600), "sub-1", ""));
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var http = HttpClient.newHttpClient();
        var json = JsonMapper.builder().build();
        relay = new RelayPvp(new KopiApi(http, json, kopi.properties(), sessions,
                new CognitoTokens(http, json, kopi.properties()), clock));
    }

    @AfterEach
    void stop() {
        kopi.close();
    }

    @Test
    void aGameIsReadWithEveryField() {
        kopi.on("GET", "/chess/games/g-1", 200, GAME);

        var game = relay.game("g-1", 2);

        assertThat(kopi.requests.getFirst().path()).isEqualTo("/chess/games/g-1");
        assertThat(game.id()).isEqualTo("g-1");
        assertThat(game.status()).isEqualTo(Status.ACTIVE);
        assertThat(game.myColor()).isEqualTo(Color.WHITE);
        assertThat(game.opponentKopiId()).isEqualTo("luis");
        assertThat(game.invitedByMe()).isTrue();
        assertThat(game.initialFen()).isEmpty();
        assertThat(game.lastPly()).isEqualTo(2);
        assertThat(game.drawOfferBy()).hasValue(Color.BLACK);
        assertThat(game.lastMoveAt()).hasValue(Instant.parse("2026-11-07T11:59:00Z"));
        assertThat(game.moves()).singleElement().satisfies(move -> {
            assertThat(move.ply()).isEqualTo(2);
            assertThat(move.uci()).isEqualTo("e7e5");
            assertThat(move.zobrist()).isEqualTo("0123456789abcdef");
        });
    }

    @Test
    void anInvitationSendsTheOpponentAndMyColour() {
        kopi.on("POST", "/chess/games", 201, GAME.replace("\"ACTIVE\"", "\"INVITED\""));

        relay.invite("luis", null, Optional.empty());

        assertThat(kopi.requests("POST", "/chess/games").getFirst().body())
                .isEqualTo("{\"opponent\":\"luis\",\"color\":\"random\",\"initialFen\":null}");
    }

    @Test
    void aMoveSendsItsNumberHashAndEnding() {
        kopi.on("POST", "/chess/games/g-1/move", 200, GAME);

        relay.move("g-1", 3, "d8h4", "fedcba9876543210", Optional.of(new PvpRelay.Ending("0-1", "CHECKMATE")));

        assertThat(kopi.requests("POST", "/chess/games/g-1/move").getFirst().body()).isEqualTo(
                "{\"ply\":3,\"uci\":\"d8h4\",\"zobrist\":\"fedcba9876543210\","
                        + "\"end\":{\"result\":\"0-1\",\"termination\":\"CHECKMATE\"}}");
    }

    @Test
    void theListHasEveryGame() {
        kopi.on("GET", "/chess/games", 200, "{\"games\": [" + GAME + "," + GAME.replace("g-1", "g-2") + "]}");

        assertThat(relay.myGames()).extracting(PvpRelay.RelayGame::id).containsExactly("g-1", "g-2");
    }

    @Test
    void aWrongPlyIsSaidSo() {
        kopi.on("POST", "/chess/games/g-1/move", 409,
                "{\"status\":409,\"code\":\"WRONG_PLY\",\"detail\":\"Se esperaba la jugada 3.\"}");

        assertThatThrownBy(() -> relay.move("g-1", 4, "e2e4", "0123456789abcdef", Optional.empty()))
                .isInstanceOfSatisfying(KopiOnlineException.class,
                        e -> assertThat(e.problem()).isEqualTo(Problem.WRONG_PLY));
    }

    @Test
    void acknowledgingAGameAlreadyGoneIsDone() {
        kopi.on("POST", "/chess/games/g-1/ack", 404, "{\"status\":404,\"code\":\"GAME_NOT_FOUND\"}");
        kopi.on("POST", "/chess/games/g-2/ack", 204, "");

        relay.acknowledge("g-1");
        relay.acknowledge("g-2");

        assertThat(kopi.requests).hasSize(2);
    }

    @Test
    void theActionsUseTheirRoutes() {
        for (var action : new String[] {"accept", "decline", "resign", "offer-draw", "accept-draw", "decline-draw"}) {
            kopi.on("POST", "/chess/games/g-1/" + action, 200, GAME);
        }
        kopi.on("POST", "/chess/games/g-1/cancel", 204, "");

        relay.accept("g-1");
        relay.decline("g-1");
        relay.resign("g-1");
        relay.offerDraw("g-1");
        relay.acceptDraw("g-1");
        relay.declineDraw("g-1");
        relay.cancel("g-1");

        assertThat(kopi.requests).extracting(FakeKopi.Request::path).containsExactly(
                "/chess/games/g-1/accept", "/chess/games/g-1/decline", "/chess/games/g-1/resign",
                "/chess/games/g-1/offer-draw", "/chess/games/g-1/accept-draw", "/chess/games/g-1/decline-draw",
                "/chess/games/g-1/cancel");
    }

    @Test
    void theWebSocketAddressCarriesTheTokenAndTheProtocol() {
        var http = HttpClient.newHttpClient();
        var json = JsonMapper.builder().build();
        var api = new KopiApi(http, json, kopi.properties(), new FakeKopi.MemorySessions(),
                new CognitoTokens(http, json, kopi.properties()), Clock.fixed(NOW, ZoneOffset.UTC));
        var socket = new RelayWebSocket(http, json, kopi.properties(), api);

        assertThat(socket.address("a.b+c").toString()).isEqualTo("ws://127.0.0.1:1/prod?protocol=1&token=a.b%2Bc");
        socket.close();
    }
}
