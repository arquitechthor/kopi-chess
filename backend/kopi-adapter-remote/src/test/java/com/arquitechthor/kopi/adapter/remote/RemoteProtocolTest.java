package com.arquitechthor.kopi.adapter.remote;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.application.game.GameNotFoundException;
import com.arquitechthor.kopi.application.game.GamePage;
import com.arquitechthor.kopi.application.game.GameSummary;
import com.arquitechthor.kopi.application.game.GameView;
import com.arquitechthor.kopi.application.game.VersionMismatchException;
import com.arquitechthor.kopi.application.port.BotRecord;
import com.arquitechthor.kopi.application.port.BotUnavailableException;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.Player;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// The remote protocol against use cases kept in memory, with the domain's
/// real games: the allow list, the errors and the shape of the JSON.
class RemoteProtocolTest {

    private static final String PHONE = "phone-1";
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<GameId, Game> games = new LinkedHashMap<>();
    private final AtomicReference<RuntimeException> botFailure = new AtomicReference<>();
    private RemoteProtocol protocol;

    @BeforeEach
    void start() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/remote/messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        var useCases = new RemoteProtocol.UseCases(
                () -> BotRecord.tally(games.values().stream()),
                query -> {
                    var found = games.values().stream().filter(query.filter()::matches)
                            .sorted(Comparator.comparing(Game::createdAt).reversed())
                            .map(game -> summary(game)).toList();
                    var from = Math.min(found.size(), query.page() * query.size());
                    var to = Math.min(found.size(), from + query.size());
                    return new GamePage(found.subList(from, to), query.page(), query.size(), found.size());
                },
                this::view,
                command -> save(Game.start(newId(), command.humanColor(), command.botProfile(), Position.initial(),
                        NOW)),
                command -> {
                    var game = load(command.gameId(), command.expectedVersion());
                    if (botFailure.get() != null) {
                        throw botFailure.get();
                    }
                    return save(game.play(command.uci(), game.humanColor().orElseThrow(), NOW));
                },
                command -> {
                    var game = load(command.gameId(), command.expectedVersion());
                    return save(game.resign(game.humanColor().orElseThrow(), NOW));
                });
        protocol = new RemoteProtocol(json, messages, useCases, "1.2.0", () -> "PC de casa");
    }

    // --- the fake use cases ------------------------------------------------

    private static GameId newId() {
        return new GameId(UUID.randomUUID());
    }

    private GameView view(GameId id) {
        var game = games.get(id);
        if (game == null) {
            throw new GameNotFoundException(id);
        }
        return GameView.of(game);
    }

    private Game load(GameId id, long expectedVersion) {
        var game = games.get(id);
        if (game == null) {
            throw new GameNotFoundException(id);
        }
        if (game.version() != expectedVersion) {
            throw new VersionMismatchException(id, expectedVersion, game.version());
        }
        return game;
    }

    private GameView save(Game game) {
        games.put(game.id(), game);
        return GameView.of(game);
    }

    private static GameSummary summary(Game game) {
        return new GameSummary(game.id(), game.origin(), game.status(), game.mode(), game.player(Color.WHITE),
                game.player(Color.BLACK), game.humanColor(), game.botProfile(), game.moves().size(), game.outcome(),
                game.result(), game.createdAt(), game.finishedAt(), game.pgnTags());
    }

    private Game botGame(Instant createdAt) {
        var game = Game.start(newId(), Color.WHITE, BotProfile.KNIGHT, Position.initial(), createdAt);
        games.put(game.id(), game);
        return game;
    }

    private Game localGame() {
        var game = Game.start(newId(), new Player.Local("Ana"), new Player.Local("Luis"), Position.initial(), NOW);
        games.put(game.id(), game);
        return game;
    }

    // --- helpers -----------------------------------------------------------

    private JsonNode send(String payload) {
        return json.readTree(protocol.handle(PHONE, payload));
    }

    private JsonNode send(String op, String fields) {
        return send("{\"v\": 1, \"op\": \"" + op + "\"" + (fields.isEmpty() ? "" : ", " + fields) + "}");
    }

    private static void assertError(JsonNode response, String code) {
        assertThat(response.path("v").intValue()).isEqualTo(1);
        assertThat(response.path("ok").booleanValue()).isFalse();
        assertThat(response.path("error").stringValue()).isEqualTo(code);
        assertThat(response.path("detail").stringValue()).isNotBlank();
    }

    private static void assertOk(JsonNode response) {
        assertThat(response.path("v").intValue()).isEqualTo(1);
        assertThat(response.path("ok").booleanValue()).as(response.toString()).isTrue();
    }

    // --- the protocol ------------------------------------------------------

    @Test
    void helloSaysWhoAnswers() {
        var response = send("hello", "\"lang\": \"es\", \"appVersion\": \"1.2.0\"");

        assertOk(response);
        assertThat(response.path("kopiChessVersion").stringValue()).isEqualTo("1.2.0");
        assertThat(response.path("remoteVersion").intValue()).isEqualTo(1);
        assertThat(response.path("name").stringValue()).isEqualTo("PC de casa");
    }

    @Test
    void botsAreDescribedInTheLanguageOfHello() {
        botGame(NOW);

        var english = send("bots", "");
        send("hello", "\"lang\": \"es\"");
        var spanish = send("bots", "");

        assertOk(english);
        assertThat(english.path("bots")).hasSize(5);
        var knight = english.path("bots").get(1);
        assertThat(knight.path("profile").stringValue()).isEqualTo("KNIGHT");
        assertThat(knight.path("level").intValue()).isEqualTo(2);
        assertThat(knight.path("description").stringValue()).startsWith("Looks a couple");
        assertThat(knight.path("record").path("unfinished").intValue()).isEqualTo(1);
        assertThat(spanish.path("bots").get(1).path("description").stringValue()).startsWith("Mira un par");
    }

    @Test
    void anUnknownLanguageIsEnglish() {
        send("hello", "\"lang\": \"fr\"");

        assertThat(send("bots", "").path("bots").get(0).path("description").stringValue()).startsWith("Plays like");
    }

    @Test
    void gamesListsOnlyGamesInProgressAgainstABotNewestFirstWithoutMoves() {
        var older = botGame(NOW.minusSeconds(60));
        var newer = botGame(NOW);
        localGame();
        var finished = botGame(NOW.plusSeconds(60));
        games.put(finished.id(), finished.resign(Color.WHITE, NOW));

        var response = send("games", "");

        assertOk(response);
        assertThat(response.path("games")).hasSize(2);
        assertThat(response.path("games").get(0).path("id").stringValue()).isEqualTo(newer.id().toString());
        assertThat(response.path("games").get(1).path("id").stringValue()).isEqualTo(older.id().toString());
        assertThat(response.path("games").get(0).has("moves")).isFalse();
        assertThat(response.path("games").get(0).path("fen").stringValue()).startsWith("rnbqkbnr");
    }

    @Test
    void gamesListsAtMostThirty() {
        for (int i = 0; i < RemoteProtocol.MAX_GAMES + 5; i++) {
            botGame(NOW.plusSeconds(i));
        }

        assertThat(send("games", "").path("games")).hasSize(RemoteProtocol.MAX_GAMES);
    }

    @Test
    void newStartsAGameAgainstABot() {
        var response = send("new", "\"botProfile\": \"ROOK\", \"humanColor\": \"WHITE\"");

        assertOk(response);
        var game = response.path("game");
        assertThat(game.path("status").stringValue()).isEqualTo("IN_PROGRESS");
        assertThat(game.path("botProfile").stringValue()).isEqualTo("ROOK");
        assertThat(game.path("humanColor").stringValue()).isEqualTo("WHITE");
        assertThat(game.path("humanToMove").booleanValue()).isTrue();
        assertThat(game.path("moves")).isEmpty();
        assertThat(game.path("lastMove").isNull()).isTrue();
        assertThat(game.path("legalMoves")).hasSize(20);
        assertThat(game.path("legalMoves").get(0).isString()).isTrue();
        assertThat(game.path("outcome").isNull()).isTrue();
        assertThat(game.path("result").isNull()).isTrue();
        assertThat(game.path("version").isIntegralNumber()).isTrue();
        assertThat(game.path("createdAt").stringValue()).isEqualTo("2026-11-11T10:00:00Z");
        assertThat(games).hasSize(1);
    }

    @Test
    void randomPicksAColour() {
        var response = send("new", "\"botProfile\": \"PAWN\", \"humanColor\": \"RANDOM\"");

        assertOk(response);
        assertThat(response.path("game").path("humanColor").stringValue()).isIn("WHITE", "BLACK");
    }

    @Test
    void gameGivesTheGameWithItsMoves() {
        var game = botGame(NOW);
        games.put(game.id(), game.play("e2e4", Color.WHITE, NOW));

        var response = send("game", "\"gameId\": \"" + game.id() + "\"");

        assertOk(response);
        assertThat(response.path("game").path("moves").get(0).path("san").stringValue()).isEqualTo("e4");
        assertThat(response.path("game").path("lastMove").path("uci").stringValue()).isEqualTo("e2e4");
    }

    @Test
    void movePlaysTheMove() {
        var game = botGame(NOW);

        var response = send("move", "\"gameId\": \"" + game.id() + "\", \"uci\": \"e2e4\", \"version\": "
                + game.version());

        assertOk(response);
        assertThat(response.path("game").path("lastMove").path("san").stringValue()).isEqualTo("e4");
        assertThat(games.get(game.id()).moves()).hasSize(1);
    }

    @Test
    void resignEndsTheGame() {
        var game = botGame(NOW);

        var response = send("resign", "\"gameId\": \"" + game.id() + "\", \"version\": " + game.version());

        assertOk(response);
        assertThat(response.path("game").path("status").stringValue()).isEqualTo("FINISHED");
        assertThat(response.path("game").path("outcome").path("termination").stringValue()).isEqualTo("RESIGNATION");
        assertThat(response.path("game").path("outcome").path("winner").stringValue()).isEqualTo("BLACK");
        assertThat(response.path("game").path("result").stringValue()).isEqualTo("BLACK_WIN");
        assertThat(response.path("game").path("legalMoves")).isEmpty();
    }

    // --- the allow list ----------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"delete", "history", "settings", "analysis", "import", "online", "HELLO"})
    void anythingOffTheAllowListIsNotAllowed(String op) {
        assertError(send(op, ""), "NOT_ALLOWED");
    }

    @Test
    void aGameThatIsNotAgainstABotIsNotAllowed() {
        var local = localGame();
        var id = "\"gameId\": \"" + local.id() + "\"";

        assertError(send("game", id), "NOT_ALLOWED");
        assertError(send("move", id + ", \"uci\": \"e2e4\", \"version\": " + local.version()), "NOT_ALLOWED");
        assertError(send("resign", id + ", \"version\": " + local.version()), "NOT_ALLOWED");
        assertThat(games.get(local.id())).isEqualTo(local);
    }

    // --- errors ------------------------------------------------------------

    @Test
    void anotherVersionOfTheProtocolIsAMismatch() {
        assertError(send("{\"v\": 2, \"op\": \"hello\"}"), "VERSION_MISMATCH");
        assertError(send("{\"op\": \"hello\"}"), "VERSION_MISMATCH");
        assertError(send("{\"v\": \"1\", \"op\": \"hello\"}"), "VERSION_MISMATCH");
    }

    @Test
    void malformedRequestsAreBadRequests() {
        var game = botGame(NOW);
        var id = "\"gameId\": \"" + game.id() + "\"";

        assertError(send("not json"), "BAD_REQUEST");
        assertError(send("[1, 2]"), "BAD_REQUEST");
        assertError(send(null), "BAD_REQUEST");
        assertError(send("{\"v\": 1}"), "BAD_REQUEST");
        assertError(send("new", "\"botProfile\": \"KING\", \"humanColor\": \"WHITE\""), "BAD_REQUEST");
        assertError(send("new", "\"botProfile\": \"PAWN\", \"humanColor\": \"GREEN\""), "BAD_REQUEST");
        assertError(send("new", "\"botProfile\": \"PAWN\""), "BAD_REQUEST");
        assertError(send("game", "\"gameId\": \"not-a-uuid\""), "BAD_REQUEST");
        assertError(send("game", ""), "BAD_REQUEST");
        assertError(send("move", id + ", \"uci\": \"e2e4\""), "BAD_REQUEST");
        assertError(send("move", id + ", \"uci\": \"hello\", \"version\": " + game.version()), "BAD_REQUEST");
        assertError(send("resign", id), "BAD_REQUEST");
    }

    @Test
    void anUnknownGameIsNotFound() {
        assertError(send("game", "\"gameId\": \"" + UUID.randomUUID() + "\""), "NOT_FOUND");
    }

    @Test
    void aStaleVersionIsAConflict() {
        var game = botGame(NOW);

        assertError(send("move", "\"gameId\": \"" + game.id() + "\", \"uci\": \"e2e4\", \"version\": "
                + (game.version() + 1)), "CONFLICT");
        assertThat(games.get(game.id()).moves()).isEmpty();
    }

    @Test
    void anIllegalMoveIsRefused() {
        var game = botGame(NOW);

        assertError(send("move", "\"gameId\": \"" + game.id() + "\", \"uci\": \"e2e5\", \"version\": "
                + game.version()), "ILLEGAL_MOVE");
    }

    @Test
    void aFinishedGameIsOver() {
        var game = botGame(NOW).resign(Color.WHITE, NOW);
        games.put(game.id(), game);

        assertError(send("move", "\"gameId\": \"" + game.id() + "\", \"uci\": \"e2e4\", \"version\": "
                + game.version()), "GAME_OVER");
    }

    @Test
    void aBotThatCannotPlayIsUnavailable() {
        var game = botGame(NOW);
        botFailure.set(new BotUnavailableException(BotProfile.KNIGHT, new IllegalStateException("no engine")));

        assertError(send("move", "\"gameId\": \"" + game.id() + "\", \"uci\": \"e2e4\", \"version\": "
                + game.version()), "BOT_UNAVAILABLE");
    }

    @Test
    void anyOtherFailureIsInternalAndSaysNothingMore() {
        var game = botGame(NOW);
        botFailure.set(new IllegalStateException("database password is hunter2"));

        var response = send("move", "\"gameId\": \"" + game.id() + "\", \"uci\": \"e2e4\", \"version\": "
                + game.version());

        assertError(response, "INTERNAL");
        assertThat(response.path("detail").stringValue()).doesNotContain("hunter2");
    }

    @Test
    void everyErrorCodeOfTheProtocolExists() {
        var codes = new ArrayList<String>();
        for (var code : RemoteProtocol.ErrorCode.values()) {
            codes.add(code.name());
        }
        assertThat(codes).containsExactlyInAnyOrder("NOT_ALLOWED", "BAD_REQUEST", "NOT_FOUND", "CONFLICT",
                "ILLEGAL_MOVE", "GAME_OVER", "BOT_UNAVAILABLE", "VERSION_MISMATCH", "SERVER_MODE_OFF", "INTERNAL");
    }
}
