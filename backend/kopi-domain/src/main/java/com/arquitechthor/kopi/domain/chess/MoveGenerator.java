package com.arquitechthor.kopi.domain.chess;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Produces the moves available in a position.
///
/// It works directly on the 0x88 cells of the [Board]: a piece's targets are
/// its cell plus a fixed offset, and `Board.isOnBoard` rejects any target
/// that fell off an edge. Moving one rank up is `+16`, one file right is
/// `+1`, so a knight's jump two ranks up and one file right is `+33`.
///
/// [#pseudoLegalMoves] follow how each piece moves, but may leave the
/// mover's own king in check; [#legalMoves] filters those out, with
/// [#isSquareAttacked] doing the hard part. Castling is the exception: it is only generated when fully legal, because
/// its conditions (not out of, through or into check) cannot be checked
/// afterwards by looking at the king's final square alone.
final class MoveGenerator {

    /// What a pawn may become on the last rank, strongest first.
    private static final List<PieceType> PROMOTION_TARGETS =
            List.of(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT);

    /// The eight L-shaped jumps of a knight.
    private static final int[] KNIGHT_OFFSETS = {33, 31, 18, 14, -14, -18, -31, -33};

    /// One step along each diagonal.
    private static final int[] DIAGONAL_OFFSETS = {17, 15, -15, -17};

    /// One step along each file and rank.
    private static final int[] STRAIGHT_OFFSETS = {16, 1, -1, -16};

    /// One step in each of the eight directions: kings step this way, queens
    /// slide this way.
    private static final int[] ALL_DIRECTIONS = {17, 16, 15, 1, -1, -15, -16, -17};

    private MoveGenerator() {}

    /// Every pseudo-legal move for the side to move.
    static List<Move> pseudoLegalMoves(Position position) {
        var board = position.board();
        var side = position.sideToMove();
        var moves = new ArrayList<Move>();
        for (var from : Square.ALL) {
            var cell = Board.cellOf(from);
            var piece = board.at(cell);
            if (piece == null || piece.color() != side) {
                continue;
            }
            switch (piece.type()) {
                case KNIGHT -> addSteps(board, side, cell, KNIGHT_OFFSETS, moves);
                case KING -> {
                    addSteps(board, side, cell, ALL_DIRECTIONS, moves);
                    addCastlings(position, cell, moves);
                }
                case BISHOP -> addSlides(board, side, cell, DIAGONAL_OFFSETS, moves);
                case ROOK -> addSlides(board, side, cell, STRAIGHT_OFFSETS, moves);
                case QUEEN -> addSlides(board, side, cell, ALL_DIRECTIONS, moves);
                case PAWN -> addPawnMoves(position, cell, moves);
            }
        }
        return List.copyOf(moves);
    }

    /// Every legal move for the side to move: the pseudo-legal ones that do
    /// not leave its own king attacked.
    ///
    /// The simplest correct approach: play each move and look. It catches
    /// every case at once (pinned pieces, the king stepping into check,
    /// moves that ignore a check, en passant opening a line to the king)
    /// without special code for any of them. Faster schemes can come once
    /// *perft* proves this one right.
    static List<Move> legalMoves(Position position) {
        var side = position.sideToMove();
        return pseudoLegalMoves(position).stream()
                .filter(move -> !isInCheck(position.apply(move).board(), side))
                .toList();
    }

    /// Whether the king of `color` is attacked.
    static boolean isInCheck(Board board, Color color) {
        return isAttacked(board, kingCell(board, color), color.opposite());
    }

    /// The cell of the king of `color`. Every position has one: [Fen#parse]
    /// checks it, and no legal move captures a king.
    private static int kingCell(Board board, Color color) {
        for (var square : Square.ALL) {
            var cell = Board.cellOf(square);
            if (holds(board, cell, color, PieceType.KING)) {
                return cell;
            }
        }
        throw new IllegalStateException("No " + color + " king on the board:\n" + board);
    }

