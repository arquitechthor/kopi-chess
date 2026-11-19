package com.arquitechthor.kopi.application.game;

/// Two people at this computer take moves back and play them again, while
/// the game is in progress.
public interface UndoMoveUseCase {

    /// Takes back the last move.
    ///
    /// @throws GameNotFoundException if there is no such game
    /// @throws VersionMismatchException if the game is not at the expected
    ///                                  version
    /// @throws NotALocalGameException if it is not a game between two people
    ///                                here
    /// @throws com.arquitechthor.kopi.domain.game.GameNotInProgressException
    ///         if the game is over
    /// @throws com.arquitechthor.kopi.domain.game.NothingToUndoException
    ///         if no move has been played
    GameView undo(UndoMoveCommand command);

    /// Plays again the move taken back last.
    ///
    /// @throws GameNotFoundException if there is no such game
    /// @throws VersionMismatchException if the game is not at the expected
    ///                                  version
    /// @throws NotALocalGameException if it is not a game between two people
    ///                                here
    /// @throws com.arquitechthor.kopi.domain.game.GameNotInProgressException
    ///         if the game is over
    /// @throws com.arquitechthor.kopi.domain.game.NothingToUndoException
    ///         if no move is waiting to be played again
    GameView redo(UndoMoveCommand command);
}
