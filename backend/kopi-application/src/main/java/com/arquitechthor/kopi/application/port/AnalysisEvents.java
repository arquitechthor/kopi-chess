package com.arquitechthor.kopi.application.port;

import com.arquitechthor.kopi.domain.game.GameId;

/// Where an analysis tells how it is going, as it goes (design §2.6, §8.4).
/// Implemented by the REST adapter with Server-Sent Events.
///
/// Fire and forget: nobody may be listening, and a listener that went away
/// must not break the analysis. Implementations never throw.
public interface AnalysisEvents {

    /// `analysedPositions` of the game's `totalPositions` have been searched
    /// (one more than its moves).
    void progress(GameId gameId, int analysedPositions, int totalPositions);

    /// The analysis is saved.
    void completed(GameId gameId);

    /// The analysis was given up, for `reason`.
    void failed(GameId gameId, String reason);

    /// Nobody listening: for tests and for running without a web adapter.
    AnalysisEvents NONE = new AnalysisEvents() {
        @Override
        public void progress(GameId gameId, int analysedPositions, int totalPositions) {}

        @Override
        public void completed(GameId gameId) {}

        @Override
        public void failed(GameId gameId, String reason) {}
    };
}
