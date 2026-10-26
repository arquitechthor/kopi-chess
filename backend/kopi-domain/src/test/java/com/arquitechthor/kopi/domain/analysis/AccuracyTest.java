package com.arquitechthor.kopi.domain.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.arquitechthor.kopi.domain.analysis.Evaluation.Centipawns;
import com.arquitechthor.kopi.domain.chess.Color;
import java.util.List;
import org.junit.jupiter.api.Test;

/// Expected values from the formula of design §8.4 in a calculator:
/// `103.1668 · exp(-0.04354 · drop) - 3.1669`.
class AccuracyTest {

    /// The win chance whose percentage is `percent`.
    private static WinChance percent(double percent) {
        return new WinChance(percent / 50 - 1);
    }

    @Test
    void losingNothingIsPerfect() {
        assertThat(Accuracy.ofMove(percent(60), percent(60))).isEqualTo(100);
        assertThat(Accuracy.ofMove(percent(60), percent(70))).isEqualTo(100);
    }

    @Test
    void tenPointsOfWinPercentCostAThird() {
        assertThat(Accuracy.ofMove(percent(60), percent(50))).isCloseTo(63.582619, within(1e-5));
    }

    @Test
    void aHangingQueenIsAlmostZero() {
        // 1.e4 d5 2.Qg4?? (Stockfish 19: +0.70 before, -9.86 after).
        var before = new Centipawns(70).winChanceFor(Color.WHITE);
        var after = new Centipawns(-986).winChanceFor(Color.WHITE);
        assertThat(Accuracy.ofMove(before, after)).isCloseTo(6.735182, within(1e-5));
    }

    @Test
    void neverBelowZero() {
        assertThat(Accuracy.ofMove(percent(100), percent(0))).isZero();
    }

    @Test
    void harmonicMeanLeansTowardsTheWorst() {
        assertThat(Accuracy.harmonicMean(List.of(100.0, 50.0))).isCloseTo(66.666667, within(1e-5));
        // A zero counts as one, so it does not divide by zero.
        assertThat(Accuracy.harmonicMean(List.of(100.0, 0.0))).isCloseTo(2 / 1.01, within(1e-9));
    }

    @Test
    void weightedMeanWeighsEachAccuracy() {
        assertThat(Accuracy.weightedMean(List.of(100.0, 40.0), List.of(1.0, 3.0))).isCloseTo(55, within(1e-9));
    }
}
