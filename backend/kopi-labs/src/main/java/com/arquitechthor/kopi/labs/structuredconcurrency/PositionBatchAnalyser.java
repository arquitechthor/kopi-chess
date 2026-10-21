package com.arquitechthor.kopi.labs.structuredconcurrency;

import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.function.Function;

/// **Structured concurrency** (JEP 505, fifth preview in Java 25).
///
/// Analyses several positions in parallel, each one in its own virtual thread,
/// and treats them as **one unit of work**:
///
/// - if every analysis succeeds, the results come back in the same order as
///   the positions;
/// - if one fails, the others are **cancelled** (interrupted) and the failure
///   is reported to the caller;
/// - when [#analyseAll] returns, no thread it started is still running.
///
/// In Kopi Chess this is the shape of the post-game analysis: N+1 positions,
/// and one failure should stop the whole job instead of leaving engine
/// searches running in the background.
///
/// @param <P> the position type (a FEN string in the tests)
/// @param <R> the analysis result type
public final class PositionBatchAnalyser<P, R> {

    private final Function<P, R> analyse;

    public PositionBatchAnalyser(Function<P, R> analyse) {
        this.analyse = analyse;
    }

    /// Analyses all positions concurrently.
    ///
    /// @throws StructuredTaskScope.FailedException if any analysis fails; its
    ///         cause is the original exception
    /// @throws InterruptedException if the calling thread is interrupted
    public List<R> analyseAll(List<P> positions) throws InterruptedException {
        // The default joiner waits for all subtasks to succeed, and on the
        // first failure cancels the rest and makes join() throw.
        try (var scope = StructuredTaskScope.open()) {
            List<Subtask<R>> subtasks = positions.stream()
                    .map(position -> scope.fork(() -> analyse.apply(position)))
                    .toList();

            scope.join();

            return subtasks.stream().map(Subtask::get).toList();
        }
    }
}
