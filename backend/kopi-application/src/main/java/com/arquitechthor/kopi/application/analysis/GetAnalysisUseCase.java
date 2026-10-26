package com.arquitechthor.kopi.application.analysis;

import com.arquitechthor.kopi.domain.game.GameId;

/// Read a game's analysis, or how it is going.
public interface GetAnalysisUseCase {

    /// @throws com.arquitechthor.kopi.application.game.GameNotFoundException
    ///         if there is no such game
    AnalysisView get(GameId gameId);
}
