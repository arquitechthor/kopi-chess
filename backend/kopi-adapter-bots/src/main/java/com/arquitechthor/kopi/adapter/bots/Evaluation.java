package com.arquitechthor.kopi.adapter.bots;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.PieceType;
import com.arquitechthor.kopi.domain.chess.Position;

/// How good a position looks to `KNIGHT`, in centipawns, from the point of
/// view of the side to move (positive: better for it).
///
/// Deliberately simple, as design §7 asks: material plus a bonus or penalty
/// for where each piece stands. The square bonuses are Kopi's own, written
/// as small formulas rather than copied tables:
///
/// - pawns are worth more the further they advance, and in the centre;
/// - knights love the centre and hate the rim; bishops a little less so;
/// - rooks like the seventh rank;
/// - the king prefers to stay home, on a castled square.
///
/// Positions are scored from White's side and mirrored for Black, so the
/// evaluation is symmetric between the colours.
final class Evaluation {

    static int value(PieceType piece) {
        return switch (piece) {
            case PAWN -> 100;
            case KNIGHT -> 320;
            case BISHOP -> 330;
            case ROOK -> 500;
            case QUEEN -> 900;
            case KING -> 0; // never captured; mate is scored by the search
        };
    }

    /// Bonus for a pawn by rank, from its own side (index 1 = starting rank).
    private static final int[] PAWN_ADVANCE = {0, 0, 5, 10, 20, 30, 50, 0};

    private Evaluation() {}

    static int of(Position position) {
        int white = 0;
        for (var entry : position.board().pieces().entrySet()) {
            var piece = entry.getValue();
            // Seen from its own side: Black's pieces are mirrored rank-wise.
            var square = entry.getKey();
            var rank = piece.color() == Color.WHITE ? square.rank() : 7 - square.rank();
            var score = value(piece.type()) + placement(piece.type(), square.file(), rank);
            white += piece.color() == Color.WHITE ? score : -score;
        }
        return position.sideToMove() == Color.WHITE ? white : -white;
    }

    /// The square bonus of a piece on `file` and `rank` (both 0–7, the rank
    /// counted from its own side).
    private static int placement(PieceType piece, int file, int rank) {
        // 1 in the four central squares … 7 on the edge.
        var fromCentre = Math.max(Math.abs(2 * file - 7), Math.abs(2 * rank - 7));
        return switch (piece) {
            case PAWN -> PAWN_ADVANCE[rank] + (rank >= 3 && (file == 3 || file == 4) ? 10 : 0);
            case KNIGHT -> (7 - fromCentre) * 5 - 15;
            case BISHOP -> (7 - fromCentre) * 3 - 9;
            case ROOK -> rank == 6 ? 20 : 0;
            case QUEEN -> 7 - fromCentre;
            case KING -> -15 * rank + (rank == 0 && (file == 1 || file == 2 || file == 6) ? 20 : 0);
        };
    }
}
