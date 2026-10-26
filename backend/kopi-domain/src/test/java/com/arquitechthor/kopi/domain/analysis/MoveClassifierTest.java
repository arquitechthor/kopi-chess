package com.arquitechthor.kopi.domain.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.arquitechthor.kopi.domain.analysis.Evaluation.Centipawns;
import com.arquitechthor.kopi.domain.analysis.Evaluation.Checkmate;
import com.arquitechthor.kopi.domain.analysis.Evaluation.MateIn;
import com.arquitechthor.kopi.domain.analysis.MoveClassifier.Facts;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Fen;
import com.arquitechthor.kopi.domain.chess.San;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/// The positions are real and so are their evaluations: Stockfish 19 at
/// depth 20, each position searched on its own as the analysis will do
/// (design §8.1), and turned to White's point of view.
class MoveClassifierTest {

    private final MoveClassifier classifier = new MoveClassifier(5);

    /// The facts of playing `played` in `fen`, the engine preferring `best`.
    private static Facts facts(String fen, String played, String best, Evaluation before, Evaluation after) {
        var position = Fen.parse(fen);
        return new Facts(position.sideToMove(), San.parse(position, played), San.parse(position, best),
                before, after, false, position.legalMoves().size());
    }

    @Nested
    class RealPositions {

        @Test
        void aMissedMateInTwo() {
            // Rd8+ Rxd8 Rxd8# was on the board; h3 still wins, but slowly.
            var move = classifier.classify(facts("r5k1/5ppp/8/8/8/8/3R1PPP/3R2K1 w - - 0 1", "h3", "Rd8+",
                    new MateIn(2), new Centipawns(714)));

            assertThat(move.category()).isEqualTo(MoveCategory.MISSED_MATE);
            assertThat(move.missedMate()).hasValue(2);
            // By win chance alone it would only be an inaccuracy: the mate rule wins.
            assertThat(move.loss()).isCloseTo(0.134590, within(1e-6));
        }

        @Test
        void playingTheMateIsBest() {
            var move = classifier.classify(facts("r5k1/5ppp/8/8/8/8/3R1PPP/3R2K1 w - - 0 1", "Rd8+", "Rd8+",
                    new MateIn(2), new MateIn(1)));

            assertThat(move.category()).isEqualTo(MoveCategory.BEST);
        }

        @Test
        void givingMateKeepsTheMate() {
            var move = classifier.classify(facts("6k1/5ppp/8/8/8/2R5/5PPP/6K1 w - - 0 1", "Rc8#", "Rc8#",
                    new MateIn(1), new Checkmate(Color.WHITE)));

            assertThat(move.category()).isEqualTo(MoveCategory.BEST);
            assertThat(move.loss()).isZero();
        }

        @Test
        void aHangingQueen() {
            // 1.e4 d5 2.Qg4?? Bxg4.
            var move = classifier.classify(facts("rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2",
                    "Qg4", "exd5", new Centipawns(70), new Centipawns(-986)));

            assertThat(move.category()).isEqualTo(MoveCategory.BLUNDER);
            assertThat(move.loss()).isCloseTo(1.076528, within(1e-6));
        }

        @Test
        void theOnlyMoveIsNeverAnError() {
            // In check from the rook on c1, and Kxa2 is the only way out:
            // lost before, lost after, but nothing better was possible.
            var move = classifier.classify(facts("1k6/8/8/8/8/8/r7/K1r5 w - - 0 1", "Kxa2", "Kxa2",
                    new MateIn(-11), new MateIn(-11)));

            assertThat(move.category()).isEqualTo(MoveCategory.BEST);
        }

