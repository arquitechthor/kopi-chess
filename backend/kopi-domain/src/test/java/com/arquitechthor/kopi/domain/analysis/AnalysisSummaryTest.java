package com.arquitechthor.kopi.domain.analysis;

import static com.arquitechthor.kopi.domain.analysis.MoveCategory.BEST;
import static com.arquitechthor.kopi.domain.analysis.MoveCategory.BLUNDER;
import static com.arquitechthor.kopi.domain.analysis.MoveCategory.BOOK;
import static com.arquitechthor.kopi.domain.analysis.MoveCategory.GOOD;
import static com.arquitechthor.kopi.domain.analysis.MoveCategory.MISSED_MATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.arquitechthor.kopi.domain.analysis.Evaluation.Centipawns;
import com.arquitechthor.kopi.domain.analysis.Evaluation.Checkmate;
import com.arquitechthor.kopi.domain.chess.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/// The expected accuracies come from a separate implementation of Lichess's
/// game accuracy (a short Python script, not this code): volatility-weighted
/// mean and harmonic mean of the move accuracies, averaged.
class AnalysisSummaryTest {

    private static List<Evaluation> centipawns(int... values) {
        return IntStream.of(values).<Evaluation>mapToObj(Centipawns::new).toList();
    }

    @Test
    void countsEachSidesCategories() {
        var summary = AnalysisSummary.of(Color.WHITE, centipawns(20, 15, 30, -280, -260),
                List.of(BOOK, BOOK, BLUNDER, GOOD));

        assertThat(summary.white().count(BOOK)).isEqualTo(1);
        assertThat(summary.white().count(BLUNDER)).isEqualTo(1);
        assertThat(summary.black().count(BOOK)).isEqualTo(1);
        assertThat(summary.black().count(GOOD)).isEqualTo(1);
        assertThat(summary.black().count(MISSED_MATE)).isZero();
    }

    @Test
    void aShortGame() {
        // White: 20 → 15, then 30 → -280 (a blunder); Black: 15 → 30, -280 → -260.
        var summary = AnalysisSummary.of(Color.WHITE, centipawns(20, 15, 30, -280, -260),
                List.of(GOOD, GOOD, BLUNDER, GOOD));

        assertThat(summary.white().accuracy()).hasValueCloseTo(38.704120, within(1e-5));
        assertThat(summary.black().accuracy()).hasValueCloseTo(93.833601, within(1e-5));
    }

    @Test
    void aLongerGameWithWiderWindows() {
        // 41 moves: windows of 4 positions, the first two repeating the
        // opening window. White mates at the end.
        var positions = new ArrayList<>(centipawns(81, 136, 88, 81, 110, 150, -53, -56, 112, 93, -55, -102, 78, 5,
                -78, -104, 125, -129, 52, 81, -70, -143, 120, -118, -120, -132, -53, -27, -135, 87, 17, 75, -50, 115,
                -31, 0, 105, -148, -107, 84, 650));
        positions.add(new Checkmate(Color.WHITE));

        var summary = AnalysisSummary.of(Color.WHITE, positions, Collections.nCopies(41, GOOD));

        assertThat(summary.white().accuracy()).hasValueCloseTo(79.990827, within(1e-5));
        assertThat(summary.black().accuracy()).hasValueCloseTo(67.304010, within(1e-5));
    }

    @Test
    void blackCanMoveFirst() {
        // Set up with Black to move: Black 30 → -40 (better for Black), White -40 → 10.
        var summary = AnalysisSummary.of(Color.BLACK, centipawns(30, -40, 10), List.of(BEST, BEST));

        assertThat(summary.black().count(BEST)).isEqualTo(1);
        assertThat(summary.black().accuracy()).hasValueCloseTo(100.0, within(1e-9));
        assertThat(summary.white().accuracy()).hasValueCloseTo(100.0, within(1e-9));
    }

    @Test
    void aSideThatNeverMovedHasNoAccuracy() {
        var summary = AnalysisSummary.of(Color.WHITE, centipawns(20, 30), List.of(BEST));

        assertThat(summary.white().accuracy()).isPresent();
        assertThat(summary.black().accuracy()).isEmpty();
        assertThat(summary.of(Color.BLACK).count(BEST)).isZero();
    }

    @Test
    void needsOneMorePositionThanMoves() {
        assertThatThrownBy(() -> AnalysisSummary.of(Color.WHITE, centipawns(20, 30), List.of(BEST, BEST)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
