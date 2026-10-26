package com.arquitechthor.kopi.domain.analysis;

import com.arquitechthor.kopi.domain.chess.Color;
import java.util.Objects;
import java.util.OptionalInt;

/// An engine's verdict on a position, always **from White's point of view**
/// (design §6.1): positive is good for White, negative for Black.
///
/// Besides centipawns and forced mates there is a third case, a position
/// that is already checkmate. The engine reports it as `mate 0` from the
/// side to move, and once turned to White's point of view that zero has no
/// sign left to say who won, so it gets its own record.
public sealed interface Evaluation {

    /// A material-and-position score: 100 is about a pawn up for White.
    record Centipawns(int value) implements Evaluation {}

    /// A forced mate: White mates in `moves` moves if positive, Black mates
    /// in `-moves` if negative.
    record MateIn(int moves) implements Evaluation {
        public MateIn {
            if (moves == 0) {
                throw new IllegalArgumentException("A mate in 0 is a Checkmate: say who won");
            }
        }
    }

    /// The position is checkmate.
    ///
    /// @param winner the side that gave mate
    record Checkmate(Color winner) implements Evaluation {
        public Checkmate {
            Objects.requireNonNull(winner, "winner");
        }
    }

    /// How likely `side` is to win from here (see [WinChance]).
    default WinChance winChanceFor(Color side) {
        return WinChance.of(this, side);
    }

    /// In how many moves `side` mates by force: `0` if it already has, empty
    /// if it has no forced mate (or is being mated).
    default OptionalInt mateFor(Color side) {
        return switch (this) {
            case Centipawns _ -> OptionalInt.empty();
            case MateIn(var moves) when (moves > 0) == (side == Color.WHITE) -> OptionalInt.of(Math.abs(moves));
            case MateIn _ -> OptionalInt.empty();
            case Checkmate(var winner) -> winner == side ? OptionalInt.of(0) : OptionalInt.empty();
        };
    }
}
