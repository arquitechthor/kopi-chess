package com.arquitechthor.kopi.domain.game;

import com.arquitechthor.kopi.domain.chess.Color;
import java.util.Objects;

/// How a game ended. A sealed interface: these six records are every way a
/// game can finish, and a `switch` over them must handle all of them (the
/// web adapter's translations, the PGN termination, the database column).
///
/// They are codes, not text (ADR-011): `Checkmate(BLACK)` becomes
/// "Black wins by checkmate" or «Ganan las negras por jaque mate» in the
/// adapters.
public sealed interface GameOutcome {

    /// The side to move is in check and has no legal move.
    ///
    /// @param winner the side that gave mate
    record Checkmate(Color winner) implements GameOutcome {
        public Checkmate {
            Objects.requireNonNull(winner, "winner");
        }
    }

    /// The side to move has no legal move and is not in check.
    record Stalemate() implements GameOutcome {}

    /// Neither side can ever give mate (see `Rules.isInsufficientMaterial`).
    record InsufficientMaterial() implements GameOutcome {}

    /// The same position for the third time. Automatic (ADR-008).
    record ThreefoldRepetition() implements GameOutcome {}

    /// Fifty moves by each side without a capture or a pawn move. Automatic
    /// (ADR-008).
    record FiftyMoveRule() implements GameOutcome {}

    /// A player gave up.
    ///
    /// @param resigned the side that resigned (the other one wins)
    record Resignation(Color resigned) implements GameOutcome {
        public Resignation {
            Objects.requireNonNull(resigned, "resigned");
        }
    }

    /// The score this ending gives.
    default GameResult result() {
        return switch (this) {
            case Checkmate(var winner) -> winsFor(winner);
            case Resignation(var resigned) -> winsFor(resigned.opposite());
            case Stalemate _, InsufficientMaterial _, ThreefoldRepetition _, FiftyMoveRule _ -> GameResult.DRAW;
        };
    }

    private static GameResult winsFor(Color winner) {
        return winner == Color.WHITE ? GameResult.WHITE_WIN : GameResult.BLACK_WIN;
    }
}
