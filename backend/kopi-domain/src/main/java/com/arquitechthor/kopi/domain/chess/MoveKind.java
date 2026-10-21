package com.arquitechthor.kopi.domain.chess;

import java.util.Objects;

/// What a move does besides going from one square to another.
///
/// A **sealed interface**: these six records are the only possible kinds,
/// and the compiler knows it. A `switch` over a `MoveKind` that forgets one
/// does not compile, which is exactly what we want when applying moves to a
/// position, where each kind changes the board differently.
public sealed interface MoveKind {

    /// A move that captures nothing, including a single pawn step.
    record Quiet() implements MoveKind {}

    /// A move that captures the piece standing on the destination square.
    record Capture() implements MoveKind {}

    /// A pawn advancing two squares from its starting rank. It leaves an
    /// en passant target behind for the opponent's next move.
    record DoublePawnPush() implements MoveKind {}

    /// A pawn capturing a pawn that has just made a double push, landing on
    /// the square it skipped. The captured pawn is *not* on the destination.
    record EnPassant() implements MoveKind {}

    /// The king moves two squares towards a rook, and the rook jumps over it.
    record Castling(CastleSide side) implements MoveKind {
        public Castling {
            Objects.requireNonNull(side, "side");
        }
    }

    /// A pawn reaching the last rank and becoming another piece.
    ///
    /// @param piece   the new piece: knight, bishop, rook or queen
    /// @param capture whether the pawn captured on its way to the last rank
    record Promotion(PieceType piece, boolean capture) implements MoveKind {
        public Promotion {
            Objects.requireNonNull(piece, "piece");
            if (!piece.isPromotionTarget()) {
                throw new IllegalArgumentException("A pawn cannot promote to a " + piece);
            }
        }
    }

    // Shared instances for the kinds without components. Records with no
    // fields are all equal to each other anyway; these just avoid `new`.
    MoveKind QUIET = new Quiet();
    MoveKind CAPTURE = new Capture();
    MoveKind DOUBLE_PAWN_PUSH = new DoublePawnPush();
    MoveKind EN_PASSANT = new EnPassant();

    /// Whether the move removes an opponent's piece.
    default boolean isCapture() {
        // Exhaustive pattern matching over the sealed hierarchy, with record
        // deconstruction and unnamed patterns (`_`) for what we don't need.
        return switch (this) {
            case Capture _, EnPassant _ -> true;
            case Promotion(_, var capture) -> capture;
            case Quiet _, DoublePawnPush _, Castling _ -> false;
        };
    }
}
