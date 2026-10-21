package com.arquitechthor.kopi.domain.chess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class PositionTest {

    private static final Position INITIAL = Position.initial();

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
}
