package com.arquitechthor.kopi.domain.chess;

import java.util.ArrayList;
import java.util.List;

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
/// So far it knows knights and kings (without castling). Sliders, pawns and
/// castling follow, one step at a time, as listed in §5.3 of the plan.
final class MoveGenerator {

    /// The eight L-shaped jumps of a knight.
    private static final int[] KNIGHT_OFFSETS = {33, 31, 18, 14, -14, -18, -31, -33};

    /// One step in each of the eight directions.
    private static final int[] KING_OFFSETS = {17, 16, 15, 1, -1, -15, -16, -17};

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
                case KING -> addSteps(board, side, cell, KING_OFFSETS, moves);
                case PAWN, BISHOP, ROOK, QUEEN -> {
                    // Not generated yet: see the class comment.
                }
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
}
