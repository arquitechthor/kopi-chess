package com.arquitechthor.kopi.adapter.persistence;

import com.arquitechthor.kopi.application.port.AnalysisJob;
import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.domain.game.GameId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/// [AnalysisJobQueue] on a PostgreSQL table (design §6.2, ADR-007).
///
/// The heart of it is [#claimNext]: one `update … returning` whose inner
/// `select … for update skip locked` takes the oldest queued row that no
/// other transaction holds. Two workers claiming at once each get a
/// different job, and neither waits for the other. Every statement is a
/// single atomic `update` or `insert`, so no explicit transaction is needed.
///
/// Times come from the caller (the use case's `Clock`), not from `now()`,
/// so tests decide what "now" and "stale" mean.
public final class JdbcAnalysisJobQueue implements AnalysisJobQueue {

    private static final String COLUMNS = "id, game_id, state, attempts, last_error, created_at";

    private final JdbcClient jdbc;

    public JdbcAnalysisJobQueue(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public AnalysisJob enqueue(GameId gameId, Instant now) {
        // Insert, or start a failed job over; any other job stays as it is
        // (the update's where clause does nothing, and nothing is returned).
        jdbc.sql("""
                insert into analysis_job (game_id, state, attempts, created_at)
                values (:gameId, 'QUEUED', 0, :now)
                on conflict (game_id) do update
                   set state = 'QUEUED', attempts = 0, last_error = null, locked_at = null, created_at = :now
                 where analysis_job.state = 'FAILED'""")
                .param("gameId", gameId.value())
                .param("now", timestamp(now))
                .update();
        return findByGameId(gameId).orElseThrow();
    }

    @Override
    public Optional<AnalysisJob> claimNext(Instant now) {
        return jdbc.sql("""
                        update analysis_job
                           set state = 'RUNNING', locked_at = :now, attempts = attempts + 1
                         where id = (select id from analysis_job
                                      where state = 'QUEUED'
                                      order by created_at, id
                                      for update skip locked
                                      limit 1)
                        returning\s""" + COLUMNS)
                .param("now", timestamp(now))
                .query(JdbcAnalysisJobQueue::job)
                .optional();
    }

    @Override
    public Optional<AnalysisJob> findByGameId(GameId gameId) {
        return jdbc.sql("select " + COLUMNS + " from analysis_job where game_id = :gameId")
                .param("gameId", gameId.value())
                .query(JdbcAnalysisJobQueue::job)
                .optional();
    }

    @Override
    public void complete(long jobId) {
        finish(jobId, "DONE", null);
    }

    @Override
    public void retry(long jobId, String error) {
        finish(jobId, "QUEUED", Objects.requireNonNull(error, "error"));
    }

    @Override
    public void fail(long jobId, String error) {
        finish(jobId, "FAILED", Objects.requireNonNull(error, "error"));
    }

    private void finish(long jobId, String state, String error) {
        var updated = jdbc.sql("""
                update analysis_job set state = :state, last_error = :error, locked_at = null
                 where id = :id and state = 'RUNNING'""")
                .param("state", state)
                .param("error", error)
                .param("id", jobId)
                .update();
        if (updated == 0) {
            throw new IllegalStateException("Job " + jobId + " is not running");
        }
    }

    @Override
    public int recoverStale(Instant lockedBefore, int maxAttempts) {
        return jdbc.sql("""
                update analysis_job
                   set state      = case when attempts < :maxAttempts then 'QUEUED' else 'FAILED' end,
                       last_error = 'Interrupted: the application stopped while analysing',
                       locked_at  = null
                 where state = 'RUNNING' and locked_at < :lockedBefore""")
                .param("maxAttempts", maxAttempts)
                .param("lockedBefore", timestamp(lockedBefore))
                .update();
    }

    private static AnalysisJob job(ResultSet rs, int rowNumber) throws SQLException {
        return new AnalysisJob(
                rs.getLong("id"),
                new GameId(rs.getObject("game_id", UUID.class)),
                AnalysisJob.State.valueOf(rs.getString("state")),
                rs.getInt("attempts"),
                Optional.ofNullable(rs.getString("last_error")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
