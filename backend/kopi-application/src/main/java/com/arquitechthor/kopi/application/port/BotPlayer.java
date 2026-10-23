package com.arquitechthor.kopi.application.port;

import com.arquitechthor.kopi.domain.chess.Move;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;

/// Chooses the bot's move. Implemented by the bots adapter (`PAWN`,
/// `KNIGHT`) and, from phase 4, by the Stockfish adapter.
///
/// A different port from the engine used for analysis (design §4.3): the
/// weakest bot needs no engine, and analysis needs no personality.
public interface BotPlayer {

    /// One of the legal moves of `position`, which has at least one, as
    /// `profile` would play it.
    ///
    /// @throws BotUnavailableException if the bot cannot answer (engine down,
    ///                                 time out)
    Move chooseMove(Position position, BotProfile profile);
}
