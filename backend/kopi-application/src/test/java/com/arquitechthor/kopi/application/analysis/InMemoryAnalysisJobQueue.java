package com.arquitechthor.kopi.application.analysis;

import com.arquitechthor.kopi.application.port.AnalysisJob;
import com.arquitechthor.kopi.application.port.AnalysisJob.State;
import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.domain.game.GameId;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/// An [AnalysisJobQueue] in a map, with the contract the database adapter
/// honours (minus the locking, which only PostgreSQL can show).
public final class InMemoryAnalysisJobQueue implements AnalysisJobQueue {

    private final Map<GameId, AnalysisJob> jobs = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public synchronized AnalysisJob enqueue(GameId gameId, Instant now) {
        var existing = jobs.get(gameId);
        if (existing == null || existing.state() == State.FAILED) {
            var id = existing == null ? nextId++ : existing.id();
            jobs.put(gameId, new AnalysisJob(id, gameId, State.QUEUED, 0, Optional.empty(), now));
        }
        return jobs.get(gameId);
    }

    @Override
    public synchronized Optional<AnalysisJob> claimNext(Instant now) {
        var next = jobs.values().stream()
                .filter(job -> job.state() == State.QUEUED)
                .min(Comparator.comparing(AnalysisJob::createdAt).thenComparing(AnalysisJob::id));
        next.ifPresent(job -> jobs.put(job.gameId(), new AnalysisJob(job.id(), job.gameId(), State.RUNNING,
                job.attempts() + 1, job.lastError(), job.createdAt())));
        return next.map(job -> jobs.get(job.gameId()));
    }

    @Override
    public synchronized Optional<AnalysisJob> findByGameId(GameId gameId) {
        return Optional.ofNullable(jobs.get(gameId));
    }

    @Override
    public void complete(long jobId) {
        finish(jobId, State.DONE, null);
    }

    @Override
    public void retry(long jobId, String error) {
        finish(jobId, State.QUEUED, error);
    }

    @Override
    public void fail(long jobId, String error) {
        finish(jobId, State.FAILED, error);
    }

    private synchronized void finish(long jobId, State state, String error) {
        var job = jobs.values().stream().filter(j -> j.id() == jobId && j.state() == State.RUNNING).findFirst()
                .orElseThrow(() -> new IllegalStateException("Job " + jobId + " is not running"));
        jobs.put(job.gameId(), new AnalysisJob(job.id(), job.gameId(), state, job.attempts(),
                Optional.ofNullable(error), job.createdAt()));
    }

    @Override
    public synchronized int recoverStale(Instant lockedBefore, int maxAttempts) {
        throw new UnsupportedOperationException("Tested against PostgreSQL (JdbcAnalysisJobQueueIT)");
    }
}
