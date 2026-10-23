package com.arquitechthor.kopi.application.game;

/// Play the person's move and, if the game goes on, the bot's reply: one
/// request per turn (design §10.1).
public interface PlayMoveUseCase {

    /// @throws GameNotFoundException if there is no such game
    /// @throws VersionMismatchException if the game is not at the expected
    ///                                  version
    /// @throws com.arquitechthor.kopi.domain.game.GameRuleException if the
    ///         game is over, it is not the person's turn, or the move is
    ///         not legal
    /// @throws com.arquitechthor.kopi.application.port.BotUnavailableException
    ///         if the bot cannot reply; nothing is saved
    /// @throws com.arquitechthor.kopi.application.port.ConcurrentGameUpdateException
    ///         if someone else saved the game first; nothing is saved
    GameView play(PlayMoveCommand command);
}
