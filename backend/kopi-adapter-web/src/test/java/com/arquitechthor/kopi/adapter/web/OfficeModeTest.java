package com.arquitechthor.kopi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/// Office mode end to end in the web adapter: the view switch, the board
/// table and typed moves (design §11.5).
@WebMvcTest
class OfficeModeTest {

    private static final Cookie OFFICE = new Cookie(OfficeController.VIEW_COOKIE, OfficeController.OFFICE);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    WebAdapterTestApplication.InMemoryGames games;

    @BeforeEach
    void noGames() {
        games.clear();
    }

    private String newGame(String color) {
        mvc.post().uri("/games").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("bot", "PAWN").param("color", color).exchange();
        return games.latest().id().toString();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    /// Types `move` in Spanish: the letters depend on the language, as a
    /// browser would send it with the language cookie.
    private MvcTestResult type(String id, String move, long version) {
        return mvc.post().uri("/games/{id}/office-moves", id).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("move", move).param("version", String.valueOf(version))
                .cookie(new Cookie(KopiLocaleResolver.COOKIE_NAME, "es")).exchange();
    }

    @Test
    void theViewParameterSwitchesToOfficeModeAndACookieRemembersIt() {
        var id = newGame("WHITE");

        var result = mvc.get().uri("/games/{id}?view=office&lang=es", id).exchange();

        assertThat(result).hasStatusOk().cookies().hasValue(OfficeController.VIEW_COOKIE, "office");
        assertThat(body(result))
                .contains("<title>Informe de operaciones</title>", "Portal del empleado", "/css/office.css")
                // None of Kopi's look, and no script at all.
                .doesNotContain("Kopi Chess", "kopi.css", "<script", "chessground");
        assertThat(body(mvc.get().uri("/games/{id}?lang=es", id).cookie(OFFICE).exchange()))
                .contains("Informe de operaciones");
    }

    @Test
    void theStandardViewLinkGoesBackToTheBoard() {
        var id = newGame("WHITE");

        var result = mvc.get().uri("/games/{id}?view=board", id).cookie(OFFICE).exchange();

        assertThat(result).hasStatusOk().cookies().hasMaxAge(OfficeController.VIEW_COOKIE, java.time.Duration.ZERO);
        assertThat(body(result)).contains("id=\"board\"");
    }

    @Test
    void theBoardIsATableWithHeadersAndLetters() {
        var id = newGame("WHITE");

        var page = body(mvc.get().uri("/games/{id}?lang=es", id).cookie(OFFICE).exchange());

        assertThat(Pattern.compile("<td[^>]*title=\"[a-h][1-8][^\"]*\"").matcher(page).results().count()).isEqualTo(64);
        // White at the bottom: rank 8 first, files a to h; Spanish letters,
        // all upper case, the side told by colour (and named in the title).
        assertThat(page).containsPattern("(?s)<th scope=\"col\">a</th>.*<th scope=\"col\">h</th>")
                .containsPattern("(?s)<th scope=\"row\">8</th>\\s*"
                        + "<td class=\"piece-black\"\\s+title=\"a8 · Torre · Negras\">T</td>\\s*"
                        + "<td class=\"piece-black\"\\s+title=\"b8 · Caballo · Negras\">C</td>")
                .containsPattern("<td class=\"piece-white\"\\s+title=\"e1 · Rey · Blancas\">R</td>")
                .containsPattern("<td class=\"piece-white\"\\s+title=\"d1 · Dama · Blancas\">D</td>")
                .containsPattern("<td\\s+title=\"e4\"></td>")
                .contains("R rey, D dama, T torre, A alfil, C caballo, P peón.")
                .contains("<span class=\"piece-white\">Verde: blancas.</span>")
                .contains("<span class=\"piece-black\">Rojo: negras.</span>");
    }

    @Test
    void playingBlackTheTableIsTurnedAndTheLastMoveIsMarked() {
        var id = newGame("BLACK");

        var page = body(mvc.get().uri("/games/{id}?lang=es", id).cookie(OFFICE).exchange());

        assertThat(page).containsPattern("(?s)<th scope=\"col\">h</th>.*<th scope=\"col\">a</th>")
                .containsPattern("(?s)<th scope=\"row\">1</th>\\s*<td class=\"piece-white\"\\s+title=\"h1 · Torre · Blancas\">T</td>")
                // The bot opened with a3: both squares marked, the empty one
                // and the one with White's pawn.
                .containsPattern("<td class=\"is-last\"\\s+title=\"a2\"></td>")
                .containsPattern("<td class=\"piece-white is-last\"\\s+title=\"a3 · Peón · Blancas\">P</td>");
    }

    @Test
    void listsTheValidCodesByPieceInTheLanguage() {
        var id = newGame("WHITE");

        assertThat(body(mvc.get().uri("/games/{id}?lang=es", id).cookie(OFFICE).exchange()))
                .contains("Caballo (C)", "Ca3 · Cc3 · Cf3 · Ch3", "Peón (P)")
                .doesNotContain("Rey (R)");
    }

    @Test
    void aTypedMoveIsPlayedAndTheReplyReported() {
        var id = newGame("WHITE");

        var result = type(id, "CF3", 0);

        assertThat(result).hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/games/" + id)
                .flash().containsEntry(OfficeController.PLAYED, "Cf3").containsEntry(OfficeController.REPLY, "a5");
        assertThat(games.latest().moves()).extracting(move -> move.move().toUci()).containsExactly("g1f3", "a7a5");
        assertThat(body(mvc.get().uri("/games/{id}?lang=es", id).cookie(OFFICE)
                .flashAttr(OfficeController.PLAYED, "Cf3").flashAttr(OfficeController.REPLY, "a5").exchange()))
                .contains("Registrado: Cf3.", "Respuesta: a5.");
    }

    @Test
    void englishLettersInEnglish() {
        var id = newGame("WHITE");

        mvc.get().uri("/games/{id}?lang=en", id).exchange();
        var result = mvc.post().uri("/games/{id}/office-moves", id).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("move", "Nf3").param("version", "0").param("lang", "en").exchange();

        assertThat(result).flash().containsEntry(OfficeController.PLAYED, "Nf3");
    }

    @Test
    void uciIsAcceptedToo() {
        var id = newGame("WHITE");

        assertThat(type(id, "e2e4", 0)).flash().containsEntry(OfficeController.PLAYED, "e4");
    }

    @Test
    void anUnknownCodeKeepsTheTextToCorrectIt() {
        var id = newGame("WHITE");

        assertThat(type(id, "Ce5", 0)).flash()
                .containsEntry(GameController.ERROR, "OFFICE_NO_MATCH")
                .containsEntry(OfficeController.TYPED, "Ce5");
        assertThat(games.latest().moves()).isEmpty();
        assertThat(body(mvc.get().uri("/games/{id}?lang=es", id).cookie(OFFICE)
                .flashAttr(GameController.ERROR, "OFFICE_NO_MATCH").flashAttr(OfficeController.TYPED, "Ce5")
                .exchange()))
                .contains("Ese código no está en la lista de códigos válidos.", "value=\"Ce5\"");
    }

    @Test
    void anAmbiguousCodeAsksWhichOneWasMeant() {
        var id = newGame("WHITE");

        assertThat(body(mvc.get().uri("/games/{id}?lang=es", id).cookie(OFFICE)
                .flashAttr(GameController.ERROR, "OFFICE_AMBIGUOUS").flashAttr(OfficeController.CANDIDATES, "axb5 / Axb5")
                .exchange()))
                .contains("¿quisiste decir axb5 / Axb5?");
    }

    @Test
    void aStaleFormIsReportedNotPlayed() {
        var id = newGame("WHITE");
        type(id, "e4", 0);

        assertThat(type(id, "d4", 0)).flash().containsEntry(GameController.ERROR, "VERSION_MISMATCH");
        assertThat(games.latest().moves()).hasSize(2);
    }

    @Test
    void aFinishedGameTakesNoCodesAndOffersANewReport() {
        var id = newGame("WHITE");
        mvc.post().uri("/games/{id}/resign", id).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("version", "0").exchange();

        assertThat(type(id, "e4", 1)).flash().containsEntry(GameController.ERROR, "GAME_NOT_IN_PROGRESS");
        assertThat(body(mvc.get().uri("/games/{id}?lang=es", id).cookie(OFFICE).exchange()))
                .contains("Las blancas abandonan. Ganan las negras.", "Nuevo informe")
                .doesNotContain("name=\"move\"");
    }

    @Test
    void theBoardPageOffersTheTextView() {
        var id = newGame("WHITE");

        assertThat(body(mvc.get().uri("/games/{id}?lang=es", id).exchange()))
                .contains("view=office", "Vista de texto");
    }
}
