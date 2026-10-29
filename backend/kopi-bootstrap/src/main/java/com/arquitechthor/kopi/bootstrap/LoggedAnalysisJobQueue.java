package com.arquitechthor.kopi.bootstrap;

import com.arquitechthor.kopi.application.port.AnalysisJob;
import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.domain.game.GameId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/// The analysis queue as the worker sees it: it logs each job's life
/// (claimed, done, retried, failed) and, from the moment a job is claimed,
/// tags the worker's thread with its game (`gameId` in the MDC).
///
/// The game is only known once the use case has claimed the job, half-way
/// through `runNext()`, so it cannot be set around the call: it is set here,
/// and [AnalysisWorker] removes it when `runNext()` returns.
final class LoggedAnalysisJobQueue implements AnalysisJobQueue {

    private static final Logger log = LoggerFactory.getLogger(LoggedAnalysisJobQueue.class);

    private final AnalysisJobQueue queue;

    LoggedAnalysisJobQueue(AnalysisJobQueue queue) {
        this.queue = Objects.requireNonNull(queue, "queue");
    }

    @Override
    public AnalysisJob enqueue(GameId gameId, Instant now) {
        return queue.enqueue(gameId, now);
    }

    @Override
    public Optional<AnalysisJob> claimNext(Instant now) {
        var job = queue.claimNext(now);
        job.ifPresent(claimed -> {
            MDC.put(GameLogFilter.GAME_ID, claimed.gameId().value().toString());
            log.info("Analysis started (attempt {})", claimed.attempts());
        });
        return job;
    }

    @Override
    public Optional<AnalysisJob> findByGameId(GameId gameId) {
        return queue.findByGameId(gameId);
    }

    @Override
    public int countQueued() {
        return queue.countQueued();
    }

    @Override
    public void complete(long jobId) {
        queue.complete(jobId);
        log.info("Analysis done");
    }

    @Override
    public void retry(long jobId, String error) {
        queue.retry(jobId, error);
        log.warn("Analysis interrupted, queued again: {}", error);
    }

    @Override
    public void fail(long jobId, String error) {
        queue.fail(jobId, error);
        log.warn("Analysis failed: {}", error);
    }

    @Override
    public int recoverStale(Instant lockedBefore, int maxAttempts) {
        return queue.recoverStale(lockedBefore, maxAttempts);
    }
}
