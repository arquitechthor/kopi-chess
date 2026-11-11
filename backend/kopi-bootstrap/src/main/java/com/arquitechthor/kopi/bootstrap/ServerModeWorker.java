package com.arquitechthor.kopi.bootstrap;

import com.arquitechthor.kopi.application.servermode.ServerModeUseCase;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/// Applies server mode (ADR-022) at start-up, since it is remembered across
/// restarts, and then every minute, so it follows the person signing in,
/// out or with another account.
final class ServerModeWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ServerModeWorker.class);
    private static final Duration EVERY = Duration.ofMinutes(1);

    private final ServerModeUseCase serverMode;
    private volatile boolean running;
    private Thread thread;

    ServerModeWorker(ServerModeUseCase serverMode) {
        this.serverMode = Objects.requireNonNull(serverMode, "serverMode");
    }

    @Override
    public synchronized void start() {
        running = true;
        thread = Thread.ofVirtual().name("server-mode").start(this::work);
    }

    private void work() {
        while (running) {
            try {
                serverMode.apply();
            } catch (RuntimeException e) {
                log.warn("Server mode could not be applied: {}", e.toString());
            }
            try {
                Thread.sleep(EVERY);
            } catch (InterruptedException _) {
                return; // stopping
            }
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