    /// Whether any piece of `byColor` attacks `square`, that is, could
    /// capture something standing there. Whatever actually stands on the
    /// square (nothing, or a piece of either colour) does not matter.
    static boolean isSquareAttacked(Board board, Square square, Color byColor) {
        return isAttacked(board, Board.cellOf(square), byColor);
    }

    /// Looks outwards from the attacked cell instead of generating every
    /// opponent move: attacks are symmetric, so a knight attacks the cell
    /// if and only if a knight stands a knight's jump away from it, a rook
    /// if the first piece along a file or rank from it is that rook, etc.
    private static boolean isAttacked(Board board, int cell, Color byColor) {
        // A white pawn attacks upwards, so it stands one rank *below* the
        // cell it attacks; a black pawn, one rank above.
        var pawnRank = byColor == Color.WHITE ? -16 : 16;
        if (holds(board, cell + pawnRank - 1, byColor, PieceType.PAWN)
                || holds(board, cell + pawnRank + 1, byColor, PieceType.PAWN)) {
            return true;
        }
        for (var offset : KNIGHT_OFFSETS) {
            if (holds(board, cell + offset, byColor, PieceType.KNIGHT)) {
                return true;
            }
        }
        for (var offset : ALL_DIRECTIONS) {
            if (holds(board, cell + offset, byColor, PieceType.KING)) {
                return true;
            }
        }
        return isAttackedAlongRays(board, cell, byColor, DIAGONAL_OFFSETS, PieceType.BISHOP)
                || isAttackedAlongRays(board, cell, byColor, STRAIGHT_OFFSETS, PieceType.ROOK);
    }

    /// Whether the first piece along any of these rays from `cell` is a
    /// `slider` or a queen of `byColor`.
    private static boolean isAttackedAlongRays(
            Board board, int cell, Color byColor, int[] offsets, PieceType slider) {
        for (var offset : offsets) {
            for (var from = cell + offset; Board.isOnBoard(from); from += offset) {
                var piece = board.at(from);
                if (piece == null) {
                    continue;
                }
                if (piece.color() == byColor && (piece.type() == slider || piece.type() == PieceType.QUEEN)) {
                    return true;
                }
                break;
            }
        }
        return false;
    }

    /// Whether `cell` is on the board and holds a `type` of `color`.
    private static boolean holds(Board board, int cell, Color color, PieceType type) {
        // A record pattern deconstructs the piece in place; it also fails
        // to match `null`, so empty cells need no separate check.
        return Board.isOnBoard(cell)
                && board.at(cell) instanceof Piece(var pieceColor, var pieceType)
                && pieceColor == color
                && pieceType == type;
    }

    /// Castling on either side for the king on `from`, when it is legal now:
    ///
    /// - the side still has the right, and its rook stands on its corner
    ///   (FEN does not guarantee that the rights match the pieces);
    /// - every square between king and rook is empty;
    /// - the king is not in check, and neither the square it crosses nor the
    ///   one it lands on is attacked. (The rook may cross an attacked square:
    ///   `b1` in queenside castling.)
    private static void addCastlings(Position position, int from, List<Move> moves) {
        var board = position.board();
        var side = position.sideToMove();
        var opponent = side.opposite();
        var homeRank = side == Color.WHITE ? 0 : 0x70;
        if (from != homeRank + 4) {
            return; // the king is not on e1/e8, so it has moved
        }
        for (var castleSide : CastleSide.values()) {
            var toward = castleSide == CastleSide.KINGSIDE ? 1 : -1;
            var rookCell = homeRank + (castleSide == CastleSide.KINGSIDE ? 7 : 0);
            var to = from + 2 * toward;
            if (position.castlingRights().has(side, castleSide)
                    && holds(board, rookCell, side, PieceType.ROOK)
                    && isEmptyBetween(board, from, rookCell, toward)
                    && !isAttacked(board, from, opponent)
                    && !isAttacked(board, from + toward, opponent)
                    && !isAttacked(board, to, opponent)) {
                moves.add(new Move(Board.squareOf(from), Board.squareOf(to), new MoveKind.Castling(castleSide)));
            }
        }
    }

