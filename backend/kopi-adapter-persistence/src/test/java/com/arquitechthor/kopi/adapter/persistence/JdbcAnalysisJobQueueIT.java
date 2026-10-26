package com.arquitechthor.kopi.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.application.port.AnalysisJob;
import com.arquitechthor.kopi.application.port.AnalysisJob.State;
import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/// The job queue against a real PostgreSQL: the `SKIP LOCKED` claim and the
/// recovery after a restart (design §6.2) only mean something on the real
/// database.
@SpringBootTest(classes = JdbcGameRepositoryIT.PersistenceTestApplication.class)
class JdbcAnalysisJobQueueIT {

    private static final Instant T0 = Instant.parse("2026-10-26T12:00:00Z");

    @Autowired
    AnalysisJobQueue queue;

    @Autowired
    GameRepository games;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void emptyTables() {
        jdbc.sql("delete from game").update();
    }

    private GameId storedGame() {
        var game = Game.start(new GameId(UUID.randomUUID()), Color.WHITE, BotProfile.PAWN, Position.initial(), T0);
        games.insert(game);
        return game.id();
    }

    @Test
    void aQueuedJobWaitsForAWorker() {
        var game = storedGame();

        var job = queue.enqueue(game, T0);

        assertThat(job.gameId()).isEqualTo(game);
        assertThat(job.state()).isEqualTo(State.QUEUED);
        assertThat(job.attempts()).isZero();
        assertThat(job.lastError()).isEmpty();
        assertThat(job.createdAt()).isEqualTo(T0);
    }

    @Test
    void enqueuingTwiceKeepsOneJob() {
        var game = storedGame();
        var first = queue.enqueue(game, T0);
        queue.claimNext(T0.plusSeconds(1));

        var second = queue.enqueue(game, T0.plusSeconds(2));

        // Still the same job, still running: asking again changes nothing.
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.state()).isEqualTo(State.RUNNING);
        assertThat(jdbc.sql("select count(*) from analysis_job").query(Integer.class).single()).isOne();
    }

    @Test
    void aFailedJobIsQueuedAgainFromScratch() {
        var game = storedGame();
        var job = queue.enqueue(game, T0);
        queue.claimNext(T0);
        queue.fail(job.id(), "engine missing");

        var again = queue.enqueue(game, T0.plusSeconds(60));

        assertThat(again.id()).isEqualTo(job.id());
        assertThat(again.state()).isEqualTo(State.QUEUED);
        assertThat(again.attempts()).isZero();
        assertThat(again.lastError()).isEmpty();
    }

    @Test
    void workersTakeTheOldestJobFirst() {
        var later = storedGame();
        var earlier = storedGame();
        queue.enqueue(later, T0.plusSeconds(10));
        queue.enqueue(earlier, T0);

        var claimed = queue.claimNext(T0.plusSeconds(20)).orElseThrow();

        assertThat(claimed.gameId()).isEqualTo(earlier);
        assertThat(claimed.state()).isEqualTo(State.RUNNING);
        assertThat(claimed.attempts()).isOne();
        assertThat(queue.claimNext(T0.plusSeconds(21))).map(AnalysisJob::gameId).hasValue(later);
        assertThat(queue.claimNext(T0.plusSeconds(22))).isEmpty();
    }

    @Test
    void aJobHeldByAnotherWorkerIsSkippedNotWaitedFor() throws Exception {
        var first = storedGame();
        var second = storedGame();
        queue.enqueue(first, T0);
        queue.enqueue(second, T0.plusSeconds(1));

        // Another worker is in the middle of claiming the oldest job: its
        // transaction holds the row lock and has not committed yet.
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var otherWorker = CompletableFuture.runAsync(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(_ -> {
                    jdbc.sql("select id from analysis_job where game_id = :id for update")
                            .param("id", first.value()).query(Long.class).single();
                    locked.countDown();
                    await(release);
                }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        try {
            // This worker does not block on the locked row: it gets the next one.
            var claimed = CompletableFuture.supplyAsync(() -> queue.claimNext(T0.plusSeconds(2)))
                    .get(5, TimeUnit.SECONDS);
            assertThat(claimed).map(AnalysisJob::gameId).hasValue(second);
        } finally {
            release.countDown();
            otherWorker.get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void aFinishedAttemptLeavesTheJobDoneQueuedOrFailed() {
        var done = queue.enqueue(storedGame(), T0);
        var retried = queue.enqueue(storedGame(), T0.plusSeconds(1));
        var failed = queue.enqueue(storedGame(), T0.plusSeconds(2));
        queue.claimNext(T0);
        queue.claimNext(T0);
        queue.claimNext(T0);

        queue.complete(done.id());
        queue.retry(retried.id(), "engine timed out");
        queue.fail(failed.id(), "engine missing");

        assertThat(queue.findByGameId(done.gameId())).map(AnalysisJob::state).hasValue(State.DONE);
        assertThat(queue.findByGameId(retried.gameId())).hasValueSatisfying(job -> {
            assertThat(job.state()).isEqualTo(State.QUEUED);
            assertThat(job.lastError()).hasValue("engine timed out");
        });
        assertThat(queue.findByGameId(failed.gameId())).hasValueSatisfying(job -> {
            assertThat(job.state()).isEqualTo(State.FAILED);
            assertThat(job.lastError()).hasValue("engine missing");
        });
    }

    @Test
    void onlyARunningJobCanBeFinished() {
        var job = queue.enqueue(storedGame(), T0);

        assertThatThrownBy(() -> queue.complete(job.id())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void afterARestartStaleJobsRunAgainOrFail() {
        var stale = queue.enqueue(storedGame(), T0);
        var exhausted = queue.enqueue(storedGame(), T0.plusSeconds(1));
        var fresh = queue.enqueue(storedGame(), T0.plusSeconds(2));
        // Claimed long ago, the second one already for the third time.
        queue.claimNext(T0);
        jdbc.sql("update analysis_job set attempts = 3 where id = :id").param("id", exhausted.id()).update();
        queue.claimNext(T0);
        // Claimed just now: its worker is still alive.
        queue.claimNext(T0.plus(Duration.ofMinutes(30)));

        var recovered = queue.recoverStale(T0.plus(Duration.ofMinutes(10)), 3);

        assertThat(recovered).isEqualTo(2);
        assertThat(state(stale)).isEqualTo(State.QUEUED);
        assertThat(state(exhausted)).isEqualTo(State.FAILED);
        assertThat(queue.findByGameId(exhausted.gameId()).flatMap(AnalysisJob::lastError))
                .hasValueSatisfying(error -> assertThat(error).contains("stopped"));
        assertThat(state(fresh)).isEqualTo(State.RUNNING);
    }

    @Test
    void aGameWithoutAJobHasNone() {
        assertThat(queue.findByGameId(storedGame())).isEqualTo(Optional.empty());
    }

    private State state(AnalysisJob job) {
        return queue.findByGameId(job.gameId()).orElseThrow().state();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
