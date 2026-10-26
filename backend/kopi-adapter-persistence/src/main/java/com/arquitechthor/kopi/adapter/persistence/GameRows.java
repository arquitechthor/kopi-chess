package com.arquitechthor.kopi.adapter.persistence;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Fen;
import com.arquitechthor.kopi.domain.chess.Move;
import com.arquitechthor.kopi.domain.chess.Pgn;
import com.arquitechthor.kopi.domain.chess.PgnGame;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameOrigin;
import com.arquitechthor.kopi.domain.game.GameOutcome;
import com.arquitechthor.kopi.domain.game.GameResult;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/// Mapping between a [Game] and its rows in `game` and `game_move`. Plain
/// Java, so it is tested without a database.
///
/// A stored game is rebuilt by **replaying** its moves from the initial
/// position through the aggregate itself (`Game.start` and `Game.play`, or
/// `Game.imported` for an imported game). The domain needs no back door
/// for persistence, and a row that does not add up (a move that is not
/// legal, a version or an ending that the replay does not reproduce) fails
/// loudly instead of loading a corrupt game.
///
/// An imported game's PGN tags are stored as PGN text, the tag pairs alone
/// (`[White "Morphy, Paul"]` lines), and read back with [Pgn].
final class GameRows {

    private GameRows() {}

    /// A row of `game`. `result`, `termination` and `finishedAt` are `null`
    /// while the game is in progress, as in the table; `humanColor` and
    /// `botProfile` for an imported game, and `pgnTags` for a played one.
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
            Instant finishedAt,
            String pgnTags) {}

    /// A row of `game_move`.
    record MoveRow(int ply, String uci, String san, String fenAfter, Instant playedAt) {}

    static GameRow gameRow(Game game) {
        return new GameRow(
                game.id().value(),
                game.origin().name(),
                game.status().name(),
                game.humanColor().map(Color::name).orElse(null),
                game.botProfile().map(BotProfile::name).orElse(null),
                game.initialPosition().toFen(),
                game.result().map(GameResult::name).orElse(null),
                game.outcome().map(GameRows::termination).orElse(null),
                game.version(),
                stored(game.createdAt()),
                game.finishedAt().map(GameRows::stored).orElse(null),
                game.origin() == GameOrigin.IMPORTED ? tagText(game) : null);
    }

    /// The tag pairs of an imported game as PGN, without movetext.
    private static String tagText(Game game) {
        var pgn = Pgn.format(new PgnGame(game.pgnTags(), game.initialPosition(), List.of(), PgnGame.Result.UNKNOWN));
        var end = pgn.indexOf("\n\n");
        return end < 0 ? "" : pgn.substring(0, end + 1);
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
        if (GameOrigin.IMPORTED.name().equals(row.origin())) {
            return imported(id, row, moves);
        }
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

    /// An imported game, rebuilt through `Game.imported` from its tags, its
    /// moves and its stored result.
    private static Game imported(GameId id, GameRow row, List<MoveRow> moves) {
        var tags = row.pgnTags().isBlank() ? Map.<String, String>of() : Pgn.parse(row.pgnTags()).tags();
        var initial = Fen.parse(row.initialFen());
        var position = initial;
        var played = new ArrayList<Move>();
        for (var move : moves) {
            var current = position;
            var legal = current.legalMoves().stream().filter(m -> m.toUci().equals(move.uci())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Stored game " + id + " has an illegal move "
                            + move.uci() + " in " + current.toFen()));
            played.add(legal);
            position = current.apply(legal);
        }
        var result = switch (GameResult.valueOf(row.result())) {
            case WHITE_WIN -> PgnGame.Result.WHITE_WINS;
            case BLACK_WIN -> PgnGame.Result.BLACK_WINS;
            case DRAW -> PgnGame.Result.DRAW;
        };
        var game = Game.imported(id, new PgnGame(tags, initial, played, result), row.createdAt());
        var termination = game.outcome().map(GameRows::termination).orElse(null);
        if (!Objects.equals(termination, row.termination())) {
            throw new IllegalStateException("Stored game " + id + " does not import as stored: termination "
                    + row.termination() + ", rebuilt " + termination);
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
            case GameOutcome.Declared _ -> "DECLARED";
        };
    }

    /// PostgreSQL keeps microseconds; a clock may give nanoseconds. Storing
    /// the truncated instant makes a reloaded game identical to the stored
    /// one.
    private static Instant stored(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }
}
