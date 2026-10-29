package com.arquitechthor.kopi.bootstrap;

import com.arquitechthor.kopi.application.port.BotPlayer;
import com.arquitechthor.kopi.domain.chess.Move;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;

/// A [BotPlayer] that times every move of the one it wraps:
/// `kopi.bot.move.duration{profile, outcome}` (design §14).
///
/// A decorator, so the use cases and the bots stay unaware of Micrometer
/// (the application ring may not even see it: `HexagonalArchitectureTest`).
/// `outcome` is `ok`, or `error` when the bot could not answer: a timeout
/// is a slow move too, and should show.
final class TimedBotPlayer implements BotPlayer {

    static final String METRIC = "kopi.bot.move.duration";

    private final BotPlayer bot;
    private final MeterRegistry registry;

    TimedBotPlayer(BotPlayer bot, MeterRegistry registry) {
        this.bot = Objects.requireNonNull(bot, "bot");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public Move chooseMove(Position position, BotProfile profile) {
        var sample = Timer.start(registry);
        var outcome = "error";
        try {
            var move = bot.chooseMove(position, profile);
            outcome = "ok";
            return move;
        } finally {
            sample.stop(Timer.builder(METRIC)
                    .description("Time a bot takes to choose its move")
                    .tag("profile", profile.name())
                    .tag("outcome", outcome)
                    .publishPercentileHistogram()
                    .register(registry));
        }
    }
}
