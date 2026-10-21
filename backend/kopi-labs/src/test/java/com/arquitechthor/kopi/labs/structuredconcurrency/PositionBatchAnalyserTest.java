package com.arquitechthor.kopi.labs.structuredconcurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PositionBatchAnalyserTest {

    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String AFTER_E4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";
    private static final String BROKEN = "not a fen";

    @Test
    void returnsTheResultsInTheOrderOfThePositions() throws InterruptedException {
        var analyser = new PositionBatchAnalyser<String, String>(fen -> fen.split(" ")[1]);

        assertThat(analyser.analyseAll(List.of(START, AFTER_E4))).containsExactly("w", "b");
    }

    @Test
    void eachPositionRunsInItsOwnVirtualThread() throws InterruptedException {
        var analyser = new PositionBatchAnalyser<String, Boolean>(_ -> Thread.currentThread().isVirtual());

        assertThat(analyser.analyseAll(List.of(START, AFTER_E4))).containsOnly(true);
    }

    @Test
    void oneFailureCancelsTheOtherAnalysesAndIsReported() {
        var slowAnalysisStarted = new CountDownLatch(1);
        var slowAnalysisWasCancelled = new AtomicBoolean(false);

        var analyser = new PositionBatchAnalyser<String, String>(fen -> {
            if (fen.equals(BROKEN)) {
                awaitQuietly(slowAnalysisStarted);
                throw new IllegalArgumentException("Invalid FEN: " + fen);
            }
            slowAnalysisStarted.countDown();
            try {
                Thread.sleep(Duration.ofMinutes(1)); // an engine search that would take long
                return "finished";
            } catch (InterruptedException _) {
                slowAnalysisWasCancelled.set(true);
                return "cancelled";
            }
        });

        assertThatThrownBy(() -> analyser.analyseAll(List.of(START, BROKEN)))
                .isInstanceOf(StructuredTaskScope.FailedException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid FEN: not a fen");

        // analyseAll() only returns once every subtask has finished, so this
        // is already settled: no thread is left running in the background.
        assertThat(slowAnalysisWasCancelled).isTrue();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
