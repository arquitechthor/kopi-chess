package com.arquitechthor.kopi.adapter.stockfish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.arquitechthor.kopi.application.port.BotUnavailableException;
import com.arquitechthor.kopi.domain.chess.Fen;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/// The Stockfish bots against the real engine. Like the domain's
/// differential test, they need the binary: `KOPI_STOCKFISH` (CI sets it)
/// or a copy in the repository's `tools/stockfish/`. Without one they are
/// skipped locally and fail in CI.
class StockfishBotsTest {

    private static final Map<BotProfile, StockfishProperties.Profile> PROFILES = Map.of(
            BotProfile.BISHOP, new StockfishProperties.Profile(3, false, 1320, Duration.ofMillis(50)),
            BotProfile.ROOK, new StockfishProperties.Profile(20, true, 1600, Duration.ofMillis(50)),
            BotProfile.QUEEN, new StockfishProperties.Profile(20, false, 1320, Duration.ofMillis(100)));

    private EnginePool<UciEngine> pool;

    private StockfishBots bots(Path binary) {
        pool = new EnginePool<>(1, () -> UciEngine.start(UciProcess.start(binary), Duration.ofSeconds(10)),
                UciEngine::isAlive, Duration.ofSeconds(5));
        return new StockfishBots(pool, PROFILES, Duration.ofSeconds(2));
    }

    @AfterEach
    void closePool() {
        if (pool != null) {
            pool.close();
        }
    }

    /// The engine binary, or the test is skipped (but not in CI).
    static Path binary() {
        var configured = System.getenv("KOPI_STOCKFISH");
        if (configured != null && !configured.isBlank()) {
            var path = Path.of(configured);
            assertThat(Files.isExecutable(path)).as("KOPI_STOCKFISH=" + configured).isTrue();
            return path;
        }
        var found = local();
        assumeTrue(found.isPresent(), "no Stockfish binary: set KOPI_STOCKFISH or put one in tools/stockfish/");
        return found.get();
    }

    private static Optional<Path> local() {
        // Surefire runs in the module folder: backend/kopi-adapter-stockfish.
        var tools = Path.of("..", "..", "tools", "stockfish");
        if (!Files.isDirectory(tools)) {
            return Optional.empty();
        }
        try (var files = Files.list(tools)) {
            return files.filter(file -> file.getFileName().toString().startsWith("stockfish"))
                    .filter(Files::isRegularFile).findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void everyStockfishProfilePlaysALegalMove() {
        var bots = bots(binary());

        for (var profile : PROFILES.keySet()) {
            var move = bots.chooseMove(Position.initial(), profile);

            assertThat(Position.initial().legalMoves()).as(profile.toString()).contains(move);
        }
    }

    @Test
    void queenFindsAMateInOne() {
        var bots = bots(binary());

        var move = bots.chooseMove(Fen.parse("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1"), BotProfile.QUEEN);

        assertThat(move.toUci()).isEqualTo("a1a8");
    }

    @Test
    void answersWithinItsTime() {
        var bots = bots(binary());
        bots.chooseMove(Position.initial(), BotProfile.QUEEN); // starts the engine

        var start = System.nanoTime();
        bots.chooseMove(Fen.parse("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3"),
                BotProfile.QUEEN);
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed).isLessThan(Duration.ofMillis(100).plusSeconds(1));
    }

    @Test
    void recoversWhenTheEngineIsKilled() {
        var bots = bots(binary());
        bots.chooseMove(Position.initial(), BotProfile.BISHOP);

        // Kill every engine process this JVM started, as if it had crashed.
        ProcessHandle.current().children().forEach(ProcessHandle::destroyForcibly);
        ProcessHandle.current().children().forEach(child -> child.onExit().join());

        var move = bots.chooseMove(Position.initial(), BotProfile.BISHOP);
        assertThat(Position.initial().legalMoves()).contains(move);
    }

    @Test
    void withoutAnEngineTheBotIsUnavailable() {
        var bots = bots(Path.of("no-such-stockfish"));

        assertThatThrownBy(() -> bots.chooseMove(Position.initial(), BotProfile.QUEEN))
                .isInstanceOfSatisfying(BotUnavailableException.class,
                        e -> assertThat(e.profile()).isEqualTo(BotProfile.QUEEN));
    }

    @Test
    void aProfileWithoutSettingsIsUnavailable() {
        var bots = new StockfishBots(new EnginePool<>(1, () -> {
            throw new UciException("unused");
        }, UciEngine::isAlive, Duration.ofSeconds(1)), Map.of(), Duration.ofSeconds(1));

        assertThat(bots.plays(BotProfile.ROOK)).isFalse();
        assertThatThrownBy(() -> bots.chooseMove(Position.initial(), BotProfile.ROOK))
                .isInstanceOf(BotUnavailableException.class);
    }
}
