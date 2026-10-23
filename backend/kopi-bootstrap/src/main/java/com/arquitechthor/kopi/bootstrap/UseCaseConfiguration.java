package com.arquitechthor.kopi.bootstrap;

import com.arquitechthor.kopi.adapter.bots.KopiBots;
import com.arquitechthor.kopi.adapter.stockfish.StockfishBots;
import com.arquitechthor.kopi.application.game.CreateGame;
import com.arquitechthor.kopi.application.game.CreateGameUseCase;
import com.arquitechthor.kopi.application.game.GetGame;
import com.arquitechthor.kopi.application.game.GetGameUseCase;
import com.arquitechthor.kopi.application.game.ListGames;
import com.arquitechthor.kopi.application.game.ListGamesUseCase;
import com.arquitechthor.kopi.application.game.PlayMove;
import com.arquitechthor.kopi.application.game.PlayMoveUseCase;
import com.arquitechthor.kopi.application.game.Resign;
import com.arquitechthor.kopi.application.game.ResignUseCase;
import com.arquitechthor.kopi.application.port.BotPlayer;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.application.port.IdGenerator;
import com.arquitechthor.kopi.domain.game.GameId;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// The use cases and the ports they need, wired by hand (ADR-002): the
/// application ring has no Spring annotations, so this class is where each
/// port meets the adapter that implements it.
///
/// No transaction wraps the use cases (ADR-014): the repository makes each
/// save atomic, and optimistic locking catches anyone who saved in between.
@Configuration(proxyBeanMethods = false)
class UseCaseConfiguration {

    /// UTC, ticking in microseconds: PostgreSQL's precision, so a game
    /// returned by a use case is identical to the same game reloaded.
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }

    @Bean
    IdGenerator idGenerator() {
        return () -> new GameId(UUID.randomUUID());
    }

    /// One port, two adapters (ADR-006): Kopi's own bots for `PAWN` and
    /// `KNIGHT`, Stockfish for the rest.
    @Bean
    BotPlayer botPlayer(StockfishBots stockfish) {
        var own = new KopiBots();
        return (position, profile) -> switch (profile) {
            case PAWN, KNIGHT -> own.chooseMove(position, profile);
            case BISHOP, ROOK, QUEEN -> stockfish.chooseMove(position, profile);
        };
    }

    @Bean
    CreateGameUseCase createGame(GameRepository games, BotPlayer bot, IdGenerator ids, Clock clock) {
        return new CreateGame(games, bot, ids, clock);
    }

    @Bean
    PlayMoveUseCase playMove(GameRepository games, BotPlayer bot, Clock clock) {
        return new PlayMove(games, bot, clock);
    }

    @Bean
    ResignUseCase resign(GameRepository games, Clock clock) {
        return new Resign(games, clock);
    }

    @Bean
    GetGameUseCase getGame(GameRepository games) {
        return new GetGame(games);
    }

    @Bean
    ListGamesUseCase listGames(GameRepository games) {
        return new ListGames(games);
    }
}
