package com.arquitechthor.kopi.domain.chess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Our rules against a reference implementation, over thousands of positions
/// reached by random games (§5.6 of the plan).
///
/// Perft proves the *number* of moves right in a few famous positions; this
/// compares, in every position of every game, the exact set of legal moves,
/// the check status and the FEN after each move with Stockfish's.
///
/// Stockfish is GPL-3.0 like Kopi Chess and is already part of the project
/// (phase 4). The test needs its binary: CI installs it; locally it is
/// found in `tools/stockfish/` or through `KOPI_STOCKFISH`, and the test is
/// skipped if there is none.
class DifferentialTest {

    @Tag("slow")
    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void legalMovesChecksAndFensMatchStockfish() throws Exception {
        var binary = StockfishOracle.locate();
        if (System.getenv("KOPI_STOCKFISH") != null) {
            // In CI a missing binary is an error, not a reason to skip.
            assertThat(binary).hasValueSatisfying(path -> assertThat(Files.isExecutable(path)).isTrue());
        }
        assumeTrue(binary.isPresent(), "no Stockfish binary: set KOPI_STOCKFISH or put one in tools/stockfish/");

        var positions = new int[1];
        try (var stockfish = new StockfishOracle(binary.get())) {
            RandomGames.play(20_261_023L, 120, 160, step -> {
                var position = step.position();
                StockfishOracle.Answer expected;
                try {
                    expected = stockfish.ask(step.start(), step.moves());
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                var where = "after " + step.moves() + " from " + step.start();

                assertThat(RandomGames.fenAsStockfishWritesIt(position)).as("FEN " + where)
                        .isEqualTo(withoutUnusableEnPassant(expected.fen(), position));
                assertThat(position.isInCheck()).as("check " + where).isEqualTo(expected.inCheck());
                assertThat(position.legalMoves().stream().map(Move::toUci).collect(Collectors.toSet()))
                        .as("legal moves " + where)
                        .isEqualTo(new HashSet<>(expected.legalMoves()));
                positions[0]++;
            });
        }
        assertThat(positions[0]).as("positions compared").isGreaterThan(5_000);
    }

    /// Stockfish's FEN with the en passant field cleared when no en passant
    /// capture is legal. Recent versions already write it that way, but
    /// older ones may write the
    /// target whenever a pawn stands next to it. The legal moves, compared
    /// in full, still catch any en passant mistake.
    private static String withoutUnusableEnPassant(String stockfishFen, Position position) {
        var fields = stockfishFen.split(" ");
        if (position.legalMoves().stream().noneMatch(move -> move.kind() instanceof MoveKind.EnPassant)) {
            fields[3] = "-";
        }
        return String.join(" ", fields);
    }

    @Test
    void fenAndSanReadBackWhatTheyWriteInEveryReachablePosition() {
        // The property of §5.6 ("for every reachable position,
        // Fen.parse(Fen.format(p)) == p"), checked over random games
        // instead of with jqwik, whose 1.x line targets JUnit 5's platform
        // while this build uses JUnit 6.
        // Small enough for every build: SAN of every legal move is costly.
        RandomGames.play(7L, 15, 100, step -> {
            var position = step.position();
            assertThat(Fen.parse(Fen.format(position))).as(position.toFen()).isEqualTo(position);
            for (var move : position.legalMoves()) {
                assertThat(San.parse(position, San.format(position, move))).isEqualTo(move);
            }
        });
    }
}
