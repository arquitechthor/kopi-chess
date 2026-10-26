package com.arquitechthor.kopi.domain.chess;

import java.io.Serial;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/// A PGN text that cannot be read as one game.
///
/// It says exactly where: the line, and for a move also its number and side,
/// so an adapter can write «Jugada 23… Nxe5: ilegal en esta posición»
/// (plan §9). The [Problem] is a code, not a sentence (ADR-011); the
/// exception message is for logs and developers only.
public final class InvalidPgnException extends IllegalArgumentException {

    /// What is wrong with the PGN.
    public enum Problem {
        /// There is no game in it: empty, or only comments.
        EMPTY,
        /// A tag pair is not `[Name "value"]`.
        MALFORMED_TAG,
        /// The `FEN` tag does not describe a position.
        INVALID_FEN,
        /// A `{` comment is never closed.
        UNTERMINATED_COMMENT,
        /// A `(` variation is never closed, or a `)` closes none.
        UNBALANCED_VARIATION,
        /// A move is not SAN at all (`Ke9`, `hello`).
        MALFORMED_MOVE,
        /// A move reads as SAN but is not legal in its position.
        ILLEGAL_MOVE,
        /// A move fits more than one legal move (`Nd2` with two knights
        /// that can go there).
        AMBIGUOUS_MOVE,
        /// There is a second game after the first: one game per import
        /// (plan §9).
        MORE_THAN_ONE_GAME
    }

    @Serial
    private static final long serialVersionUID = 1L;

    private final Problem problem;
    private final int line;
    private final String text;
    private final int moveNumber;
    private final Color side;

    /// A problem that is not about one move.
    InvalidPgnException(Problem problem, int line, String text) {
        this(problem, line, text, 0, null);
    }

    /// A problem with a move: `moveNumber` and `side` say which one.
    InvalidPgnException(Problem problem, int line, String text, int moveNumber, Color side) {
        Objects.requireNonNull(problem, "problem");
        Objects.requireNonNull(text, "text");
        this.problem = problem;
        this.line = line;
        this.text = text;
        this.moveNumber = moveNumber;
        this.side = side;
        super(problem + " at line " + line
                + (side == null ? "" : ", move " + moveNumber + (side == Color.WHITE ? "." : "...")) + ": " + text);
    }

    public Problem problem() {
        return problem;
    }

    /// The line it is on, from 1.
    public int line() {
        return line;
    }

    /// The offending text, as written (a move, a tag, a FEN…).
    public String text() {
        return text;
    }

    /// For a move: its number, as in `23. Nf3` or `23... Nxe5`.
    public OptionalInt moveNumber() {
        return side == null ? OptionalInt.empty() : OptionalInt.of(moveNumber);
    }

    /// For a move: who was to play it.
    public Optional<Color> side() {
        return Optional.ofNullable(side);
    }
}
