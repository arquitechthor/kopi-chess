package com.arquitechthor.kopi.application.port;

import com.arquitechthor.kopi.domain.game.GameId;
import java.time.Instant;
import java.util.Optional;

/// The analyses waiting to run: a durable queue, one job per game (design
/// §6.2, ADR-007). Implemented by the persistence adapter on a PostgreSQL
/// table, so no broker is needed and a job survives a restart.
///
/// Several workers may claim at once: each job goes to exactly one of them.
public interface AnalysisJobQueue {

    /// Queues the analysis of `gameId`. Idempotent: a game already queued,
    /// running or done keeps its job as it is; a `FAILED` one is queued
    /// again from scratch (no attempts, no error).
    ///
    /// @return the game's job, as it is now
    AnalysisJob enqueue(GameId gameId, Instant now);

    /// Takes the oldest queued job for this worker: `RUNNING`, one more
    /// attempt, locked at `now`. Jobs another worker is claiming at the same
    /// moment are skipped, not waited for.
    ///
    /// @return the job, or nothing if none is queued
    Optional<AnalysisJob> claimNext(Instant now);

    /// The game's job, if it has one.
    Optional<AnalysisJob> findByGameId(GameId gameId);

    /// The job's analysis is saved: `DONE`.
    void complete(long jobId);

    /// The attempt failed but may succeed later: back to `QUEUED`, keeping
    /// `error` as the reason.
    void retry(long jobId, String error);

    /// Given up: `FAILED`, with `error` as the reason.
    void fail(long jobId, String error);

    /// For startup (design §6.2): jobs still `RUNNING` since before
    /// `lockedBefore` lost their worker (the application stopped). Those
    /// with fewer than `maxAttempts` attempts go back to `QUEUED`; the rest
    /// are `FAILED`. Nothing stays "running" forever.
    ///
    /// @return how many jobs were recovered or failed
    int recoverStale(Instant lockedBefore, int maxAttempts);
}
