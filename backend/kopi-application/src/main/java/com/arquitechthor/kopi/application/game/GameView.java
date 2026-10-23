package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Move;
import com.arquitechthor.kopi.domain.chess.San;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameOutcome;
import com.arquitechthor.kopi.domain.game.GameResult;
import com.arquitechthor.kopi.domain.game.GameStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/// A game as the adapters show it: everything a page or a JSON response
/// needs, already computed by the domain, so no adapter applies chess rules
/// (design §10.2, §11.3).
///
/// @param id          the game
/// @param status      in progress or finished
/// @param humanColor  the colour the person plays
/// @param botProfile  the opponent
/// @param fen         the current position
/// @param sideToMove  whose turn it is
/// @param inCheck     whether the side to move is in check
/// @param moves       every move played, in order
/// @param legalMoves  the legal moves of the current position, sorted by
///                    UCI; empty when the game is over
/// @param outcome     how it ended, once it has
/// @param result      the score, once it has ended
/// @param version     for optimistic locking (`If-Match`)
/// @param createdAt   when it started
/// @param finishedAt  when it ended, once it has
public record GameView(
        GameId id,
        GameStatus status,
        Color humanColor,
        BotProfile botProfile,
        String fen,
        Color sideToMove,
        boolean inCheck,
        List<PlayedMoveView> moves,
        List<LegalMoveView> legalMoves,
        Optional<GameOutcome> outcome,
        Optional<GameResult> result,
        long version,
        Instant createdAt,
        Optional<Instant> finishedAt) {

    public GameView {
        moves = List.copyOf(moves);
        legalMoves = List.copyOf(legalMoves);
    }

    /// A move of the game.
    ///
    /// @param ply 1 for White's first move…
    /// @param uci `e2e4`
    /// @param san `e4`
    public record PlayedMoveView(int ply, String uci, String san) {}

    /// A move that may be played now. Both notations: UCI for the board and
    /// the API, SAN for the move list and office mode.
    ///
    /// @param uci `g1f3`
    /// @param san `Nf3`
    public record LegalMoveView(String uci, String san) {}

    /// The last move played, if any: the board highlights it.
    public Optional<PlayedMoveView> lastMove() {
        return moves.isEmpty() ? Optional.empty() : Optional.of(moves.getLast());
    }

    /// Whether it is the person's turn in a game still in progress.
    public boolean isHumanToMove() {
        return status == GameStatus.IN_PROGRESS && sideToMove == humanColor;
    }

    static GameView of(Game game) {
        var position = game.currentPosition();
        var moves = game.moves().stream()
                .map(played -> new PlayedMoveView(played.ply(), played.move().toUci(), played.san()))
                .toList();
        var legalMoves = game.status() == GameStatus.FINISHED
                ? List.<LegalMoveView>of()
                : position.legalMoves().stream()
                        .sorted(Comparator.comparing(Move::toUci))
                        .map(move -> new LegalMoveView(move.toUci(), San.format(position, move)))
                        .toList();
        return new GameView(game.id(), game.status(), game.humanColor(), game.botProfile(), position.toFen(),
                position.sideToMove(), position.isInCheck(), moves, legalMoves, game.outcome(), game.result(),
                game.version(), game.createdAt(), game.finishedAt());
    }
}
