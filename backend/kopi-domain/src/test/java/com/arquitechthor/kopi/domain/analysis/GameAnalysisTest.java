package com.arquitechthor.kopi.domain.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.arquitechthor.kopi.domain.analysis.Evaluation.Centipawns;
import com.arquitechthor.kopi.domain.analysis.MoveClassifier.Classification;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.game.GameId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameAnalysisTest {

    private static final GameId GAME = new GameId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final AnalysisSettings SETTINGS = new AnalysisSettings(new SearchLimits.Nodes(1_000_000), 2, 5);
    private static final Instant START = Instant.parse("2026-10-26T12:00:00Z");

    private static MoveAnalysis move(int ply, int before, int after, MoveCategory category) {
        return new MoveAnalysis(ply, new Centipawns(before), new Centipawns(after), "e2e4", List.of("e4"),
                new Classification(category, 0, OptionalInt.empty()));
    }

    private static GameAnalysis analysis(List<MoveAnalysis> moves) {
        return new GameAnalysis(GAME, "Stockfish 19", SETTINGS, Color.WHITE, Optional.empty(), moves, START,
                START.plusSeconds(90));
    }

    @Test
    void sumsUpItsMoves() {
        var summary = analysis(List.of(
                move(1, 20, 15, MoveCategory.BOOK),
                move(2, 15, 30, MoveCategory.BOOK),
                move(3, 30, -280, MoveCategory.BLUNDER),
                move(4, -280, -260, MoveCategory.GOOD))).summary();

        // The same game as AnalysisSummaryTest#aShortGame: same accuracies.
        assertThat(summary.white().count(MoveCategory.BLUNDER)).isEqualTo(1);
        assertThat(summary.white().accuracy()).hasValueCloseTo(38.704120, within(1e-5));
        assertThat(summary.black().count(MoveCategory.GOOD)).isEqualTo(1);
    }

    @Test
    void eachMoveStartsWhereTheLastOneEnded() {
        assertThatThrownBy(() -> analysis(List.of(move(1, 20, 15, MoveCategory.GOOD),
                move(2, 40, 30, MoveCategory.GOOD))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not start where");
    }

    @Test
    void movesArePliesInOrderFromOne() {
        assertThatThrownBy(() -> analysis(List.of(move(2, 20, 15, MoveCategory.GOOD))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aGameWithoutMovesHasNothingToAnalyse() {
        assertThatThrownBy(() -> analysis(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyAMissedMateHasAMissedMateLine() {
        var missed = new MoveAnalysis(1, new Evaluation.MateIn(2), new Centipawns(714), "d2d8",
                List.of("Rd8+", "Rxd8", "Rxd8#"),
                new Classification(MoveCategory.MISSED_MATE, 0.13, OptionalInt.of(2)));

        assertThat(missed.missedMateLine()).hasValue(List.of("Rd8+", "Rxd8", "Rxd8#"));
        assertThat(move(1, 20, 15, MoveCategory.GOOD).missedMateLine()).isEmpty();
    }

    @Test
    void settingsAreChecked() {
        assertThatThrownBy(() -> new AnalysisSettings(new SearchLimits.Depth(16), 0, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnalysisSettings(new SearchLimits.Depth(16), 2, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
