package com.arquitechthor.kopi.bootstrap;

import com.arquitechthor.kopi.adapter.bots.KopiBots;
import com.arquitechthor.kopi.adapter.openings.LichessOpeningBook;
import com.arquitechthor.kopi.adapter.stockfish.StockfishBots;
import com.arquitechthor.kopi.application.analysis.GetAnalysis;
import com.arquitechthor.kopi.application.analysis.GetAnalysisUseCase;
import com.arquitechthor.kopi.application.analysis.RequestAnalysis;
import com.arquitechthor.kopi.application.analysis.RequestAnalysisUseCase;
import com.arquitechthor.kopi.application.analysis.RunAnalysisJob;
import com.arquitechthor.kopi.application.analysis.RunAnalysisJobUseCase;
import com.arquitechthor.kopi.application.game.CreateGame;
import com.arquitechthor.kopi.application.game.CreateGameUseCase;
import com.arquitechthor.kopi.application.game.ExportPgn;
import com.arquitechthor.kopi.application.game.ExportPgnUseCase;
import com.arquitechthor.kopi.application.game.GetGame;
import com.arquitechthor.kopi.application.game.GetGameUseCase;
import com.arquitechthor.kopi.application.game.ListGames;
import com.arquitechthor.kopi.application.game.ListGamesUseCase;
import com.arquitechthor.kopi.application.game.PlayMove;
import com.arquitechthor.kopi.application.game.PlayMoveUseCase;
import com.arquitechthor.kopi.application.game.Resign;
import com.arquitechthor.kopi.application.game.ResignUseCase;
import com.arquitechthor.kopi.application.port.AnalysisEvents;
import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.application.port.AnalysisRepository;
import com.arquitechthor.kopi.application.port.BotPlayer;
import com.arquitechthor.kopi.application.port.ChessEngine;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.application.port.IdGenerator;
import com.arquitechthor.kopi.application.port.OpeningBook;
import com.arquitechthor.kopi.domain.game.GameId;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/// The use cases and the ports they need, wired by hand (ADR-002): the
/// application ring has no Spring annotations, so this class is where each
/// port meets the adapter that implements it.
///
/// No transaction wraps the use cases (ADR-014): the repository makes each
/// save atomic, and optimistic locking catches anyone who saved in between.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AnalysisProperties.class)
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
    /// `KNIGHT`, Stockfish for the rest. `@Primary` because `StockfishBots`
    /// is a `BotPlayer` bean too; the use cases must get this one.
    @Bean
    @Primary
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
    PlayMoveUseCase playMove(GameRepository games, BotPlayer bot, AnalysisJobQueue analyses, Clock clock) {
        return new PlayMove(games, bot, analyses, clock);
    }

    @Bean
    ResignUseCase resign(GameRepository games, AnalysisJobQueue analyses, Clock clock) {
        return new Resign(games, analyses, clock);
    }

    @Bean
    ExportPgnUseCase exportPgn(GameRepository games) {
        return new ExportPgn(games);
    }

    @Bean
    GetGameUseCase getGame(GameRepository games) {
        return new GetGame(games);
    }

    @Bean
    ListGamesUseCase listGames(GameRepository games) {
        return new ListGames(games);
    }

    /// Loaded on the first lookup, in the analysis worker: the application
    /// does not wait a second for it at startup (ADR-009).
    @Bean
    OpeningBook openingBook() {
        return LichessOpeningBook.loadedOnFirstUse();
    }

    @Bean
    RequestAnalysisUseCase requestAnalysis(GameRepository games, AnalysisJobQueue queue, Clock clock) {
        return new RequestAnalysis(games, queue, clock);
    }

    @Bean
    GetAnalysisUseCase getAnalysis(GameRepository games, AnalysisJobQueue queue, AnalysisRepository analyses) {
        return new GetAnalysis(games, queue, analyses);
    }

    /// `events` is the REST adapter's Server-Sent Events registry.
    @Bean
    RunAnalysisJobUseCase runAnalysisJob(AnalysisJobQueue queue, GameRepository games, ChessEngine engine,
            OpeningBook book, AnalysisRepository analyses, AnalysisEvents events, AnalysisProperties properties,
            Clock clock) {
        return new RunAnalysisJob(queue, games, engine, book, analyses, events, properties.settings(),
                properties.maxAttempts(), clock);
    }

    /// Starts with the application and stops with it (`SmartLifecycle`).
    @Bean
    AnalysisWorker analysisWorker(RunAnalysisJobUseCase runner, AnalysisJobQueue queue,
            AnalysisProperties properties, Clock clock) {
        return new AnalysisWorker(runner, queue, properties, clock);
    }
}
