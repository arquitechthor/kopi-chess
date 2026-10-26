package com.arquitechthor.kopi.adapter.rest;

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
import com.arquitechthor.kopi.application.port.AnalysisJob;
import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.application.port.BotPlayer;
import com.arquitechthor.kopi.application.port.BotUnavailableException;
import com.arquitechthor.kopi.application.port.ConcurrentGameUpdateException;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.domain.chess.Move;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/// The REST adapter on its own (`@WebMvcTest`), in front of the **real** use
/// cases, with an in-memory repository, a predictable bot and a fixed clock.
/// Errors then come from where they really come from, not from mocks.
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(RestAdapterConfiguration.class)
class RestAdapterTestApplication {

    static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");

    /// The bot: the first legal move in UCI order. `QUEEN` is "down", to
    /// test the `503`.
    static final BotPlayer BOT = (position, profile) -> {
        if (profile == BotProfile.QUEEN) {
            throw new BotUnavailableException(profile, new IllegalStateException("engine down"));
        }
        return position.legalMoves().stream().min(Comparator.comparing(Move::toUci)).orElseThrow();
    };

    @Bean
    InMemoryGames games() {
        return new InMemoryGames();
    }

    @Bean
    Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Bean
    CreateGameUseCase createGame(InMemoryGames games, Clock clock) {
        var ids = new AtomicLong();
        return new CreateGame(games, BOT, () -> new GameId(new UUID(0, ids.incrementAndGet())), clock);
    }

    @Bean
    GetGameUseCase getGame(InMemoryGames games) {
        return new GetGame(games);
    }

    @Bean
    ListGamesUseCase listGames(InMemoryGames games) {
        return new ListGames(games);
    }

    @Bean
    QueuedAnalyses analyses() {
        return new QueuedAnalyses();
    }

    @Bean
    PlayMoveUseCase playMove(InMemoryGames games, QueuedAnalyses analyses, Clock clock) {
        return new PlayMove(games, BOT, analyses, clock);
    }

    @Bean
    ResignUseCase resign(InMemoryGames games, QueuedAnalyses analyses, Clock clock) {
        return new Resign(games, analyses, clock);
    }

    /// A [GameRepository] in a map, honouring the optimistic locking contract.
    static final class InMemoryGames implements GameRepository {

        private final Map<GameId, Game> games = new LinkedHashMap<>();

        @Override
        public synchronized Optional<Game> findById(GameId id) {
            return Optional.ofNullable(games.get(id));
        }

        @Override
        public synchronized void insert(Game game) {
            games.put(game.id(), game);
        }

        @Override
        public synchronized void update(Game game, long loadedVersion) {
            if (games.get(game.id()).version() != loadedVersion) {
                throw new ConcurrentGameUpdateException(game.id(), loadedVersion);
            }
            games.put(game.id(), game);
        }

        @Override
        public synchronized List<Game> findRecent(int offset, int limit) {
            return games.values().stream().toList().reversed().stream().skip(offset).limit(limit).toList();
        }

        @Override
        public synchronized long count() {
            return games.size();
        }

        synchronized void clear() {
            games.clear();
        }
    }

    /// The analysis queue: the games whose analysis was queued, nothing
    /// more (analysing is not this adapter's business).
    static final class QueuedAnalyses implements AnalysisJobQueue {

        final List<GameId> queued = new CopyOnWriteArrayList<>();

        @Override
        public AnalysisJob enqueue(GameId gameId, Instant now) {
            queued.add(gameId);
            return new AnalysisJob(queued.size(), gameId, AnalysisJob.State.QUEUED, 0, Optional.empty(), now);
        }

        @Override
        public Optional<AnalysisJob> claimNext(Instant now) {
            return Optional.empty();
        }

        @Override
        public Optional<AnalysisJob> findByGameId(GameId gameId) {
            return Optional.empty();
        }

        @Override
        public void complete(long jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void retry(long jobId, String error) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void fail(long jobId, String error) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int recoverStale(Instant lockedBefore, int maxAttempts) {
            return 0;
        }
    }
}
