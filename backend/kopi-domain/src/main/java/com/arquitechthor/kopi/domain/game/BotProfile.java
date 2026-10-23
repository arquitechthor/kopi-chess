package com.arquitechthor.kopi.domain.game;

/// The five bots, from the weakest to the strongest.
///
/// Named after pieces rather than BEGINNER … EXPERT: friendlier, and they do
/// not promise a particular strength (design §5.3, §7). How each one plays
/// is the bots adapter's business; the domain only knows who the opponent
/// is.
public enum BotProfile {
    /// Random moves, with a taste for captures and checks.
    PAWN,
    /// A short search of its own.
    KNIGHT,
    /// Stockfish, heavily limited.
    BISHOP,
    /// Stockfish, moderately limited.
    ROOK,
    /// Stockfish at full strength within its time budget.
    QUEEN;

    /// 1 for `PAWN` … 5 for `QUEEN`.
    public int level() {
        return ordinal() + 1;
    }
}
