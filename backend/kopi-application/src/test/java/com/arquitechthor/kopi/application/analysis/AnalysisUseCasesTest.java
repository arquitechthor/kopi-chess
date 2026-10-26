package com.arquitechthor.kopi.application.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.application.game.GameNotFoundException;
import com.arquitechthor.kopi.application.game.InMemoryGameRepository;
import com.arquitechthor.kopi.application.port.AnalysisEvents;
import com.arquitechthor.kopi.application.port.AnalysisJob;
import com.arquitechthor.kopi.application.port.AnalysisJob.State;
import com.arquitechthor.kopi.application.port.AnalysisRepository;
import com.arquitechthor.kopi.application.port.ChessEngine;
import com.arquitechthor.kopi.application.port.EngineAnalysis;
import com.arquitechthor.kopi.application.port.EngineUnavailableException;
import com.arquitechthor.kopi.application.port.OpeningBook;
import com.arquitechthor.kopi.domain.analysis.AnalysisSettings;
import com.arquitechthor.kopi.domain.analysis.Evaluation;
import com.arquitechthor.kopi.domain.analysis.GameAnalysis;
import com.arquitechthor.kopi.domain.analysis.MoveAnalysis;
import com.arquitechthor.kopi.domain.analysis.MoveCategory;
import com.arquitechthor.kopi.domain.analysis.SearchLimits;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Move;
import com.arquitechthor.kopi.domain.chess.Opening;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The analysis use cases with test doubles: a scripted engine, a small
/// opening book, the game and job stores in memory (ADR-002).
class AnalysisUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    private static final AnalysisSettings SETTINGS = new AnalysisSettings(new SearchLimits.Nodes(1_000_000), 2, 5);
    /// 1.e4 e5 2.Qh5 Nc6 3.Bc4 Nf6?? 4.Qxf7#: the Scholar's mate.
    private static final String SCHOLARS_MATE = "e2e4 e7e5 d1h5 b8c6 f1c4 g8f6 h5f7";

    private final InMemoryGameRepository games = new InMemoryGameRepository();
    private final InMemoryAnalysisJobQueue queue = new InMemoryAnalysisJobQueue();
    private final Map<GameId, GameAnalysis> saved = new HashMap<>();
    private final AnalysisRepository analyses = new AnalysisRepository() {
        @Override
        public Optional<GameAnalysis> findByGameId(GameId id) {
            return Optional.ofNullable(saved.get(id));
        }

        @Override
        public void save(GameAnalysis analysis) {
            saved.put(analysis.gameId(), analysis);
        }
    };
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ScriptedEngine engine = new ScriptedEngine();
    private int nextId = 1;

    /// A stored game against `PAWN` where these UCI moves were played.
    private Game storedGame(String moves) {
        var game = Game.start(new GameId(new UUID(0, nextId++)), Color.WHITE, BotProfile.PAWN, Position.initial(), NOW);
        if (!moves.isBlank()) {
            for (var uci : moves.split(" ")) {
                game = game.play(uci, game.currentPosition().sideToMove(), NOW);
            }
        }
        games.insert(game);
        return game;
    }

    private Game resigned(String moves) {
        var game = storedGame(moves);
        var over = game.resign(game.currentPosition().sideToMove(), NOW);
        games.update(over, game.version());
        return over;
    }

    private final RecordedEvents events = new RecordedEvents();

    private RunAnalysisJob runner(OpeningBook book, int maxAttempts) {
        return new RunAnalysisJob(queue, games, engine, book, analyses, events, SETTINGS, maxAttempts, clock);
    }

    /// The events published, as text: `progress 3/8`, `completed`, `failed …`.
    private static final class RecordedEvents implements AnalysisEvents {

        final List<String> published = new ArrayList<>();
        boolean broken;

        @Override
        public void progress(GameId gameId, int analysedPositions, int totalPositions) {
            record("progress " + analysedPositions + "/" + totalPositions);
        }

        @Override
        public void completed(GameId gameId) {
            record("completed");
        }

        @Override
        public void failed(GameId gameId, String reason) {
            record("failed " + reason);
        }

        private void record(String event) {
            published.add(event);
            if (broken) {
                throw new IllegalStateException("listener gone");
            }
        }
    }

    /// An engine that answers from a script: for the n-th position asked,
    /// the n-th evaluation and line. Positions past the script are a mistake
    /// of the test.
    private static final class ScriptedEngine implements ChessEngine {

        private final List<Evaluation> evaluations = new ArrayList<>();
        private final List<List<String>> lines = new ArrayList<>();
        private boolean down;
        int calls;

        /// The next position: its evaluation and the engine's line in UCI
        /// (none for a finished position).
        ScriptedEngine then(Evaluation evaluation, String... line) {
            evaluations.add(evaluation);
            lines.add(Arrays.asList(line));
            return this;
        }

        @Override
        public EngineAnalysis analyse(Position position, SearchLimits limits, int multiPv) {
            if (down) {
                throw new EngineUnavailableException("Stockfish is not installed", null);
            }
            var line = new ArrayList<Move>();
            var current = position;
            for (var uci : lines.get(calls)) {
                var move = current.legalMoves().stream().filter(m -> m.toUci().equals(uci)).findFirst().orElseThrow();
                line.add(move);
                current = current.apply(move);
            }
            var evaluation = evaluations.get(calls++);
            return new EngineAnalysis("Stockfish 19", evaluation,
                    line.isEmpty() ? List.of() : List.of(new EngineAnalysis.Line(evaluation, line)));
        }
    }

    /// The engine's verdicts on the Scholar's mate, position by position.
    private void scriptScholarsMate() {
        engine.then(new Evaluation.Centipawns(30), "e2e4")
                .then(new Evaluation.Centipawns(30), "e7e5")
                .then(new Evaluation.Centipawns(30), "g1f3")
                .then(new Evaluation.Centipawns(0), "b8c6")
                .then(new Evaluation.Centipawns(20), "f1c4")
                .then(new Evaluation.Centipawns(20), "g7g6", "h5f3")
                .then(new Evaluation.MateIn(1), "h5f7")
                .then(new Evaluation.Checkmate(Color.WHITE));
    }

    @Nested
    class Run {

        /// The book knows the first three positions, not the fourth, and the
        /// fifth again (a later name does not bring the book back).
        private final OpeningBook book = position -> switch (position.toFen().split(" ")[0]) {
            case "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR" -> Optional.of(new Opening("B00", "King's Pawn Game"));
            case "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR" -> Optional.of(new Opening("C20", "King's Pawn Game"));
            case "rnbqkbnr/pppp1ppp/8/4p2Q/4P3/8/PPPP1PPP/RNB1KBNR" ->
                    Optional.of(new Opening("C20", "King's Pawn Game: Wayward Queen Attack"));
            case "r1bqkbnr/pppp1ppp/2n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR" ->
                    Optional.of(new Opening("C20", "Wayward Queen Attack, with Bc4"));
            default -> Optional.empty();
        };

        @Test
        void judgesAFinishedGameAndSavesIt() {
            var game = storedGame(SCHOLARS_MATE);
            queue.enqueue(game.id(), NOW);
            scriptScholarsMate();

            assertThat(runner(book, 3).runNext()).isTrue();

            var analysis = saved.get(game.id());
            assertThat(analysis.engine()).isEqualTo("Stockfish 19");
            assertThat(analysis.settings()).isEqualTo(SETTINGS);
            assertThat(analysis.firstMover()).isEqualTo(Color.WHITE);
            assertThat(analysis.moves()).extracting(move -> move.classification().category()).containsExactly(
                    MoveCategory.BOOK, MoveCategory.BOOK, MoveCategory.BOOK,
                    // 3...Nc6 has no name: the book ends here, for good.
                    MoveCategory.BEST, MoveCategory.BEST,
                    // 3...Nf6?? allows mate in one.
                    MoveCategory.BLUNDER,
                    MoveCategory.BEST);
            assertThat(queue.findByGameId(game.id())).map(AnalysisJob::state).hasValue(State.DONE);
        }

        @Test
        void searchesEachPositionOnce() {
            var game = storedGame(SCHOLARS_MATE);
            queue.enqueue(game.id(), NOW);
            scriptScholarsMate();

            runner(book, 3).runNext();

            // Seven moves, eight positions: what follows a move is what
            // precedes the next.
            assertThat(engine.calls).isEqualTo(8);
            var moves = saved.get(game.id()).moves();
            assertThat(moves.get(6).before()).isEqualTo(new Evaluation.MateIn(1));
            assertThat(moves.get(6).after()).isEqualTo(new Evaluation.Checkmate(Color.WHITE));
        }

        @Test
        void keepsTheLastNamedOpeningAndTheEnginesLineInSan() {
            var game = storedGame(SCHOLARS_MATE);
            queue.enqueue(game.id(), NOW);
            scriptScholarsMate();

            runner(book, 3).runNext();

            var analysis = saved.get(game.id());
            assertThat(analysis.opening()).hasValue(new Opening("C20", "Wayward Queen Attack, with Bc4"));
            MoveAnalysis blunder = analysis.moves().get(5);
            assertThat(blunder.bestMove()).isEqualTo("g7g6");
            assertThat(blunder.bestLine()).containsExactly("g6", "Qf3");
        }

        @Test
        void aGameWithoutANamedPositionHasNoOpening() {
            var game = storedGame(SCHOLARS_MATE);
            queue.enqueue(game.id(), NOW);
            scriptScholarsMate();

            runner(_ -> Optional.empty(), 3).runNext();

            assertThat(saved.get(game.id()).opening()).isEmpty();
            assertThat(saved.get(game.id()).moves().getFirst().classification().category())
                    .isNotEqualTo(MoveCategory.BOOK);
        }

        @Test
        void tellsHowFarItGotAndHowItEnded() {
            var game = storedGame(SCHOLARS_MATE);
            queue.enqueue(game.id(), NOW);
            scriptScholarsMate();

            runner(book, 3).runNext();

            assertThat(events.published).containsExactly("progress 1/8", "progress 2/8", "progress 3/8",
                    "progress 4/8", "progress 5/8", "progress 6/8", "progress 7/8", "progress 8/8", "completed");
        }

        @Test
        void aBrokenListenerDoesNotBreakTheAnalysis() {
            var game = storedGame(SCHOLARS_MATE);
            queue.enqueue(game.id(), NOW);
            scriptScholarsMate();
            events.broken = true;

            runner(book, 3).runNext();

            assertThat(saved).containsKey(game.id());
            assertThat(queue.findByGameId(game.id())).map(AnalysisJob::state).hasValue(State.DONE);
        }

        @Test
        void nothingQueuedNothingDone() {
            assertThat(runner(book, 3).runNext()).isFalse();
            assertThat(engine.calls).isZero();
        }

        @Test
        void anEngineFailureIsRetriedThenGivenUp() {
            var game = resigned("e2e4");
            queue.enqueue(game.id(), NOW);
            engine.down = true;
            var runner = runner(book, 2);

            runner.runNext();
            assertThat(queue.findByGameId(game.id())).hasValueSatisfying(job -> {
                assertThat(job.state()).isEqualTo(State.QUEUED);
                assertThat(job.lastError()).hasValueSatisfying(error -> assertThat(error).contains("not installed"));
            });

            runner.runNext();
            assertThat(queue.findByGameId(game.id())).map(AnalysisJob::state).hasValue(State.FAILED);
            assertThat(saved).isEmpty();
            // Only the final failure is news: a retry is still on its way.
            assertThat(events.published).singleElement()
                    .satisfies(event -> assertThat(event).startsWith("failed").contains("not installed"));
        }

        @Test
        void aGameThatIsGoneFailsAtOnce() {
            queue.enqueue(new GameId(new UUID(9, 9)), NOW);

            runner(book, 3).runNext();

            assertThat(queue.findByGameId(new GameId(new UUID(9, 9)))).hasValueSatisfying(job -> {
                assertThat(job.state()).isEqualTo(State.FAILED);
                assertThat(job.lastError()).hasValueSatisfying(error -> assertThat(error).contains("no longer exists"));
            });
        }
    }

    @Nested
    class Get {

        private GetAnalysis reader() {
            return new GetAnalysis(games, queue, analyses);
        }

        @Test
        void aFinishedAnalysisWithItsJobAndTheMovesPlayed() {
            var game = storedGame(SCHOLARS_MATE);
            queue.enqueue(game.id(), NOW);
            scriptScholarsMate();
            runner(_ -> Optional.empty(), 3).runNext();

            var view = reader().get(game.id());

            assertThat(view.isDone()).isTrue();
            assertThat(view.job()).map(AnalysisJob::state).hasValue(State.DONE);
            assertThat(view.analysis()).hasValue(saved.get(game.id()));
            assertThat(view.moves()).extracting(AnalysisView.PlayedMove::san)
                    .containsExactly("e4", "e5", "Qh5", "Nc6", "Bc4", "Nf6", "Qxf7#");
            assertThat(view.initialFen()).isEqualTo(Position.initial().toFen());
            assertThat(view.moves().getFirst().uci()).isEqualTo("e2e4");
            assertThat(view.moves().getFirst().fenAfter())
                    .isEqualTo("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1");
        }

        @Test
        void aQueuedAnalysisHasAJobButNoResultYet() {
            var game = resigned("e2e4");
            queue.enqueue(game.id(), NOW);

            var view = reader().get(game.id());

            assertThat(view.isDone()).isFalse();
            assertThat(view.job()).map(AnalysisJob::state).hasValue(State.QUEUED);
        }

        @Test
        void aGameNeverAnalysedHasNeither() {
            var game = storedGame("e2e4");

            var view = reader().get(game.id());

            assertThat(view.job()).isEmpty();
            assertThat(view.analysis()).isEmpty();
        }

        @Test
        void anUnknownGameIsNotFound() {
            assertThatThrownBy(() -> reader().get(new GameId(new UUID(7, 7))))
                    .isInstanceOf(GameNotFoundException.class);
        }
    }

    @Nested
    class Request {

        private RequestAnalysis requests() {
            return new RequestAnalysis(games, queue, clock);
        }

        @Test
        void queuesAFinishedGame() {
            var game = resigned("e2e4");

            var job = requests().request(game.id());

            assertThat(job.state()).isEqualTo(State.QUEUED);
            assertThat(job.gameId()).isEqualTo(game.id());
        }

        @Test
        void askingTwiceKeepsOneJob() {
            var game = resigned("e2e4");

            var first = requests().request(game.id());
            var second = requests().request(game.id());

            assertThat(second.id()).isEqualTo(first.id());
        }

        @Test
        void aGameInProgressCannotBeAnalysed() {
            var game = storedGame("e2e4");

            assertThatThrownBy(() -> requests().request(game.id()))
                    .isInstanceOfSatisfying(AnalysisNotPossibleException.class,
                            e -> assertThat(e.reason()).isEqualTo(AnalysisNotPossibleException.Reason.NOT_FINISHED));
        }

        @Test
        void aGameWithoutMovesHasNothingToAnalyse() {
            var game = resigned("");

            assertThatThrownBy(() -> requests().request(game.id()))
                    .isInstanceOfSatisfying(AnalysisNotPossibleException.class,
                            e -> assertThat(e.reason()).isEqualTo(AnalysisNotPossibleException.Reason.NO_MOVES));
        }

        @Test
        void anUnknownGameIsNotFound() {
            assertThatThrownBy(() -> requests().request(new GameId(new UUID(7, 7))))
                    .isInstanceOf(GameNotFoundException.class);
        }
    }
}
