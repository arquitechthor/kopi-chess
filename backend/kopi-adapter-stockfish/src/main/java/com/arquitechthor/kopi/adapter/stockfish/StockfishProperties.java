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
@ConfigurationProperties("kopi.stockfish")
public record StockfishProperties(
        @DefaultValue("stockfish") String path,
        @DefaultValue("1") int poolSize,
        @DefaultValue("10s") Duration startTimeout,
        @DefaultValue("5s") Duration waitTimeout,
        @DefaultValue("2s") Duration moveMargin,
        Map<BotProfile, Profile> profiles) {

    public StockfishProperties {
        profiles = profiles == null ? Map.of() : Map.copyOf(profiles);
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
