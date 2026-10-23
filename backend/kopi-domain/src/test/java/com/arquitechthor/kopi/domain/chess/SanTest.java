package com.arquitechthor.kopi.domain.chess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.domain.chess.InvalidSanException.Problem;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SanTest {

    /// The SAN of the legal move written as `uci` in `fen`.
    private static String san(String fen, String uci) {
        var position = Fen.parse(fen);
        var move = position.legalMoves().stream()
                .filter(candidate -> candidate.toUci().equals(uci))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No legal move " + uci + " in " + fen));
        return San.format(position, move);
    }

    /// The UCI of the move that `san` stands for in `fen`.
    private static String uci(String fen, String san) {
        return San.parse(Fen.parse(fen), san).toUci();
    }

    @Nested
    class Format {

        @Test
        void pawnMovesAreJustTheDestination() {
            var fen = "4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1";

            assertThat(san(fen, "e4e5")).isEqualTo("e5");
        }

        @Test
        void pawnCapturesStartWithTheFileTheyLeave() {
            var fen = "4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1";

            assertThat(san(fen, "e4d5")).isEqualTo("exd5");
        }

        @Test
        void enPassantIsWrittenAsAnOrdinaryPawnCapture() {
            var fen = "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2";

            assertThat(san(fen, "e5d6")).isEqualTo("exd6");
        }

        @Test
        void piecesGetTheirUpperCaseLetter() {
            assertThat(san(Fen.INITIAL, "g1f3")).isEqualTo("Nf3");
        }

        @Test
        void pieceCapturesHaveAnX() {
            var fen = "4k3/8/4r3/8/3N4/8/8/4K3 w - - 0 1";

            assertThat(san(fen, "d4e6")).isEqualTo("Nxe6");
        }

        @Test
        void twoPiecesOnDifferentFilesAreToldApartByTheFile() {
            var fen = "4k3/8/8/8/8/5N2/8/1N2K3 w - - 0 1";

            assertThat(san(fen, "b1d2")).isEqualTo("Nbd2");
            assertThat(san(fen, "f3d2")).isEqualTo("Nfd2");
        }

        @Test
        void twoPiecesOnTheSameFileAreToldApartByTheRank() {
            var fen = "k7/8/8/8/8/4R3/8/4R1K1 w - - 0 1";

            assertThat(san(fen, "e1e2")).isEqualTo("R1e2");
            assertThat(san(fen, "e3e2")).isEqualTo("R3e2");
        }

        @Test
        void sometimesOnlyTheFullSquareTellsThemApart() {
            // Three queens reach e2: d1 shares its file with d3 and its rank
            // with f1, so it needs both.
            var fen = "k7/8/8/8/8/3Q4/8/3Q1QK1 w - - 0 1";

            assertThat(san(fen, "d1e2")).isEqualTo("Qd1e2");
            assertThat(san(fen, "d3e2")).isEqualTo("Q3e2");
            assertThat(san(fen, "f1e2")).isEqualTo("Qfe2");
        }

        @Test
        void aPinnedPieceDoesNotCauseDisambiguation() {
            // The knight on f3 is pinned by the bishop on e4, so only the
            // knight on b1 can really go to d2.
            var fen = "4k3/8/8/8/4b3/5N2/8/1N5K w - - 0 1";

            assertThat(san(fen, "b1d2")).isEqualTo("Nd2");
        }

        @Test
        void castlingIsWrittenWithLetterOs() {
            var fen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1";

            assertThat(san(fen, "e1g1")).isEqualTo("O-O");
            assertThat(san(fen, "e1c1")).isEqualTo("O-O-O");
        }

        @Test
        void promotionsNameTheNewPiece() {
            var fen = "4k3/1P6/8/8/8/8/8/4K3 w - - 0 1";

            assertThat(san(fen, "b7b8n")).isEqualTo("b8=N");
            assertThat(san(fen, "b7b8q")).isEqualTo("b8=Q+");
        }

        @Test
        void promotionsMayCapture() {
            var fen = "r3k3/1P6/8/8/8/8/8/4K3 w - - 0 1";

            assertThat(san(fen, "b7a8q")).isEqualTo("bxa8=Q+");
        }

        @Test
        void checksEndWithAPlus() {
            var fen = "4k3/8/8/8/8/8/8/R3K3 w Q - 0 1";

            assertThat(san(fen, "a1a8")).isEqualTo("Ra8+");
            assertThat(san(fen, "e1c1")).isEqualTo("O-O-O");
        }

        @Test
        void checkmateEndsWithAHash() {
            // Fool's mate.
            var fen = "rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq - 0 2";

            assertThat(san(fen, "d8h4")).isEqualTo("Qh4#");
        }
    }

    @Nested
    class Parse {

        @Test
        void readsBackEveryMoveItFormats() {
            // Every legal move two plies deep from positions full of
            // castling, promotions, en passant and pins.
            for (var fen : List.of(
                    Fen.INITIAL,
                    "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
                    "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
                    "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
                    "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")) {
                var root = Fen.parse(fen);
                for (var first : root.legalMoves()) {
                    assertRoundTrip(root, first);
                    var next = root.apply(first);
                    for (var second : next.legalMoves()) {
                        assertRoundTrip(next, second);
                    }
                }
            }
        }

        private static void assertRoundTrip(Position position, Move move) {
            var san = San.format(position, move);
            assertThat(San.parse(position, san)).as("%s in %s", san, position.toFen()).isEqualTo(move);
        }

        @Test
        void acceptsCastlingWrittenWithZeros() {
            var fen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1";

            assertThat(uci(fen, "0-0")).isEqualTo("e1g1");
            assertThat(uci(fen, "0-0-0")).isEqualTo("e1c1");
        }

        @Test
        void ignoresCheckMarksAndAnnotations() {
            assertThat(uci(Fen.INITIAL, "Nf3+")).isEqualTo("g1f3");
            assertThat(uci(Fen.INITIAL, "Nf3!?")).isEqualTo("g1f3");
            assertThat(uci(Fen.INITIAL, " e4 ")).isEqualTo("e2e4");
        }

        @Test
        void acceptsAPromotionWithoutTheEqualsSign() {
            assertThat(uci("4k3/1P6/8/8/8/8/8/4K3 w - - 0 1", "b8Q")).isEqualTo("b7b8q");
        }

        @Test
        void acceptsTheEnPassantSuffix() {
            assertThat(uci("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2", "exd6 e.p.")).isEqualTo("e5d6");
        }

        @Test
        void acceptsMoreDisambiguationThanNeeded() {
            assertThat(uci(Fen.INITIAL, "Ngf3")).isEqualTo("g1f3");
            assertThat(uci(Fen.INITIAL, "Ng1f3")).isEqualTo("g1f3");
        }

        @Test
        void aPawnWithoutAFileOnlyMovesStraightAhead() {
            // "d5" is the push from d4, never the capture from e4 or c4.
            var fen = "4k3/8/8/3p4/2PPP3/8/8/4K3 w - - 0 1";

            assertThatThrownBy(() -> uci(fen, "d5"))
                    .isInstanceOfSatisfying(InvalidSanException.class,
                            e -> assertThat(e.problem()).isEqualTo(Problem.NO_SUCH_MOVE));
            assertThat(uci(fen, "cxd5")).isEqualTo("c4d5");
            assertThat(uci(fen, "exd5")).isEqualTo("e4d5");
        }

        @Test
        void refusesAMoveThatCouldBeMoreThanOne() {
            var fen = "4k3/8/8/8/8/5N2/8/1N2K3 w - - 0 1";

            assertThatThrownBy(() -> uci(fen, "Nd2"))
                    .isInstanceOfSatisfying(InvalidSanException.class, e -> {
                        assertThat(e.problem()).isEqualTo(Problem.AMBIGUOUS);
                        assertThat(e.san()).isEqualTo("Nd2");
                    });
        }

        @Test
        void refusesAMoveThatIsNotLegalHere() {
            assertThatThrownBy(() -> uci(Fen.INITIAL, "Nf6"))
                    .isInstanceOfSatisfying(InvalidSanException.class,
                            e -> assertThat(e.problem()).isEqualTo(Problem.NO_SUCH_MOVE));
            assertThatThrownBy(() -> uci(Fen.INITIAL, "O-O"))
                    .isInstanceOfSatisfying(InvalidSanException.class,
                            e -> assertThat(e.problem()).isEqualTo(Problem.NO_SUCH_MOVE));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "hello", "Ke9", "Xe4", "e4e5e6", "O-O-O-O"})
        void refusesTextThatIsNotSan(String text) {
            assertThatThrownBy(() -> uci(Fen.INITIAL, text))
                    .isInstanceOfSatisfying(InvalidSanException.class,
                            e -> assertThat(e.problem()).isEqualTo(Problem.MALFORMED));
        }
    }
}
