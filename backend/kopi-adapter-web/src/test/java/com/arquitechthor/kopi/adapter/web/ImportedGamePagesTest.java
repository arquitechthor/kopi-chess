package com.arquitechthor.kopi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.application.port.AnalysisJob;
import com.arquitechthor.kopi.domain.chess.Pgn;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/// An imported game (plan §9) on the existing pages: it has no person and
/// no bot, so they show its players and its declared result instead.
@WebMvcTest
class ImportedGamePagesTest {

    private static final Instant NOW = Instant.parse("2026-10-26T15:00:00Z");

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

    /// An imported game whose PGN says White won, without a mate.
    private Game imported() {
        var game = Game.imported(new GameId(UUID.randomUUID()),
                Pgn.parse("[White \"Morphy, Paul\"]\n[Black \"Duke Karl\"]\n\n1. e4 e5 2. Nf3 d6 1-0"), NOW);
        games.insert(game);
        return game;
    }

    private String page(String uri, Object... variables) {
        try {
            return mvc.get().uri(uri, variables).exchange().getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void theGamePageShowsItsPlayersAndItsDeclaredResult() {
        var game = imported();

        var html = page("/games/{id}?lang=es", game.id());

        assertThat(html).contains("Partida importada · Morphy, Paul – Duke Karl")
                .contains("Ganaron las blancas (resultado según el PGN).")
                .contains("data-orientation=\"white\"", "data-movable=\"false\"")
                .doesNotContain("Abandonar");
    }

    @Test
    void theRecentGamesListSaysImported() {
        imported();

        assertThat(page("/?lang=en")).contains("Imported (PGN)", "1–0");
    }

    @Test
    void itsReviewNamesThePlayers() {
        var game = imported();
        jobs.put(new AnalysisJob(1, game.id(), AnalysisJob.State.RUNNING, 1, Optional.empty(), NOW));

        assertThat(page("/games/{id}/analysis?lang=en", game.id())).contains("Analysis · Morphy, Paul – Duke Karl");
    }
}
