package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.domain.game.GameId;
import java.util.List;

/// The simulation mode: moves tried out from where a game stands, for both
/// sides, that are never saved. Nothing changes in the game; the client
/// keeps the moves and sends them all each time.
public interface SimulateMovesUseCase {

    /// At most this many moves in one simulation.
    int MAX_MOVES = 300;

    /// Where the game would stand after `uciMoves`, played in turn from its
    /// current position.
    ///
    /// @throws GameNotFoundException if there is no such game
    /// @throws IllegalSimulatedMoveException if a move is not legal where it
    ///                                       is played, or there are too many
    SimulationView simulate(GameId gameId, List<String> uciMoves);
}
