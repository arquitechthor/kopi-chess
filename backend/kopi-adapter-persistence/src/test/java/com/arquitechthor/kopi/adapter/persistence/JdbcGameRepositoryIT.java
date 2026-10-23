package com.arquitechthor.kopi.adapter.persistence;

import static com.arquitechthor.kopi.adapter.persistence.GameRowsTest.assertSameGame;
import static com.arquitechthor.kopi.adapter.persistence.GameRowsTest.newGame;
import static com.arquitechthor.kopi.adapter.persistence.GameRowsTest.play;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.application.port.ConcurrentGameUpdateException;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/// The repository against a real PostgreSQL (Testcontainers), with the
/// Flyway migrations applied. Needs Docker; CI has it.
@SpringBootTest(classes = JdbcGameRepositoryIT.PersistenceTestApplication.class)
class JdbcGameRepositoryIT {

    /// Just this adapter, Spring Boot's JDBC and Flyway auto-configuration,
    /// and a throwaway database.
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(PersistenceAdapterConfiguration.class)
    static class PersistenceTestApplication {

        /// Same image as `compose.yaml` and kopi-bootstrap's tests; keep them in sync.
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18.6-alpine3.24");
        }
    }

    @Autowired
    GameRepository repository;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void emptyTables() {
        jdbc.sql("delete from game").update();
    }

    @Test
    void storesAndLoadsANewGame() {
        var game = newGame(Color.WHITE);

        repository.insert(game);

        assertThat(repository.findById(game.id())).hasValueSatisfying(loaded -> assertSameGame(loaded, game));
    }

    @Test
    void anUnknownIdFindsNothing() {
        assertThat(repository.findById(new GameId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void anUpdateAppendsTheNewMoves() {
        var game = newGame(Color.WHITE);
        repository.insert(game);
        var later = play(game, "e2e4 e7e5");

        repository.update(later, 0);

        assertThat(repository.findById(game.id())).hasValueSatisfying(loaded -> assertSameGame(loaded, later));
        assertThat(jdbc.sql("select san from game_move where game_id = :id order by ply")
                .param("id", game.id().value()).query(String.class).list()).containsExactly("e4", "e5");
    }

    @Test
    void aFinishedGameIsStoredWithItsEnding() {
        var game = newGame(Color.WHITE);
        repository.insert(game);
        var mated = play(game, "f2f3 e7e5 g2g4 d8h4");

        repository.update(mated, 0);

        var row = jdbc.sql("select status, result, termination from game where id = :id")
                .param("id", game.id().value())
                .query((rs, _) -> rs.getString("status") + " " + rs.getString("result") + " "
                        + rs.getString("termination"))
                .single();
        assertThat(row).isEqualTo("FINISHED BLACK_WIN CHECKMATE");
        assertThat(repository.findById(game.id())).hasValueSatisfying(loaded -> assertSameGame(loaded, mated));
    }

    @Test
    void aStaleUpdateIsRefusedAndStoresNothing() {
        var game = newGame(Color.WHITE);
        repository.insert(game);
        var first = play(game, "e2e4 e7e5");
        repository.update(first, 0);
        // A second request that also loaded version 0.
        var second = play(game, "d2d4 d7d5");

        assertThatThrownBy(() -> repository.update(second, 0)).isInstanceOf(ConcurrentGameUpdateException.class);
        assertThat(repository.findById(game.id())).hasValueSatisfying(loaded -> assertSameGame(loaded, first));
    }

    @Test
    void listsTheNewestGamesFirst() {
        for (int i = 0; i < 3; i++) {
            repository.insert(Game.start(new GameId(new UUID(0, i + 1)), Color.WHITE, BotProfile.values()[i],
                    Position.initial(), Instant.parse("2026-10-23T12:00:00Z").plusSeconds(i)));
        }

        var page = repository.findRecent(0, 2);

        assertThat(page).extracting(Game::botProfile).containsExactly(BotProfile.BISHOP, BotProfile.KNIGHT);
        assertThat(repository.findRecent(2, 2)).extracting(Game::botProfile).containsExactly(BotProfile.PAWN);
        assertThat(repository.count()).isEqualTo(3);
    }
}
