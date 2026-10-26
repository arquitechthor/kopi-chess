package com.arquitechthor.kopi.domain.chess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.domain.chess.InvalidPgnException.Problem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/// The games in `src/test/resources/pgn/` are famous historical games
/// written the way Lichess and Chess.com export them (clock and evaluation
/// comments, their tag sets): real formats, without real players' data.
class PgnTest {

    private static String resource(String name) {
        try (var stream = Objects.requireNonNull(PgnTest.class.getResourceAsStream("/pgn/" + name), name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> san(PgnGame game) {
        var position = game.initialPosition();
        var san = new ArrayList<String>();
        for (var move : game.moves()) {
            san.add(San.format(position, move));
            position = position.apply(move);
        }
        return san;
    }

    @Nested
    class RealExports {

        @Test
        void aLichessExportWithEvaluationsCommentsAndVariations() {
            var game = Pgn.parse(resource("lichess-opera-game.pgn"));

            assertThat(game.tag("White")).hasValue("Morphy, Paul");
            assertThat(game.tag("ECO")).hasValue("C41");
            assertThat(game.tags()).hasSize(17).containsKeys("Event", "Site", "Termination");
            assertThat(game.moves()).hasSize(33);
            assertThat(san(game)).startsWith("e4", "e5", "Nf3", "d6", "d4", "Bg4")
                    .endsWith("Qb8+", "Nxb8", "Rd8#");
            assertThat(game.result()).isEqualTo(PgnGame.Result.WHITE_WINS);
            // The rules agree with the PGN: the last move mates.
            assertThat(Rules.isCheckmate(game.finalPosition())).isTrue();
        }

        @Test
        void aChessComExportWithClocks() {
            var game = Pgn.parse(resource("chesscom-immortal-game.pgn"));

            assertThat(game.tag("Link")).hasValue("https://www.chess.com/games/view/immortal-game");
            assertThat(game.moves()).hasSize(45);
            assertThat(san(game).getLast()).isEqualTo("Be7#");
            assertThat(Rules.isCheckmate(game.finalPosition())).isTrue();
            // Chess.com's own record of the final position, which we reach too.
            assertThat(game.finalPosition().toFen()).startsWith(game.tag("CurrentPosition").orElseThrow());
        }

        @Test
        void aDraw() {
            var game = Pgn.parse(resource("chesscom-bongcloud-draw.pgn"));

            assertThat(san(game)).containsExactly("e4", "e5", "Ke2", "Ke7", "Ke1", "Ke8", "Ke2", "Ke7", "Ke1", "Ke8");
            assertThat(game.result()).isEqualTo(PgnGame.Result.DRAW);
        }

        @Test
        void aGameFromAPositionWithBlackToMove() {
            var game = Pgn.parse(resource("from-position.pgn"));

            assertThat(game.initialPosition().sideToMove()).isEqualTo(Color.BLACK);
            assertThat(san(game)).containsExactly("Rd1+", "Rxd1", "Rxd1#");
            assertThat(game.result()).isEqualTo(PgnGame.Result.BLACK_WINS);
            assertThat(Rules.isCheckmate(game.finalPosition())).isTrue();
        }

        @Test
        void anIllegalMoveIsReportedExactlyWhereItIs() {
            assertThatThrownBy(() -> Pgn.parse(resource("illegal-move.pgn")))
                    .isInstanceOfSatisfying(InvalidPgnException.class, e -> {
                        assertThat(e.problem()).isEqualTo(Problem.ILLEGAL_MOVE);
                        assertThat(e.text()).isEqualTo("Nxe5");
                        assertThat(e.moveNumber()).hasValue(9);
                        assertThat(e.side()).hasValue(Color.BLACK);
                        assertThat(e.line()).isEqualTo(7);
                    });
        }
    }

    @Nested
    class Tolerance {

        @Test
        void movetextAloneIsEnough() {
            var game = Pgn.parse("1. e4 e5 2. Nf3 *");

            assertThat(game.tags()).isEmpty();
            assertThat(game.moves()).hasSize(3);
            assertThat(game.result()).isEqualTo(PgnGame.Result.UNKNOWN);
        }

        @Test
        void numbersStuckToMovesAndBlacksContinuationNumbers() {
            var game = Pgn.parse("1.e4 1...e5 2.Nf3 2... Nc6 *");

            assertThat(san(game)).containsExactly("e4", "e5", "Nf3", "Nc6");
        }

        @Test
        void nestedVariationsWithCommentsInsideAreSkipped() {
            var game = Pgn.parse("1. e4 (1. d4 d5 (1... Nf6 { a comment with ) and ( }) 2. c4) e5 *");

            assertThat(san(game)).containsExactly("e4", "e5");
        }

        @Test
        void nagsAnnotationsLineCommentsAndEscapesAreSkipped() {
            var game = Pgn.parse("""
                    % a line for programs only
                    1. e4 $1 e5 !? ; a comment to the end of the line ( not a variation
                    2. Nf3!! Nc6?? 3. Bb5 $2 *""");

            assertThat(san(game)).containsExactly("e4", "e5", "Nf3", "Nc6", "Bb5");
        }

        @Test
        void castlingWithZeros() {
            var game = Pgn.parse("1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. 0-0 *");

            assertThat(san(game).getLast()).isEqualTo("O-O");
        }

        @Test
        void withoutATerminationMarkerTheResultTagCounts() {
            var game = Pgn.parse("[Result \"0-1\"]\n\n1. f3 e5 2. g4 Qh4#");

            assertThat(game.result()).isEqualTo(PgnGame.Result.BLACK_WINS);
        }

        @Test
        void escapedQuotesInATag() {
            var game = Pgn.parse("[Event \"The \\\"Opera\\\" game\"]\n\n1. e4 *");

            assertThat(game.tag("Event")).hasValue("The \"Opera\" game");
        }

        @Test
        void aByteOrderMarkAndWindowsLineEndings() {
            var game = Pgn.parse("﻿[Event \"x\"]\r\n\r\n1. e4 e5 *\r\n");

            assertThat(game.tag("Event")).hasValue("x");
            assertThat(game.moves()).hasSize(2);
        }
    }

    @Nested
    class Problems {

        private static InvalidPgnException problem(String pgn) {
            try {
                Pgn.parse(pgn);
            } catch (InvalidPgnException e) {
                return e;
            }
            throw new AssertionError("Read without complaint: " + pgn);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "''                                        | EMPTY",
                "{ only a comment }                        | EMPTY",
                "[Event \"x\"                              | MALFORMED_TAG",
                "[Event x]                                 | MALFORMED_TAG",
                "[FEN \"not a position\"] 1. e4 *          | INVALID_FEN",
                "1. e4 { never closed                      | UNTERMINATED_COMMENT",
                "1. e4 (1. d4 d5                           | UNBALANCED_VARIATION",
                "1. e4 ) e5                                | UNBALANCED_VARIATION",
                "1. e4 e5 2. Ke9                           | MALFORMED_MOVE",
                "1. e4 e5 2. e5                            | ILLEGAL_MOVE",
                "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Qe3 *    | ILLEGAL_MOVE",
                "1. e4 * [Event \"second\"] 1. d4 *        | MORE_THAN_ONE_GAME",
                "1. e4 e5 1-0 1. d4                        | MORE_THAN_ONE_GAME"})
        void eachProblemHasItsCode(String pgn, Problem expected) {
            assertThat(problem(pgn).problem()).isEqualTo(expected);
        }

        @Test
        void anAmbiguousMoveSaysWhichMove() {
            // Knights on c3 and g1 can both reach e2.
            var e = problem("1. e4 e5 2. Nc3 Nc6 3. Ne2 *");

            assertThat(e.problem()).isEqualTo(Problem.AMBIGUOUS_MOVE);
            assertThat(e.text()).isEqualTo("Ne2");
            assertThat(e.moveNumber()).hasValue(3);
            assertThat(e.side()).hasValue(Color.WHITE);
        }

        @Test
        void aWhiteMoveFromAPositionKeepsItsNumber() {
            // The FEN says move 40: errors count from there.
            var e = problem("[FEN \"4k3/8/8/8/8/8/8/4K3 w - - 0 40\"]\n\n40. Kd1 Kd7 41. Ke9 *");

            assertThat(e.problem()).isEqualTo(Problem.MALFORMED_MOVE);
            assertThat(e.moveNumber()).hasValue(41);
            assertThat(e.side()).hasValue(Color.WHITE);
            assertThat(e.line()).isEqualTo(3);
        }

        @Test
        void aTagProblemHasNoMove() {
            var e = problem("[Event \"x\"\n1. e4 *");

            assertThat(e.moveNumber()).isEmpty();
            assertThat(e.side()).isEmpty();
            assertThat(e.line()).isEqualTo(1);
        }
    }
}
