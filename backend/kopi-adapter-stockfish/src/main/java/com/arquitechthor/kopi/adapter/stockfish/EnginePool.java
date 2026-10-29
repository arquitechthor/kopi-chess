package com.arquitechthor.kopi.adapter.stockfish;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/// A fixed number of engines, each lent to one caller at a time.
///
/// - **Lazy:** an engine is started the first time it is needed, so the
///   application starts (and the low-level bots play) even without
///   Stockfish.
/// - **Self-healing:** an engine that fails while lent, or is found dead
///   when lent again, is closed and replaced by a new one on the next loan.
///   [#discarded] counts them: the `kopi.engine.restarts` metric.
/// - **Bounded wait:** a caller waits at most `waitTimeout` for a free
///   engine.
///
/// @param <E> the engine type: [UciEngine] in production, a fake in tests
final class EnginePool<E extends AutoCloseable> implements AutoCloseable {

    private final Supplier<E> factory;
    private final Predicate<E> alive;
    private final Duration waitTimeout;
    private final Semaphore permits;
    private final ConcurrentLinkedQueue<E> idle = new ConcurrentLinkedQueue<>();
    private final LongAdder discarded = new LongAdder();
    private volatile boolean closed;

    /// @param size        how many engines may exist at once
    /// @param factory     starts a new engine; may throw [UciException]
    /// @param alive       whether an idle engine is still usable
    /// @param waitTimeout how long a caller waits for a free engine
    EnginePool(int size, Supplier<E> factory, Predicate<E> alive, Duration waitTimeout) {
        if (size < 1) {
            throw new IllegalArgumentException("The pool needs at least one engine: " + size);
        }
        this.factory = Objects.requireNonNull(factory, "factory");
        this.alive = Objects.requireNonNull(alive, "alive");
        this.waitTimeout = Objects.requireNonNull(waitTimeout, "waitTimeout");
        this.permits = new Semaphore(size, true);
    }

    /// Runs `work` with an engine lent exclusively to it.
    ///
    /// If `work` throws, the engine is not trusted again: it is closed, and
    /// the exception goes on to the caller.
    ///
    /// @throws UciException if no engine is free in time, or one cannot be
    ///                      started
    <T> T withEngine(Function<E, T> work) {
        if (closed) {
            throw new UciException("The engine pool is closed");
        }
        acquire();
        try {
            var engine = idleOrNew();
            T result;
            try {
                result = work.apply(engine);
            } catch (RuntimeException e) {
                discard(engine);
                throw e;
            }
            if (closed) {
                closeQuietly(engine);
            } else {
                idle.add(engine);
            }
            return result;
        } finally {
            permits.release();
        }
    }

    private void acquire() {
        try {
            if (!permits.tryAcquire(waitTimeout.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new UciException("No engine free within " + waitTimeout);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UciException("Interrupted while waiting for an engine", e);
        }
    }

    /// An idle engine that is still alive, or a new one.
    private E idleOrNew() {
        E engine;
        while ((engine = idle.poll()) != null) {
            if (alive.test(engine)) {
                return engine;
            }
            discard(engine); // died while idle
        }
        return factory.get();
    }

    /// An engine that is no longer trusted: closed, and counted.
    private void discard(E engine) {
        discarded.increment();
        closeQuietly(engine);
    }

    /// How many engines have been thrown away, because they failed or died,
    /// since the pool was created. Each one is replaced by a new engine on
    /// the next loan; closing the pool discards nothing.
    long discarded() {
        return discarded.sum();
    }

    /// How many engines are waiting, started, to be lent.
    int idleCount() {
        return idle.size();
    }

    /// Closes every idle engine; engines on loan are closed when returned.
    @Override
    public void close() {
        closed = true;
        E engine;
        while ((engine = idle.poll()) != null) {
            closeQuietly(engine);
        }
    }

    private static void closeQuietly(AutoCloseable engine) {
        try {
            engine.close();
        } catch (Exception _) {
            // closing is best effort
        }
    }
}
