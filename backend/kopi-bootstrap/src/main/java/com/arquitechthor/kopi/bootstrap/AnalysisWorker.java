package com.arquitechthor.kopi.bootstrap;

import com.arquitechthor.kopi.application.analysis.RunAnalysisJobUseCase;
import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.SmartLifecycle;

/// The background side of the analysis (ADR-007): a few virtual threads,
/// each claiming the next queued job over and over, and resting a moment
/// when there is none.
///
/// On start it first recovers the jobs a previous run left `RUNNING`
/// (design §6.2): this application is the only worker, so whatever was
/// running before it started has lost its worker. They go back to the
/// queue, or fail if they have used up their attempts.
///
/// While a job runs, its log lines carry its `gameId`: set when the job is
/// claimed ([LoggedAnalysisJobQueue]), removed here when it ends, so the
/// next job (or the rest) never logs under the wrong game.
///
/// On stop, the workers are interrupted; a job cut short stays `RUNNING`
/// and is recovered the next time the application starts.
final class AnalysisWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AnalysisWorker.class);

    private final RunAnalysisJobUseCase runner;
    private final AnalysisJobQueue queue;
    private final AnalysisProperties properties;
    private final Clock clock;
    private final List<Thread> threads = new ArrayList<>();
    private volatile boolean running;

    AnalysisWorker(RunAnalysisJobUseCase runner, AnalysisJobQueue queue, AnalysisProperties properties, Clock clock) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.queue = Objects.requireNonNull(queue, "queue");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public synchronized void start() {
        if (!properties.enabled()) {
            log.info("Analysis workers disabled (kopi.analysis.enabled=false)");
            return;
        }
        var recovered = queue.recoverStale(clock.instant(), properties.maxAttempts());
        if (recovered > 0) {
            log.info("Recovered {} analysis job(s) left running by a previous run", recovered);
        }
        running = true;
        for (int i = 1; i <= properties.workers(); i++) {
            threads.add(Thread.ofVirtual().name("analysis-worker-" + i).start(this::work));
        }
    }

    private void work() {
        while (running) {
            boolean ran;
            try {
                ran = runner.runNext();
            } catch (RuntimeException e) {
                // The job records an analysis that fails; this is the queue
                // itself failing (the database is down…). Wait and try again.
                log.warn("Analysis worker failed; retrying in {}", properties.pollInterval(), e);
                ran = false;
            } finally {
                MDC.remove(GameLogFilter.GAME_ID);
            }
            if (!ran) {
                rest(properties.pollInterval());
            }
        }
    }

    private void rest(Duration pause) {
        try {
            Thread.sleep(pause);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        threads.forEach(Thread::interrupt);
        for (var thread : threads) {
            try {
                thread.join(Duration.ofSeconds(5));
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        threads.clear();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
