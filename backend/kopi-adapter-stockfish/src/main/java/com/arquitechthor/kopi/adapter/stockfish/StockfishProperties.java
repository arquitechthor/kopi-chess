package com.arquitechthor.kopi.adapter.stockfish;

import com.arquitechthor.kopi.domain.game.BotProfile;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// `kopi.stockfish.*` in `application.yml`: where the engine is and how
/// each Stockfish bot plays (design §7: the values live in configuration,
/// not in code).
///
/// @param path         the engine binary; `stockfish` means "on the PATH"
/// @param poolSize     how many engine processes may run at once
/// @param startTimeout how long the engine may take to answer `uci` and
///                     `isready`
/// @param waitTimeout  how long a move waits for a free engine
/// @param moveMargin   time allowed on top of a profile's `movetime`
///                     before the move is given up
/// @param profiles     how `BISHOP`, `ROOK` and `QUEEN` play
/// @param analysis     the engines that judge finished games
@ConfigurationProperties("kopi.stockfish")
public record StockfishProperties(
        @DefaultValue("stockfish") String path,
        @DefaultValue("1") int poolSize,
        @DefaultValue("10s") Duration startTimeout,
        @DefaultValue("5s") Duration waitTimeout,
        @DefaultValue("2s") Duration moveMargin,
        Map<BotProfile, Profile> profiles,
        Analysis analysis) {

    public StockfishProperties {
        profiles = profiles == null ? Map.of() : Map.copyOf(profiles);
        analysis = analysis == null ? Analysis.DEFAULTS : analysis;
    }

    /// The analysis engines (design §8.2): their own pool, so analysing a
    /// game never makes a bot wait.
    ///
    /// @param poolSize        how many analysis engines may run at once
    ///                        (one analysis at a time in the MVP)
    /// @param threads         `Threads` of each engine
    /// @param hashMb          `Hash` of each engine, in MB; fixed, so
    ///                        results do not depend on the machine
    /// @param waitTimeout     how long a position waits for a free engine
    /// @param positionTimeout how long one position may take
    public record Analysis(
            @DefaultValue("1") int poolSize,
            @DefaultValue("1") int threads,
            @DefaultValue("64") int hashMb,
            @DefaultValue("5m") Duration waitTimeout,
            @DefaultValue("60s") Duration positionTimeout) {

        static final Analysis DEFAULTS = new Analysis(1, 1, 64, Duration.ofMinutes(5), Duration.ofSeconds(60));
    }

    /// How one bot uses Stockfish.
    ///
    /// @param skillLevel    `Skill Level`, 0–20 (20: no handicap)
    /// @param limitStrength `UCI_LimitStrength`: play at `elo`
    /// @param elo           `UCI_Elo`, 1320–3190 in Stockfish 19; only
    ///                      used with `limitStrength`
    /// @param movetime      thinking time per move
    public record Profile(
            @DefaultValue("20") int skillLevel,
            @DefaultValue("false") boolean limitStrength,
            @DefaultValue("1320") int elo,
            @DefaultValue("1s") Duration movetime) {}
}
