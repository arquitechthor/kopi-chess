package com.arquitechthor.kopi.adapter.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.application.history.GameRecord;
import com.arquitechthor.kopi.application.port.AccountHistory;
import com.arquitechthor.kopi.application.port.AccountHistory.DeleteResult;
import com.arquitechthor.kopi.application.port.AccountHistory.PutResult;
import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.application.port.KopiOnlineException.Problem;
import com.arquitechthor.kopi.application.port.KopiSessionStore.StoredSession;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.Player;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/// The history in the Kopi account (`docs/protocolo-rele.md` §9) against a
/// fake backend.
class RelayAccountHistoryTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final String ID = "0b6c5a52-4a9e-4d0e-9a51-2f3c7c1d8e10";

    private FakeKopi kopi;
    private AccountHistory history;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void start() throws IOException {
        kopi = new FakeKopi();
        var sessions = new FakeKopi.MemorySessions();
        sessions.save(new StoredSession("access-1", "refresh-1", NOW.plusSeconds(3600), "sub-1", ""));
        var http = HttpClient.newHttpClient();
        history = new RelayAccountHistory(new KopiApi(http, json, kopi.properties(), sessions,
                new CognitoTokens(http, json, kopi.properties()), Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    @AfterEach
    void stop() {
        kopi.close();
    }

    private static GameRecord sample() {
        var game = Game.start(new GameId(UUID.fromString(ID)), new Player.Local(""), new Player.Bot(BotProfile.KNIGHT),
                Position.initial(), NOW);
        game = game.play("e2e4", game.currentPosition().sideToMove(), NOW.plusSeconds(5));
        return GameRecord.of(game);
    }

    @Test
    void aGameGoesUpAsItsOwnJsonAndComesBackTheSame() throws Exception {
        kopi.on("PUT", "/chess/history/" + ID, 200,
                "{\"gameId\": \"" + ID + "\", \"rev\": 3, \"deleted\": false, \"changedAt\": \"2026-11-07T12:00:00.000Z\"}");

        var result = history.put(new GameId(UUID.fromString(ID)), 2, sample());

        assertThat(result).isEqualTo(new PutResult.Stored(3));
        var sent = json.readTree(kopi.requests("PUT", "/chess/history/" + ID).getFirst().body());
        assertThat(sent.path("rev").asLong()).isEqualTo(2);
        assertThat(sent.path("data").path("format").asInt()).isEqualTo(1);
        assertThat(sent.path("data").path("moves").get(0).path("uci").asString()).isEqualTo("e2e4");
        assertThat(RelayAccountHistory.record(sent.path("data"))).isEqualTo(sample());
    }

    @Test
    void theChangesArePagedWithTheCursor() {
        var data = json.writeValueAsString(RelayAccountHistory.data(sample()));
        kopi.on("GET", "/chess/history", 200, """
                {"entries": [
                   {"gameId": "%s", "rev": 2, "deleted": false, "changedAt": "2026-11-07T12:00:00.000Z", "data": %s},
                   {"gameId": "1b6c5a52-4a9e-4d0e-9a51-2f3c7c1d8e10", "rev": 4, "deleted": true, "changedAt": "2026-11-07T12:00:00.000Z", "data": null},
                   {"gameId": "2b6c5a52-4a9e-4d0e-9a51-2f3c7c1d8e10", "rev": 1, "deleted": false, "changedAt": "2026-11-07T12:00:00.000Z", "data": {"format": 2}}
                 ],
                 "cursor": "000017625401234#", "more": true, "tombstoneDays": 180}""".formatted(ID, data));

        var changes = history.changes("000017625400000#abc");

        assertThat(kopi.requests.getFirst().query()).isEqualTo("limit=200&since=000017625400000%23abc");
        assertThat(changes.cursor()).isEqualTo("000017625401234#");
        assertThat(changes.more()).isTrue();
        assertThat(changes.entries()).hasSize(3);
        assertThat(changes.entries().get(0).game()).hasValue(sample());
        assertThat(changes.entries().get(1).deleted()).isTrue();
        assertThat(changes.entries().get(2).game()).as("a newer format is left alone").isEmpty();
    }

    @Test
    void aConflictBringsTheCurrentEntry() {
        var data = json.writeValueAsString(RelayAccountHistory.data(sample()));
        kopi.on("PUT", "/chess/history/" + ID, 409, """
                {"status": 409, "error": "Conflict", "code": "REV_CONFLICT", "detail": "x",
                 "current": {"gameId": "%s", "rev": 5, "deleted": false, "changedAt": "2026-11-07T12:00:00.000Z", "data": %s}}"""
                .formatted(ID, data));

        var result = history.put(new GameId(UUID.fromString(ID)), 0, sample());

        assertThat(result).isInstanceOfSatisfying(PutResult.Conflict.class, conflict -> {
            assertThat(conflict.current()).hasValueSatisfying(entry -> {
                assertThat(entry.rev()).isEqualTo(5);
                assertThat(entry.game()).hasValue(sample());
            });
        });
    }

    @Test
    void aConflictWithoutTheGameSaysSo() {
        kopi.on("PUT", "/chess/history/" + ID, 409,
                "{\"status\": 409, \"code\": \"REV_CONFLICT\", \"current\": null}");

        var result = history.put(new GameId(UUID.fromString(ID)), 4, sample());

        assertThat(result).isEqualTo(new PutResult.Conflict(java.util.Optional.empty()));
    }

    @Test
    void aDeletionSendsTheRevision() {
        kopi.on("DELETE", "/chess/history/" + ID, 204, "");

        assertThat(history.delete(new GameId(UUID.fromString(ID)), 7)).isEqualTo(new DeleteResult.Done());
        assertThat(kopi.requests.getFirst().query()).isEqualTo("rev=7");
    }

    @Test
    void otherErrorsAreTheRelayProblems() {
        kopi.on("PUT", "/chess/history/" + ID, 413, "{\"status\": 413, \"code\": \"TOO_LARGE\"}");

        assertThatThrownBy(() -> history.put(new GameId(UUID.fromString(ID)), 0, sample()))
                .isInstanceOf(KopiOnlineException.class)
                .extracting(e -> ((KopiOnlineException) e).problem()).isEqualTo(Problem.TOO_LARGE);
    }

    @Test
    void deletingEverythingCallsTheWholeHistory() {
        kopi.on("DELETE", "/chess/history", 204, "");

        history.deleteAll();

        assertThat(kopi.requests("DELETE", "/chess/history")).hasSize(1);
    }
}
