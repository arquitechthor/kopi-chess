package com.arquitechthor.kopi.application.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.application.analysis.InMemoryAnalysisJobQueue;
import com.arquitechthor.kopi.application.port.AnalysisJob;
import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.application.port.BotUnavailableException;
import com.arquitechthor.kopi.application.port.ConcurrentGameUpdateException;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Fen;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameNotInProgressException;
import com.arquitechthor.kopi.domain.game.GameOutcome;
import com.arquitechthor.kopi.domain.game.GameResult;
import com.arquitechthor.kopi.domain.game.GameStatus;
import com.arquitechthor.kopi.domain.game.IllegalMoveException;
import com.arquitechthor.kopi.domain.game.NotYourTurnException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The use cases with test doubles for their ports: no Spring, no database
/// (ADR-002).
class GameUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");

    private final InMemoryGameRepository repository = new InMemoryGameRepository();
    private final ScriptedBot bot = new ScriptedBot();
    private final InMemoryAnalysisJobQueue analyses = new InMemoryAnalysisJobQueue();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private int nextId = 1;

    private GameId newId() {
        return new GameId(new UUID(0, nextId++));
    }

    private GameView create(Color humanColor) {
        return new CreateGame(repository, bot, this::newId, clock)
                .create(new CreateGameCommand(humanColor, BotProfile.PAWN));
    }

    private GameView play(GameView game, String uci) {
        return new PlayMove(repository, bot, analyses, clock).play(new PlayMoveCommand(game.id(), uci, game.version()));
    }

    @Nested
    class Create {

        @Test
        void storesANewGameWaitingForWhite() {
            var game = create(Color.WHITE);

            assertThat(game.id()).isEqualTo(new GameId(new UUID(0, 1)));
            assertThat(game.status()).isEqualTo(GameStatus.IN_PROGRESS);
            assertThat(game.fen()).isEqualTo(Fen.INITIAL);
            assertThat(game.moves()).isEmpty();
            assertThat(game.legalMoves()).hasSize(20);
            assertThat(game.isHumanToMove()).isTrue();
            assertThat(game.version()).isZero();
            assertThat(game.createdAt()).isEqualTo(NOW);
            assertThat(repository.findById(game.id())).isPresent();
            assertThat(bot.calls).isZero();
        }

        @Test
        void theBotOpensWhenItHasWhite() {
            bot.playing("e2e4");

            var game = create(Color.BLACK);

            assertThat(game.moves()).extracting(GameView.PlayedMoveView::san).containsExactly("e4");
            assertThat(game.sideToMove()).isEqualTo(Color.BLACK);
            assertThat(game.isHumanToMove()).isTrue();
            assertThat(game.version()).isEqualTo(1);
            assertThat(repository.findById(game.id())).hasValueSatisfying(stored -> assertThat(stored.version()).isEqualTo(1));
        }
    }

    @Nested
    class Play {

        @Test
        void playsTheMoveAndTheBotsReplyInOneRequest() {
            var game = create(Color.WHITE);
            bot.playing("e7e5");

            var after = play(game, "e2e4");

            assertThat(after.moves()).extracting(GameView.PlayedMoveView::uci).containsExactly("e2e4", "e7e5");
            assertThat(after.lastMove()).hasValueSatisfying(last -> assertThat(last.san()).isEqualTo("e5"));
            assertThat(after.isHumanToMove()).isTrue();
            assertThat(after.version()).isEqualTo(2);
            assertThat(repository.findById(game.id()).orElseThrow().version()).isEqualTo(2);
        }

        @Test
        void listsTheLegalMovesInUciAndSan() {
            var game = create(Color.WHITE);

            assertThat(game.legalMoves()).contains(
                    new GameView.LegalMoveView("g1f3", "Nf3"), new GameView.LegalMoveView("e2e4", "e4"));
        }

        @Test
        void acceptsUpperCaseAndSpaces() {
            var game = create(Color.WHITE);

            var after = play(game, " E2E4 ");

            assertThat(after.moves().getFirst().uci()).isEqualTo("e2e4");
        }

        @Test
        void aStaleVersionChangesNothing() {
            var game = create(Color.WHITE);
            play(game, "e2e4");
            var writes = repository.writes;

            // The same request again, still at version 0 (a double click).
            assertThatThrownBy(() -> play(game, "e2e4"))
                    .isInstanceOfSatisfying(VersionMismatchException.class, e -> {
                        assertThat(e.expected()).isZero();
                        assertThat(e.actual()).isEqualTo(2);
                    });
            assertThat(repository.writes).isEqualTo(writes);
        }

        @Test
        void anIllegalMoveChangesNothing() {
            var game = create(Color.WHITE);

            assertThatThrownBy(() -> play(game, "e2e5")).isInstanceOf(IllegalMoveException.class);
            assertThat(repository.findById(game.id()).orElseThrow().version()).isZero();
            assertThat(bot.calls).isZero();
        }

        @Test
        void textThatIsNotUciIsRefusedBeforeLoadingAnything() {
            assertThatThrownBy(() -> new PlayMoveCommand(newId(), "Nf3", 0))
                    .isInstanceOfSatisfying(InvalidUciException.class, e -> assertThat(e.text()).isEqualTo("Nf3"));
            assertThatThrownBy(() -> new PlayMoveCommand(newId(), null, 0)).isInstanceOf(InvalidUciException.class);
        }

        @Test
        void anUnknownGameIsReported() {
            var id = newId();

            assertThatThrownBy(() -> new PlayMove(repository, bot, analyses, clock).play(new PlayMoveCommand(id, "e2e4", 0)))
                    .isInstanceOfSatisfying(GameNotFoundException.class, e -> assertThat(e.gameId()).isEqualTo(id));
        }

        @Test
        void theBotIsNotAskedWhenThePersonsMoveEndsTheGame() {
            // Fool's mate the other way round: the person has Black.
            bot.playing("f2f3", "g2g4");
            var game = create(Color.BLACK);
            game = play(game, "e7e5");

            var after = play(game, "d8h4");

            assertThat(after.outcome()).contains(new GameOutcome.Checkmate(Color.BLACK));
            assertThat(after.result()).contains(GameResult.BLACK_WIN);
            assertThat(after.legalMoves()).isEmpty();
            assertThat(after.finishedAt()).contains(NOW);
            assertThat(bot.calls).isEqualTo(2);
        }

        @Test
        void theBotsReplyMayEndTheGame() {
            bot.playing("e7e5", "d8h4");
            var game = create(Color.WHITE);
            game = play(game, "f2f3");

            var after = play(game, "g2g4");

            assertThat(after.status()).isEqualTo(GameStatus.FINISHED);
            assertThat(after.outcome()).contains(new GameOutcome.Checkmate(Color.BLACK));
            assertThat(after.isHumanToMove()).isFalse();
        }

        @Test
        void aFinishedGameTakesNoMoreMoves() {
            var game = create(Color.WHITE);
            var resigned = new Resign(repository, analyses, clock).resign(new ResignCommand(game.id(), 0));

            assertThatThrownBy(() -> play(resigned, "e2e4")).isInstanceOf(GameNotInProgressException.class);
        }

        @Test
        void itMustBeThePersonsTurn() {
            // A stored game left with the bot to move (it could not reply).
            var stuck = Game.start(newId(), Color.BLACK, BotProfile.PAWN, Fen.parse(Fen.INITIAL), NOW);
            repository.insert(stuck);

            assertThatThrownBy(() -> new PlayMove(repository, bot, analyses, clock)
                            .play(new PlayMoveCommand(stuck.id(), "e7e5", 0)))
                    .isInstanceOfSatisfying(NotYourTurnException.class,
                            e -> assertThat(e.color()).isEqualTo(Color.BLACK));
        }

        @Test
        void ifTheBotCannotReplyNothingIsSaved() {
            var game = create(Color.WHITE);
            bot.unavailable();

            assertThatThrownBy(() -> play(game, "e2e4")).isInstanceOf(BotUnavailableException.class);
            assertThat(repository.findById(game.id()).orElseThrow().moves()).isEmpty();
        }

        @Test
        void ifSomeoneElseSavesFirstNothingOfOursIsSaved() {
            var game = create(Color.WHITE);
            var stored = repository.findById(game.id()).orElseThrow();
            // While the bot thinks, another request resigns the game.
            bot.whileThinking(() -> repository.storeDirectly(stored.resign(Color.WHITE, NOW)));

            assertThatThrownBy(() -> play(game, "e2e4")).isInstanceOf(ConcurrentGameUpdateException.class);
            assertThat(repository.findById(game.id()).orElseThrow().outcome())
                    .contains(new GameOutcome.Resignation(Color.WHITE));
        }

        @Test
        void movesAreTimedByTheClock() {
            var game = create(Color.WHITE);
            var later = Clock.offset(clock, Duration.ofMinutes(3));

            var after = new PlayMove(repository, bot, analyses, later).play(new PlayMoveCommand(game.id(), "e2e4", 0));

            assertThat(repository.findById(after.id()).orElseThrow().moves())
                    .allSatisfy(played -> assertThat(played.playedAt()).isEqualTo(NOW.plusSeconds(180)));
        }
    }

    @Nested
    class ResignGame {

        @Test
        void thePersonResignsAndTheBotWins() {
            var game = create(Color.WHITE);

            var after = new Resign(repository, analyses, clock).resign(new ResignCommand(game.id(), 0));

            assertThat(after.outcome()).contains(new GameOutcome.Resignation(Color.WHITE));
            assertThat(after.result()).contains(GameResult.BLACK_WIN);
            assertThat(after.version()).isEqualTo(1);
            assertThat(repository.findById(game.id()).orElseThrow().status()).isEqualTo(GameStatus.FINISHED);
        }

        @Test
        void needsTheCurrentVersion() {
            var game = play(create(Color.WHITE), "e2e4");

            assertThatThrownBy(() -> new Resign(repository, analyses, clock).resign(new ResignCommand(game.id(), 0)))
                    .isInstanceOf(VersionMismatchException.class);
        }
    }

    /// Finishing a game queues its analysis (plan §8.3).
    @Nested
    class QueueTheAnalysis {

        @Test
        void aMoveThatEndsTheGameQueuesIt() {
            bot.playing("e7e5", "d8h4");
            var game = play(create(Color.WHITE), "f2f3");

            var after = play(game, "g2g4");

            assertThat(after.status()).isEqualTo(GameStatus.FINISHED);
            assertThat(analyses.findByGameId(after.id())).map(AnalysisJob::state).hasValue(AnalysisJob.State.QUEUED);
        }

        @Test
        void aGameThatGoesOnQueuesNothing() {
            var after = play(create(Color.WHITE), "e2e4");

            assertThat(analyses.findByGameId(after.id())).isEmpty();
        }

        @Test
        void aResignationQueuesIt() {
            var game = play(create(Color.WHITE), "e2e4");

            var after = new Resign(repository, analyses, clock).resign(new ResignCommand(game.id(), game.version()));

            assertThat(analyses.findByGameId(after.id())).isPresent();
        }

        @Test
        void aResignationBeforeAnyMoveHasNothingToAnalyse() {
            var game = create(Color.WHITE);

            new Resign(repository, analyses, clock).resign(new ResignCommand(game.id(), 0));

            assertThat(analyses.findByGameId(game.id())).isEmpty();
        }

        @Test
        void ifQueuingFailsTheGameIsStillSaved() {
            var game = play(create(Color.WHITE), "e2e4");
            AnalysisJobQueue broken = new BrokenQueue();

            var after = new Resign(repository, broken, clock).resign(new ResignCommand(game.id(), game.version()));

            assertThat(after.status()).isEqualTo(GameStatus.FINISHED);
            assertThat(repository.findById(game.id()).orElseThrow().status()).isEqualTo(GameStatus.FINISHED);
        }
    }

    /// A queue whose database is down.
    private static final class BrokenQueue implements AnalysisJobQueue {

        @Override
        public AnalysisJob enqueue(GameId gameId, Instant now) {
            throw new IllegalStateException("database down");
        }

        @Override
        public Optional<AnalysisJob> claimNext(Instant now) {
            throw new IllegalStateException("database down");
        }

        @Override
        public Optional<AnalysisJob> findByGameId(GameId gameId) {
            throw new IllegalStateException("database down");
        }

        @Override
        public void complete(long jobId) {
            throw new IllegalStateException("database down");
        }

        @Override
        public void retry(long jobId, String error) {
            throw new IllegalStateException("database down");
        }

        @Override
        public void fail(long jobId, String error) {
            throw new IllegalStateException("database down");
        }

        @Override
        public int recoverStale(Instant lockedBefore, int maxAttempts) {
            throw new IllegalStateException("database down");
        }
    }

    @Nested
    class Read {

        @Test
        void getsAGameById() {
            var game = play(create(Color.WHITE), "e2e4");

            var read = new GetGame(repository).get(game.id());

            assertThat(read).isEqualTo(game);
        }

        @Test
        void listsTheNewestGamesFirstOnePageAtATime() {
            for (int i = 0; i < 5; i++) {
                var later = Clock.offset(clock, Duration.ofMinutes(i));
                new CreateGame(repository, bot, GameUseCasesTest.this::newId, later)
                        .create(new CreateGameCommand(Color.WHITE, BotProfile.values()[i]));
            }
            var list = new ListGames(repository);

            var first = list.list(new ListGamesQuery(0, 2));
            var last = list.list(new ListGamesQuery(2, 2));

            assertThat(first.games()).extracting(GameSummary::botProfile)
                    .containsExactly(BotProfile.QUEEN, BotProfile.ROOK);
            assertThat(first.total()).isEqualTo(5);
            assertThat(first.totalPages()).isEqualTo(3);
            assertThat(last.games()).extracting(GameSummary::botProfile).containsExactly(BotProfile.PAWN);
        }

        @Test
        void pagesAreValidated() {
            assertThatIllegalArgumentException().isThrownBy(() -> new ListGamesQuery(-1, 10));
            assertThatIllegalArgumentException().isThrownBy(() -> new ListGamesQuery(0, 0));
            assertThatIllegalArgumentException().isThrownBy(() -> new ListGamesQuery(0, ListGamesQuery.MAX_SIZE + 1));
        }
    }
}
