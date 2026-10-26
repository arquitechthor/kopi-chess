package com.arquitechthor.kopi.application.port;

import com.arquitechthor.kopi.domain.game.GameId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/// A request to analyse one game, as the [AnalysisJobQueue] keeps it.
///
/// @param id        the job
/// @param gameId    the game to analyse; at most one job per game
/// @param state     where the job is
/// @param attempts  how many times a worker has claimed it
/// @param lastError why the last attempt failed, if it did
/// @param createdAt when it was first queued; the queue is served in this
///                  order
public record AnalysisJob(long id, GameId gameId, State state, int attempts, Optional<String> lastError,
                          Instant createdAt) {

    public AnalysisJob {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(lastError, "lastError");
        Objects.requireNonNull(createdAt, "createdAt");
        if (attempts < 0) {
            throw new IllegalArgumentException("Attempts cannot be negative: " + attempts);
        }
    }

    /// `QUEUED` → `RUNNING` → `DONE`, or back to `QUEUED` to try again, or
    /// `FAILED` for good.
    public enum State {
        /// Waiting for a worker.
        QUEUED,
        /// A worker has it.
        RUNNING,
        /// The analysis is saved.
        DONE,
        /// Given up; [#lastError] says why. Asking again queues it anew.
        FAILED
    }
}
