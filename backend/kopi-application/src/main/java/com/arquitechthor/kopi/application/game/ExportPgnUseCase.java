package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.domain.game.GameId;

/// A game as PGN, to save it or open it in another program (plan §9).
public interface ExportPgnUseCase {

    /// The game's PGN text, finished or not.
    ///
    /// @throws GameNotFoundException if there is no such game
    String export(GameId gameId);
}
