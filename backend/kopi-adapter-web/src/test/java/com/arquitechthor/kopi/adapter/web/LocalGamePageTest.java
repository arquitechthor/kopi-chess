package com.arquitechthor.kopi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.domain.game.GameMode;
import com.arquitechthor.kopi.domain.game.GameOutcome;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/// Two people at this computer (ADR-018): the home page form, the board
/// that turns, the names, resigning and agreeing to a draw.
@WebMvcTest
class LocalGamePageTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    WebAdapterTestApplication.InMemoryGames games;

    @BeforeEach
    void noGames() {
        games.clear();
    }

    private String newLocalGame() {
        assertThat(mvc.post().uri("/games/local").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("white", "Ana").param("black", "Luis"))
                .hasStatus(HttpStatus.FOUND);
        return games.latest().id().toString();
    }

    private String page(String uri, Object... vars) {
        try {
            return mvc.get().uri(uri, vars).exchange().getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    private void play(String id, String uci) {
        var version = games.latest().version();
        assertThat(mvc.post().uri("/games/{id}/moves", id).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("uci", uci).param("version", String.valueOf(version)))
                .hasStatus(HttpStatus.FOUND);
    }

    @Test
    void theHomePageOffersAGameForTwoPeople() {
        assertThat(page("/?lang=en")).contains("Two people at this computer", "action=\"/games/local\"",
                "name=\"white\"", "name=\"black\"");
        assertThat(page("/?lang=es")).contains("Dos personas en este equipo");
    }

    @Test
    void theFormStartsAGameBetweenTheTwoNames() {
        var id = newLocalGame();

        assertThat(games.latest().mode()).isEqualTo(GameMode.LOCAL);
        assertThat(page("/games/{id}?lang=en", id))
                .contains("Two people · Ana – Luis", "Ana to move", "Ana resigns", "Agree to a draw")
                .contains("data-orientation=\"white\"", "data-movable=\"true\"", "data-movable-color=\"white\"")
                .doesNotContain("Against");
    }

    @Test
    void theBoardTurnsAfterEveryMove() {
        var id = newLocalGame();
        play(id, "e2e4");

        assertThat(page("/games/{id}?lang=es", id))
                .contains("Mueve Luis", "Abandona Luis", "data-orientation=\"black\"",
                        "data-movable-color=\"black\"");
    }

    @Test
    void aMissingNameSendsTheFormBack() {
        assertThat(mvc.post().uri("/games/local").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("white", "Ana").param("black", "  "))
                .hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/#local-game")
                .flash().containsEntry(GameController.LOCAL_ERROR, "INVALID_NAME");
        assertThat(games.count(com.arquitechthor.kopi.application.port.GameFilter.ALL)).isZero();
    }

    @Test
    void theSideToMoveResigns() {
        var id = newLocalGame();
        play(id, "e2e4");

        assertThat(mvc.post().uri("/games/{id}/resign", id).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("version", String.valueOf(games.latest().version())))
                .hasStatus(HttpStatus.FOUND);
        assertThat(page("/games/{id}?lang=en", id)).contains("Black resigned. White wins.",
                "Play again, swapping colours", "name=\"white\" value=\"Luis\"", "name=\"black\" value=\"Ana\"");
    }

    @Test
    void bothAgreeToADraw() {
        var id = newLocalGame();

        assertThat(mvc.post().uri("/games/{id}/draw", id).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("version", String.valueOf(games.latest().version())))
                .hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/games/" + id);
        assertThat(games.latest().outcome()).hasValue(new GameOutcome.DrawByAgreement());
        assertThat(page("/games/{id}?lang=es", id)).contains("Tablas de mutuo acuerdo.");
    }

    @Test
    void aDrawIsNotOfferedAgainstABot() {
        assertThat(mvc.post().uri("/games").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("bot", "PAWN").param("color", "WHITE")).hasStatus(HttpStatus.FOUND);
        var id = games.latest().id().toString();

        assertThat(page("/games/{id}?lang=en", id)).doesNotContain("Agree to a draw");
        assertThat(mvc.post().uri("/games/{id}/draw", id).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("version", "0"))
                .flash().containsEntry(GameController.ERROR, "NOT_A_LOCAL_GAME");
    }

    @Test
    void theHistoryAndTheRecentGamesShowBothNames() {
        newLocalGame();

        assertThat(page("/games?lang=en")).contains("Ana – Luis");
        assertThat(page("/?lang=en")).contains("Ana – Luis");
    }

    @Test
    void officeModeNamesWhoIsToMove() {
        var id = newLocalGame();

        assertThat(page("/games/{id}?view=office&lang=en", id)).contains("Ana to move");
    }
}
