package com.arquitechthor.kopi.domain.analysis;

/// What a move was worth (design §8.3). Codes, not text (ADR-011): the
/// adapters show "Blunder" or «Error grave».
///
/// `BRILLIANT`, `GREAT` and the like are left out on purpose (design §8.5):
/// they need heuristics that are easy to get wrong.
public enum MoveCategory {
    /// Still in the opening book.
    BOOK,
    /// The engine's choice, or as good as it.
    BEST,
    /// Lost less than 0.1 of win chance.
    GOOD,
    /// Lost from 0.1 to 0.2.
    INACCURACY,
    /// Lost from 0.2 to 0.3.
    MISTAKE,
    /// Lost 0.3 or more.
    BLUNDER,
    /// Had a short forced mate and let it go. Wins over every other
    /// category.
    MISSED_MATE
}
