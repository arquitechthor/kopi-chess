package com.arquitechthor.kopi.adapter.stockfish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.application.port.EngineAnalysis;
import com.arquitechthor.kopi.application.port.EngineUnavailableException;
import com.arquitechthor.kopi.application.port.SearchLimits;
import com.arquitechthor.kopi.domain.analysis.Evaluation;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Fen;
import com.arquitechthor.kopi.domain.chess.Move;
import com.arquitechthor.kopi.domain.chess.Position;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StockfishAnalysisTest {

    private static final SearchLimits DEPTH_12 = new SearchLimits.Depth(12);

    private static List<String> uci(EngineAnalysis.Line line) {
        return line.moves().stream().map(Move::toUci).toList();
    }

    /// Against the real engine: skipped locally without a binary, run in CI
    /// (see [StockfishBotsTest#binary]).
    @Nested
    class RealEngine {

        private StockfishAnalysis analysis;

        private StockfishAnalysis analysis(Path binary) {
            var properties = new StockfishProperties(binary.toString(), 1, Duration.ofSeconds(10),
                    Duration.ofSeconds(5), Duration.ofSeconds(2), null, null);
            analysis = new StockfishAdapterConfiguration().stockfishAnalysis(properties);
            return analysis;
        }

        @AfterEach
        void close() {
            if (analysis != null) {
                analysis.close();
            }
        }

        @Test
        void findsAMateInTwoWithItsLine() {
            var result = analysis(StockfishBotsTest.binary())
                    .analyse(Fen.parse("r5k1/5ppp/8/8/8/8/3R1PPP/3R2K1 w - - 0 1"), DEPTH_12, 1);

            assertThat(result.engine()).startsWith("Stockfish");
            assertThat(result.evaluation()).isEqualTo(new Evaluation.MateIn(2));
            assertThat(uci(result.lines().getFirst())).containsExactly("d2d8", "a8d8", "d1d8");
            assertThat(result.bestMove()).map(Move::toUci).hasValue("d2d8");
        }

        @Test
        void scoresFromWhitesPointOfView() {
            // The same mate, mirrored: Black mates, so the mate is negative.
            var result = analysis(StockfishBotsTest.binary())
                    .analyse(Fen.parse("3r2k1/3r1ppp/8/8/8/8/5PPP/R5K1 b - - 0 1"), DEPTH_12, 1);

            assertThat(result.evaluation()).isEqualTo(new Evaluation.MateIn(-2));
        }

        @Test
        void givesTheTwoBestLinesBestFirst() {
            var result = analysis(StockfishBotsTest.binary()).analyse(Position.initial(), DEPTH_12, 2);

            assertThat(result.lines()).hasSize(2);
            var first = (Evaluation.Centipawns) result.lines().get(0).evaluation();
            var second = (Evaluation.Centipawns) result.lines().get(1).evaluation();
            assertThat(first.value()).isGreaterThanOrEqualTo(second.value());
            assertThat(result.evaluation()).isEqualTo(first);
        }

        @Test
        void searchesByNodesToo() {
            var result = analysis(StockfishBotsTest.binary())
                    .analyse(Position.initial(), new SearchLimits.Nodes(20_000), 1);

            assertThat(result.bestMove()).isPresent();
        }

        @Test
        void aCheckmateNeedsNoSearch() {
            // After Rc8#: Stockfish would say "mate 0", without a winner.
            var result = analysis(StockfishBotsTest.binary())
                    .analyse(Fen.parse("2R3k1/5ppp/8/8/8/8/5PPP/6K1 b - - 1 1"), DEPTH_12, 2);

            assertThat(result.evaluation()).isEqualTo(new Evaluation.Checkmate(Color.WHITE));
            assertThat(result.lines()).isEmpty();
            assertThat(result.bestMove()).isEmpty();
        }

        @Test
        void aStalemateIsEven() {
            var result = analysis(StockfishBotsTest.binary())
                    .analyse(Fen.parse("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"), DEPTH_12, 1);

            assertThat(result.evaluation()).isEqualTo(new Evaluation.Centipawns(0));
            assertThat(result.lines()).isEmpty();
        }

        @Test
        void withoutAnEngineItIsUnavailable() {
            var engine = analysis(Path.of("no-such-stockfish"));

            assertThatThrownBy(() -> engine.analyse(Position.initial(), DEPTH_12, 1))
                    .isInstanceOf(EngineUnavailableException.class);
        }
    }

    /// Against a scripted engine: what the adapter does with odd answers.
    @Nested
    class Scripted {

        private static StockfishAnalysis answering(List<String> goAnswer) {
            var pool = new EnginePool<>(1,
                    () -> UciEngine.start(ScriptedConnection.answeringGo(goAnswer), Duration.ofSeconds(1)),
                    UciEngine::isAlive, Duration.ofSeconds(1));
            return new StockfishAnalysis(pool, Duration.ofSeconds(1));
        }

        @Test
        void asksForFullStrengthAndTheLinesWanted() {
            var connection = ScriptedConnection.answeringGo(List.of(
                    "info depth 10 multipv 1 score cp 30 pv e2e4 e7e5",
                    "info depth 10 multipv 2 score cp 25 pv d2d4 d7d5",
                    "bestmove e2e4"));
            var pool = new EnginePool<>(1, () -> UciEngine.start(connection, Duration.ofSeconds(1)),
                    UciEngine::isAlive, Duration.ofSeconds(1));

            var result = new StockfishAnalysis(pool, Duration.ofSeconds(1))
                    .analyse(Position.initial(), new SearchLimits.Depth(10), 2);

            assertThat(connection.sent).contains("setoption name Skill Level value 20",
                    "setoption name UCI_LimitStrength value false", "setoption name MultiPV value 2",
                    "go depth 10");
            assertThat(result.lines()).extracting(EngineAnalysis.Line::evaluation)
                    .containsExactly(new Evaluation.Centipawns(30), new Evaluation.Centipawns(25));
            assertThat(uci(result.lines().get(1))).containsExactly("d2d4", "d7d5");
        }

        @Test
        void turnsBlacksScoresToWhitesPointOfView() {
            var result = answering(List.of("info depth 10 multipv 1 score cp 40 pv e7e5", "bestmove e7e5"))
                    .analyse(Fen.parse("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"),
                            DEPTH_12, 1);

            assertThat(result.evaluation()).isEqualTo(new Evaluation.Centipawns(-40));
        }

        @Test
        void anIllegalMoveInTheLineIsRefused() {
            var engine = answering(List.of("info depth 10 multipv 1 score cp 30 pv e2e5", "bestmove e2e5"));

            assertThatThrownBy(() -> engine.analyse(Position.initial(), DEPTH_12, 1))
                    .isInstanceOf(EngineUnavailableException.class)
                    .hasStackTraceContaining("e2e5");
        }

        @Test
        void aSearchWithoutScoresIsRefused() {
            var engine = answering(List.of("bestmove e2e4"));

            assertThatThrownBy(() -> engine.analyse(Position.initial(), DEPTH_12, 1))
                    .isInstanceOf(EngineUnavailableException.class);
        }

        @Test
        void atLeastOneLine() {
            var engine = answering(List.of());

            assertThatThrownBy(() -> engine.analyse(Position.initial(), DEPTH_12, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
