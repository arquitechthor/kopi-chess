package com.arquitechthor.kopi.adapter.bots;

import com.arquitechthor.kopi.domain.chess.Move;
import com.arquitechthor.kopi.domain.chess.MoveKind;
import com.arquitechthor.kopi.domain.chess.PieceType;
import com.arquitechthor.kopi.domain.chess.Position;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/// `PAWN`, level 1: plays like a child who has just learnt the moves
/// (design §7).
///
/// It picks a legal move at random, but not uniformly: captures, checks and
/// queen promotions are more tempting than quiet moves. It never looks at
/// what the opponent can do next, so it happily takes a defended pawn with
/// its queen or leaves pieces hanging.
///
/// | Move                  | Weight |
/// |-----------------------|--------|
/// | any legal move        | 1      |
/// | … that captures       | +3     |
/// | … that gives check    | +2     |
/// | … that promotes to a queen | +3 |
final class PawnBot {

    static final int BASE_WEIGHT = 1;
    static final int CAPTURE_BONUS = 3;
    static final int CHECK_BONUS = 2;
    static final int QUEEN_PROMOTION_BONUS = 3;

    /// Where the randomness comes from. A supplier, not a generator, so the
    /// application can hand each request thread its own
    /// (`ThreadLocalRandom::current`) and tests a seeded one.
    private final Supplier<? extends RandomGenerator> random;

    PawnBot(Supplier<? extends RandomGenerator> random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /// One of the legal moves of `position`, drawn by weight.
    ///
    /// @throws IllegalArgumentException if `position` has no legal move
    Move choose(Position position) {
        var moves = position.legalMoves();
        if (moves.isEmpty()) {
            throw new IllegalArgumentException("No legal move in " + position.toFen());
        }
        var weights = moves.stream().mapToInt(move -> weight(position, move)).toArray();
        var draw = random.get().nextInt(Arrays.stream(weights).sum());
        for (int i = 0; i < moves.size(); i++) {
            draw -= weights[i];
            if (draw < 0) {
                return moves.get(i);
            }
        }
        throw new AssertionError("unreachable: the draw is below the total weight");
    }

    private static int weight(Position position, Move move) {
        var weight = BASE_WEIGHT;
        if (move.isCapture()) {
            weight += CAPTURE_BONUS;
        }
        if (position.apply(move).isInCheck()) {
            weight += CHECK_BONUS;
        }
        if (move.kind() instanceof MoveKind.Promotion(var piece, _) && piece == PieceType.QUEEN) {
            weight += QUEEN_PROMOTION_BONUS;
        }
        return weight;
    }
}