    /// Whether every cell strictly between `from` and `to`, stepping by
    /// `step`, is empty.
    private static boolean isEmptyBetween(Board board, int from, int to, int step) {
        for (var cell = from + step; cell != to; cell += step) {
            if (board.at(cell) != null) {
                return false;
            }
        }
        return true;
    }

    /// Single steps by each offset, for pieces that jump (knights) or only
    /// move one square (kings). A target is fine if it is empty (a quiet
    /// move) or holds an opponent's piece (a capture).
    private static void addSteps(Board board, Color side, int from, int[] offsets, List<Move> moves) {
        for (var offset : offsets) {
            var to = from + offset;
            if (!Board.isOnBoard(to)) {
                continue;
            }
            var target = board.at(to);
            if (target == null) {
                moves.add(new Move(Board.squareOf(from), Board.squareOf(to), MoveKind.QUIET));
            } else if (target.color() != side) {
                moves.add(new Move(Board.squareOf(from), Board.squareOf(to), MoveKind.CAPTURE));
            }
        }
    }

    /// Pushes, captures, promotions and en passant for the pawn on `from`.
    ///
    /// Pawns are the only pieces whose direction depends on their colour:
    /// White's go up the board (`+16`), Black's go down (`-16`). They
    /// capture one file to either side of that step.
    private static void addPawnMoves(Position position, int from, List<Move> moves) {
        var board = position.board();
        var side = position.sideToMove();
        var forward = side == Color.WHITE ? 16 : -16;
        var startRank = side == Color.WHITE ? 1 : 6;

        var oneStep = from + forward;
        if (Board.isOnBoard(oneStep) && board.at(oneStep) == null) {
            addPawnMove(from, oneStep, false, moves);
            var twoSteps = oneStep + forward;
            if (from >> 4 == startRank && board.at(twoSteps) == null) {
                moves.add(new Move(Board.squareOf(from), Board.squareOf(twoSteps), MoveKind.DOUBLE_PAWN_PUSH));
            }
        }

        var enPassantTarget = position.enPassantTarget().map(Board::cellOf);
        for (var to : new int[] {oneStep - 1, oneStep + 1}) {
            if (!Board.isOnBoard(to)) {
                continue;
            }
            var target = board.at(to);
            if (target != null && target.color() != side) {
                addPawnMove(from, to, true, moves);
            } else if (target == null && enPassantTarget.equals(Optional.of(to))) {
                moves.add(new Move(Board.squareOf(from), Board.squareOf(to), MoveKind.EN_PASSANT));
            }
        }
    }

    /// A single pawn step to `to`, quiet or capturing; on the last rank it
    /// becomes one promotion per possible piece.
    private static void addPawnMove(int from, int to, boolean capture, List<Move> moves) {
        var origin = Board.squareOf(from);
        var destination = Board.squareOf(to);
        var lastRank = to >> 4;
        if (lastRank == 0 || lastRank == 7) {
            for (var piece : PROMOTION_TARGETS) {
                moves.add(new Move(origin, destination, new MoveKind.Promotion(piece, capture)));
            }
        } else {
            moves.add(new Move(origin, destination, capture ? MoveKind.CAPTURE : MoveKind.QUIET));
        }
    }

    /// Rays in each direction, for bishops, rooks and queens. A ray goes on
    /// through empty squares, ends with a capture on the first opponent's
    /// piece, and ends just before the first own piece or the edge.
    private static void addSlides(Board board, Color side, int from, int[] offsets, List<Move> moves) {
        for (var offset : offsets) {
            for (var to = from + offset; Board.isOnBoard(to); to += offset) {
                var target = board.at(to);
                if (target == null) {
                    moves.add(new Move(Board.squareOf(from), Board.squareOf(to), MoveKind.QUIET));
                    continue;
                }
                if (target.color() != side) {
                    moves.add(new Move(Board.squareOf(from), Board.squareOf(to), MoveKind.CAPTURE));
                }
                break;
            }
        }
    }
}
