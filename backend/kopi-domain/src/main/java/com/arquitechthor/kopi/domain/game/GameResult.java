package com.arquitechthor.kopi.domain.game;

/// The score of a finished game, as PGN writes it: `1-0`, `0-1`, `1/2-1/2`.
public enum GameResult {
    WHITE_WIN,
    BLACK_WIN,
    DRAW
}