        @Test
        void threePawnsAreOnlyAnInaccuracyWhenFarAhead() {
            // Rxc8+ keeps +9.14; Rc3 offers a rook trade and lets it drop to
            // +6.05. Three pawns, but White still wins easily.
            var move = classifier.classify(facts("2r3k1/pp3pp1/7p/8/3B4/8/PP3PPP/2R3K1 w - - 0 1", "Rc3", "Rxc8+",
                    new Centipawns(914), new Centipawns(605)));

            assertThat(move.category()).isEqualTo(MoveCategory.INACCURACY);
            assertThat(move.loss()).isCloseTo(0.127801, within(1e-6));
        }
    }

    @Nested
    class Rules {

        private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

        /// White plays 1.a3 from the initial position, the engine wanting
        /// 1.e4, and the position goes from `before` to `after` (centipawns).
        private Facts a3(int before, int after) {
            return facts(START, "a3", "e4", new Centipawns(before), new Centipawns(after));
        }

        @ParameterizedTest(name = "{0} → {1} cp: {2}")
        @CsvSource({
                // Win chances: 0 cp → 0, -50 → -0.0918, -100 → -0.1821,
                // -150 → -0.2693, -200 → -0.3524.
                "0, -50, GOOD",
                "0, -100, INACCURACY",
                "0, -150, MISTAKE",
                "0, -200, BLUNDER",
                // From +10.00 the same 200 cp barely matter.
                "1000, 800, GOOD"})
        void byWinChanceLost(int before, int after, MoveCategory expected) {
            assertThat(classifier.classify(a3(before, after)).category()).isEqualTo(expected);
        }

        @Test
        void asGoodAsTheEngineMoveIsBest() {
            assertThat(classifier.classify(a3(30, 28)).category()).isEqualTo(MoveCategory.BEST);
            assertThat(classifier.classify(a3(30, 60)).category()).isEqualTo(MoveCategory.BEST);
        }

        @Test
        void theEngineMoveIsBestEvenIfTheScoresDisagree() {
            // Two searches of different positions: the second one saw deeper.
            var move = facts(START, "e4", "e4", new Centipawns(30), new Centipawns(-40));
            assertThat(classifier.classify(move).category()).isEqualTo(MoveCategory.BEST);
        }

        @Test
        void aForcedMoveIsBestEvenIfTheScoresDisagree() {
            var move = facts("1k6/8/8/8/8/8/r7/K1r5 w - - 0 1", "Kxa2", "Kxa2",
                    new Centipawns(-600), new Centipawns(-900));
            assertThat(classifier.classify(move).category()).isEqualTo(MoveCategory.BEST);
        }

        @Test
        void aBookMoveIsBook() {
            var move = a3(30, -60);
            var inBook = new Facts(move.mover(), move.played(), move.best(), move.before(), move.after(),
                    true, move.legalMoves());
            assertThat(classifier.classify(inBook).category()).isEqualTo(MoveCategory.BOOK);
        }

        @Test
        void aLongerMateIsNotMissed() {
            var move = facts(START, "a3", "e4", new MateIn(3), new MateIn(6));
            assertThat(classifier.classify(move).category()).isEqualTo(MoveCategory.BEST);
        }

        @Test
        void aMateBeyondTheLimitIsJudgedByWinChance() {
            var move = facts(START, "a3", "e4", new MateIn(6), new Centipawns(714));
            assertThat(classifier.classify(move).category()).isEqualTo(MoveCategory.INACCURACY);
        }

        @Test
        void blackMissesAMateToo() {
            // The mate in two of above, mirrored: Rd1+ Rxd1 Rxd1#, and Black plays h6.
            var position = "3r2k1/3r1ppp/8/8/8/8/5PPP/R5K1 b - - 0 1";
            var move = facts(position, "h6", "Rd1+", new MateIn(-2), new Centipawns(-720));

            var classification = classifier.classify(move);
            assertThat(classification.category()).isEqualTo(MoveCategory.MISSED_MATE);
            assertThat(classification.missedMate()).hasValue(2);
        }

        @Test
        void theLimitIsAtLeastOne() {
            assertThatThrownBy(() -> new MoveClassifier(0)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
