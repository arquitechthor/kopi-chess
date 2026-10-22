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
/// The moves are *pseudo-legal*: they follow how each piece moves, but may
/// leave the mover's own king in check. Filtering those out comes later,
/// once squares can be tested for attacks.
///
/// It knows every piece; only castling is missing, as it needs to test
/// squares for attacks (§5.3 of the plan).
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
                case KING -> addSteps(board, side, cell, ALL_DIRECTIONS, moves);
                case BISHOP -> addSlides(board, side, cell, DIAGONAL_OFFSETS, moves);
                case ROOK -> addSlides(board, side, cell, STRAIGHT_OFFSETS, moves);
                case QUEEN -> addSlides(board, side, cell, ALL_DIRECTIONS, moves);
                case PAWN -> addPawnMoves(position, cell, moves);
            }
        }
        return List.copyOf(moves);
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
