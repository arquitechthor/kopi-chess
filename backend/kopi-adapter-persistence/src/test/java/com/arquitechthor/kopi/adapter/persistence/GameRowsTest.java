package com.arquitechthor.kopi.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.tuple;

import com.arquitechthor.kopi.adapter.persistence.GameRows.GameRow;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Fen;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameOutcome;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameRowsTest {

    private static final GameId ID = new GameId(UUID.fromString("7c1e0000-0000-0000-0000-000000000001"));
    private static final Instant START = Instant.parse("2026-10-23T12:00:00Z");

    static Game newGame(Color humanColor) {
        return Game.start(ID, humanColor, BotProfile.PAWN, Position.initial(), START);
    }

    /// Plays `moves` (UCI, space-separated), each by the side to move, one
    /// second apart.
    static Game play(Game game, String moves) {
        for (var uci : moves.split(" ")) {
            game = game.play(uci, game.currentPosition().sideToMove(), START.plusSeconds(game.moves().size() + 1L));
        }
        return game;
    }

    /// The game rebuilt from its own rows.
    private static Game roundTrip(Game game) {
        return GameRows.game(GameRows.gameRow(game), GameRows.moveRows(game));
    }

    static void assertSameGame(Game actual, Game expected) {
        assertThat(actual.id()).isEqualTo(expected.id());
        assertThat(actual.status()).isEqualTo(expected.status());
        assertThat(actual.humanColor()).isEqualTo(expected.humanColor());
        assertThat(actual.botProfile()).isEqualTo(expected.botProfile());
        assertThat(actual.initialPosition()).isEqualTo(expected.initialPosition());
        assertThat(actual.currentPosition()).isEqualTo(expected.currentPosition());
        assertThat(actual.moves()).isEqualTo(expected.moves());
        assertThat(actual.outcome()).isEqualTo(expected.outcome());
        assertThat(actual.version()).isEqualTo(expected.version());
        assertThat(actual.createdAt()).isEqualTo(expected.createdAt());
        assertThat(actual.finishedAt()).isEqualTo(expected.finishedAt());
        assertThat(actual.origin()).isEqualTo(expected.origin());
        assertThat(actual.pgnTags()).containsExactlyEntriesOf(expected.pgnTags());
    }

    @Test
    void writesTheGameRowLikeTheTable() {
        var game = play(newGame(Color.WHITE), "e2e4 e7e5");

        var row = GameRows.gameRow(game);

        assertThat(row).isEqualTo(new GameRow(ID.value(), "PLAYED", "IN_PROGRESS", "WHITE", "PAWN", Fen.INITIAL,
                null, null, 2, START, null, null));
        assertThat(GameRows.moveRows(game)).extracting(GameRows.MoveRow::ply, GameRows.MoveRow::uci,
                        GameRows.MoveRow::san)
                .containsExactly(
                        tuple(1, "e2e4", "e4"),
                        tuple(2, "e7e5", "e5"));
    }

    @Test
    void aNewGameComesBackTheSame() {
        var game = newGame(Color.BLACK);

        assertSameGame(roundTrip(game), game);
    }

    @Test
    void aGameInProgressComesBackTheSame() {
        var game = play(newGame(Color.WHITE), "e2e4 e7e5 g1f3 b8c6 f1b5");

        assertSameGame(roundTrip(game), game);
    }

    @Test
    void aCheckmateComesBackFinished() {
        var game = play(newGame(Color.WHITE), "f2f3 e7e5 g2g4 d8h4");

        var restored = roundTrip(game);

        assertSameGame(restored, game);
        assertThat(GameRows.gameRow(game).termination()).isEqualTo("CHECKMATE");
        assertThat(GameRows.gameRow(game).result()).isEqualTo("BLACK_WIN");
    }

    @Test
    void aResignationComesBackWithTheRightLoser() {
        var game = play(newGame(Color.BLACK), "e2e4").resign(Color.BLACK, START.plusSeconds(30));

        var restored = roundTrip(game);

        assertSameGame(restored, game);
        assertThat(restored.outcome()).contains(new GameOutcome.Resignation(Color.BLACK));
    }

    @Test
    void aGameFromAnotherStartingPositionComesBackTheSame() {
        var game = Game.start(ID, Color.WHITE, BotProfile.PAWN, Fen.parse("4k3/1P6/8/8/8/8/8/4K3 w - - 0 1"), START)
                .play("b7b8q", Color.WHITE, START.plusSeconds(1));

        assertSameGame(roundTrip(game), game);
    }

    @Test
    void timesAreKeptToTheMicrosecondLikePostgreSql() {
        var precise = Instant.parse("2026-10-23T12:00:00.123456789Z");
        var game = Game.start(ID, Color.WHITE, BotProfile.PAWN, Position.initial(), precise);

        assertThat(GameRows.gameRow(game).createdAt()).isEqualTo(Instant.parse("2026-10-23T12:00:00.123456Z"));
    }

    @Test
    void refusesRowsWhoseVersionDoesNotReplay() {
        var game = play(newGame(Color.WHITE), "e2e4");
        var row = GameRows.gameRow(game);
        var tampered = new GameRow(row.id(), row.origin(), row.status(), row.humanColor(), row.botProfile(),
                row.initialFen(), row.result(), row.termination(), 7, row.createdAt(), row.finishedAt(), null);

        assertThatIllegalStateException().isThrownBy(() -> GameRows.game(tampered, GameRows.moveRows(game)));
    }

    @Test
    void refusesRowsWhoseEndingDoesNotReplay() {
        // Stored as checkmate, but the moves end nothing.
        var game = play(newGame(Color.WHITE), "e2e4");
        var row = GameRows.gameRow(game);
        var tampered = new GameRow(row.id(), row.origin(), "FINISHED", row.humanColor(), row.botProfile(),
                row.initialFen(), "WHITE_WIN", "CHECKMATE", row.version(), row.createdAt(), START, null);

        assertThatIllegalStateException().isThrownBy(() -> GameRows.game(tampered, GameRows.moveRows(game)));
    }
}
