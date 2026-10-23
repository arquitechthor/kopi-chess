package com.arquitechthor.kopi.domain.game;

import java.io.Serial;

/// A move or a resignation in a game that has already finished.
public final class GameNotInProgressException extends GameRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    GameNotInProgressException(GameId gameId) {
        super(gameId, "the game is over");
    }
}
