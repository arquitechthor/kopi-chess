package com.arquitechthor.kopi.domain.chess;

import java.io.Serial;
import java.util.Objects;

/// A SAN string that does not stand for exactly one legal move of a position.
///
/// The [Problem] is a code, not a sentence: adapters translate it for people
/// (ADR-011). The exception message is for logs and developers only.
public final class InvalidSanException extends IllegalArgumentException {

    /// What is wrong with the SAN.
    public enum Problem {
        /// The text is not SAN at all (`hello`, `Ke9`).
        MALFORMED,
        /// It reads as SAN, but no legal move of the position matches it.
        NO_SUCH_MOVE,
        /// More than one legal move matches it (`Nd2` with knights on `b1`
        /// and `f3`): it needs a file or rank to tell them apart.
        AMBIGUOUS
    }

    @Serial
    private static final long serialVersionUID = 1L;

    private final Problem problem;
    private final String san;
    private final String fen;

    InvalidSanException(Problem problem, String san, Position position) {
        Objects.requireNonNull(problem, "problem");
        var fen = position.toFen();
        var message = problem + ": \"" + san + "\" in " + fen;
        this.problem = problem;
        this.san = san;
        this.fen = fen;
        super(message);
    }

    public Problem problem() {
        return problem;
    }

    /// The text as it was given.
    public String san() {
        return san;
    }

    /// The position it was read in.
    public String fen() {
        return fen;
    }
}
