package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.domain.game.GameId;

/// The person deletes a game, whatever its state: in progress, finished or
/// imported, analysed or not.
public interface DeleteGameUseCase {

    /// Deletes the game with its moves and its analysis.
    ///
    /// @throws GameNotFoundException if there is no such game
    void delete(GameId gameId);
}
