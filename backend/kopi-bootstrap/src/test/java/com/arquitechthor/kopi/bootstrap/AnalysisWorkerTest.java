package com.arquitechthor.kopi.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.application.port.AnalysisJob;
import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.domain.game.GameId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/// The worker's lifecycle, without Spring or a database: the queue's own
/// behaviour is tested against PostgreSQL (`JdbcAnalysisJobQueueIT`).
class AnalysisWorkerTest {

    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static AnalysisProperties properties(boolean enabled) {
        return new AnalysisProperties(1_000, null, 2, 5, 3, 2, Duration.ofMillis(10), enabled);
    }

    /// A queue that only records the recovery.
    private static final class RecordingQueue implements AnalysisJobQueue {

        Instant lockedBefore;
        int maxAttempts;

        @Override
        public int recoverStale(Instant lockedBefore, int maxAttempts) {
            this.lockedBefore = lockedBefore;
            this.maxAttempts = maxAttempts;
            return 1;
        }

        @Override
        public AnalysisJob enqueue(GameId gameId, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<AnalysisJob> claimNext(Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<AnalysisJob> findByGameId(GameId gameId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void complete(long jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void retry(long jobId, String error) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void fail(long jobId, String error) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void onStartItRecoversWhatAPreviousRunLeftRunningThenWorks() throws Exception {
        var queue = new RecordingQueue();
        var runs = new AtomicInteger();
        var bothWorkersRan = new CountDownLatch(4);
        var worker = new AnalysisWorker(() -> {
            runs.incrementAndGet();
            bothWorkersRan.countDown();
            return false; // nothing queued: rest and ask again
        }, queue, properties(true), CLOCK);

        worker.start();
        try {
            // Everything running before this start lost its worker.
            assertThat(queue.lockedBefore).isEqualTo(NOW);
            assertThat(queue.maxAttempts).isEqualTo(3);
            assertThat(bothWorkersRan.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(worker.isRunning()).isTrue();
        } finally {
            worker.stop();
        }
        assertThat(worker.isRunning()).isFalse();
        var afterStop = runs.get();
        Thread.sleep(100);
        assertThat(runs.get()).isEqualTo(afterStop);
    }

    @Test
    void aFailingQueueDoesNotStopTheWorker() throws Exception {
        var calls = new CountDownLatch(3);
        var worker = new AnalysisWorker(() -> {
            calls.countDown();
            throw new IllegalStateException("database down");
        }, new RecordingQueue(), properties(true), CLOCK);

        worker.start();
        try {
            assertThat(calls.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            worker.stop();
        }
    }

    @Test
    void disabledItDoesNothing() {
        var queue = new RecordingQueue();
        var worker = new AnalysisWorker(() -> {
            throw new AssertionError("must not run");
        }, queue, properties(false), CLOCK);

        worker.start();

        assertThat(worker.isRunning()).isFalse();
        assertThat(queue.lockedBefore).isNull();
    }
}
