package com.arquitechthor.kopi.domain.game;

import java.io.Serial;
import java.util.Objects;

/// A game read from PGN that cannot become an imported game. The PGN itself
/// was fine (that is `InvalidPgnException`'s business); the game is not
/// one Kopi can keep.
///
/// The [Problem] is a code, not a sentence (ADR-011).
public final class InvalidImportException extends RuntimeException {

    /// What is wrong with the game.
    public enum Problem {
        /// No move at all: nothing to look at or analyse.
        NO_MOVES,
        /// The PGN says `*` and the board shows no ending: an unfinished
        /// game. Only finished games are imported (plan §9).
        NOT_FINISHED,
        /// The PGN's result says something else than the board: `1-0` after
        /// Black mates, `1/2-1/2` after a mate, a win after a stalemate.
        RESULT_CONTRADICTS_BOARD
    }

    @Serial
    private static final long serialVersionUID = 1L;

    private final Problem problem;

    public InvalidImportException(Problem problem, String detail) {
        Objects.requireNonNull(problem, "problem");
        this.problem = problem;
        super(problem + ": " + detail);
    }

    public Problem problem() {
        return problem;
    }
}
