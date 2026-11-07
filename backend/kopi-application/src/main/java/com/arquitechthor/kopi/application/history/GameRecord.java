package com.arquitechthor.kopi.application.history;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Fen;
import com.arquitechthor.kopi.domain.chess.Move;
import com.arquitechthor.kopi.domain.chess.PgnGame;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameOrigin;
import com.arquitechthor.kopi.domain.game.GameOutcome;
import com.arquitechthor.kopi.domain.game.GameResult;
import com.arquitechthor.kopi.domain.game.Player;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/// A game as it travels to the Kopi account (ADR-021): plain data, with no
/// derived positions, so another computer rebuilds it by **replaying** the
/// moves through the domain, as the database does. A record that does not
/// add up (an illegal move, an ending the replay does not reproduce) never
/// becomes a game.
///
/// @param id          the game's id, the same on every computer
/// @param origin      `PLAYED` or `IMPORTED`
/// @param white       `BOT:PAWN`, `LOCAL:Ana`, `REMOTE:luis`; `null` for an
///                    imported game
/// @param black       the same for black
/// @param initialFen  where it started
/// @param moves       every move with the time it was played
/// @param termination how it ended (the names of the `game` table), `null`
///                    while in progress
/// @param result      `WHITE_WIN`, `BLACK_WIN` or `DRAW`; `null` while in
///                    progress
/// @param createdAt   when it started (or was imported)
/// @param finishedAt  when it ended, `null` while in progress
/// @param pgnTags     an imported game's tags; empty for a played one
public record GameRecord(
        String id,
        String origin,
        String white,
        String black,
        String initialFen,
        List<RecordedMove> moves,
        String termination,
        String result,
        Instant createdAt,
        Instant finishedAt,
        Map<String, String> pgnTags) {

    public GameRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(initialFen, "initialFen");
        Objects.requireNonNull(createdAt, "createdAt");
        moves = moves == null ? List.of() : List.copyOf(moves);
        pgnTags = pgnTags == null ? Map.of() : Map.copyOf(pgnTags);
    }

    /// One move: what was played and when.
    public record RecordedMove(String uci, Instant playedAt) {
        public RecordedMove {
            Objects.requireNonNull(uci, "uci");
            Objects.requireNonNull(playedAt, "playedAt");
        }
    }

    /// The record of `game`.
    public static GameRecord of(Game game) {
        return new GameRecord(
                game.id().value().toString(),
                game.origin().name(),
                game.player(Color.WHITE).map(GameRecord::playerText).orElse(null),
                game.player(Color.BLACK).map(GameRecord::playerText).orElse(null),
                game.initialPosition().toFen(),
                game.moves().stream().map(m -> new RecordedMove(m.move().toUci(), m.playedAt())).toList(),
                game.outcome().map(GameRecord::termination).orElse(null),
                game.result().map(GameResult::name).orElse(null),
                game.createdAt(),
                game.finishedAt().orElse(null),
                game.origin() == GameOrigin.IMPORTED ? game.pgnTags() : Map.of());
    }

    /// The moves as UCI, to compare two versions of the same game.
    public List<String> uciMoves() {
        return moves.stream().map(RecordedMove::uci).toList();
    }

    /// The game this record describes, rebuilt by replaying its moves.
    ///
    /// @throws IllegalArgumentException if the record does not add up
    public Game toGame() {
        try {
            var gameId = new GameId(UUID.fromString(id));
            var game = GameOrigin.IMPORTED.name().equals(origin) ? imported(gameId) : played(gameId);
            var replayed = game.outcome().map(GameRecord::termination).orElse(null);
            if (!Objects.equals(replayed, termination)) {
                throw new IllegalArgumentException("ends as " + replayed + ", recorded " + termination);
            }
            return game;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Game " + id + " does not replay: " + e.getMessage(), e);
        }
    }

    private Game played(GameId gameId) {
        var game = Game.start(gameId, player(white), player(black), Fen.parse(initialFen), createdAt);
        for (var move : moves) {
            game = game.play(move.uci(), game.currentPosition().sideToMove(), move.playedAt());
        }
        if (game.outcome().isEmpty() && termination != null) {
            var at = finishedAt == null ? createdAt : finishedAt;
            game = switch (termination) {
                case "RESIGNATION" -> game.resign(
                        GameResult.valueOf(result) == GameResult.WHITE_WIN ? Color.BLACK : Color.WHITE, at);
                case "DRAW_AGREEMENT" -> game.agreeDraw(at);
                case "INACTIVITY" -> game.endByInactivity(at);
                default -> game;
            };
        }
        return game;
    }

    private Game imported(GameId gameId) {
        var initial = Fen.parse(initialFen);
        var position = initial;
        var played = new ArrayList<Move>();
        for (var recorded : moves) {
            var current = position;
            var move = current.legalMoves().stream().filter(m -> m.toUci().equals(recorded.uci())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(recorded.uci() + " is not legal"));
            played.add(move);
            position = current.apply(move);
        }
        var pgnResult = switch (GameResult.valueOf(result)) {
            case WHITE_WIN -> PgnGame.Result.WHITE_WINS;
            case BLACK_WIN -> PgnGame.Result.BLACK_WINS;
            case DRAW -> PgnGame.Result.DRAW;
        };
        return Game.imported(gameId, new PgnGame(pgnTags, initial, played, pgnResult), createdAt);
    }

    /// The name of an ending, as in the `game` table.
    static String termination(GameOutcome outcome) {
        return switch (outcome) {
            case GameOutcome.Checkmate _ -> "CHECKMATE";
            case GameOutcome.Stalemate _ -> "STALEMATE";
            case GameOutcome.InsufficientMaterial _ -> "INSUFFICIENT_MATERIAL";
            case GameOutcome.ThreefoldRepetition _ -> "THREEFOLD_REPETITION";
            case GameOutcome.FiftyMoveRule _ -> "FIFTY_MOVE_RULE";
            case GameOutcome.Resignation _ -> "RESIGNATION";
            case GameOutcome.Declared _ -> "DECLARED";
            case GameOutcome.DrawByAgreement _ -> "DRAW_AGREEMENT";
            case GameOutcome.Inactivity _ -> "INACTIVITY";
        };
    }

    static String playerText(Player player) {
        return switch (player) {
            case Player.Bot(var profile) -> "BOT:" + profile.name();
            case Player.Local(var name) -> "LOCAL:" + name;
            case Player.Remote(var kopiId) -> "REMOTE:" + kopiId;
        };
    }

    static Player player(String text) {
        var colon = text == null ? -1 : text.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("not a player: " + text);
        }
        var value = text.substring(colon + 1);
        return switch (text.substring(0, colon)) {
            case "BOT" -> new Player.Bot(BotProfile.valueOf(value));
            case "LOCAL" -> new Player.Local(value);
            case "REMOTE" -> new Player.Remote(value);
            default -> throw new IllegalArgumentException("not a player: " + text);
        };
    }
}
