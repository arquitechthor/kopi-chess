package com.arquitechthor.kopi.domain.chess;

import static com.arquitechthor.kopi.domain.chess.Color.BLACK;
import static com.arquitechthor.kopi.domain.chess.Color.WHITE;
import static com.arquitechthor.kopi.domain.chess.PieceType.KING;
import static com.arquitechthor.kopi.domain.chess.PieceType.KNIGHT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BoardTest {

    private static final Square E4 = Square.parse("e4");
    private static final Piece WHITE_KNIGHT = new Piece(WHITE, KNIGHT);

    @Test
    void anEmptyBoardHasNoPieces() {
        assertThat(Board.empty().pieces()).isEmpty();
        assertThat(Board.empty().pieceAt(E4)).isEmpty();
    }

    @Test
    void changingABoardReturnsANewOneAndLeavesTheOriginalAlone() {
        var empty = Board.empty();
        var withKnight = empty.withPiece(E4, WHITE_KNIGHT);
        var emptyAgain = withKnight.withoutPiece(E4);

        assertThat(empty.pieceAt(E4)).isEmpty();
        assertThat(withKnight.pieceAt(E4)).contains(WHITE_KNIGHT);
        assertThat(emptyAgain).isEqualTo(empty).isNotSameAs(empty);
    }

    @Test
    void listsPiecesFromA1ToH8() {
        var board = Board.of(Map.of(
                Square.parse("h8"), new Piece(BLACK, KING),
                Square.parse("a1"), new Piece(WHITE, KING),
                E4, WHITE_KNIGHT));

        assertThat(board.pieces().sequencedKeySet()).extracting(Square::toString).containsExactly("a1", "e4", "h8");
        assertThat(board.pieces().firstEntry().getValue()).isEqualTo(new Piece(WHITE, KING));
    }

    @Test
    void the0x88TrickDetectsEveryWayOffTheBoard() {
        int e4 = Board.cellOf(E4);
        assertThat(Board.isOnBoard(e4)).isTrue();
        assertThat(Board.squareOf(e4)).isEqualTo(E4);

        int h4 = Board.cellOf(Square.parse("h4"));
        int a1 = Board.cellOf(Square.parse("a1"));
        int h8 = Board.cellOf(Square.parse("h8"));
        assertThat(Board.isOnBoard(h4 + 1)).as("right of h4").isFalse();
        assertThat(Board.isOnBoard(a1 - 1)).as("left of a1").isFalse();
        assertThat(Board.isOnBoard(a1 - 16)).as("below a1").isFalse();
        assertThat(Board.isOnBoard(h8 + 16)).as("above h8").isFalse();
        assertThat(Board.isOnBoard(h8 + 17)).as("diagonally past h8").isFalse();
    }

    @Test
    void everySquareMapsToAnOnBoardCellAndBack() {
        assertThat(Square.ALL).allSatisfy(square -> {
            assertThat(Board.isOnBoard(Board.cellOf(square))).isTrue();
            assertThat(Board.squareOf(Board.cellOf(square))).isEqualTo(square);
        });
    }

    @Test
    void drawsADiagram() {
        assertThat(Position.initial().board()).hasToString("""
                8 r n b q k b n r
                7 p p p p p p p p
                6 . . . . . . . .
                5 . . . . . . . .
                4 . . . . . . . .
                3 . . . . . . . .
                2 P P P P P P P P
                1 R N B Q K B N R
                  a b c d e f g h""");
    }
}
