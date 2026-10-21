package com.arquitechthor.kopi.domain.chess;

import static com.arquitechthor.kopi.domain.chess.CastleSide.KINGSIDE;
import static com.arquitechthor.kopi.domain.chess.CastleSide.QUEENSIDE;
import static com.arquitechthor.kopi.domain.chess.Color.BLACK;
import static com.arquitechthor.kopi.domain.chess.Color.WHITE;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CastlingRightsTest {

    @Test
    void writesFen() {
        assertThat(CastlingRights.ALL.toFen()).isEqualTo("KQkq");
        assertThat(CastlingRights.NONE.toFen()).isEqualTo("-");
        assertThat(new CastlingRights(false, true, true, false).toFen()).isEqualTo("Qk");
    }

    @Test
    void removingOneCastlingKeepsTheOthers() {
        var rights = CastlingRights.ALL.without(WHITE, QUEENSIDE);

        assertThat(rights.has(WHITE, QUEENSIDE)).isFalse();
        assertThat(rights.has(WHITE, KINGSIDE)).isTrue();
        assertThat(rights.has(BLACK, QUEENSIDE)).isTrue();
        assertThat(rights.toFen()).isEqualTo("Kkq");
    }

    @Test
    void removingAColourRemovesBothOfItsCastlings() {
        assertThat(CastlingRights.ALL.without(BLACK).toFen()).isEqualTo("KQ");
        assertThat(CastlingRights.ALL.without(BLACK).without(WHITE)).isEqualTo(CastlingRights.NONE);
    }
}
