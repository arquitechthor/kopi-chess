package com.arquitechthor.kopi.domain.chess;

/// Which castlings are still possible in principle: neither the king nor the
/// rook involved has moved (or been captured).
///
/// Having the right does not mean castling is legal *now*: the squares between
/// king and rook must be empty and the king must not pass through check. That
/// is checked by the move generator.
public record CastlingRights(
        boolean whiteKingside,
        boolean whiteQueenside,
        boolean blackKingside,
        boolean blackQueenside) {

    public static final CastlingRights ALL = new CastlingRights(true, true, true, true);
    public static final CastlingRights NONE = new CastlingRights(false, false, false, false);

    /// Whether `color` may still castle on `side`.
    public boolean has(Color color, CastleSide side) {
        return switch (color) {
            case WHITE -> side == CastleSide.KINGSIDE ? whiteKingside : whiteQueenside;
            case BLACK -> side == CastleSide.KINGSIDE ? blackKingside : blackQueenside;
        };
    }

    /// These rights minus one castling (after a rook moves, for instance).
    public CastlingRights without(Color color, CastleSide side) {
        return new CastlingRights(
                whiteKingside && !(color == Color.WHITE && side == CastleSide.KINGSIDE),
                whiteQueenside && !(color == Color.WHITE && side == CastleSide.QUEENSIDE),
                blackKingside && !(color == Color.BLACK && side == CastleSide.KINGSIDE),
                blackQueenside && !(color == Color.BLACK && side == CastleSide.QUEENSIDE));
    }

    /// These rights minus both castlings of one side (after its king moves).
    public CastlingRights without(Color color) {
        return without(color, CastleSide.KINGSIDE).without(color, CastleSide.QUEENSIDE);
    }

    /// The FEN field: some of `KQkq` in that order, or `-` for none.
    public String toFen() {
        var fen = (whiteKingside ? "K" : "") + (whiteQueenside ? "Q" : "")
                + (blackKingside ? "k" : "") + (blackQueenside ? "q" : "");
        return fen.isEmpty() ? "-" : fen;
    }
}
