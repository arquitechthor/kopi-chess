package com.arquitechthor.kopi.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.domain.analysis.Evaluation;
import com.arquitechthor.kopi.domain.chess.Color;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvaluationTextTest {

    @Test
    void writesEachEvaluationInTheEnginesWords() {
        assertThat(EvaluationText.format(new Evaluation.Centipawns(-34))).isEqualTo("cp -34");
        assertThat(EvaluationText.format(new Evaluation.MateIn(-3))).isEqualTo("mate -3");
        assertThat(EvaluationText.format(new Evaluation.Checkmate(Color.WHITE))).isEqualTo("checkmate WHITE");
    }

    @Test
    void readsBackWhatItWrites() {
        for (var evaluation : List.of(new Evaluation.Centipawns(0), new Evaluation.Centipawns(1500),
                new Evaluation.MateIn(7), new Evaluation.MateIn(-1), new Evaluation.Checkmate(Color.BLACK))) {
            assertThat(EvaluationText.parse(EvaluationText.format(evaluation))).isEqualTo(evaluation);
        }
    }

    @Test
    void refusesAnythingElse() {
        assertThatThrownBy(() -> EvaluationText.parse("cp")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EvaluationText.parse("wdl 500")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EvaluationText.parse("mate 0")).isInstanceOf(IllegalArgumentException.class);
    }
}
