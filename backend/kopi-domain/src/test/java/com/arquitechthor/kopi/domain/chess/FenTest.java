package com.arquitechthor.kopi.domain.chess;

import static com.arquitechthor.kopi.domain.chess.Color.BLACK;
import static com.arquitechthor.kopi.domain.chess.Color.WHITE;
import static com.arquitechthor.kopi.domain.chess.InvalidFenException.Problem.INVALID_CASTLING_RIGHTS;
import static com.arquitechthor.kopi.domain.chess.InvalidFenException.Problem.INVALID_EN_PASSANT_SQUARE;
import static com.arquitechthor.kopi.domain.chess.InvalidFenException.Problem.INVALID_FULLMOVE_NUMBER;
import static com.arquitechthor.kopi.domain.chess.InvalidFenException.Problem.INVALID_HALFMOVE_CLOCK;
import static com.arquitechthor.kopi.domain.chess.InvalidFenException.Problem.INVALID_PIECE_PLACEMENT;
import static com.arquitechthor.kopi.domain.chess.InvalidFenException.Problem.INVALID_SIDE_TO_MOVE;
import static com.arquitechthor.kopi.domain.chess.InvalidFenException.Problem.MISSING_KING;
import static com.arquitechthor.kopi.domain.chess.InvalidFenException.Problem.PAWN_ON_BACK_RANK;
import static com.arquitechthor.kopi.domain.chess.InvalidFenException.Problem.TOO_MANY_KINGS;
import static com.arquitechthor.kopi.domain.chess.InvalidFenException.Problem.WRONG_NUMBER_OF_FIELDS;
import static com.arquitechthor.kopi.domain.chess.PieceType.KING;
import static com.arquitechthor.kopi.domain.chess.PieceType.PAWN;
import static com.arquitechthor.kopi.domain.chess.PieceType.QUEEN;
import static com.arquitechthor.kopi.domain.chess.PieceType.ROOK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FenTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            Fen.INITIAL,
            // Openings, with en passant targets for both colours
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
            "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2",
            "rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2",
            "rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3",
            // After the Fool's mate (as printed by Stockfish's `d`)
            "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3",
            // The six perft reference positions (Chess Programming Wiki)
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
            "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1",
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
            "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
            // Endgames and odd castling rights
            "8/8/8/4k3/8/8/8/4K3 w - - 0 1",
            "4k3/8/8/8/8/8/8/3QK3 w - - 0 1",
            "8/P7/8/8/8/8/8/k6K w - - 0 1",
            "r3k2r/8/8/8/8/8/8/R3K2R w Kq - 0 1",
            "8/8/8/8/8/8/6k1/4K2R w K - 49 120",
            "6k1/5ppp/8/8/8/8/5PPP/3R2K1 b - - 0 1",
            "n1n5/PPPk4/8/8/8/8/4Kppp/5N1N b - - 0 1",
            "8/8/1k6/8/2pP4/8/5K2/8 b - d3 0 1",
    })
    void formatsWhatItParses(String fen) {
        assertThat(Fen.format(Fen.parse(fen))).isEqualTo(fen);
    }

    @Test
    void readsTheInitialPosition() {
        var position = Position.initial();

        assertThat(position.sideToMove()).isEqualTo(WHITE);
        assertThat(position.castlingRights()).isEqualTo(CastlingRights.ALL);
        assertThat(position.enPassantTarget()).isEmpty();
        assertThat(position.halfmoveClock()).isZero();
        assertThat(position.fullmoveNumber()).isEqualTo(1);
        assertThat(position.board().pieces()).hasSize(32);
        assertThat(position.pieceAt(Square.parse("e1"))).contains(new Piece(WHITE, KING));
        assertThat(position.pieceAt(Square.parse("d8"))).contains(new Piece(BLACK, QUEEN));
        assertThat(position.pieceAt(Square.parse("h7"))).contains(new Piece(BLACK, PAWN));
        assertThat(position.pieceAt(Square.parse("e4"))).isEmpty();
    }

    @Test
    void readsEveryField() {
        var position = Fen.parse("rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w Kq f6 3 7");

        assertThat(position.castlingRights()).isEqualTo(new CastlingRights(true, false, false, true));
        assertThat(position.enPassantTarget()).contains(Square.parse("f6"));
        assertThat(position.halfmoveClock()).isEqualTo(3);
        assertThat(position.fullmoveNumber()).isEqualTo(7);
    }

    @Test
    void acceptsTheFourFieldFormWithDefaultClocks() {
        var position = Fen.parse("4k3/8/8/8/8/8/8/R3K3 b Q -");

        assertThat(position.halfmoveClock()).isZero();
        assertThat(position.fullmoveNumber()).isEqualTo(1);
        assertThat(position.pieceAt(Square.parse("a1"))).contains(new Piece(WHITE, ROOK));
        assertThat(position.toFen()).isEqualTo("4k3/8/8/8/8/8/8/R3K3 b Q - 0 1");
    }

    @Test
    void toleratesExtraWhitespace() {
        assertThat(Fen.parse("  " + Fen.INITIAL.replace(" ", "   ") + "\n")).isEqualTo(Position.initial());
    }

    @Test
    void positionsAreEqualByValue() {
        assertThat(Fen.parse(Fen.INITIAL)).isEqualTo(Position.initial()).hasSameHashCodeAs(Position.initial());
        assertThat(Fen.parse(Fen.INITIAL).enPassantTarget()).isEqualTo(Optional.empty());
    }

    @ParameterizedTest(name = "{1}: {0}")
    @CsvSource(delimiter = '|', value = {
            "                                                               | WRONG_NUMBER_OF_FIELDS",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq             | WRONG_NUMBER_OF_FIELDS",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0         | WRONG_NUMBER_OF_FIELDS",
            "rnbqkbnr/pppppppp/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1         | INVALID_PIECE_PLACEMENT",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNRR w KQkq - 0 1      | INVALID_PIECE_PLACEMENT",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPP/RNBQKBNR w KQkq - 0 1        | INVALID_PIECE_PLACEMENT",
            "rnbqkbnr/pppppppp/9/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1       | INVALID_PIECE_PLACEMENT",
            "rnbqkbnr/ppppxppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1       | INVALID_PIECE_PLACEMENT",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1       | INVALID_SIDE_TO_MOVE",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR W KQkq - 0 1       | INVALID_SIDE_TO_MOVE",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w kqKQ - 0 1       | INVALID_CASTLING_RIGHTS",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KKq - 0 1        | INVALID_CASTLING_RIGHTS",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w X - 0 1          | INVALID_CASTLING_RIGHTS",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq e9 0 1      | INVALID_EN_PASSANT_SQUARE",
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e3 0 1    | INVALID_EN_PASSANT_SQUARE",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq e4 0 1      | INVALID_EN_PASSANT_SQUARE",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - -1 1      | INVALID_HALFMOVE_CLOCK",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - x 1       | INVALID_HALFMOVE_CLOCK",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 0       | INVALID_FULLMOVE_NUMBER",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 one     | INVALID_FULLMOVE_NUMBER",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQ1BNR w kq - 0 1         | MISSING_KING",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBKKBNR w kq - 0 1         | TOO_MANY_KINGS",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNP w Qkq - 0 1        | PAWN_ON_BACK_RANK",
            "pnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQk - 0 1        | PAWN_ON_BACK_RANK",
    })
    void reportsWhatIsWrong(String fen, InvalidFenException.Problem expected) {
        assertThatThrownBy(() -> Fen.parse(fen))
                .isInstanceOfSatisfying(InvalidFenException.class, e -> {
                    assertThat(e.problem()).isEqualTo(expected);
                    assertThat(e.getMessage()).startsWith(expected.name());
                });
    }

    @Test
    void reportsANullFen() {
        assertThatThrownBy(() -> Fen.parse(null))
                .isInstanceOfSatisfying(InvalidFenException.class,
                        e -> assertThat(e.problem()).isEqualTo(WRONG_NUMBER_OF_FIELDS));
    }

    @Test
    void everyProblemIsCovered() {
        // Keeps the parameterised test above honest when a Problem is added.
        assertThat(InvalidFenException.Problem.values()).containsExactlyInAnyOrder(
                WRONG_NUMBER_OF_FIELDS, INVALID_PIECE_PLACEMENT, INVALID_SIDE_TO_MOVE, INVALID_CASTLING_RIGHTS,
                INVALID_EN_PASSANT_SQUARE, INVALID_HALFMOVE_CLOCK, INVALID_FULLMOVE_NUMBER,
                MISSING_KING, TOO_MANY_KINGS, PAWN_ON_BACK_RANK);
    }
}
