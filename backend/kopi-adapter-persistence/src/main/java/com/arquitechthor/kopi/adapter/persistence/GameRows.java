package com.arquitechthor.kopi.adapter.persistence;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Fen;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameOutcome;
import com.arquitechthor.kopi.domain.game.GameResult;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/// Mapping between a [Game] and its rows in `game` and `game_move`. Plain
/// Java, so it is tested without a database.
///
/// A stored game is rebuilt by **replaying** its moves from the initial
/// position through the aggregate itself (`Game.start`, `Game.play`). The
/// domain needs no back door for persistence, and a row that does not add
/// up (a move that is not legal, a version or an ending that the replay
/// does not reproduce) fails loudly instead of loading a corrupt game.
final class GameRows {

    private GameRows() {}

    /// A row of `game`. `result`, `termination` and `finishedAt` are `null`
    /// while the game is in progress, as in the table.
    record GameRow(
            UUID id,
            String origin,
            String status,
            String humanColor,
            String botProfile,
            String initialFen,
            String result,
            String termination,
            long version,
            Instant createdAt,
            Instant finishedAt) {}

    /// A row of `game_move`.
    record MoveRow(int ply, String uci, String san, String fenAfter, Instant playedAt) {}

    static GameRow gameRow(Game game) {
        return new GameRow(
                game.id().value(),
                game.origin().name(),
                game.status().name(),
                game.humanColor().name(),
                game.botProfile().name(),
                game.initialPosition().toFen(),
                game.result().map(GameResult::name).orElse(null),
                game.outcome().map(GameRows::termination).orElse(null),
                game.version(),
                stored(game.createdAt()),
                game.finishedAt().map(GameRows::stored).orElse(null));
    }

    static List<MoveRow> moveRows(Game game) {
        return game.moves().stream()
                .map(played -> new MoveRow(played.ply(), played.move().toUci(), played.san(),
                        played.positionAfter().toFen(), stored(played.playedAt())))
                .toList();
    }

    /// The game these rows describe, rebuilt by replaying its moves.
    ///
    /// @throws IllegalStateException if the rows are inconsistent
    static Game game(GameRow row, List<MoveRow> moves) {
        var id = new GameId(row.id());
        var humanColor = Color.valueOf(row.humanColor());
        var game = Game.start(id, humanColor, BotProfile.valueOf(row.botProfile()), Fen.parse(row.initialFen()),
                row.createdAt());
        for (var move : moves) {
            game = game.play(move.uci(), game.currentPosition().sideToMove(), move.playedAt());
        }
        if ("RESIGNATION".equals(row.termination())) {
            // The loser is the one who resigned.
            var resigned = GameResult.valueOf(row.result()) == GameResult.WHITE_WIN ? Color.BLACK : Color.WHITE;
            game = game.resign(resigned, row.finishedAt());
        }

        var termination = game.outcome().map(GameRows::termination).orElse(null);
        if (!Objects.equals(termination, row.termination()) || game.version() != row.version()) {
            throw new IllegalStateException("Stored game " + id + " does not replay as stored: termination "
                    + row.termination() + " and version " + row.version() + ", replayed " + termination
                    + " and version " + game.version());
        }
        return game;
    }

    /// The `termination` column for an outcome. Exhaustive over the sealed
    /// [GameOutcome]: a new kind of ending would not compile until it has a
    /// name here (and in the table's check constraint).
    static String termination(GameOutcome outcome) {
        return switch (outcome) {
            case GameOutcome.Checkmate _ -> "CHECKMATE";
            case GameOutcome.Stalemate _ -> "STALEMATE";
            case GameOutcome.InsufficientMaterial _ -> "INSUFFICIENT_MATERIAL";
            case GameOutcome.ThreefoldRepetition _ -> "THREEFOLD_REPETITION";
            case GameOutcome.FiftyMoveRule _ -> "FIFTY_MOVE_RULE";
            case GameOutcome.Resignation _ -> "RESIGNATION";
        };
    }

    /// PostgreSQL keeps microseconds; a clock may give nanoseconds. Storing
    /// the truncated instant makes a reloaded game identical to the stored
    /// one.
    private static Instant stored(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }
}
