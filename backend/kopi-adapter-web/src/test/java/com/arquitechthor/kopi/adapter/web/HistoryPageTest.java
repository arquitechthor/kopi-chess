package com.arquitechthor.kopi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Pgn;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/// The history page (plan §9): every game, filtered, a page at a time.
@WebMvcTest
class HistoryPageTest {

    private static final Instant NOW = Instant.parse("2026-10-26T16:00:00Z");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    WebAdapterTestApplication.InMemoryGames games;

    @BeforeEach
    void empty() {
        games.clear();
    }

    private String page(String uri) {
        try {
            return mvc.get().uri(uri).exchange().getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    private static int rows(String html) {
        return (int) Pattern.compile("<tr>\\s*<td><a href=\"/games/").matcher(html).results().count();
    }

    /// A game against `PAWN` as White, resigned (lost) or still going.
    private Game played(boolean resigned) {
        var game = Game.start(new GameId(UUID.randomUUID()), Color.WHITE, BotProfile.PAWN, Position.initial(), NOW)
                .play("e2e4", Color.WHITE, NOW).play("e7e5", Color.BLACK, NOW);
        if (resigned) {
            game = game.resign(Color.WHITE, NOW);
        }
        games.insert(game);
        return game;
    }

    private Game imported() {
        var game = Game.imported(new GameId(UUID.randomUUID()),
                Pgn.parse("[White \"Morphy, Paul\"]\n[Black \"Duke Karl\"]\n\n1. e4 e5 1-0"), NOW);
        games.insert(game);
        return game;
    }

    @Test
    void itIsInTheMenuAndLinkedFromTheHomePage() {
        played(false);

        assertThat(page("/?lang=es")).contains(">Historial</a>", "Ver todo el historial");
    }

    @Test
    void theBotChoiceShowsTheRecordAgainstEachBot() {
        played(true);   // lost
        played(false);  // unfinished
        imported();     // does not count

        var html = page("/?lang=es");

        // Pawn: one lost, one unfinished, as a small table; the others never played.
        assertThat(html).containsPattern("(?s)<caption class=\"sr-only\">Tu balance contra Peón</caption>"
                        + "\\s*<tr><th scope=\"row\">Ganadas</th><td>0</td></tr>"
                        + "\\s*<tr><th scope=\"row\">Perdidas</th><td>1</td></tr>"
                        + "\\s*<tr><th scope=\"row\">Tablas</th><td>0</td></tr>"
                        + "\\s*<tr><th scope=\"row\">Inconclusas</th><td>1</td></tr>")
                .contains("Aún sin partidas.")
                // Each bot with its picture.
                .contains("src=\"/img/bots/pawn.jpg\"", "src=\"/img/bots/queen.jpg\"");
    }

    @Test
    void listsEveryGameWithWhatItMeantForYou() {
        var lost = played(true);
        played(false);
        imported();

        var html = page("/games?lang=es");

        assertThat(rows(html)).isEqualTo(3);
        assertThat(html).contains("3 partidas")
                // Resigning is losing; a game still going can be resumed.
                .contains(">Perdida<", ">En curso<")
                // An imported game: its players, no "for you".
                .contains("Morphy, Paul – Duke Karl")
                // Finished games link to their review.
                .contains("/games/" + lost.id() + "/analysis");
    }

    @Test
    void filtersByOriginAndByResult() {
        played(true);
        played(false);
        imported();

        assertThat(rows(page("/games?origin=IMPORTED"))).isOne();
        assertThat(rows(page("/games?origin=PLAYED"))).isEqualTo(2);
        assertThat(rows(page("/games?result=IN_PROGRESS"))).isOne();
        // White won the imported game, Black the resigned one.
        assertThat(rows(page("/games?result=WHITE_WIN"))).isOne();
        assertThat(rows(page("/games?origin=PLAYED&result=WHITE_WIN"))).isZero();
        assertThat(page("/games?origin=PLAYED&result=WHITE_WIN&lang=en"))
                .contains("No game matches these filters.");
        // The form shows the filters chosen.
        assertThat(page("/games?origin=IMPORTED&lang=en")).contains("<option value=\"IMPORTED\" selected=\"selected\"");
    }

    @Test
    void pagesKeepTheFilters() {
        for (int i = 0; i < HistoryController.PAGE_SIZE + 3; i++) {
            played(true);
        }

        var first = page("/games?origin=PLAYED&lang=en");
        var second = page("/games?origin=PLAYED&page=1&lang=en");

        assertThat(rows(first)).isEqualTo(HistoryController.PAGE_SIZE);
        assertThat(first).contains("Page 1 of 2", "href=\"/games?page=1&amp;origin=PLAYED\"")
                .doesNotContain("Newer");
        assertThat(rows(second)).isEqualTo(3);
        assertThat(second).contains("Page 2 of 2", "href=\"/games?page=0&amp;origin=PLAYED\"")
                .doesNotContain("Older →");
    }
}
