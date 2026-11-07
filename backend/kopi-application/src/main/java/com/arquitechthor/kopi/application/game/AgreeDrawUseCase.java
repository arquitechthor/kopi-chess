package com.arquitechthor.kopi.application.game;

/// Two people at this computer agree to a draw (ADR-018).
public interface AgreeDrawUseCase {

    /// @throws GameNotFoundException if there is no such game
    /// @throws VersionMismatchException if the game is not at the expected
    ///                                  version
    /// @throws com.arquitechthor.kopi.domain.game.GameNotInProgressException
    ///         if the game is already over
    /// @throws NotALocalGameException if it is not a game between two people
    ///                                here
    GameView agree(AgreeDrawCommand command);
}
