package com.arquitechthor.kopi.domain.chess;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RulesTest {

    private static final String FOOLS_MATE = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3";
    private static final String STALEMATE = "7k/5Q2/6K1/8/8/8/8/8 b - - 0 1";

    @Nested
    class CheckmateAndStalemate {

        @Test
        void checkmateIsCheckWithNoWayOut() {
            var position = Fen.parse(FOOLS_MATE);

            assertThat(Rules.isCheck(position)).isTrue();
            assertThat(Rules.isCheckmate(position)).isTrue();
            assertThat(Rules.isStalemate(position)).isFalse();
        }

        @Test
        void stalemateIsNoMoveWithoutCheck() {
            var position = Fen.parse(STALEMATE);

            assertThat(Rules.isCheck(position)).isFalse();
            assertThat(Rules.isCheckmate(position)).isFalse();
            assertThat(Rules.isStalemate(position)).isTrue();
        }

        @Test
        void aCheckWithAnAnswerIsNeitherMateNorStalemate() {
            var position = Fen.parse("4k3/8/8/8/8/8/4r3/R3K3 w Q - 0 1");

            assertThat(Rules.isCheck(position)).isTrue();
            assertThat(Rules.isCheckmate(position)).isFalse();
            assertThat(Rules.isStalemate(position)).isFalse();
        }

        @Test
        void theInitialPositionIsNone() {
            var position = Position.initial();

            assertThat(Rules.isCheck(position)).isFalse();
            assertThat(Rules.isCheckmate(position)).isFalse();
            assertThat(Rules.isStalemate(position)).isFalse();
        }
    }

    @Nested
    class InsufficientMaterial {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
            "4k3/8/8/8/8/8/8/4K3 w - - 0 1",        // K vs K
            "4k3/8/8/8/8/8/8/2B1K3 w - - 0 1",      // K+B vs K
            "4k3/8/8/8/8/8/8/1N2K3 w - - 0 1",      // K+N vs K
            "4kn2/8/8/8/8/8/8/4K3 w - - 0 1",       // K vs K+N
            "4kb2/8/8/8/8/8/8/2B1K3 w - - 0 1",     // K+B (c1, dark) vs K+B (f8, dark)
            "2b1k3/8/8/8/8/8/8/1B2K3 w - - 0 1",    // K+B (b1, light) vs K+B (c8, light)
            "4k3/8/8/8/8/8/8/B1B1K3 w - - 0 1",     // two bishops on the same colour cannot mate
        })
        void noPossibleMateIsADraw(String fen) {
            assertThat(Rules.isInsufficientMaterial(Fen.parse(fen))).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
            Fen.INITIAL,
            "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1",      // a pawn can promote
            "4k3/8/8/8/8/8/8/R3K3 w - - 0 1",       // a rook mates
            "4k3/8/8/8/8/8/8/3QK3 w - - 0 1",       // so does a queen
            "4k3/8/8/8/8/8/8/1NN1K3 w - - 0 1",     // two knights can mate (with help)
            "4k3/8/8/8/8/8/8/1NB1K3 w - - 0 1",     // knight and bishop mate
            "4k3/8/8/8/8/8/8/2BBK3 w - - 0 1",      // bishops on both colours mate
            "4kn2/8/8/8/8/8/8/1N2K3 w - - 0 1",     // K+N vs K+N: a helpmate exists
            "2b1k3/8/8/8/8/8/8/2B1K3 w - - 0 1",    // c1 (dark) vs c8 (light): a helpmate exists
        })
        void anyPossibleMateIsNot(String fen) {
            assertThat(Rules.isInsufficientMaterial(Fen.parse(fen))).isFalse();
        }
    }

    @Nested
    class FiftyMoveRule {

        @Test
        void appliesAfterFiftyMovesByEachSideWithoutCaptureOrPawnMove() {
            assertThat(Rules.isFiftyMoveRule(Fen.parse("4k3/8/8/8/8/8/8/R3K3 w - - 100 80"))).isTrue();
            assertThat(Rules.isFiftyMoveRule(Fen.parse("4k3/8/8/8/8/8/8/R3K3 w - - 99 80"))).isFalse();
        }

        @Test
        void isCountedByTheHalfmoveClockThatApplyKeeps() {
            var position = Fen.parse("4k3/8/8/8/8/8/8/R3K3 w - - 99 80");

            var after = position.apply(San.parse(position, "Ra2"));

            assertThat(Rules.isFiftyMoveRule(after)).isTrue();
        }
    }
}
