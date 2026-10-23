package com.arquitechthor.kopi.domain.game;

/// Whether a game can still be played.
///
/// ```
///   start ──► IN_PROGRESS ──(an ending, or a resignation)──► FINISHED
/// ```
public enum GameStatus {
    IN_PROGRESS,
    FINISHED
}
