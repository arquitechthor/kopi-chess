package com.arquitechthor.kopi.adapter.stockfish;

import java.util.Objects;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/// `/actuator/health` → `stockfish`: borrows an engine from the pool and asks
/// `isready`. Up with the engine's name; down with the reason (no binary,
/// no answer…).
///
/// Not part of the readiness group (`application.yml`): without Stockfish
/// the application still serves games against `PAWN` and `KNIGHT`.
final class StockfishHealthIndicator implements HealthIndicator {

    private final EnginePool<UciEngine> pool;

    StockfishHealthIndicator(EnginePool<UciEngine> pool) {
        this.pool = Objects.requireNonNull(pool, "pool");
    }

    @Override
    public Health health() {
        try {
            var name = pool.withEngine(engine -> {
                engine.waitUntilReady();
                return engine.name();
            });
            return Health.up().withDetail("engine", name).build();
        } catch (UciException e) {
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}
