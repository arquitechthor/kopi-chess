package com.arquitechthor.kopi.application.game;

/// Bring in a game played elsewhere (Lichess, Chess.com, over the board) to
/// review it like any other (plan §9).
public interface ImportPgnUseCase {

    /// Stores the game in `pgn` as a finished, imported game and queues its
    /// analysis.
    ///
    /// @throws PgnTooLargeException if the text is longer than the limit
    /// @throws com.arquitechthor.kopi.domain.chess.InvalidPgnException if
    ///         the text is not one game of legal moves (it says where)
    /// @throws com.arquitechthor.kopi.domain.game.InvalidImportException if
    ///         the game has no moves, is unfinished or its result
    ///         contradicts the board
    GameView importPgn(String pgn);
}
