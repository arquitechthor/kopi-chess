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
