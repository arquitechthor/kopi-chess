package com.arquitechthor.kopi.domain.chess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/// The reference positions and node counts come from the Chess Programming
/// Wiki (https://www.chessprogramming.org/Perft_Results). Between them they
/// cover castling, promotions, en passant and the awkward cases around them.
class PerftTest {

    private static final String KIWIPETE = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";

    @ParameterizedTest(name = "{0} at depth {2}: {3} nodes")
    @CsvSource(delimiter = ';', textBlock = """
            initial;    rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1;       0;          1
            initial;    rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1;       1;         20
            initial;    rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1;       2;        400
            initial;    rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1;       3;      8 902
            initial;    rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1;       4;    197 281
            kiwipete;   r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1; 1;  48
            kiwipete;   r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1; 2;  2 039
            kiwipete;   r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1; 3; 97 862
            position 3; 8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1;                     4;     43 238
            position 3; 8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1;                     5;    674 624
            position 4; r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1; 3; 9 467
            position 4; r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1; 4; 422 333
            position 5; rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8;      3;     62 379
            position 6; r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10; 3; 89 890
            """)
    void countsTheReferenceNodes(String name, String fen, int depth, String expected) {
        var nodes = Perft.count(Fen.parse(fen), depth);

        assertThat(nodes).isEqualTo(nodes(expected));
    }

    /// The deeper counts take seconds each: they run in CI, not on every
    /// local build (`./mvnw verify -Pslow-tests` runs them locally).
    @Tag("slow")
    @ParameterizedTest(name = "{0} at depth {2}: {3} nodes")
    @CsvSource(delimiter = ';', textBlock = """
            initial;    rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1;       5;  4 865 609
            kiwipete;   r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1; 4; 4 085 603
            position 3; 8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1;                     6; 11 030 083
            position 4; r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1; 5; 15 833 292
            position 5; rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8;      4;  2 103 487
            position 6; r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10; 4; 3 894 594
            """)
    void countsTheDeepReferenceNodes(String name, String fen, int depth, String expected) {
        var nodes = Perft.count(Fen.parse(fen), depth);

        assertThat(nodes).isEqualTo(nodes(expected));
    }

    @Test
    void dividesTheCountByRootMoveInUciOrder() {
        // Given: Stockfish's `go perft 2` from the initial position gives 20
        // for every root move.
        var position = Position.initial();

        // When
        var divide = Perft.divide(position, 2);

        // Then
        assertThat(divide).hasSize(20).containsEntry("e2e4", 20L).containsEntry("g1f3", 20L);
        assertThat(List.copyOf(divide.sequencedKeySet())).isSorted();
        assertThat(divide.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(400);
    }

    @Test
    void theDivideOfKiwipeteMatchesStockfish() {
        // A few branches of Stockfish's `go perft 3` on Kiwipete, including
        // both castlings and a pawn capture.
        var divide = Perft.divide(Fen.parse(KIWIPETE), 3);

        assertThat(divide).containsAllEntriesOf(Map.of(
                "e1g1", 2_059L,
                "e1c1", 1_887L,
                "d5e6", 2_241L,
                "e2a6", 1_907L));
        assertThat(divide.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(97_862);
    }

    @Test
    void refusesANegativeDepth() {
        assertThatIllegalArgumentException().isThrownBy(() -> Perft.count(Position.initial(), -1));
        assertThatIllegalArgumentException().isThrownBy(() -> Perft.divide(Position.initial(), 0));
    }

    /// "4 865 609" → 4865609: the tables keep the thousands readable.
    private static long nodes(String text) {
        return Long.parseLong(text.replace(" ", ""));
    }
}
