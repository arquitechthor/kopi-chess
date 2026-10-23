package com.arquitechthor.kopi.domain.game;

/// Where a game comes from.
public enum GameOrigin {
    /// Played here, against one of the bots.
    PLAYED,
    /// Imported from a PGN (phase 6). It arrives finished.
    IMPORTED
}
