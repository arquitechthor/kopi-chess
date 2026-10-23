package com.arquitechthor.kopi.application.game;

/// The person gives up; the bot wins.
public interface ResignUseCase {

    /// @throws GameNotFoundException if there is no such game
    /// @throws VersionMismatchException if the game is not at the expected
    ///                                  version
    /// @throws com.arquitechthor.kopi.domain.game.GameNotInProgressException
    ///         if the game is already over
    GameView resign(ResignCommand command);
}
