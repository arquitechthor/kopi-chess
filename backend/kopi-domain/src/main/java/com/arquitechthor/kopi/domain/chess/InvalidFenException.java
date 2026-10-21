package com.arquitechthor.kopi.domain.chess;

import java.io.Serial;
import java.util.Objects;

/// A FEN string that cannot be turned into a [Position].
///
/// The [Problem] is a code, not a sentence: adapters translate it for people
/// (ADR-011). The exception message is for logs and developers only.
public final class InvalidFenException extends IllegalArgumentException {

    /// What is wrong with the FEN.
    public enum Problem {
        WRONG_NUMBER_OF_FIELDS,
        INVALID_PIECE_PLACEMENT,
        INVALID_SIDE_TO_MOVE,
        INVALID_CASTLING_RIGHTS,
        INVALID_EN_PASSANT_SQUARE,
        INVALID_HALFMOVE_CLOCK,
        INVALID_FULLMOVE_NUMBER,
        MISSING_KING,
        TOO_MANY_KINGS,
        PAWN_ON_BACK_RANK
    }

    @Serial
    private static final long serialVersionUID = 1L;

    private final Problem problem;
    private final String fen;

    InvalidFenException(Problem problem, String fen, String detail) {
        // Flexible constructor bodies (Java 25, JEP 513): we can validate and
        // build the message *before* calling super(...), and even assign
        // fields, as long as we don't use `this` yet.
        Objects.requireNonNull(problem, "problem");
        var message = problem + " in FEN \"" + fen + "\": " + detail;
        this.problem = problem;
        this.fen = fen;
        super(message);
    }

    public Problem problem() {
        return problem;
    }

    public String fen() {
        return fen;
    }
}
