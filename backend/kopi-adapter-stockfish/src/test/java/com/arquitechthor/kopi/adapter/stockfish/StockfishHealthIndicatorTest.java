package com.arquitechthor.kopi.adapter.stockfish;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class StockfishHealthIndicatorTest {

    @Test
    void isUpWithTheEngineNameWhenItAnswers() {
        var pool = new EnginePool<>(1,
                () -> UciEngine.start(ScriptedConnection.answeringGo(List.of()), Duration.ofSeconds(1)),
                UciEngine::isAlive, Duration.ofSeconds(1));

        var health = new StockfishHealthIndicator(pool).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("engine", "Stockfish 19");
    }

    @Test
    void isDownWithTheReasonWhenThereIsNoEngine() {
        var pool = new EnginePool<UciEngine>(1, () -> {
            throw new UciException("Cannot start the engine at stockfish");
        }, UciEngine::isAlive, Duration.ofSeconds(1));

        var health = new StockfishHealthIndicator(pool).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("error", "Cannot start the engine at stockfish");
    }
}
