package com.arquitechthor.kopi.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.application.port.AnalysisRepository;
import com.arquitechthor.kopi.application.port.BotUnavailableException;
import com.arquitechthor.kopi.domain.analysis.AnalysisSettings;
import com.arquitechthor.kopi.domain.analysis.MoveClassifier.Classification;
import com.arquitechthor.kopi.domain.analysis.Evaluation;
import com.arquitechthor.kopi.domain.analysis.GameAnalysis;
import com.arquitechthor.kopi.domain.analysis.MoveAnalysis;
import com.arquitechthor.kopi.domain.analysis.MoveCategory;
import com.arquitechthor.kopi.domain.analysis.SearchLimits;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.GameId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The decorators that measure the ports (design §14). The metrics as
/// Prometheus sees them are checked in `KopiApplicationIT`.
class MetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Nested
    class BotMoves {

        @Test
        void eachMoveIsTimedByProfile() {
            var bot = new TimedBotPlayer((position, _) -> position.legalMoves().getFirst(), registry);

            bot.chooseMove(Position.initial(), BotProfile.PAWN);
            bot.chooseMove(Position.initial(), BotProfile.PAWN);
            bot.chooseMove(Position.initial(), BotProfile.ROOK);

            assertThat(registry.get(TimedBotPlayer.METRIC).tags("profile", "PAWN", "outcome", "ok").timer().count())
                    .isEqualTo(2);
            assertThat(registry.get(TimedBotPlayer.METRIC).tags("profile", "ROOK", "outcome", "ok").timer().count())
                    .isOne();
        }

        @Test
        void aBotThatCannotAnswerIsTimedToo() {
            var failure = new BotUnavailableException(BotProfile.QUEEN, new IllegalStateException("time out"));
            var bot = new TimedBotPlayer((_, _) -> {
                throw failure;
            }, registry);

            assertThatThrownBy(() -> bot.chooseMove(Position.initial(), BotProfile.QUEEN)).isSameAs(failure);

            assertThat(registry.get(TimedBotPlayer.METRIC).tags("profile", "QUEEN", "outcome", "error").timer()
                    .count()).isOne();
        }
    }

    @Nested
    class Analyses {

        private static final Instant START = Instant.parse("2026-10-29T10:00:00Z");

        private final List<GameAnalysis> saved = new ArrayList<>();
        private final AnalysisRepository stored = new AnalysisRepository() {
            @Override
            public Optional<GameAnalysis> findByGameId(GameId id) {
                return Optional.empty();
            }

            @Override
            public void save(GameAnalysis analysis) {
                saved.add(analysis);
            }
        };

        @Test
        void eachSavedAnalysisRecordsHowLongItTook() {
            var analyses = new TimedAnalysisRepository(stored, registry);
            var analysis = analysisTaking(90);

            analyses.save(analysis);

            var duration = registry.get(TimedAnalysisRepository.METRIC).timer();
            assertThat(saved).containsExactly(analysis);
            assertThat(duration.count()).isOne();
            assertThat(duration.totalTime(TimeUnit.SECONDS)).isEqualTo(90);
        }

        @Test
        void anAnalysisThatCannotBeSavedIsNotCounted() {
            var analyses = new TimedAnalysisRepository(new AnalysisRepository() {
                @Override
                public Optional<GameAnalysis> findByGameId(GameId id) {
                    return Optional.empty();
                }

                @Override
                public void save(GameAnalysis analysis) {
                    throw new IllegalStateException("database down");
                }
            }, registry);

            assertThatThrownBy(() -> analyses.save(analysisTaking(90))).isInstanceOf(IllegalStateException.class);

            assertThat(registry.get(TimedAnalysisRepository.METRIC).timer().count()).isZero();
        }

        /// 1.e4, judged in `seconds`.
        private static GameAnalysis analysisTaking(long seconds) {
            var move = new MoveAnalysis(1, new Evaluation.Centipawns(35), new Evaluation.Centipawns(30), "e2e4",
                    List.of("e4"), new Classification(MoveCategory.BOOK, 0.0092, OptionalInt.empty()));
            return new GameAnalysis(new GameId(UUID.randomUUID()), "Stockfish 19",
                    new AnalysisSettings(new SearchLimits.Nodes(1_000_000), 2, 5), Color.WHITE, Optional.empty(),
                    List.of(move), START, START.plusSeconds(seconds));
        }
    }
}
