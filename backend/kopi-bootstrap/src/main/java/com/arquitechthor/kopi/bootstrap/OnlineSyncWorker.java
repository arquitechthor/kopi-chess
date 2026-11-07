package com.arquitechthor.kopi.bootstrap;

import com.arquitechthor.kopi.application.online.OnlineUseCase;
import com.arquitechthor.kopi.application.port.RelayNotifications;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/// Keeps the online games in step with the relay in the background (plan of
/// player versus player, phase D):
///
/// - when the relay notifies news (a friend's move, an invitation), at once;
/// - and every couple of minutes anyway, in case a notification was lost.
///
/// Both are quiet without a Kopi session. Syncs never overlap: a
/// notification during a sync just asks for one more right after.
final class OnlineSyncWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OnlineSyncWorker.class);
    private static final Duration EVERY = Duration.ofMinutes(2);

    private final OnlineUseCase online;
    private final RelayNotifications notifications;
    private final AtomicBoolean wanted = new AtomicBoolean();
    private volatile boolean running;
    private Thread thread;

    OnlineSyncWorker(OnlineUseCase online, RelayNotifications notifications) {
        this.online = Objects.requireNonNull(online, "online");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
    }

    @Override
    public synchronized void start() {
        running = true;
        wanted.set(true); // once at start-up
        thread = Thread.ofVirtual().name("online-sync").start(this::work);
        notifications.listen(this::requestSync);
    }

    /// Asks for a sync as soon as possible.
    void requestSync() {
        wanted.set(true);
        var current = thread;
        if (current != null) {
            current.interrupt(); // wakes it if it is waiting
        }
    }

    private void work() {
        var lastSync = 0L;
        while (running) {
            if (wanted.getAndSet(false) || System.nanoTime() - lastSync >= EVERY.toNanos()) {
                try {
                    online.sync();
                } catch (RuntimeException e) {
                    log.warn("Online games could not be synced: {}", e.toString());
                }
                lastSync = System.nanoTime();
            }
            try {
                Thread.sleep(Duration.ofSeconds(5));
            } catch (InterruptedException _) {
                // woken up by requestSync (or by stop)
            }
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        notifications.stop();
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
