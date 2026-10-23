package com.arquitechthor.kopi.domain.chess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PositionTest {

    private static final Position INITIAL = Position.initial();

    /// The FEN after playing the legal move written as `uci` in `fen`.
    private static String play(String fen, String uci) {
        var position = Fen.parse(fen);
        return position.apply(legalMove(position, uci)).toFen();
    }

    /// The legal move written as `uci`, which must exist.
    private static Move legalMove(Position position, String uci) {
        return position.legalMoves().stream()
                .filter(move -> move.toUci().equals(uci))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No legal move " + uci + " in " + position.toFen()));
    }

    /// The legal moves of `fen`, in UCI.
    private static List<String> legalMoves(String fen) {
        return Fen.parse(fen).legalMoves().stream().map(Move::toUci).toList();
    }

    @Test
    void theHalfmoveClockCannotBeNegative() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Position(
                INITIAL.board(), INITIAL.sideToMove(), INITIAL.castlingRights(), Optional.empty(), -1, 1));
    }

    @Test
    void theFullmoveNumberStartsAtOne() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Position(
                INITIAL.board(), INITIAL.sideToMove(), INITIAL.castlingRights(), Optional.empty(), 0, 0));
    }

    @Test
    void noComponentMayBeNull() {
        assertThatNullPointerException().isThrownBy(() -> new Position(
                INITIAL.board(), INITIAL.sideToMove(), INITIAL.castlingRights(), null, 0, 1));
    }

    @Test
    void writesItselfAsFen() {
        assertThat(INITIAL.toFen()).isEqualTo(Fen.INITIAL);
    }

    @Nested
    class Apply {

        @Test
        void movesThePieceAndPassesTheTurn() {
            // Given
            var fen = Fen.INITIAL;

            // When
            var after = play(fen, "g1f3");

            // Then
            assertThat(after).isEqualTo("rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1");
        }

        @Test
        void leavesTheOriginalPositionUntouched() {
            var position = Position.initial();

            position.apply(legalMove(position, "e2e4"));

            assertThat(position.toFen()).isEqualTo(Fen.INITIAL);
        }

        @Test
        void theFullmoveNumberIncreasesAfterBlacksMove() {
            var fen = "rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1";

            var after = play(fen, "g8f6");

            assertThat(after).isEqualTo("rnbqkb1r/pppppppp/5n2/8/8/5N2/PPPPPPPP/RNBQKB1R w KQkq - 2 2");
        }

        @Test
        void aDoublePushLeavesAnEnPassantTarget() {
            var after = play(Fen.INITIAL, "e2e4");

            assertThat(after).isEqualTo("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1");
        }

        @Test
        void theEnPassantTargetLastsOnlyOneMove() {
            var fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";

            var after = play(fen, "g8f6");

            assertThat(after).isEqualTo("rnbqkb1r/pppppppp/5n2/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 1 2");
        }

        @Test
        void aCaptureRemovesThePieceAndResetsTheHalfmoveClock() {
            var fen = "4k3/8/4r3/8/3N4/8/8/4K3 w - - 7 30";

            var after = play(fen, "d4e6");

            assertThat(after).isEqualTo("4k3/8/4N3/8/8/8/8/4K3 b - - 0 30");
        }

        @Test
        void enPassantRemovesThePawnBesideTheDestination() {
            var fen = "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2";

            var after = play(fen, "e5d6");

            assertThat(after).isEqualTo("4k3/8/3P4/8/8/8/8/4K3 b - - 0 2");
        }

        @Test
        void blackCapturesEnPassantToo() {
            var fen = "4k3/8/8/8/3Pp3/8/8/4K3 b - d3 0 2";

            var after = play(fen, "e4d3");

            assertThat(after).isEqualTo("4k3/8/8/8/8/3p4/8/4K3 w - - 0 3");
        }

        @Test
        void aPromotionReplacesThePawnWithTheChosenPiece() {
            var fen = "4k3/1P6/8/8/8/8/8/4K3 w - - 0 1";

            assertThat(play(fen, "b7b8q")).isEqualTo("1Q2k3/8/8/8/8/8/8/4K3 b - - 0 1");
            assertThat(play(fen, "b7b8n")).isEqualTo("1N2k3/8/8/8/8/8/8/4K3 b - - 0 1");
        }

        @Test
        void aPromotionMayCapture() {
            var fen = "r3k3/1P6/8/8/8/8/8/4K3 w - - 0 1";

            var after = play(fen, "b7a8r");

            assertThat(after).isEqualTo("R3k3/8/8/8/8/8/8/4K3 b - - 0 1");
        }

        @Test
        void castlingMovesTheRookOverTheKing() {
            var fen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 3 10";

            assertThat(play(fen, "e1g1")).isEqualTo("r3k2r/8/8/8/8/8/8/R4RK1 b kq - 4 10");
            assertThat(play(fen, "e1c1")).isEqualTo("r3k2r/8/8/8/8/8/8/2KR3R b kq - 4 10");
        }

        @Test
        void blackCastlesOnTheEighthRank() {
            var fen = "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 3 10";

            assertThat(play(fen, "e8g8")).isEqualTo("r4rk1/8/8/8/8/8/8/R3K2R w KQ - 4 11");
            assertThat(play(fen, "e8c8")).isEqualTo("2kr3r/8/8/8/8/8/8/R3K2R w KQ - 4 11");
        }

        @Test
        void aKingMoveLosesBothCastlingRights() {
            var fen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1";

            var after = play(fen, "e1e2");

            assertThat(after).isEqualTo("r3k2r/8/8/8/8/8/4K3/R6R b kq - 1 1");
        }

        @Test
        void aRookMoveLosesTheCastlingOnItsSide() {
            var fen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1";

            assertThat(play(fen, "h1h4")).isEqualTo("r3k2r/8/8/8/7R/8/8/R3K3 b Qkq - 1 1");
            assertThat(play(fen, "a1a4")).isEqualTo("r3k2r/8/8/8/R7/8/8/4K2R b Kkq - 1 1");
        }

        @Test
        void capturingARookOnItsCornerLosesTheOpponentsCastling() {
            // The classic trap: the black rook never moved, it was captured.
            var fen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1";

            var after = play(fen, "h1h8");

            assertThat(after).isEqualTo("r3k2R/8/8/8/8/8/8/R3K3 b Qq - 0 1");
        }

        @Test
        void refusesAMoveFromAnEmptySquare() {
            var move = new Move(Square.parse("e4"), Square.parse("e5"), MoveKind.QUIET);

            assertThatIllegalArgumentException().isThrownBy(() -> INITIAL.apply(move));
        }
    }

    @Nested
    class LegalMoves {

        @Test
        void theInitialPositionHasTwentyMoves() {
            assertThat(INITIAL.legalMoves()).hasSize(20);
        }

        @Test
        void aPinnedPieceCannotLeaveTheLineOfThePin() {
            // The bishop on e2 shields its king from the rook on e7.
            var fen = "4k3/4r3/8/8/8/8/4B3/4K3 w - - 0 1";

            assertThat(legalMoves(fen)).noneMatch(move -> move.startsWith("e2"));
        }

        @Test
        void aPinnedRookMayStillSlideAlongThePin() {
            var fen = "4k3/4r3/8/8/8/8/4R3/4K3 w - - 0 1";

            assertThat(legalMoves(fen)).filteredOn(move -> move.startsWith("e2"))
                    .containsExactlyInAnyOrder("e2e3", "e2e4", "e2e5", "e2e6", "e2e7");
        }

        @Test
        void theKingCannotStepIntoCheck() {
            // The rook on d8 controls the whole d-file.
            var fen = "3rk3/8/8/8/8/8/8/4K3 w - - 0 1";

            assertThat(legalMoves(fen)).containsExactlyInAnyOrder("e1e2", "e1f1", "e1f2");
        }

        @Test
        void inCheckOnlyMovesThatEndTheCheckAreLegal() {
            // Castling is impossible in check; the a1 rook cannot help.
            var fen = "4k3/8/8/8/8/8/4r3/R3K3 w Q - 0 1";

            assertThat(legalMoves(fen)).containsExactlyInAnyOrder("e1d1", "e1e2", "e1f1");
        }

        @Test
        void enPassantCannotExposeTheKingAlongTheRank() {
            // Capturing on d6 would empty both d5 and e5, opening the fifth
            // rank between the rook on h5 and the king on a5.
            var fen = "4k3/8/8/K2pP2r/8/8/8/8 w - d6 0 2";

            assertThat(MoveGenerator.pseudoLegalMoves(Fen.parse(fen))).extracting(Move::toUci).contains("e5d6");
            assertThat(legalMoves(fen)).doesNotContain("e5d6").contains("e5e6");
        }

        @Test
        void checkmateLeavesNoLegalMoves() {
            // Fool's mate.
            var position = Fen.parse("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3");

            assertThat(position.isInCheck()).isTrue();
            assertThat(position.legalMoves()).isEmpty();
        }

        @Test
        void stalemateLeavesNoLegalMovesWithoutCheck() {
            var position = Fen.parse("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");

            assertThat(position.isInCheck()).isFalse();
            assertThat(position.legalMoves()).isEmpty();
        }

        @Test
        void theInitialPositionIsNotCheck() {
            assertThat(INITIAL.isInCheck()).isFalse();
        }
    }
}
