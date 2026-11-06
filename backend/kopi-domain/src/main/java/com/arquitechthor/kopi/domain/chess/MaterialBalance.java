package com.arquitechthor.kopi.domain.chess;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/// Who is ahead in material, as chess sites show it next to the players: the
/// pieces one side has more of than the other, and the difference in points
/// (pawn 1, knight 3, bishop 3, rook 5, queen 9).
///
/// It is read from the board alone, side against side, not from the moves
/// or from the starting set. So a promoted pawn counts as what it became (a
/// side with two queens has not lost a queen, it is a queen up and a pawn
/// down), and a game that began from a custom position does not show the
/// pieces that were never there as captured.
///
/// @param whiteAhead the piece types White has more of, once per extra piece,
///                   from pawn to queen (`[KNIGHT]`: one knight more)
/// @param blackAhead the same for Black
/// @param points     White's points minus Black's: positive when White is
///                   ahead, negative when Black is, 0 when level
public record MaterialBalance(List<PieceType> whiteAhead, List<PieceType> blackAhead, int points) {

    /// Nothing on either side: the starting position, or any level trade.
    public static final MaterialBalance LEVEL = new MaterialBalance(List.of(), List.of(), 0);

    public MaterialBalance {
        whiteAhead = List.copyOf(Objects.requireNonNull(whiteAhead, "whiteAhead"));
        blackAhead = List.copyOf(Objects.requireNonNull(blackAhead, "blackAhead"));
    }

    /// The balance of the pieces on `board`.
    public static MaterialBalance of(Board board) {
        var difference = new EnumMap<PieceType, Integer>(PieceType.class);
        for (var piece : board.pieces().values()) {
            difference.merge(piece.type(), piece.color() == Color.WHITE ? 1 : -1, Integer::sum);
        }
        var whiteAhead = new ArrayList<PieceType>();
        var blackAhead = new ArrayList<PieceType>();
        var points = 0;
        // EnumMap keeps the declaration order: pawn first, queen last.
        for (var entry : difference.entrySet()) {
            var type = entry.getKey();
            int more = entry.getValue();
            var side = more > 0 ? whiteAhead : blackAhead;
            for (int i = 0; i < Math.abs(more); i++) {
                side.add(type);
            }
            points += more * value(type);
        }
        return new MaterialBalance(whiteAhead, blackAhead, points);
    }

    /// The pieces `color` has more of than its opponent.
    public List<PieceType> ahead(Color color) {
        return color == Color.WHITE ? whiteAhead : blackAhead;
    }

    /// How many points `color` is ahead by; 0 if level or behind.
    public int pointsAhead(Color color) {
        return Math.max(0, color == Color.WHITE ? points : -points);
    }

    /// The usual values. The king is never captured, so it counts for
    /// nothing; exhaustive, so a new piece type would not compile without one.
    static int value(PieceType type) {
        return switch (type) {
            case PAWN -> 1;
            case KNIGHT, BISHOP -> 3;
            case ROOK -> 5;
            case QUEEN -> 9;
            case KING -> 0;
        };
    }
}
