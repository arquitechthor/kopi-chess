package com.arquitechthor.kopi.adapter.stockfish;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.adapter.stockfish.InfoLine.Bound;
import com.arquitechthor.kopi.adapter.stockfish.InfoLine.Score.Centipawns;
import com.arquitechthor.kopi.adapter.stockfish.InfoLine.Score.MateIn;
import com.arquitechthor.kopi.domain.chess.Color;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// Real lines from Stockfish 19 (docs/notas/stockfish-opciones.md).
class InfoLineTest {

    @Test
    void readsDepthScoreAndPrincipalVariation() {
        var line = InfoLine.parse("info depth 15 seldepth 25 multipv 1 score cp 32 nodes 284019 nps 1420095 "
                + "hashfull 98 tbhits 0 time 200 pv e2e4 c7c5 b1c3").orElseThrow();

        assertThat(line).isEqualTo(new InfoLine(15, 1, new Centipawns(32), Bound.EXACT, List.of("e2e4", "c7c5", "b1c3")));
    }

    @Test
    void readsAMateScore() {
        var line = InfoLine.parse("info depth 1 seldepth 3 multipv 1 score mate 1 nodes 31 nps 15500 "
                + "hashfull 0 tbhits 0 time 2 pv d8h4").orElseThrow();

        assertThat(line.score()).isEqualTo(new MateIn(1));
        assertThat(line.pv()).containsExactly("d8h4");
    }

    @Test
    void readsTheMateZeroOfAFinishedPosition() {
        assertThat(InfoLine.parse("info depth 0 score mate 0")).hasValueSatisfying(line -> {
            assertThat(line.score()).isEqualTo(new MateIn(0));
            assertThat(line.pv()).isEmpty();
        });
    }

    @Test
    void readsTheCandidateNumberAndSkipsWdl() {
        var line = InfoLine.parse("info depth 12 seldepth 22 multipv 2 score cp 21 wdl 68 927 5 nodes 3198 "
                + "pv d2d4 g8f6 c2c4").orElseThrow();

        assertThat(line.multiPv()).isEqualTo(2);
        assertThat(line.score()).isEqualTo(new Centipawns(21));
    }

    @Test
    void readsBounds() {
        assertThat(InfoLine.parse("info depth 20 score cp 40 lowerbound nodes 1 pv e2e4")).hasValueSatisfying(
                line -> assertThat(line.bound()).isEqualTo(Bound.LOWER));
        assertThat(InfoLine.parse("info depth 20 score cp 40 upperbound nodes 1 pv e2e4")).hasValueSatisfying(
                line -> assertThat(line.bound()).isEqualTo(Bound.UPPER));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "info string Available processors: 0-7",
        "info string NNUE evaluation using nn-1a298aa575a0.nnue (109MiB, (86896, 1024, 32, 32, 1))",
        "info depth 5 currmove e2e4 currmovenumber 1",
        "bestmove e2e4 ponder e7e5",
        "info depth score cp",
        "info depth x score cp 3",
        "",
    })
    void ignoresLinesWithoutAScore(String text) {
        assertThat(InfoLine.parse(text)).isEmpty();
    }

    @Test
    void scoresAreTurnedToWhitesPointOfView() {
        // After 1.e4 Black is to move and Stockfish says -27: White is better.
        assertThat(new Centipawns(-27).forWhite(Color.BLACK)).isEqualTo(new Centipawns(27));
        assertThat(new Centipawns(32).forWhite(Color.WHITE)).isEqualTo(new Centipawns(32));
        // Black to move mates in one: for White, mated in one.
        assertThat(new MateIn(1).forWhite(Color.BLACK)).isEqualTo(new MateIn(-1));
        assertThat(new MateIn(-3).forWhite(Color.WHITE)).isEqualTo(new MateIn(-3));
    }
}
