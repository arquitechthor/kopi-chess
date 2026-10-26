package com.arquitechthor.kopi.application.analysis;

import com.arquitechthor.kopi.application.port.AnalysisJob;
import com.arquitechthor.kopi.domain.game.GameId;

/// Ask for the analysis of a finished game. Finishing a game already asks
/// for it; this is for asking again (after a failure) or explicitly.
public interface RequestAnalysisUseCase {

    /// Queues the analysis. Idempotent: a game queued, being analysed or
    /// analysed keeps its job; a failed one is queued again.
    ///
    /// @return the game's job, as it is now
    /// @throws com.arquitechthor.kopi.application.game.GameNotFoundException
    ///         if there is no such game
    /// @throws AnalysisNotPossibleException if the game is not finished or
    ///                                      has no moves
    AnalysisJob request(GameId gameId);
}
