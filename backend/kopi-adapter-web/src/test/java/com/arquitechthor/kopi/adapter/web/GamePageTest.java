package com.arquitechthor.kopi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.domain.chess.Fen;
import com.arquitechthor.kopi.domain.game.GameStatus;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest
class GamePageTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    WebAdapterTestApplication.InMemoryGames games;

    @BeforeEach
    void noGames() {
        games.clear();
    }

    /// Submits the home page form and returns the new game's id.
    private String newGame(String color) {
        assertThat(mvc.post().uri("/games").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("bot", "PAWN").param("color", color))
                .hasStatus(HttpStatus.FOUND);
        return games.latest().id().toString();
    }

    private String gamePage(String id) {
        try {
            return mvc.get().uri("/games/{id}?lang=en", id).exchange().getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e); // UTF-8 is always supported
        }
    }

    @Test
    void theHomePageFormStartsAGameAndShowsIt() {
        var result = mvc.post().uri("/games").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("bot", "PAWN").param("color", "WHITE").exchange();
        var id = games.latest().id().toString();

        assertThat(result).hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/games/" + id);
        assertThat(mvc.get().uri("/games/{id}?lang=en", id)).hasStatusOk().bodyText()
                .contains("data-fen=\"" + Fen.INITIAL + "\"")
                .contains("data-orientation=\"white\"", "data-movable=\"true\"")
                .contains("&quot;e2e4&quot;")
                .contains("Against Pawn", "Your move", "No moves yet.")
                .contains("/vendor/chessground-9.2.1/chessground.base.css", "/js/board.js");
    }

    @Test
    void playingBlackTheBoardIsTurnedAndTheBotHasMoved() {
        var id = newGame("BLACK");

        assertThat(gamePage(id)).contains("data-orientation=\"black\"", "data-last-move=\"a2a3\"")
                .contains("<span class=\"move\">a3</span>");
    }

    @Test
    void aMoveIsPlayedAndTheBotReplies() {
        var id = newGame("WHITE");

        assertThat(mvc.post().uri("/games/{id}/moves", id).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("uci", "g1f3").param("version", "0"))
                .hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/games/" + id);

        // Figurine notation in the move list: ♘f3.
        assertThat(gamePage(id)).contains("<span class=\"move\">♘f3</span>", "<span class=\"move\">a5</span>")
                .contains("name=\"version\" value=\"2\"");
    }

    @Test
    void anIllegalMoveShowsTheGameAgainWithAMessage() {
        var id = newGame("WHITE");

        var result = mvc.post().uri("/games/{id}/moves", id).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("uci", "e2e5").param("version", "0").exchange();

        assertThat(result).hasStatus(HttpStatus.FOUND).flash().containsEntry(GameController.ERROR, "ILLEGAL_MOVE");
        assertThat(mvc.get().uri("/games/{id}?lang=es", id).flashAttr(GameController.ERROR, "ILLEGAL_MOVE"))
                .hasStatusOk().bodyText().contains("Esa jugada no es legal aquí.");
    }

    @Test
    void aStaleVersionIsReportedNotPlayed() {
        var id = newGame("WHITE");
        mvc.post().uri("/games/{id}/moves", id).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("uci", "e2e4").param("version", "0").exchange();

        // The same form again, as from a second tab.
        assertThat(mvc.post().uri("/games/{id}/moves", id).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("uci", "d2d4").param("version", "0"))
                .flash().containsEntry(GameController.ERROR, "VERSION_MISMATCH");
        assertThat(games.latest().version()).isEqualTo(2);
    }

    @Test
    void resigningEndsTheGame() {
        var id = newGame("WHITE");

        mvc.post().uri("/games/{id}/resign", id).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("version", "0").exchange();

        assertThat(games.latest().status()).isEqualTo(GameStatus.FINISHED);
        assertThat(gamePage(id)).contains("White resigned. Black wins.", "data-movable=\"false\"")
                .doesNotContain("/resign\"");
    }

    @Test
    void anUnavailableBotSendsYouBackHome() {
        assertThat(mvc.post().uri("/games").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("bot", "QUEEN").param("color", "BLACK"))
                .hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/")
                .flash().containsEntry(GameController.ERROR, "BOT_UNAVAILABLE");
        assertThat(games.count()).isZero();
    }

    @Test
    void anUnknownGameIsAFriendly404() {
        assertThat(mvc.get().uri("/games/{id}?lang=es", "11111111-1111-1111-1111-111111111111"))
                .hasStatus(HttpStatus.NOT_FOUND).bodyText().contains("Partida no encontrada");
        assertThat(mvc.get().uri("/games/not-a-uuid")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void theHomePageListsRecentGamesAndOnlyThePlayableBots() {
        newGame("WHITE");

        assertThat(mvc.get().uri("/?lang=es")).hasStatusOk().bodyText()
                .contains("Partidas recientes", "En juego")
                .containsPattern("value=\"PAWN\"(?![^>]*disabled)")
                .containsPattern("value=\"KNIGHT\"(?![^>]*disabled)")
                .containsPattern("value=\"QUEEN\"[^>]*disabled")
                .contains("Próximamente.");
    }

    @Test
    void figurinesReplaceOnlyPieceLetters() {
        assertThat(Figurines.of("Nbxd2+")).isEqualTo("♘bxd2+");
        assertThat(Figurines.of("exd8=Q#")).isEqualTo("exd8=♕#");
        assertThat(Figurines.of("O-O-O")).isEqualTo("O-O-O");
        assertThat(Figurines.of("Kxb2")).isEqualTo("♔xb2");
    }
}
