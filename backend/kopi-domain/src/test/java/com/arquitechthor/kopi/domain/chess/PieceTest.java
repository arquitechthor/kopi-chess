package com.arquitechthor.kopi.domain.chess;

import static com.arquitechthor.kopi.domain.chess.Color.BLACK;
import static com.arquitechthor.kopi.domain.chess.Color.WHITE;
import static com.arquitechthor.kopi.domain.chess.PieceType.KING;
import static com.arquitechthor.kopi.domain.chess.PieceType.KNIGHT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class PieceTest {

    @Test
    void colorsAreOpposites() {
        assertThat(WHITE.opposite()).isEqualTo(BLACK);
        assertThat(BLACK.opposite()).isEqualTo(WHITE);
    }

    @Test
    void whitePiecesAreUpperCaseInFen() {
        assertThat(new Piece(WHITE, KNIGHT).fenChar()).isEqualTo('N');
        assertThat(new Piece(BLACK, KNIGHT).fenChar()).isEqualTo('n');
        assertThat(new Piece(WHITE, KING).fenChar()).isEqualTo('K');
    }

    @ParameterizedTest
    @ValueSource(chars = {'P', 'N', 'B', 'R', 'Q', 'K', 'p', 'n', 'b', 'r', 'q', 'k'})
    void fenCharactersRoundTrip(char fenChar) {
        assertThat(Piece.fromFenChar(fenChar).fenChar()).isEqualTo(fenChar);
    }

    @ParameterizedTest
    @ValueSource(chars = {'x', '1', ' ', '/', 'A'})
    void rejectsCharactersThatAreNotPieces(char notAPiece) {
        assertThatIllegalArgumentException().isThrownBy(() -> Piece.fromFenChar(notAPiece));
    }

    @Test
    void piecesAreEqualByValue() {
        assertThat(new Piece(WHITE, KNIGHT)).isEqualTo(Piece.fromFenChar('N'));
    }

    @ParameterizedTest
    @EnumSource(value = PieceType.class, names = {"BISHOP", "ROOK", "QUEEN"})
    void bishopsRooksAndQueensAreSliders(PieceType type) {
        assertThat(type.isSlider()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = PieceType.class, names = {"PAWN", "KNIGHT", "KING"})
    void pawnsKnightsAndKingsAreNotSliders(PieceType type) {
        assertThat(type.isSlider()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = PieceType.class, names = {"PAWN", "KING"})
    void pawnsCannotPromoteToPawnsOrKings(PieceType type) {
        assertThat(type.isPromotionTarget()).isFalse();
    }
}
