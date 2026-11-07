package com.arquitechthor.kopi.bootstrap;

import com.arquitechthor.kopi.application.history.HistoryUseCase;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/// Keeps the history in the Kopi account in step in the background
/// (ADR-021): a minute after start-up and then every few minutes. Quiet
/// while it is off, signed out or with another account; the account page
/// shows problems.
final class HistorySyncWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(HistorySyncWorker.class);
    private static final Duration FIRST = Duration.ofMinutes(1);
    private static final Duration EVERY = Duration.ofMinutes(3);

    private final HistoryUseCase history;
    private volatile boolean running;
    private Thread thread;

    HistorySyncWorker(HistoryUseCase history) {
        this.history = Objects.requireNonNull(history, "history");
    }

    @Override
    public synchronized void start() {
        running = true;
        thread = Thread.ofVirtual().name("history-sync").start(this::work);
    }

    private void work() {
        var wait = FIRST;
        while (running) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException _) {
                return; // stopping
            }
            try {
                history.sync();
            } catch (RuntimeException e) {
                log.warn("The history could not be synced: {}", e.toString());
            }
            wait = EVERY;
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
