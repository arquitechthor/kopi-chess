package com.arquitechthor.kopi.adapter.stockfish;

import com.arquitechthor.kopi.application.port.BotPlayer;
import com.arquitechthor.kopi.application.port.BotUnavailableException;
import com.arquitechthor.kopi.domain.chess.Move;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/// `BISHOP`, `ROOK` and `QUEEN`: Stockfish with a different handicap each
/// (design §7), implementing the same [BotPlayer] port as Kopi's own bots.
///
/// Engines are shared by the three profiles, so every move sets the
/// profile's options again before searching: a few lines of UCI, nothing
/// next to the search itself.
///
/// Whatever goes wrong (no binary, a crash, a timeout, a nonsense answer)
/// becomes a [BotUnavailableException]: the use case saves nothing and the
/// game stays as it was (REST: `503`).
public final class StockfishBots implements BotPlayer {

    private final EnginePool<UciEngine> pool;
    private final Map<BotProfile, StockfishProperties.Profile> profiles;
    private final Duration moveMargin;

    StockfishBots(EnginePool<UciEngine> pool, Map<BotProfile, StockfishProperties.Profile> profiles,
            Duration moveMargin) {
        this.pool = Objects.requireNonNull(pool, "pool");
        this.profiles = Map.copyOf(profiles);
        this.moveMargin = Objects.requireNonNull(moveMargin, "moveMargin");
    }

    /// Whether `profile` is configured to play with Stockfish.
    public boolean plays(BotProfile profile) {
        return profiles.containsKey(profile);
    }

    @Override
    public Move chooseMove(Position position, BotProfile profile) {
        var settings = profiles.get(profile);
        if (settings == null) {
            throw new BotUnavailableException(profile,
                    new IllegalStateException("No Stockfish settings for " + profile));
        }
        var uci = searchWithOneRetry(position, profile, settings);
        return position.legalMoves().stream()
                .filter(move -> move.toUci().equals(uci))
                .findFirst()
                .orElseThrow(() -> new BotUnavailableException(profile,
                        new UciException("The engine chose " + uci + ", not a legal move in " + position.toFen())));
    }

    /// The engine's move in UCI. If the engine dies or misbehaves (the pool
    /// then discards it), it is tried once more with a fresh engine before
    /// giving up: a crash between two moves costs nothing but a restart.
    private String searchWithOneRetry(Position position, BotProfile profile, StockfishProperties.Profile settings) {
        try {
            return search(position, settings);
        } catch (UciException firstFailure) {
            try {
                return search(position, settings);
            } catch (UciException | IllegalArgumentException e) {
                e.addSuppressed(firstFailure);
                throw new BotUnavailableException(profile, e);
            }
        } catch (IllegalArgumentException e) {
            throw new BotUnavailableException(profile, e); // settings out of the engine's range
        }
    }

    /// The engine's move in UCI, with the profile's options set first.
    private String search(Position position, StockfishProperties.Profile settings) {
        return pool.withEngine(engine -> {
            engine.setOption("Skill Level", settings.skillLevel());
            engine.setOption("UCI_LimitStrength", settings.limitStrength());
            if (settings.limitStrength()) {
                engine.setOption("UCI_Elo", settings.elo());
            }
            engine.waitUntilReady();
            var result = engine.search(position.toFen(), "movetime " + settings.movetime().toMillis(),
                    settings.movetime().plus(moveMargin));
            return result.bestMove().orElseThrow(() -> new UciException("No move in " + position.toFen()));
        });
    }
}
