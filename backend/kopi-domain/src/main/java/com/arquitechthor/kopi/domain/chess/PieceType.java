package com.arquitechthor.kopi.domain.chess;

/// The six kinds of chess pieces, regardless of colour.
public enum PieceType {
    PAWN('p'),
    KNIGHT('n'),
    BISHOP('b'),
    ROOK('r'),
    QUEEN('q'),
    KING('k');

    private final char letter;

    PieceType(char letter) {
        this.letter = letter;
    }

    /// The lower-case letter used by FEN and UCI: `n` for a knight, `q` for a
    /// queen. (SAN uses the upper-case form and omits it for pawns.)
    public char letter() {
        return letter;
    }

    /// Bishops, rooks and queens move any number of squares along a line.
    public boolean isSlider() {
        return switch (this) {
            case BISHOP, ROOK, QUEEN -> true;
            case PAWN, KNIGHT, KING -> false;
        };
    }

    /// A pawn reaching the last rank may become a knight, bishop, rook or
    /// queen — never a pawn or a king.
    public boolean isPromotionTarget() {
        return switch (this) {
            case KNIGHT, BISHOP, ROOK, QUEEN -> true;
            case PAWN, KING -> false;
        };
    }

    /// The piece type for a FEN/UCI letter, in either case (`N` or `n`).
    ///
    /// @throws IllegalArgumentException if the letter is not a piece letter
    public static PieceType fromLetter(char letter) {
        return switch (Character.toLowerCase(letter)) {
            case 'p' -> PAWN;
            case 'n' -> KNIGHT;
            case 'b' -> BISHOP;
            case 'r' -> ROOK;
            case 'q' -> QUEEN;
            case 'k' -> KING;
            default -> throw new IllegalArgumentException("Not a piece letter: '" + letter + "'");
        };
    }
}
