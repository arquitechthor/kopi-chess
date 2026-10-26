package com.arquitechthor.kopi.application.port;

import com.arquitechthor.kopi.domain.chess.Position;

/// A chess engine used to *judge* positions, for post-game analysis
/// (design §8). A different port from [BotPlayer] (design §4.3): analysis
/// wants full strength and the engine's scores, a bot wants a move played
/// with some personality.
///
/// Implemented by the Stockfish adapter.
public interface ChessEngine {

    /// The engine's verdict on `position`: its `multiPv` best lines, each
    /// with its evaluation from White's point of view.
    ///
    /// A position where the game is over has no lines and an exact
    /// evaluation: `Checkmate` or 0 (stalemate).
    ///
    /// @param multiPv how many candidate lines to ask for, at least 1 (the
    ///                analysis asks for 2: design §8.2)
    /// @throws EngineUnavailableException if the engine cannot answer
    ///                                    (not installed, crashed, timed out)
    EngineAnalysis analyse(Position position, SearchLimits limits, int multiPv);
}
