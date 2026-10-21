package com.arquitechthor.kopi.domain.chess;

import java.util.Objects;

/// A piece of a given colour: a white knight, a black queen…
///
/// A record rather than an enum of twelve constants: it reads naturally
/// (`new Piece(WHITE, KNIGHT)`), it can be deconstructed with record patterns
/// (`case Piece(var color, var type)`), and equality is by value, so two
/// white knights are always equal.
///
/// @param color the side the piece belongs to
/// @param type  what kind of piece it is
public record Piece(Color color, PieceType type) {

    public Piece {
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(type, "type");
    }

    /// The FEN character: upper case for White (`N`), lower case for Black (`n`).
    public char fenChar() {
        return color == Color.WHITE ? Character.toUpperCase(type.letter()) : type.letter();
    }

    /// The piece for a FEN character: `N` is a white knight, `n` a black one.
    ///
    /// @throws IllegalArgumentException if the character is not a piece letter
    public static Piece fromFenChar(char fenChar) {
        var type = PieceType.fromLetter(fenChar);
        var color = Character.isUpperCase(fenChar) ? Color.WHITE : Color.BLACK;
        return new Piece(color, type);
    }
}
