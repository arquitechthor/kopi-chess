package com.arquitechthor.kopi.adapter.openings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.domain.chess.Fen;
import com.arquitechthor.kopi.domain.chess.Opening;
import com.arquitechthor.kopi.domain.chess.Position;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LichessOpeningBookTest {

    /// Loaded once: it replays every line of the dataset.
    private static final LichessOpeningBook BOOK = LichessOpeningBook.load();

    private static Opening lookup(String pgn) {
        return BOOK.lookup(LichessOpeningBook.replay(pgn)).orElseThrow();
    }

    @Test
    void everyLineOfTheDatasetIsANamedPosition() throws IOException {
        // Every line replays with Kopi's rules (load() would have failed
        // otherwise) and reaches a position no other line reaches.
        assertThat(BOOK.size()).isEqualTo(datasetLines()).isEqualTo(3865);
    }

    @Test
    void findsAnOpeningByItsMoves() {
        assertThat(lookup("1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6"))
                .isEqualTo(new Opening("B90", "Sicilian Defense: Najdorf Variation"));
        assertThat(lookup("1. e4 e5 2. Nf3 Nc6 3. Bb5"))
                .isEqualTo(new Opening("C60", "Ruy Lopez"));
    }

    @Test
    void findsTranspositions() {
        // The book lists the Queen's Gambit Declined after 1.d4 d5 2.c4 e6;
        // the English move order reaches the same position.
        var english = lookup("1. c4 e6 2. d4 d5");
        assertThat(english).isEqualTo(lookup("1. d4 d5 2. c4 e6"));
        assertThat(english.name()).startsWith("Queen's Gambit Declined");
    }

    @Test
    void ignoresTheMoveClocks() {
        // 1.e4 e5 2.Nf3 Nc6 3.Bb5, written with other clocks.
        var ruyLopez = Fen.parse("r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 17 40");
        assertThat(BOOK.lookup(ruyLopez)).hasValue(new Opening("C60", "Ruy Lopez"));
    }

    @Test
    void ignoresAnEnPassantSquareNobodyCanUse() {
        // After 1.e4 the FEN names e3, but no black pawn can take there:
        // the book's "King's Pawn Game" must still be found.
        var withSquare = Fen.parse("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1");
        assertThat(BOOK.lookup(withSquare)).map(Opening::eco).hasValue("B00");
    }

    @Test
    void theStartingPositionAndUnknownPositionsHaveNoName() {
        assertThat(BOOK.lookup(Position.initial())).isEmpty();
        assertThat(BOOK.lookup(Fen.parse("4k3/8/8/8/8/8/8/4K3 w - - 0 1"))).isEmpty();
    }

    @Test
    void replaySkipsMoveNumbers() {
        assertThat(LichessOpeningBook.replay("1. e4 e5 2. Nf3").toFen())
                .isEqualTo("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2");
    }

    @Test
    void anOpeningNeedsAnEcoCode() {
        assertThatThrownBy(() -> new Opening("F00", "Nonsense")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Opening("C60", " ")).isInstanceOf(IllegalArgumentException.class);
    }

    private static long datasetLines() throws IOException {
        long lines = 0;
        for (var file : LichessOpeningBook.FILES) {
            var stream = LichessOpeningBookTest.class.getClassLoader().getResourceAsStream("openings/" + file);
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                lines += reader.lines().skip(1).filter(line -> !line.isBlank()).count();
            }
        }
        return lines;
    }
}
