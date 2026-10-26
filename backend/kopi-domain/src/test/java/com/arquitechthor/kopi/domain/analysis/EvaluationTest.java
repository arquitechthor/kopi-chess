package com.arquitechthor.kopi.domain.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.domain.analysis.Evaluation.Centipawns;
import com.arquitechthor.kopi.domain.analysis.Evaluation.Checkmate;
import com.arquitechthor.kopi.domain.analysis.Evaluation.MateIn;
import com.arquitechthor.kopi.domain.chess.Color;
import org.junit.jupiter.api.Test;

class EvaluationTest {

    @Test
    void aMateBelongsToTheSideItsSignSays() {
        assertThat(new MateIn(3).mateFor(Color.WHITE)).hasValue(3);
        assertThat(new MateIn(3).mateFor(Color.BLACK)).isEmpty();
        assertThat(new MateIn(-2).mateFor(Color.BLACK)).hasValue(2);
        assertThat(new MateIn(-2).mateFor(Color.WHITE)).isEmpty();
    }

    @Test
    void aCheckmateIsAMateInZeroForTheWinner() {
        assertThat(new Checkmate(Color.BLACK).mateFor(Color.BLACK)).hasValue(0);
        assertThat(new Checkmate(Color.BLACK).mateFor(Color.WHITE)).isEmpty();
    }

    @Test
    void centipawnsAreNeverAMate() {
        assertThat(new Centipawns(5000).mateFor(Color.WHITE)).isEmpty();
    }

    @Test
    void aMateInZeroMustSayWhoWon() {
        // Stockfish's `mate 0` after a checkmate: from White's point of view
        // it has no sign, so it is a Checkmate(winner) instead.
        assertThatThrownBy(() -> new MateIn(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Checkmate");
    }
}
