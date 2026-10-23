package com.arquitechthor.kopi.domain.chess;

/// The rules that end a game, as far as a single position can tell.
///
/// Threefold repetition needs the history of the game, so it belongs to the
/// `Game` aggregate (phase 3), which compares [Position#zobristHash]es.
/// Which ending wins when several apply at once (a move that mates and also
/// reaches the fifty-move limit is mate) is also the aggregate's decision;
/// see ADR-008.
public final class Rules {

    private Rules() {}

    /// The side to move is in check.
    public static boolean isCheck(Position position) {
        return position.isInCheck();
    }

    /// The side to move is in check and has no legal move: it has lost.
    public static boolean isCheckmate(Position position) {
        return position.isInCheck() && position.legalMoves().isEmpty();
    }

    /// The side to move is not in check but has no legal move: a draw.
    public static boolean isStalemate(Position position) {
        return !position.isInCheck() && position.legalMoves().isEmpty();
    }

    /// Fifty moves by each side (100 half-moves) without a capture or a pawn
    /// move: a draw. FIDE lets a player *claim* it; Kopi Chess applies it
    /// automatically (ADR-008).
    public static boolean isFiftyMoveRule(Position position) {
        return position.halfmoveClock() >= 100;
    }

    /// Neither side can ever checkmate, whatever both play: a draw.
    ///
    /// That is the case when, besides the kings, there is
    ///
    /// - nothing;
    /// - a single knight; or
    /// - only bishops, all on squares of the same colour (one bishop
    ///   against none, or bishops of both sides on the same colour).
    ///
    /// Two knights, knight against knight, or bishops on both colours are
    /// *not* included: mate is possible there, even if only with the
    /// loser's help. Other dead positions (blocked pawn chains, for
    /// instance) are not detected (ADR-008).
    public static boolean isInsufficientMaterial(Position position) {
        int knights = 0;
        int bishops = 0;
        boolean bishopOnLight = false;
        boolean bishopOnDark = false;
        for (var entry : position.board().pieces().entrySet()) {
            switch (entry.getValue().type()) {
                case KING -> {}
                case PAWN, ROOK, QUEEN -> {
                    return false;
                }
                case KNIGHT -> knights++;
                case BISHOP -> {
                    bishops++;
                    var square = entry.getKey();
                    // a1 is dark: a square is dark when file + rank is even.
                    if ((square.file() + square.rank()) % 2 == 0) {
                        bishopOnDark = true;
                    } else {
                        bishopOnLight = true;
                    }
                }
            }
        }
        if (knights > 0) {
            return knights == 1 && bishops == 0;
        }
        return !(bishopOnLight && bishopOnDark);
    }
}
