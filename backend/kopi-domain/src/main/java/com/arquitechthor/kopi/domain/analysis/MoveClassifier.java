package com.arquitechthor.kopi.domain.analysis;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Move;
import java.util.Objects;
import java.util.OptionalInt;

/// Puts one move in a [MoveCategory], from the engine's evaluations of the
/// positions before and after it (design §8.3, ADR-005).
///
/// The rules, first match wins:
///
/// 1. `MISSED_MATE`: before the move the mover had a mate in at most
///    [#missedMateLimit] moves, and after it has none at all. A longer mate
///    still counts as kept.
/// 2. `BEST` for a forced move (the only legal one): it cannot be an error,
///    whatever the two scores say.
/// 3. `BOOK`: the position after the move is still in the opening book.
/// 4. `BEST`: the engine's own move, or one that loses at most
///    [#BEST_TOLERANCE] of win chance (two searches of nearly the same
///    position never agree to the last decimal).
/// 5. By the win chance lost: `GOOD` below 0.1, `INACCURACY` below 0.2,
///    `MISTAKE` below 0.3, `BLUNDER` from there on (Lichess's thresholds).
public final class MoveClassifier {

    /// Up to half a percentage point of Win % lost still counts as the best.
    static final double BEST_TOLERANCE = 0.01;

    private static final double INACCURACY = 0.1;
    private static final double MISTAKE = 0.2;
    private static final double BLUNDER = 0.3;

    private final int missedMateLimit;

    /// @param missedMateLimit the longest mate whose loss is a
    ///                        `MISSED_MATE` (design §8.3 suggests 5); a
    ///                        longer one is judged by win chance alone
    public MoveClassifier(int missedMateLimit) {
        if (missedMateLimit < 1) {
            throw new IllegalArgumentException("The missed mate limit must be at least 1: " + missedMateLimit);
        }
        this.missedMateLimit = missedMateLimit;
    }

    /// Everything the classifier looks at for one move.
    ///
    /// @param mover      who played it
    /// @param played     the move played
    /// @param best       the engine's best move in the position before
    /// @param before     the evaluation of the position before the move
    /// @param after      the evaluation of the position after it
    /// @param inBook     whether the game is still in the opening book
    ///                   after this move
    /// @param legalMoves how many legal moves the mover had
    public record Facts(Color mover, Move played, Move best, Evaluation before, Evaluation after,
                        boolean inBook, int legalMoves) {

        public Facts {
            Objects.requireNonNull(mover, "mover");
            Objects.requireNonNull(played, "played");
            Objects.requireNonNull(best, "best");
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
            if (legalMoves < 1) {
                throw new IllegalArgumentException("A move was played, so there was a legal one: " + legalMoves);
            }
        }
    }

    /// The category of a move and why.
    ///
    /// @param category   what the move was worth
    /// @param loss       the win chance the mover gave away (negative if
    ///                   the position got better for them)
    /// @param missedMate for a `MISSED_MATE`, the length of the mate the
    ///                   engine had found; empty otherwise
    public record Classification(MoveCategory category, double loss, OptionalInt missedMate) {

        public Classification {
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(missedMate, "missedMate");
            if (missedMate.isPresent() != (category == MoveCategory.MISSED_MATE)) {
                throw new IllegalArgumentException("Only a MISSED_MATE says which mate was missed");
            }
        }
    }

    public Classification classify(Facts move) {
        var mover = move.mover();
        var loss = move.before().winChanceFor(mover).lossTo(move.after().winChanceFor(mover));
        var mateBefore = move.before().mateFor(mover);
        if (mateBefore.isPresent() && mateBefore.getAsInt() <= missedMateLimit
                && move.after().mateFor(mover).isEmpty()) {
            return new Classification(MoveCategory.MISSED_MATE, loss, mateBefore);
        }
        return new Classification(category(move, loss), loss, OptionalInt.empty());
    }

    private static MoveCategory category(Facts move, double loss) {
        if (move.legalMoves() == 1) {
            return MoveCategory.BEST;
        }
        if (move.inBook()) {
            return MoveCategory.BOOK;
        }
        if (move.played().equals(move.best()) || loss <= BEST_TOLERANCE) {
            return MoveCategory.BEST;
        }
        if (loss < INACCURACY) {
            return MoveCategory.GOOD;
        }
        if (loss < MISTAKE) {
            return MoveCategory.INACCURACY;
        }
        if (loss < BLUNDER) {
            return MoveCategory.MISTAKE;
        }
        return MoveCategory.BLUNDER;
    }
}
