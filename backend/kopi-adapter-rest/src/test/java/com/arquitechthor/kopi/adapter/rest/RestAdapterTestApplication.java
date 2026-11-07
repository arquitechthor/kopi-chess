package com.arquitechthor.kopi.adapter.rest;

import com.arquitechthor.kopi.application.account.AccountView;
import com.arquitechthor.kopi.application.analysis.GetAnalysis;
import com.arquitechthor.kopi.application.analysis.GetAnalysisUseCase;
import com.arquitechthor.kopi.application.analysis.RequestAnalysis;
import com.arquitechthor.kopi.application.analysis.RequestAnalysisUseCase;
import com.arquitechthor.kopi.application.game.AgreeDraw;
import com.arquitechthor.kopi.application.game.AgreeDrawUseCase;
import com.arquitechthor.kopi.application.game.CreateGame;
import com.arquitechthor.kopi.application.game.CreateGameUseCase;
import com.arquitechthor.kopi.application.game.CreateLocalGame;
import com.arquitechthor.kopi.application.game.CreateLocalGameUseCase;
import com.arquitechthor.kopi.application.game.DeleteGame;
import com.arquitechthor.kopi.application.game.DeleteGameUseCase;
import com.arquitechthor.kopi.application.game.ExportPgn;
import com.arquitechthor.kopi.application.game.ExportPgnUseCase;
import com.arquitechthor.kopi.application.game.GetBotRecords;
import com.arquitechthor.kopi.application.game.GetBotRecordsUseCase;
import com.arquitechthor.kopi.application.game.GetGame;
import com.arquitechthor.kopi.application.game.GetGameUseCase;
import com.arquitechthor.kopi.application.game.ImportPgn;
import com.arquitechthor.kopi.application.game.ImportPgnUseCase;
import com.arquitechthor.kopi.application.game.ListGames;
import com.arquitechthor.kopi.application.game.ListGamesUseCase;
import com.arquitechthor.kopi.application.game.PlayMove;
import com.arquitechthor.kopi.application.game.PlayMoveUseCase;
import com.arquitechthor.kopi.application.game.Resign;
import com.arquitechthor.kopi.application.game.ResignUseCase;
import com.arquitechthor.kopi.application.online.LobbyView;
import com.arquitechthor.kopi.application.online.OnlineUseCase;
import com.arquitechthor.kopi.application.port.AnalysisJob;
import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.application.port.AnalysisRepository;
import com.arquitechthor.kopi.application.port.BotPlayer;
import com.arquitechthor.kopi.application.port.BotRecord;
import com.arquitechthor.kopi.application.port.BotUnavailableException;
import com.arquitechthor.kopi.application.port.ConcurrentGameUpdateException;
import com.arquitechthor.kopi.application.port.GameFilter;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.domain.analysis.GameAnalysis;
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
import java.util.concurrent.ConcurrentHashMap;
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

    /// A small limit, so the test of the limit does not need 100 KB.
    static final int PGN_LIMIT_BYTES = 4 * 1024;

    @Bean
    ImportPgnUseCase importPgn(InMemoryGames games, QueuedAnalyses analyses, Clock clock) {
        var ids = new AtomicLong();
        return new ImportPgn(games, () -> new GameId(new UUID(1, ids.incrementAndGet())), analyses, clock,
                PGN_LIMIT_BYTES);
    }

    @Bean
    GetBotRecordsUseCase botRecords(InMemoryGames games) {
        return new GetBotRecords(games);
    }

    @Bean
    ExportPgnUseCase exportPgn(InMemoryGames games) {
        return new ExportPgn(games);
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
    SavedAnalyses savedAnalyses() {
        return new SavedAnalyses();
    }

    @Bean
    RequestAnalysisUseCase requestAnalysis(InMemoryGames games, QueuedAnalyses analyses, Clock clock) {
        return new RequestAnalysis(games, analyses, clock);
    }

    @Bean
    GetAnalysisUseCase getAnalysis(InMemoryGames games, QueuedAnalyses analyses, SavedAnalyses saved) {
        return new GetAnalysis(games, analyses, saved);
    }

    @Bean
    PlayMoveUseCase playMove(InMemoryGames games, QueuedAnalyses analyses, Clock clock) {
        return new PlayMove(games, BOT, analyses, clock);
    }

    /// Online play, faked (phase D): an empty lobby, and a refusal when
    /// `failure` is set.
    @Bean
    FakeOnline fakeOnline() {
        return new FakeOnline();
    }

    static final class FakeOnline implements OnlineUseCase {
        KopiOnlineException.Problem failure;
        final java.util.List<String> calls = new java.util.ArrayList<>();

        private void record(String call) {
            calls.add(call);
            if (failure != null) {
                throw new KopiOnlineException(failure, "fake");
            }
        }

        @Override
        public LobbyView lobby() {
            return new LobbyView(new AccountView(true, java.util.Optional.empty(), java.util.Optional.of("Ana_92"),
                    false, false), java.util.Optional.empty(), java.util.List.of(), java.util.List.of(),
                    java.util.List.of(), java.util.List.of());
        }

        @Override
        public void invite(String kopiId, java.util.Optional<com.arquitechthor.kopi.domain.chess.Color> color) {
            record("invite " + kopiId);
        }

        @Override
        public GameId accept(String relayGameId) {
            record("accept " + relayGameId);
            return new GameId(new UUID(9, 9));
        }

        @Override
        public void decline(String relayGameId) {
            record("decline " + relayGameId);
        }

        @Override
        public void cancel(String relayGameId) {
            record("cancel " + relayGameId);
        }

        @Override
        public void dismiss(String relayGameId) {
            record("dismiss " + relayGameId);
        }

        @Override
        public void sync() {}

        @Override
        public java.util.Optional<OnlineStatus> status(GameId gameId) {
            return java.util.Optional.empty();
        }

        @Override
        public com.arquitechthor.kopi.application.game.GameView offerDraw(GameId gameId) {
            record("offerDraw");
            return null;
        }

        @Override
        public com.arquitechthor.kopi.application.game.GameView acceptDraw(GameId gameId) {
            record("acceptDraw");
            return null;
        }

        @Override
        public com.arquitechthor.kopi.application.game.GameView declineDraw(GameId gameId) {
            record("declineDraw");
            return null;
        }
    }

    @Bean
    CreateLocalGameUseCase createLocalGame(InMemoryGames games, Clock clock) {
        var ids = new AtomicLong();
        return new CreateLocalGame(games, () -> new GameId(new UUID(1, ids.incrementAndGet())), clock);
    }

    @Bean
    AgreeDrawUseCase agreeDraw(InMemoryGames games, QueuedAnalyses analyses, Clock clock) {
        return new AgreeDraw(games, analyses, clock);
    }

    @Bean
    ResignUseCase resign(InMemoryGames games, QueuedAnalyses analyses, Clock clock) {
        return new Resign(games, analyses, clock);
    }

    @Bean
    DeleteGameUseCase deleteGame(InMemoryGames games) {
        return new DeleteGame(games);
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
            var stored = games.get(game.id());
            if (stored == null || stored.version() != loadedVersion) {
                throw new ConcurrentGameUpdateException(game.id(), loadedVersion);
            }
            games.put(game.id(), game);
        }

        @Override
        public synchronized boolean delete(GameId id) {
            return games.remove(id) != null;
        }

        @Override
        public synchronized List<Game> findRecent(GameFilter filter, int offset, int limit) {
            return games.values().stream().filter(filter::matches).toList().reversed().stream()
                    .skip(offset).limit(limit).toList();
        }

        @Override
        public synchronized long count(GameFilter filter) {
            return games.values().stream().filter(filter::matches).count();
        }

        @Override
        public synchronized Map<BotProfile, BotRecord> recordsAgainstBots() {
            return BotRecord.tally(games.values().stream());
        }

        synchronized void clear() {
            games.clear();
        }
    }

    /// The analysis queue, in a map: tests put jobs in any state with
    /// [#put] (analysing is not this adapter's business).
    static final class QueuedAnalyses implements AnalysisJobQueue {

        private final Map<GameId, AnalysisJob> jobs = new ConcurrentHashMap<>();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public AnalysisJob enqueue(GameId gameId, Instant now) {
            return jobs.compute(gameId, (_, job) -> job == null || job.state() == AnalysisJob.State.FAILED
                    ? new AnalysisJob(ids.incrementAndGet(), gameId, AnalysisJob.State.QUEUED, 0, Optional.empty(), now)
                    : job);
        }

        void put(AnalysisJob job) {
            jobs.put(job.gameId(), job);
        }

        void clear() {
            jobs.clear();
        }

        @Override
        public Optional<AnalysisJob> claimNext(Instant now) {
            return Optional.empty();
        }

        @Override
        public Optional<AnalysisJob> findByGameId(GameId gameId) {
            return Optional.ofNullable(jobs.get(gameId));
        }

        @Override
        public int countQueued() {
            throw new UnsupportedOperationException();
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

    /// Finished analyses, in a map.
    static final class SavedAnalyses implements AnalysisRepository {

        private final Map<GameId, GameAnalysis> analyses = new ConcurrentHashMap<>();

        @Override
        public Optional<GameAnalysis> findByGameId(GameId id) {
            return Optional.ofNullable(analyses.get(id));
        }

        @Override
        public void save(GameAnalysis analysis) {
            analyses.put(analysis.gameId(), analysis);
        }

        void clear() {
            analyses.clear();
        }
    }
}
