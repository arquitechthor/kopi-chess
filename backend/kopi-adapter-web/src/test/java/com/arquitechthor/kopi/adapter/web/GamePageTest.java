package com.arquitechthor.kopi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Fen;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameStatus;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
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
    void aLiveMoveAnswersOnlyThePartOfThePageThatChanges() {
        var id = newGame("WHITE");

        var result = mvc.post().uri("/games/{id}/live-moves?lang=es", id)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("uci", "g1f3").param("version", "0").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyText()
                .startsWith("<div id=\"game-live\"")
                .doesNotContain("<html", "<nav", "id=\"board\"")
                // The new position after the bot's reply, for the board to animate.
                .contains("data-last-move=\"a7a5\"", "data-version=\"2\"", "data-movable=\"true\"")
                .contains("data-thinking=\"El bot está pensando…\"")
                .contains("<span class=\"move\">♘f3</span>", "<span class=\"move\">a5</span>", "Te toca");
        assertThat(games.latest().version()).isEqualTo(2);
    }

    @Test
    void aRefusedLiveMoveAnswersTheFragmentWithTheMessageAndTheSamePosition() {
        var id = newGame("WHITE");

        var result = mvc.post().uri("/games/{id}/live-moves?lang=es", id)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("uci", "e2e5").param("version", "0").exchange();

        assertThat(result).hasStatusOk().bodyText()
                .contains("Esa jugada no es legal aquí.", "data-version=\"0\"", "data-fen=\"" + Fen.INITIAL + "\"");
        assertThat(games.latest().moves()).isEmpty();
    }

    @Test
    void thePageStillWorksWithoutJavaScript() {
        // The form keeps its classic action; the script only enhances it.
        var id = newGame("WHITE");

        assertThat(gamePage(id)).contains("action=\"/games/" + id + "/moves\"",
                "data-live-action=\"/games/" + id + "/live-moves\"");
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
    void aFinishedGameOffersTheSameBotAgainWithTheOtherColour() {
        var id = newGame("WHITE");
        mvc.post().uri("/games/{id}/resign", id).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("version", "0").exchange();

        assertThat(gamePage(id))
                .contains("<form method=\"post\" action=\"/games\">",
                        "name=\"bot\" value=\"PAWN\"", "name=\"color\" value=\"BLACK\"", ">Play again</button>")
                .contains("href=\"/\">Choose another bot</a>")
                .doesNotContain(">New game</a>");
        assertThat(mvc.get().uri("/games/{id}?lang=es", id)).bodyText()
                .contains(">Jugar otra vez</button>", "href=\"/\">Elegir otro bot</a>");
    }

    @Test
    void playingBlackPlayAgainGivesWhite() {
        var id = newGame("BLACK");
        mvc.post().uri("/games/{id}/resign", id).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("version", "1").exchange();

        assertThat(gamePage(id)).contains("name=\"color\" value=\"WHITE\"");
    }

    @Test
    void aGameInProgressOnlyOffersANewGame() {
        var id = newGame("WHITE");

        assertThat(gamePage(id)).contains("href=\"/\">New game</a>")
                .doesNotContain("Play again", "action=\"/games\"", "Choose another bot");
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
    void theHomePageListsRecentGamesAndEveryBot() {
        newGame("WHITE");

        assertThat(mvc.get().uri("/?lang=es")).hasStatusOk().bodyText()
                .contains("Partidas recientes", "En juego")
                .containsPattern("value=\"PAWN\"(?![^>]*disabled)")
                .containsPattern("value=\"KNIGHT\"(?![^>]*disabled)")
                .containsPattern("value=\"QUEEN\"(?![^>]*disabled)")
                .doesNotContain("Próximamente.");
    }

    @Test
    void figurinesReplaceOnlyPieceLetters() {
        assertThat(Figurines.of("Nbxd2+")).isEqualTo("♘bxd2+");
        assertThat(Figurines.of("exd8=Q#")).isEqualTo("exd8=♕#");
        assertThat(Figurines.of("O-O-O")).isEqualTo("O-O-O");
        assertThat(Figurines.of("Kxb2")).isEqualTo("♔xb2");
    }

    @Test
    void aCaptureShowsWhoIsAheadInMaterial() {
        // 1.e4 d5 2.exd5: White is a pawn up. Playing Black, White is at the top.
        var game = Game.start(new GameId(UUID.randomUUID()), Color.BLACK, BotProfile.PAWN, Position.initial(),
                        Instant.EPOCH)
                .play("e2e4", Color.WHITE, Instant.EPOCH).play("d7d5", Color.BLACK, Instant.EPOCH)
                .play("e4d5", Color.WHITE, Instant.EPOCH);
        games.insert(game);

        assertThat(gamePage(game.id().toString()))
                .contains("class=\"material\" role=\"group\" aria-label=\"Material\"")
                .contains("<span class=\"material-side\">White</span>",
                        "<span class=\"material-pieces\">♟︎</span>",
                        "<span class=\"material-points\">+1</span>")
                .doesNotContain("material-side\">Black");
        assertThat(mvc.get().uri("/games/{id}?lang=es", game.id().toString())).bodyText()
                .contains("<span class=\"material-side\">Blancas</span>");
    }

    @Test
    void levelMaterialShowsNothing() {
        var id = newGame("WHITE");

        assertThat(gamePage(id)).doesNotContain("class=\"material\"");
    }
}
