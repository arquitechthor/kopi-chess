package com.arquitechthor.kopi.domain.game;

/// What kind of game it is, from who plays each colour (ADR-018).
public enum GameMode {
    /// A person here against one of the bots.
    AGAINST_BOT,
    /// Two people taking turns at this computer.
    LOCAL,
    /// A person here against a friend online, through the relay.
    ONLINE,
    /// Imported from a PGN: nobody plays it here.
    IMPORTED
}
