package com.arquitechthor.kopi.domain.chess;

import java.util.Objects;
import java.util.Optional;

/// Everything needed to continue a game from a given moment: the board plus
/// the state FEN records alongside it. Immutable.
///
/// Structural checks (one king per side, no pawns on the first or last rank)
/// happen where positions enter the system from outside, in [Fen#parse].
/// Positions created by applying legal moves are valid by construction, and
/// re-checking them here would slow down move generation for nothing.
///
/// @param board           where the pieces stand
/// @param sideToMove      who plays next
/// @param castlingRights  which castlings are still possible in principle
/// @param enPassantTarget the square a pawn skipped with a double push on the
///                        previous move, if any. `Optional` makes "there is
///                        none" explicit instead of hiding it behind `null`.
/// @param halfmoveClock   half-moves since the last capture or pawn move (for
///                        the fifty-move rule)
/// @param fullmoveNumber  starts at 1 and increases after Black's move
public record Position(
        Board board,
        Color sideToMove,
        CastlingRights castlingRights,
        Optional<Square> enPassantTarget,
        int halfmoveClock,
        int fullmoveNumber) {

    public Position {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(sideToMove, "sideToMove");
        Objects.requireNonNull(castlingRights, "castlingRights");
        Objects.requireNonNull(enPassantTarget, "enPassantTarget");
        if (halfmoveClock < 0) {
            throw new IllegalArgumentException("Halfmove clock cannot be negative: " + halfmoveClock);
        }
        if (fullmoveNumber < 1) {
            throw new IllegalArgumentException("Fullmove number starts at 1: " + fullmoveNumber);
        }
    }

    /// The standard starting position.
    public static Position initial() {
        return Fen.parse(Fen.INITIAL);
    }

    /// The piece on a square, if any.
    public Optional<Piece> pieceAt(Square square) {
        return board.pieceAt(square);
    }

    /// This position in FEN.
    public String toFen() {
        return Fen.format(this);
    }
}
