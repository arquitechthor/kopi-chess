package com.arquitechthor.kopi.domain.chess;

import java.util.Objects;

/// A move: where a piece goes from and to, and what kind of move it is.
///
/// Moves are produced by the move generator for a given position, so they
/// carry everything needed to apply them (capture, castling, promotion…).
/// Text coming from outside (a UCI string typed by a user or sent by an
/// engine) is matched against the legal moves of the position, never turned
/// into a `Move` on its own.
///
/// @param from where the moving piece stands (for castling: the king)
/// @param to   where it ends up (for castling: the king's destination)
/// @param kind what else the move does
public record Move(Square from, Square to, MoveKind kind) {

    public Move {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(kind, "kind");
        if (from.equals(to)) {
            throw new IllegalArgumentException("A move must change square: " + from);
        }
    }

    /// Whether the move removes an opponent's piece.
    public boolean isCapture() {
        return kind.isCapture();
    }

    /// UCI notation, as spoken with engines: `e2e4`, `e7e8q`. Castling is
    /// written as the king's move (`e1g1`).
    public String toUci() {
        var uci = from.toString() + to;
        return kind instanceof MoveKind.Promotion(var piece, _) ? uci + piece.letter() : uci;
    }

    @Override
    public String toString() {
        return toUci();
    }
}
