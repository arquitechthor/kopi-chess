package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.domain.game.GameId;

/// One game, as the adapters show it.
public interface GetGameUseCase {

    /// @throws GameNotFoundException if there is no such game
    GameView get(GameId id);
}
