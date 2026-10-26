package com.arquitechthor.kopi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.domain.game.GameOrigin;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/// The "Import PGN" page (plan §9): a pasted PGN or a `.pgn` file becomes
/// an imported game, or comes back with the reason, in the visitor's
/// language, and the line where it goes wrong.
@WebMvcTest
class ImportPageTest {

    private static final String FOOLS_MATE = "[White \"Anon\"]\n[Black \"Someone\"]\n\n1. f3 e5 2. g4 Qh4# 0-1";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    WebAdapterTestApplication.InMemoryGames games;

    @Autowired
    WebAdapterTestApplication.QueuedAnalyses jobs;

    @BeforeEach
    void empty() {
        games.clear();
        jobs.clear();
    }

    /// Posts the form as a browser does: multipart, with the text and an
    /// optional file.
    private MvcTestResult submit(String pgn, MockMultipartFile file, String language) {
        var request = MockMvcRequestBuilders.multipart("/games/import").param("pgn", pgn).param("lang", language);
        if (file != null) {
            request.file(file);
        }
        return mvc.perform(request);
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void theFormIsInTheMenuAndOnTheHomePage() {
        assertThat(mvc.get().uri("/?lang=es")).bodyText().contains("/games/import", "Importar PGN",
                "Importar una partida");
        assertThat(mvc.get().uri("/games/import?lang=es")).hasStatusOk().bodyText()
                .contains("Importar una partida", "enctype=\"multipart/form-data\"", "name=\"pgn\"", "name=\"file\"",
                        "accept=\".pgn,");
    }

    @Test
    void aPastedPgnIsImportedAndItsReviewOpens() {
        var result = submit(FOOLS_MATE, null, "en");

        var game = games.latest();
        assertThat(result).hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/games/" + game.id() + "/analysis");
        assertThat(game.origin()).isEqualTo(GameOrigin.IMPORTED);
        assertThat(jobs.findByGameId(game.id())).isPresent();
    }

    @Test
    void aChosenFileWinsOverTheTextBox() {
        var file = new MockMultipartFile("file", "game.pgn", "application/x-chess-pgn",
                FOOLS_MATE.getBytes(StandardCharsets.UTF_8));

        var result = submit("", file, "en");

        assertThat(result).hasStatus(HttpStatus.FOUND);
        assertThat(games.latest().pgnTags()).containsEntry("White", "Anon");
    }

    @Test
    void anIllegalMoveComesBackWithWhereAndTheLineQuoted() {
        var pgn = "[Event \"x\"]\n\n1. e4 e5 2. Nf3 Nc6\n3. Bb5 Nxe5 *";

        var html = body(submit(pgn, null, "es"));

        assertThat(html).contains("Línea 4, jugada 3… Nxe5:", "no es una jugada legal en esa posición.")
                .contains("<pre class=\"import-quote\">3. Bb5 Nxe5 *</pre>")
                // The text stays in the box, to fix it there.
                .contains("1. e4 e5 2. Nf3 Nc6");
        assertThat(games.count()).isZero();
    }

    @Test
    void aProblemWithTheGameRatherThanTheTextHasNoLine() {
        var html = body(submit("1. e4 e5 2. Nf3 *", null, "en"));

        assertThat(html).contains("The game is not finished").doesNotContain("Line ");
    }

    @Test
    void anEmptyFormSaysWhatToDo() {
        assertThat(body(submit("  ", null, "en"))).contains("Paste a PGN or choose a file.");
    }
}
