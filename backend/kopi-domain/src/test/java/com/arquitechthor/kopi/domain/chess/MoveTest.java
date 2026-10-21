package com.arquitechthor.kopi.domain.chess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MoveTest {

    private static Move move(String from, String to, MoveKind kind) {
        return new Move(Square.parse(from), Square.parse(to), kind);
    }

    @Test
    void writesUci() {
        assertThat(move("e2", "e4", MoveKind.DOUBLE_PAWN_PUSH).toUci()).isEqualTo("e2e4");
        assertThat(move("g1", "f3", MoveKind.QUIET).toUci()).isEqualTo("g1f3");
    }

    @Test
    void castlingIsWrittenAsTheKingsMove() {
        assertThat(move("e1", "g1", new MoveKind.Castling(CastleSide.KINGSIDE)).toUci()).isEqualTo("e1g1");
        assertThat(move("e8", "c8", new MoveKind.Castling(CastleSide.QUEENSIDE)).toUci()).isEqualTo("e8c8");
    }

    @Test
    void promotionsAppendTheNewPieceInLowerCase() {
        assertThat(move("e7", "e8", new MoveKind.Promotion(PieceType.QUEEN, false)).toUci()).isEqualTo("e7e8q");
        assertThat(move("b2", "a1", new MoveKind.Promotion(PieceType.KNIGHT, true)).toUci()).isEqualTo("b2a1n");
    }

    @Test
    void knowsWhichKindsCapture() {
        assertThat(MoveKind.CAPTURE.isCapture()).isTrue();
        assertThat(MoveKind.EN_PASSANT.isCapture()).isTrue();
        assertThat(new MoveKind.Promotion(PieceType.ROOK, true).isCapture()).isTrue();

        assertThat(MoveKind.QUIET.isCapture()).isFalse();
        assertThat(MoveKind.DOUBLE_PAWN_PUSH.isCapture()).isFalse();
        assertThat(new MoveKind.Castling(CastleSide.KINGSIDE).isCapture()).isFalse();
        assertThat(new MoveKind.Promotion(PieceType.ROOK, false).isCapture()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = PieceType.class, names = {"PAWN", "KING"})
    void rejectsPromotionsToPawnsOrKings(PieceType piece) {
        assertThatIllegalArgumentException().isThrownBy(() -> new MoveKind.Promotion(piece, false));
    }

    @Test
    void aMoveMustChangeSquare() {
        assertThatIllegalArgumentException().isThrownBy(() -> move("e2", "e2", MoveKind.QUIET));
    }

    @Test
    void movesAreEqualByValue() {
        assertThat(move("e2", "e4", MoveKind.DOUBLE_PAWN_PUSH))
                .isEqualTo(move("e2", "e4", new MoveKind.DoublePawnPush()));
    }
}
