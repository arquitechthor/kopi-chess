package com.arquitechthor.kopi.adapter.stockfish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class EnginePoolTest {

    /// A stand-in for an engine: alive until killed or closed.
    static final class FakeEngine implements AutoCloseable {
        final int number;
        boolean alive = true;
        boolean closed;

        FakeEngine(int number) {
            this.number = number;
        }

        @Override
        public void close() {
            closed = true;
            alive = false;
        }
    }

    private final List<FakeEngine> started = new ArrayList<>();

    private EnginePool<FakeEngine> pool(int size) {
        return new EnginePool<>(size, () -> {
            var engine = new FakeEngine(started.size() + 1);
            started.add(engine);
            return engine;
        }, engine -> engine.alive, Duration.ofMillis(200));
    }

    @Test
    void startsEnginesOnlyWhenNeededAndReusesThem() {
        var pool = pool(2);
        assertThat(started).isEmpty();

        pool.withEngine(engine -> engine.number);
        pool.withEngine(engine -> engine.number);

        assertThat(started).hasSize(1);
        assertThat(pool.idleCount()).isEqualTo(1);
    }

    @Test
    void anEngineThatFailsIsClosedAndReplaced() {
        var pool = pool(1);

        assertThatThrownBy(() -> pool.withEngine(_ -> {
            throw new UciException("crashed");
        })).isInstanceOf(UciException.class);
        int next = pool.withEngine(engine -> engine.number);

        assertThat(started.getFirst().closed).isTrue();
        assertThat(next).isEqualTo(2);
    }

    @Test
    void anEngineThatDiedWhileIdleIsReplaced() {
        var pool = pool(1);
        pool.withEngine(engine -> engine.number);
        started.getFirst().alive = false; // killed from outside

        int replacement = pool.withEngine(engine -> engine.number);

        assertThat(replacement).isEqualTo(2);
    }

    @Test
    void lendsEachEngineToOneCallerAtATime() throws Exception {
        var pool = pool(1);
        var inUse = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = Thread.ofVirtual().start(() -> pool.withEngine(engine -> {
            inUse.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return engine.number;
        }));
        inUse.await(2, TimeUnit.SECONDS);

        // The only engine is lent: a second caller gives up after the wait.
        assertThatThrownBy(() -> pool.withEngine(engine -> engine.number))
                .isInstanceOf(UciException.class).hasMessageContaining("No engine free");

        release.countDown();
        holder.join(2_000);
        int again = pool.withEngine(engine -> engine.number);
        assertThat(again).isEqualTo(1);
    }

    @Test
    void closingThePoolClosesItsEngines() {
        var pool = pool(1);
        pool.withEngine(engine -> engine.number);

        pool.close();

        assertThat(started.getFirst().closed).isTrue();
        assertThatThrownBy(() -> pool.withEngine(engine -> engine.number)).isInstanceOf(UciException.class);
    }

    @Test
    void anEngineThatCannotStartIsReported() {
        var pool = new EnginePool<FakeEngine>(1, () -> {
            throw new UciException("no binary");
        }, engine -> engine.alive, Duration.ofMillis(100));

        assertThatThrownBy(() -> pool.withEngine(engine -> engine.number)).hasMessage("no binary");
        // The permit was given back: the next caller tries again.
        assertThatThrownBy(() -> pool.withEngine(engine -> engine.number)).hasMessage("no binary");
    }
}
