package com.arquitechthor.kopi.domain.chess;

import java.util.List;
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

    /// Every legal move for the side to move. Empty means the game is over:
    /// checkmate if [#isInCheck], stalemate otherwise.
    public List<Move> legalMoves() {
        return MoveGenerator.legalMoves(this);
    }

    /// Whether the king of the side to move is attacked.
    public boolean isInCheck() {
        return MoveGenerator.isInCheck(board, sideToMove);
    }

    /// The position after playing `move`.
    ///
    /// The move must come from this position's [#legalMoves]: it already
    /// says what it does (captures, castles, promotes…), so it is applied as
    /// given and not validated again. That is what keeps this cheap enough
    /// to call millions of times in *perft*.
    ///
    /// @throws IllegalArgumentException if no piece stands on `move.from()`
    public Position apply(Move move) {
        var mover = board.pieceAt(move.from())
                .orElseThrow(() -> new IllegalArgumentException("No piece on " + move.from() + " to play " + move));
        var from = move.from();
        var to = move.to();
        var lifted = board.withoutPiece(from);

        // Exhaustive over the sealed `MoveKind`: a seventh kind of move
        // would not compile until it is handled here.
        var nextBoard = switch (move.kind()) {
            case MoveKind.Quiet _, MoveKind.Capture _, MoveKind.DoublePawnPush _ -> lifted.withPiece(to, mover);
            case MoveKind.EnPassant _ ->
                // The captured pawn stands beside the mover, on its rank,
                // not on the destination square.
                lifted.withoutPiece(Square.of(to.file(), from.rank())).withPiece(to, mover);
            case MoveKind.Promotion(var piece, _) -> lifted.withPiece(to, new Piece(sideToMove, piece));
            case MoveKind.Castling(var side) -> {
                var rank = from.rank();
                var rookFrom = Square.of(side == CastleSide.KINGSIDE ? 7 : 0, rank);
                var rookTo = Square.of(side == CastleSide.KINGSIDE ? 5 : 3, rank);
                var rook = board.pieceAt(rookFrom).orElseThrow();
                yield lifted.withoutPiece(rookFrom).withPiece(rookTo, rook).withPiece(to, mover);
            }
        };

        var nextRights = mover.type() == PieceType.KING ? castlingRights.without(sideToMove) : castlingRights;
        // A move from a corner moves that rook; a move to a corner captures
        // it. Either way, castling with it is gone for good.
        nextRights = withoutCorner(withoutCorner(nextRights, from), to);

        var nextEnPassantTarget = move.kind() instanceof MoveKind.DoublePawnPush
                ? Optional.of(Square.of(from.file(), (from.rank() + to.rank()) / 2))
                : Optional.<Square>empty();
        var nextHalfmoveClock = mover.type() == PieceType.PAWN || move.isCapture() ? 0 : halfmoveClock + 1;
        var nextFullmoveNumber = sideToMove == Color.BLACK ? fullmoveNumber + 1 : fullmoveNumber;

        return new Position(nextBoard, sideToMove.opposite(), nextRights, nextEnPassantTarget,
                nextHalfmoveClock, nextFullmoveNumber);
    }

    /// `rights` minus the castling whose rook starts on `square`, if it is a
    /// corner.
    private static CastlingRights withoutCorner(CastlingRights rights, Square square) {
        return switch (square.index()) {
            case 0 -> rights.without(Color.WHITE, CastleSide.QUEENSIDE); // a1
            case 7 -> rights.without(Color.WHITE, CastleSide.KINGSIDE); // h1
            case 56 -> rights.without(Color.BLACK, CastleSide.QUEENSIDE); // a8
            case 63 -> rights.without(Color.BLACK, CastleSide.KINGSIDE); // h8
            default -> rights;
        };
    }
}
