package com.arquitechthor.kopi.domain.chess;

import static com.arquitechthor.kopi.domain.chess.PieceType.BISHOP;
import static com.arquitechthor.kopi.domain.chess.PieceType.KNIGHT;
import static com.arquitechthor.kopi.domain.chess.PieceType.PAWN;
import static com.arquitechthor.kopi.domain.chess.PieceType.QUEEN;
import static com.arquitechthor.kopi.domain.chess.PieceType.ROOK;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaterialBalanceTest {

    private static MaterialBalance balance(String fen) {
        return Fen.parse(fen).materialBalance();
    }

    @Test
    void theStartingPositionIsLevel() {
        // Given / When
        var balance = Position.initial().materialBalance();

        // Then
        assertThat(balance).isEqualTo(MaterialBalance.LEVEL);
        assertThat(balance.pointsAhead(Color.WHITE)).isZero();
        assertThat(balance.pointsAhead(Color.BLACK)).isZero();
    }

    @Test
    void aCapturedPieceIsCountedForTheOtherSide() {
        // Given: Black's g8 knight is gone, and White has lost two pawns to
        // Black's one: Black is a knight down and a pawn up.
        var fen = "rnbqkb1r/pp1ppppp/8/2pP4/8/8/PPP2PPP/RNBQKBNR b KQkq - 0 4";

        // When
        var balance = balance(fen);

        // Then: 3 − 1.
        assertThat(balance.whiteAhead()).containsExactly(KNIGHT);
        assertThat(balance.blackAhead()).containsExactly(PAWN);
        assertThat(balance.points()).isEqualTo(2);
        assertThat(balance.pointsAhead(Color.WHITE)).isEqualTo(2);
        assertThat(balance.pointsAhead(Color.BLACK)).isZero();
    }

    @Test
    void aLevelTradeShowsNothing() {
        // Given: both knights on f3 and f6 are gone.
        var fen = "rnbqkb1r/pppppppp/8/8/8/8/PPPPPPPP/RNBQKB1R w KQkq - 0 5";

        // When / Then
        assertThat(balance(fen)).isEqualTo(MaterialBalance.LEVEL);
    }

    @Test
    void aPromotedPawnCountsAsWhatItBecame() {
        // Given: White's a-pawn became a second queen (on h5); Black has
        // everything.
        var fen = "rnbqkbnr/pppppppp/8/7Q/8/8/1PPPPPPP/RNBQKBNR b KQkq - 0 1";

        // When
        var balance = balance(fen);

        // Then: a side with two queens has not lost a queen; it is a queen
        // up and a pawn down.
        assertThat(balance.whiteAhead()).containsExactly(QUEEN);
        assertThat(balance.blackAhead()).containsExactly(PAWN);
        assertThat(balance.points()).isEqualTo(9 - 1);
    }

    @Test
    void theExtraPiecesGoFromPawnToQueenOncePerPiece() {
        // Given: Black has two pawns and both rooks more; White, a bishop.
        var fen = "r3k2r/pppppppp/8/8/8/8/PPPPPP2/4KB2 w kq - 0 30";

        // When
        var balance = balance(fen);

        // Then
        assertThat(balance.blackAhead()).containsExactly(PAWN, PAWN, ROOK, ROOK);
        assertThat(balance.whiteAhead()).containsExactly(BISHOP);
        assertThat(balance.points()).isEqualTo(3 - 2 - 10);
        assertThat(balance.pointsAhead(Color.BLACK)).isEqualTo(9);
        assertThat(balance.ahead(Color.BLACK)).isEqualTo(balance.blackAhead());
    }

    @Test
    void aGameFromACustomPositionDoesNotCountMissingPiecesAsCaptured() {
        // Given: a rook endgame set up from FEN, nothing captured in the game.
        var fen = "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1";

        // When / Then: only the rook makes a difference.
        assertThat(balance(fen).whiteAhead()).containsExactly(ROOK);
        assertThat(balance(fen).blackAhead()).isEmpty();
        assertThat(balance(fen).points()).isEqualTo(5);
    }
}
