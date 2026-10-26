package com.arquitechthor.kopi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.adapter.web.OfficeMoveInput.Candidate;
import com.arquitechthor.kopi.adapter.web.OfficeMoveInput.Resolution;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OfficeMoveInputTest {

    @Nested
    class Notation {

        private final OfficeNotation spanish = OfficeNotation.of('R', 'D', 'T', 'A', 'C');
        private final OfficeNotation english = OfficeNotation.of('K', 'Q', 'R', 'B', 'N');

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
            "Nf3, Cf3",
            "Kxe2, Rxe2",
            "Rae1, Tae1",
            "Qxd8+, Dxd8+",
            "Bb5#, Ab5#",
            "e8=Q, e8=D",
            "exd8=N+, exd8=C+",
            "O-O-O, O-O-O",
            "e4, e4",
        })
        void writesSanWithTheLettersOfTheLanguage(String san, String spanishSan) {
            assertThat(spanish.localise(san)).isEqualTo(spanishSan);
            assertThat(english.localise(san)).isEqualTo(san);
        }

        @Test
        void knowsTheLetterOfEachPiece() {
            assertThat(spanish.king()).isEqualTo('R');
            assertThat(List.of(spanish.king(), spanish.queen(), spanish.rook(), spanish.bishop(), spanish.knight(),
                    spanish.pawn())).containsExactly('R', 'D', 'T', 'A', 'C', 'P');
        }
    }

    @Nested
    class Resolve {

        /// The legal moves of the initial position that matter here, in Spanish.
        private final List<Candidate> initial = List.of(
                new Candidate("e2e4", "e4"), new Candidate("e2e3", "e3"),
                new Candidate("g1f3", "Cf3"), new Candidate("b1c3", "Cc3"));

        private Resolution resolve(String text, List<Candidate> moves) {
            return OfficeMoveInput.resolve(text, moves);
        }

        @ParameterizedTest(name = "\"{0}\" is {1}")
        @CsvSource(delimiter = '|', textBlock = """
                e4      | e2e4
                E4      | e2e4
                ' e4 '  | e2e4
                Cf3     | g1f3
                cf3     | g1f3
                CF3     | g1f3
                Cf3+    | g1f3
                g1f3    | g1f3
                G1F3    | g1f3
                """)
        void acceptsTheSameMoveWrittenInManyWays(String text, String uci) {
            assertThat(resolve(text, initial)).isEqualTo(new Resolution.Match(uci, uciToSan(uci)));
        }

        private String uciToSan(String uci) {
            return initial.stream().filter(c -> c.uci().equals(uci)).findFirst().orElseThrow().san();
        }

        @Test
        void castlingMayBeWrittenWithZeros() {
            var moves = List.of(new Candidate("e1g1", "O-O"), new Candidate("e1c1", "O-O-O"));

            assertThat(resolve("0-0", moves)).isEqualTo(new Resolution.Match("e1g1", "O-O"));
            assertThat(resolve("o-o-o", moves)).isEqualTo(new Resolution.Match("e1c1", "O-O-O"));
        }

        @Test
        void theCaptureSignAndTheEqualsSignAreOptional() {
            var moves = List.of(new Candidate("e5d6", "exd6"), new Candidate("b7b8q", "b8=D"),
                    new Candidate("b7b8n", "b8=C"));

            assertThat(resolve("ed6", moves)).isEqualTo(new Resolution.Match("e5d6", "exd6"));
            assertThat(resolve("b8D", moves)).isEqualTo(new Resolution.Match("b7b8q", "b8=D"));
            assertThat(resolve("b8=c", moves)).isEqualTo(new Resolution.Match("b7b8n", "b8=C"));
        }

        @Test
        void asksWhenTheTextFitsSeveralMoves() {
            // In Spanish A is the bishop and a is a file: ignoring case,
            // "axb5" is both a pawn capture and a bishop capture.
            var moves = List.of(new Candidate("a4b5", "axb5"), new Candidate("d3b5", "Axb5"),
                    new Candidate("d3c4", "Ac4"));

            assertThat(resolve("AXB5", moves)).isEqualTo(new Resolution.Ambiguous(List.of("axb5", "Axb5")));
        }

        @Test
        void theExactCaseSettlesAnAmbiguity() {
            // Written exactly as one of them, it is that one.
            var moves = List.of(new Candidate("a4b5", "axb5"), new Candidate("d3b5", "Axb5"));

            assertThat(resolve("Axb5", moves)).isEqualTo(new Resolution.Match("d3b5", "Axb5"));
            assertThat(resolve("axb5", moves)).isEqualTo(new Resolution.Match("a4b5", "axb5"));
        }

        @Test
        void somethingThatIsNoLegalMoveIsNoMatch() {
            assertThat(resolve("Ce5", initial)).isEqualTo(new Resolution.NoMatch());
            assertThat(resolve("hello", initial)).isEqualTo(new Resolution.NoMatch());
            assertThat(resolve("", initial)).isEqualTo(new Resolution.NoMatch());
            assertThat(resolve(null, initial)).isEqualTo(new Resolution.NoMatch());
        }
    }
}
