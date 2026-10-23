package com.arquitechthor.kopi.domain.chess;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.HashSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class ZobristTest {

    /// The position after playing `moves` (SAN, separated by spaces) from `fen`.
    private static Position play(String fen, String moves) {
        var position = Fen.parse(fen);
        for (var san : moves.split(" ")) {
            position = position.apply(San.parse(position, san));
        }
        return position;
    }

    @Test
    void isTheSameEveryTime() {
        // A fixed seed: the hashes do not change between runs or JVMs, so
        // they could even be stored.
        assertThat(Position.initial().zobristHash()).isEqualTo(Fen.parse(Fen.INITIAL).zobristHash());
        // Pinned: if this changes, the table (seed or order) changed.
        assertThat(Position.initial().zobristHash()).isEqualTo(1_860_386_934_984_769_455L);
    }

    @Test
    void ignoresTheClocks() {
        // The knights go out and come back: same position, other clocks.
        var back = play(Fen.INITIAL, "Nf3 Nf6 Ng1 Ng8");

        assertThat(back.toFen()).isEqualTo("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 4 3");
        assertThat(back.zobristHash()).isEqualTo(Position.initial().zobristHash());
    }

    @Test
    void dependsOnTheSideToMove() {
        var white = Fen.parse("4k3/8/8/8/8/8/8/R3K3 w - - 0 1");
        var black = Fen.parse("4k3/8/8/8/8/8/8/R3K3 b - - 0 1");

        assertThat(white.zobristHash()).isNotEqualTo(black.zobristHash());
    }

    @Test
    void dependsOnTheCastlingRights() {
        var withRights = Fen.parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        var withoutOne = Fen.parse("r3k2r/8/8/8/8/8/8/R3K2R w Qkq - 0 1");

        assertThat(withRights.zobristHash()).isNotEqualTo(withoutOne.zobristHash());
    }

    @Test
    void countsAnEnPassantTargetOnlyWhenTheCaptureIsPossible() {
        // After 1.e4 e5 2.Nf3 and 1.Nf3 e5 2.e4 the board is the same, but
        // the second one has an en passant target on e3. No black pawn can
        // capture there, so for repetition both are the same position.
        var viaKnight = play(Fen.INITIAL, "e4 e5 Nf3");
        var viaPawn = play(Fen.INITIAL, "Nf3 e5 e4");

        assertThat(viaPawn.enPassantTarget()).isPresent();
        assertThat(viaPawn.zobristHash()).isEqualTo(viaKnight.zobristHash());
    }

    @Test
    void countsAnEnPassantTargetWhenTheCaptureIsLegal() {
        var canCapture = Fen.parse("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2");
        var cannot = Fen.parse("4k3/8/8/3pP3/8/8/8/4K3 w - - 0 2");

        assertThat(canCapture.zobristHash()).isNotEqualTo(cannot.zobristHash());
    }

    @Test
    void anEnPassantCaptureThatWouldExposeTheKingDoesNotCount() {
        // The capture on d6 is pseudo-legal but illegal (the rook on h5
        // would see the king on a5), so the target changes nothing.
        var target = Fen.parse("4k3/8/8/K2pP2r/8/8/8/8 w - d6 0 2");
        var none = Fen.parse("4k3/8/8/K2pP2r/8/8/8/8 w - - 0 2");

        assertThat(target.zobristHash()).isEqualTo(none.zobristHash());
    }

    /// A few seconds: runs in CI with the other slow tests.
    @Tag("slow")
    @Test
    void differentPositionsGetDifferentHashes() {
        // Every position three plies deep from Kiwipete: one hash per
        // position, no collisions.
        var hashes = new HashMap<String, Long>();
        collect(Fen.parse("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"), 3, hashes);

        assertThat(hashes).hasSizeGreaterThan(10_000);
        assertThat(new HashSet<>(hashes.values())).hasSameSizeAs(hashes.keySet());
    }

    /// Records each position under what makes it "the same" for repetition:
    /// the first three FEN fields, plus the en passant target only when an
    /// en passant capture is legal (the clocks never count).
    private static void collect(Position position, int depth, HashMap<String, Long> hashes) {
        var fields = position.toFen().split(" ");
        var enPassant = position.legalMoves().stream().anyMatch(move -> move.kind() == MoveKind.EN_PASSANT)
                ? fields[3] : "-";
        hashes.put(fields[0] + ' ' + fields[1] + ' ' + fields[2] + ' ' + enPassant, position.zobristHash());
        if (depth > 0) {
            for (var move : position.legalMoves()) {
                collect(position.apply(move), depth - 1, hashes);
            }
        }
    }
}
