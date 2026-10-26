package com.arquitechthor.kopi.domain.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.arquitechthor.kopi.domain.analysis.Evaluation.Centipawns;
import com.arquitechthor.kopi.domain.analysis.Evaluation.Checkmate;
import com.arquitechthor.kopi.domain.analysis.Evaluation.MateIn;
import com.arquitechthor.kopi.domain.chess.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/// The expected values were worked out apart from this code, with the
/// formula of design §8.3 in a calculator: `2 / (1 + exp(-0.00368208 · cp)) - 1`.
class WinChanceTest {

    @ParameterizedTest(name = "{0} cp → {1}")
    @CsvSource({
            "0, 0.0",
            "100, 0.182052",
            "-100, -0.182052",
            "300, 0.502251",
            "1000, 0.950895",
            // Capped at a thousand: more is not more winning.
            "1500, 0.950895",
            "-5000, -0.950895"})
    void centipawnsFollowTheCurve(int cp, double expected) {
        assertThat(WinChance.of(new Centipawns(cp), Color.WHITE).value()).isCloseTo(expected, within(1e-6));
    }

    @Test
    void blackSeesTheOppositeChance() {
        assertThat(WinChance.of(new Centipawns(100), Color.BLACK).value()).isCloseTo(-0.182052, within(1e-6));
        assertThat(WinChance.of(new MateIn(-3), Color.BLACK).value()).isEqualTo(1);
    }

    @Test
    void aForcedMateOrACheckmateIsCertain() {
        assertThat(WinChance.of(new MateIn(7), Color.WHITE).value()).isEqualTo(1);
        assertThat(WinChance.of(new MateIn(-1), Color.WHITE).value()).isEqualTo(-1);
        assertThat(WinChance.of(new Checkmate(Color.BLACK), Color.WHITE).value()).isEqualTo(-1);
        assertThat(WinChance.of(new Checkmate(Color.BLACK), Color.BLACK).value()).isEqualTo(1);
    }

    @Test
    void percentIsLichessWinPercent() {
        assertThat(WinChance.of(new Centipawns(100), Color.WHITE).percent()).isCloseTo(59.1026, within(1e-4));
        assertThat(WinChance.of(new Centipawns(0), Color.WHITE).percent()).isEqualTo(50);
    }

    @Test
    void lossIsWhatWasGivenAway() {
        var before = new WinChance(0.5);
        assertThat(before.lossTo(new WinChance(0.2))).isCloseTo(0.3, within(1e-12));
        assertThat(before.lossTo(new WinChance(0.7))).isCloseTo(-0.2, within(1e-12));
    }

    @Test
    void staysWithinMinusOneAndOne() {
        assertThatThrownBy(() -> new WinChance(1.01)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WinChance(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }
}
