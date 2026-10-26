package com.arquitechthor.kopi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.domain.analysis.Evaluation;
import com.arquitechthor.kopi.domain.analysis.MoveCategory;
import com.arquitechthor.kopi.domain.chess.Color;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvalGraphTest {

    @Test
    void drawsWhitesWinPercentFromLeftToRight() {
        // Even (50 %), White winning by force (100 %), Black has mated (0 %).
        var graph = EvalGraph.of(
                List.of(new Evaluation.Centipawns(0), new Evaluation.MateIn(3), new Evaluation.Checkmate(Color.BLACK)),
                List.of(MoveCategory.BLUNDER, MoveCategory.MISSED_MATE));

        assertThat(graph.line()).isEqualTo("0.0,60.0 300.0,0.0 600.0,120.0");
        assertThat(graph.area()).isEqualTo("M0,120 L0.0,60.0 L300.0,0.0 L600.0,120.0 L600.0,120 Z");
    }

    @Test
    void marksMistakesBlundersAndMissedMatesAfterTheMove() {
        var graph = EvalGraph.of(
                List.of(new Evaluation.Centipawns(0), new Evaluation.Centipawns(0), new Evaluation.Centipawns(0),
                        new Evaluation.Centipawns(0)),
                List.of(MoveCategory.BEST, MoveCategory.MISTAKE, MoveCategory.INACCURACY));

        assertThat(graph.markers()).singleElement().satisfies(marker -> {
            assertThat(marker.ply()).isEqualTo(2);
            assertThat(marker.x()).isEqualTo("400.0");
            assertThat(marker.category()).isEqualTo(MoveCategory.MISTAKE);
        });
    }

    @Test
    void needsOneMorePositionThanMoves() {
        assertThatThrownBy(() -> EvalGraph.of(List.of(new Evaluation.Centipawns(0)), List.of(MoveCategory.BEST)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void longLinesAreCutButMatesAreShownInFull() {
        var long_ = List.of("e6", "d4", "d5", "Nc3", "Bb4", "e5", "Ne7", "h4");

        assertThat(ReviewPage.line(long_, false)).isEqualTo("e6 d4 d5 ♘c3 ♗b4 e5 …");
        assertThat(ReviewPage.line(long_, true)).isEqualTo("e6 d4 d5 ♘c3 ♗b4 e5 ♘e7 h4");
        assertThat(ReviewPage.line(List.of("Qxf7#"), false)).isEqualTo("♕xf7#");
    }

    @Test
    void evaluationsReadLikeAPlayerWritesThem() {
        assertThat(ReviewPage.evaluation(new Evaluation.Centipawns(35))).isEqualTo("+0.35");
        assertThat(ReviewPage.evaluation(new Evaluation.Centipawns(-120))).isEqualTo("-1.20");
        assertThat(ReviewPage.evaluation(new Evaluation.Centipawns(0))).isEqualTo("+0.00");
        assertThat(ReviewPage.evaluation(new Evaluation.MateIn(3))).isEqualTo("#3");
        assertThat(ReviewPage.evaluation(new Evaluation.MateIn(-2))).isEqualTo("#-2");
        assertThat(ReviewPage.evaluation(new Evaluation.Checkmate(Color.WHITE))).isEqualTo("1–0");
    }
}
