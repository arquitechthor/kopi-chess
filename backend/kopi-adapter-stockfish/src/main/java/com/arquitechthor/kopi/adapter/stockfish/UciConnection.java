package com.arquitechthor.kopi.adapter.stockfish;

import java.time.Duration;

/// A line-based conversation with a UCI engine: commands go in, lines come
/// out. [UciProcess] talks to a real process; tests use a scripted one, so
/// the protocol in [UciEngine] is tested without Stockfish.
interface UciConnection extends AutoCloseable {

    /// Sends one command.
    ///
    /// @throws UciException if the engine is gone
    void send(String command);

    /// The next line the engine wrote, waiting at most `timeout`.
    ///
    /// @throws UciException if nothing arrives in time or the engine is gone
    String receive(Duration timeout);

    /// Whether the engine is still running.
    boolean isAlive();

    /// Ends the conversation (and the process). Never throws.
    @Override
    void close();
}
