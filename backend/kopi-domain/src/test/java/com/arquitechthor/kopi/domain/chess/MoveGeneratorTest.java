package com.arquitechthor.kopi.domain.chess;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MoveGeneratorTest {

    /// The pseudo-legal moves of the piece on `from`, in UCI.
    private static List<String> movesFrom(String fen, String from) {
        return MoveGenerator.pseudoLegalMoves(Fen.parse(fen)).stream()
                .filter(move -> move.from().equals(Square.parse(from)))
                .map(Move::toUci)
                .toList();
    }

    /// The pseudo-legal move from `from` to `to`, which must exist.
    private static Move moveBetween(String fen, String from, String to) {
        return MoveGenerator.pseudoLegalMoves(Fen.parse(fen)).stream()
                .filter(move -> move.from().equals(Square.parse(from)) && move.to().equals(Square.parse(to)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No move " + from + to + " in " + fen));
    }

    @Nested
    class Knight {

        @Test
        void reachesEightSquaresFromTheCentre() {
            // Given
            var fen = "4k3/8/8/8/3N4/8/8/4K3 w - - 0 1";

            // When
            var moves = movesFrom(fen, "d4");

            // Then
            assertThat(moves).containsExactlyInAnyOrder(
                    "d4b3", "d4b5", "d4c2", "d4c6", "d4e2", "d4e6", "d4f3", "d4f5");
        }

        @Test
        void reachesOnlyTwoSquaresFromACorner() {
            var fen = "4k3/8/8/8/8/8/8/N3K3 w - - 0 1";

            var moves = movesFrom(fen, "a1");

            assertThat(moves).containsExactlyInAnyOrder("a1b3", "a1c2");
        }

        @Test
        void doesNotWrapAroundTheEdgeOfTheBoard() {
            // A knight on h4 must not "reappear" on the a-file.
            var fen = "4k3/8/8/8/7N/8/8/4K3 w - - 0 1";

            var moves = movesFrom(fen, "h4");

            assertThat(moves).containsExactlyInAnyOrder("h4g2", "h4f3", "h4f5", "h4g6");
        }

        @Test
        void jumpsOverPiecesButCannotLandOnItsOwn() {
            // The knight on d4 is boxed in by its own pawns, which it jumps
            // over, and two of its targets hold white pieces.
            var fen = "4k3/8/8/2PPP3/2PNP3/2PPPB2/4R3/4K3 w - - 0 1";

            var moves = movesFrom(fen, "d4");

            assertThat(moves).containsExactlyInAnyOrder("d4b3", "d4b5", "d4c2", "d4c6", "d4e6", "d4f5");
        }

        @Test
        void capturesAnOpponentsPiece() {
            var fen = "4k3/8/4r3/8/3N4/8/8/4K3 w - - 0 1";

            var capture = moveBetween(fen, "d4", "e6");
            var quiet = moveBetween(fen, "d4", "c6");

            assertThat(capture.kind()).isEqualTo(MoveKind.CAPTURE);
            assertThat(quiet.kind()).isEqualTo(MoveKind.QUIET);
        }

        @Test
        void blackKnightsMoveWhenBlackIsToMove() {
            var fen = "4k3/8/8/8/8/8/8/1n2K3 b - - 0 1";

            var moves = movesFrom(fen, "b1");

            assertThat(moves).containsExactlyInAnyOrder("b1a3", "b1c3", "b1d2");
        }
    }

    @Nested
    class King {

        @Test
        void reachesEightSquaresFromTheCentre() {
            var fen = "4k3/8/8/8/3K4/8/8/8 w - - 0 1";

            var moves = movesFrom(fen, "d4");

            assertThat(moves).containsExactlyInAnyOrder(
                    "d4c3", "d4c4", "d4c5", "d4d3", "d4d5", "d4e3", "d4e4", "d4e5");
        }

        @Test
        void reachesOnlyThreeSquaresFromACorner() {
            var fen = "4k3/8/8/8/8/8/8/7K w - - 0 1";

            var moves = movesFrom(fen, "h1");

            assertThat(moves).containsExactlyInAnyOrder("h1g1", "h1g2", "h1h2");
        }

        @Test
        void cannotLandOnItsOwnPieces() {
            var fen = "4k3/8/8/8/8/8/3PPP2/3QK3 w - - 0 1";

            var moves = movesFrom(fen, "e1");

            assertThat(moves).containsExactly("e1f1");
        }

        @Test
        void capturesAnOpponentsPiece() {
            var fen = "4k3/8/8/8/8/8/4p3/4K3 w - - 0 1";

            var capture = moveBetween(fen, "e1", "e2");

            assertThat(capture.kind()).isEqualTo(MoveKind.CAPTURE);
        }

        @Test
        void doesNotCastleYet() {
            // Castling has its own step (rights, empty and unattacked
            // squares); for now the king only takes single steps.
            var fen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1";

            var moves = movesFrom(fen, "e1");

            assertThat(moves).containsExactlyInAnyOrder("e1d1", "e1d2", "e1e2", "e1f1", "e1f2");
        }
    }

    @Nested
    class Bishop {

        @Test
        void slidesAlongBothDiagonalsOnAnEmptyBoard() {
            // Given
            var fen = "4k3/8/8/8/3B4/8/8/4K3 w - - 0 1";

            // When
            var moves = movesFrom(fen, "d4");

            // Then
            assertThat(moves).containsExactlyInAnyOrder(
                    "d4c3", "d4b2", "d4a1",
                    "d4e5", "d4f6", "d4g7", "d4h8",
                    "d4c5", "d4b6", "d4a7",
                    "d4e3", "d4f2", "d4g1");
        }

        @Test
        void stopsBeforeItsOwnPieceAndOnAnOpponentsPiece() {
            // The white pawn on f6 blocks the ray before it; the black knight
            // on b2 is captured, and a1 behind it is out of reach.
            var fen = "4k3/8/5P2/8/3B4/8/1n6/4K3 w - - 0 1";

            var moves = movesFrom(fen, "d4");

            assertThat(moves).containsExactlyInAnyOrder(
                    "d4e5", "d4c3", "d4b2", "d4c5", "d4b6", "d4a7", "d4e3", "d4f2", "d4g1");
            assertThat(moveBetween(fen, "d4", "b2").kind()).isEqualTo(MoveKind.CAPTURE);
        }
    }

    @Nested
    class Rook {

        @Test
        void slidesAlongItsFileAndRankUntilItsOwnKing() {
            var fen = "4k3/8/8/8/8/8/8/R5K1 w - - 0 1";

            var moves = movesFrom(fen, "a1");

            assertThat(moves).containsExactlyInAnyOrder(
                    "a1a2", "a1a3", "a1a4", "a1a5", "a1a6", "a1a7", "a1a8",
                    "a1b1", "a1c1", "a1d1", "a1e1", "a1f1");
        }

        @Test
        void doesNotWrapAroundTheEdgeOfTheBoard() {
            // Sliding right from h4 must stop at once, not continue on a5.
            var fen = "4k3/8/8/8/7R/8/8/4K3 w - - 0 1";

            var moves = movesFrom(fen, "h4");

            assertThat(moves).containsExactlyInAnyOrder(
                    "h4h5", "h4h6", "h4h7", "h4h8",
                    "h4h3", "h4h2", "h4h1",
                    "h4g4", "h4f4", "h4e4", "h4d4", "h4c4", "h4b4", "h4a4");
        }

        @Test
        void capturesTheFirstOpponentsPieceOnItsWay() {
            var fen = "4k3/8/8/8/R2pp3/8/8/4K3 w - - 0 1";

            var moves = movesFrom(fen, "a4");

            assertThat(moves).contains("a4b4", "a4c4", "a4d4").doesNotContain("a4e4");
            assertThat(moveBetween(fen, "a4", "d4").kind()).isEqualTo(MoveKind.CAPTURE);
            assertThat(moveBetween(fen, "a4", "c4").kind()).isEqualTo(MoveKind.QUIET);
        }
    }

    @Nested
    class Queen {

        @Test
        void combinesTheRookAndTheBishopOnAnEmptyBoard() {
            var fen = "4k3/8/8/8/3Q4/8/8/7K w - - 0 1";

            var moves = movesFrom(fen, "d4");

            assertThat(moves).hasSize(27).contains("d4d8", "d4a4", "d4h8", "d4a1", "d4g1", "d4a7");
        }

        @Test
        void stopsOnAnOpponentsPiece() {
            var fen = "4k3/8/8/8/3Q1p2/8/8/7K w - - 0 1";

            var moves = movesFrom(fen, "d4");

            assertThat(moves).contains("d4e4", "d4f4").doesNotContain("d4g4", "d4h4");
        }
    }

    @Nested
    class Pawn {

        @Test
        void advancesOneOrTwoSquaresFromItsStartingRank() {
            // Given
            var fen = Fen.INITIAL;

            // When
            var single = moveBetween(fen, "e2", "e3");
            var twoSquares = moveBetween(fen, "e2", "e4");

            // Then
            assertThat(single.kind()).isEqualTo(MoveKind.QUIET);
            assertThat(twoSquares.kind()).isEqualTo(MoveKind.DOUBLE_PAWN_PUSH);
            assertThat(movesFrom(fen, "e2")).hasSize(2);
        }

        @Test
        void blackPawnsAdvanceDownTheBoard() {
            var fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";

            var moves = movesFrom(fen, "d7");

            assertThat(moves).containsExactlyInAnyOrder("d7d6", "d7d5");
            assertThat(moveBetween(fen, "d7", "d5").kind()).isEqualTo(MoveKind.DOUBLE_PAWN_PUSH);
        }

        @Test
        void advancesOnlyOneSquareAwayFromItsStartingRank() {
            var fen = "4k3/8/8/8/8/4P3/8/4K3 w - - 0 1";

            var moves = movesFrom(fen, "e3");

            assertThat(moves).containsExactly("e3e4");
        }

        @Test
        void cannotAdvanceIntoAnyPiece() {
            // A piece right in front blocks both pushes, and the pawn does
            // not capture straight ahead.
            var fen = "4k3/8/8/8/8/4n3/4P3/4K3 w - - 0 1";

            var moves = movesFrom(fen, "e2");

            assertThat(moves).isEmpty();
        }

        @Test
        void cannotJumpOverAPieceWithTheDoublePush() {
            var fen = "4k3/8/8/8/4N3/8/4P3/4K3 w - - 0 1";

            var moves = movesFrom(fen, "e2");

            assertThat(moves).containsExactly("e2e3");
        }

        @Test
        void capturesDiagonallyForwardButNotItsOwnPieces() {
            // Black pieces on d3 and f3; in the second position, f3 holds a
            // white knight instead.
            var fen = "4k3/8/8/8/8/3n1b2/4P3/4K3 w - - 0 1";
            var withOwnPiece = "4k3/8/8/8/8/3n1N2/4P3/4K3 w - - 0 1";

            var moves = movesFrom(fen, "e2");
            var movesNextToOwnPiece = movesFrom(withOwnPiece, "e2");

            assertThat(moves).containsExactlyInAnyOrder("e2e3", "e2e4", "e2d3", "e2f3");
            assertThat(moveBetween(fen, "e2", "d3").kind()).isEqualTo(MoveKind.CAPTURE);
            assertThat(movesNextToOwnPiece).containsExactlyInAnyOrder("e2e3", "e2e4", "e2d3");
        }

        @Test
        void doesNotCaptureAroundTheEdgeOfTheBoard() {
            // From h2, "forward and to the right" is off the board, not a3.
            var fen = "4k3/8/8/8/8/n5n1/7P/4K3 w - - 0 1";

            var moves = movesFrom(fen, "h2");

            assertThat(moves).containsExactlyInAnyOrder("h2h3", "h2h4", "h2g3");
        }

        @Test
        void promotesToAnyOfFourPiecesOnTheLastRank() {
            var fen = "4k3/1P6/8/8/8/8/8/4K3 w - - 0 1";

            var moves = movesFrom(fen, "b7");

            assertThat(moves).containsExactlyInAnyOrder("b7b8q", "b7b8r", "b7b8b", "b7b8n");
            assertThat(moveBetween(fen, "b7", "b8").kind())
                    .isInstanceOf(MoveKind.Promotion.class)
                    .isNotEqualTo(new MoveKind.Promotion(PieceType.QUEEN, true));
        }

        @Test
        void promotesAlsoWhenCapturing() {
            // The push to e8 is blocked by the king; the capture on d8 is not.
            var fen = "3rk3/4P3/8/8/8/8/8/4K3 w - - 0 1";

            var moves = MoveGenerator.pseudoLegalMoves(Fen.parse(fen)).stream()
                    .filter(move -> move.from().equals(Square.parse("e7")))
                    .toList();

            assertThat(moves).extracting(Move::kind).containsExactlyInAnyOrder(
                    new MoveKind.Promotion(PieceType.QUEEN, true),
                    new MoveKind.Promotion(PieceType.ROOK, true),
                    new MoveKind.Promotion(PieceType.BISHOP, true),
                    new MoveKind.Promotion(PieceType.KNIGHT, true));
            assertThat(moves).allSatisfy(move -> assertThat(move.to()).isEqualTo(Square.parse("d8")));
        }

        @Test
        void blackPawnsPromoteOnTheFirstRank() {
            var fen = "4k3/8/8/8/8/8/6p1/4K3 b - - 0 1";

            var moves = movesFrom(fen, "g2");

            assertThat(moves).containsExactlyInAnyOrder("g2g1q", "g2g1r", "g2g1b", "g2g1n");
        }

        @Test
        void capturesEnPassantOnTheTargetSquare() {
            // Black has just played d7-d5 next to the white pawn on e5.
            var fen = "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2";

            var moves = movesFrom(fen, "e5");

            assertThat(moves).containsExactlyInAnyOrder("e5e6", "e5d6");
            assertThat(moveBetween(fen, "e5", "d6").kind()).isEqualTo(MoveKind.EN_PASSANT);
        }

        @Test
        void blackCapturesEnPassantToo() {
            var fen = "4k3/8/8/8/3Pp3/8/8/4K3 b - d3 0 1";

            var capture = moveBetween(fen, "e4", "d3");

            assertThat(capture.kind()).isEqualTo(MoveKind.EN_PASSANT);
        }

        @Test
        void cannotCaptureEnPassantWithoutATarget() {
            // Same pawns, but d7-d5 was not the last move.
            var fen = "4k3/8/8/3pP3/8/8/8/4K3 w - - 0 2";

            var moves = movesFrom(fen, "e5");

            assertThat(moves).containsExactly("e5e6");
        }
    }

    @Test
    void theInitialPositionHasTwentyMoves() {
        // Sixteen pawn moves and four knight moves: perft(1) of the start.
        var moves = MoveGenerator.pseudoLegalMoves(Position.initial());

        assertThat(moves).hasSize(20);
    }

    @Test
    void slidersAreBlockedInTheInitialPosition() {
        var initial = Fen.INITIAL;

        for (var square : List.of("a1", "c1", "d1", "f1", "h1")) {
            assertThat(movesFrom(initial, square)).as("moves from %s", square).isEmpty();
        }
    }

    @Test
    void onlyThePiecesOfTheSideToMoveMove() {
        var fen = "n3k3/8/8/8/8/8/8/N3K3 w - - 0 1";

        var moves = MoveGenerator.pseudoLegalMoves(Fen.parse(fen));

        assertThat(moves).allSatisfy(move -> assertThat(move.from().rank()).isZero());
    }

    @Test
    void inTheInitialPositionOnlyTheKnightsCanJump() {
        var moves = MoveGenerator.pseudoLegalMoves(Position.initial()).stream()
                .map(Move::toUci)
                .toList();

        assertThat(moves).contains("b1a3", "b1c3", "g1f3", "g1h3").doesNotContain("e1e2", "e1d1", "e1f1");
    }
}
