package com.arquitechthor.kopi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.application.port.AnalysisJob;
import com.arquitechthor.kopi.domain.analysis.AnalysisSettings;
import com.arquitechthor.kopi.domain.analysis.Evaluation;
import com.arquitechthor.kopi.domain.analysis.GameAnalysis;
import com.arquitechthor.kopi.domain.analysis.MoveAnalysis;
import com.arquitechthor.kopi.domain.analysis.MoveCategory;
import com.arquitechthor.kopi.domain.analysis.MoveClassifier.Classification;
import com.arquitechthor.kopi.domain.analysis.SearchLimits;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Opening;
import com.arquitechthor.kopi.domain.game.GameId;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/// The review page (plan §8.5) in each state of its analysis, in front of
/// the real use cases.
@WebMvcTest
class ReviewPageTest {

    private static final Instant START = Instant.parse("2026-10-26T12:00:00Z");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    WebAdapterTestApplication.InMemoryGames games;

    @Autowired
    WebAdapterTestApplication.QueuedAnalyses jobs;

    @Autowired
    WebAdapterTestApplication.SavedAnalyses analyses;

    @BeforeEach
    void empty() {
        games.clear();
        jobs.clear();
        analyses.clear();
    }

    /// A game as White against `PAWN`: e4, the bot's a5, then resigned.
    private String finishedGame() {
        var id = newGame();
        mvc.post().uri("/games/{id}/moves", id).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("uci", "e2e4").param("version", "0").exchange();
        mvc.post().uri("/games/{id}/resign", id).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("version", "2").exchange();
        return id;
    }

    private String newGame() {
        mvc.post().uri("/games").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("bot", "PAWN").param("color", "WHITE").exchange();
        return games.latest().id().toString();
    }

    private String page(String uri, Object... variables) {
        try {
            return mvc.get().uri(uri, variables).exchange().getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    private GameId gameId(String id) {
        return new GameId(UUID.fromString(id));
    }

    /// 1.e4 a5: e4 from the book, then a5, which (in this made-up verdict)
    /// let a mate in two slip.
    private void analysed(String id) {
        analyses.save(new GameAnalysis(gameId(id), "Stockfish 19",
                new AnalysisSettings(new SearchLimits.Nodes(1_000_000), 2, 5), Color.WHITE,
                Optional.of(new Opening("B00", "Ware Defense")),
                List.of(new MoveAnalysis(1, new Evaluation.Centipawns(30), new Evaluation.MateIn(-2), "e2e4",
                                List.of("e4", "e5", "Nf3"), new Classification(MoveCategory.BOOK, 0.0, OptionalInt.empty())),
                        new MoveAnalysis(2, new Evaluation.MateIn(-2), new Evaluation.Centipawns(120), "d8h4",
                                List.of("Qh4", "g3", "Qxe4#"),
                                new Classification(MoveCategory.MISSED_MATE, 1.2, OptionalInt.of(2)))),
                START, START.plusSeconds(3)));
        jobs.put(new AnalysisJob(1, gameId(id), AnalysisJob.State.DONE, 1, Optional.empty(), START));
    }

    @Test
    void aFinishedGameLinksToItsReview() {
        var id = finishedGame();
        var playing = newGame();

        assertThat(page("/games/{id}?lang=en", id)).contains("/games/" + id + "/analysis", "Review the game");
        assertThat(page("/games/{id}?lang=en", playing)).doesNotContain("Review the game");
    }

    @Test
    void whileItRunsItFollowsTheAnalysisLive() {
        var id = finishedGame(); // finishing queued it

        var html = page("/games/{id}/analysis?lang=es", id);

        assertThat(html).contains("Revisión de la partida", "Esperando su turno…")
                .contains("id=\"analysis-progress\"")
                .contains("data-events=\"/api/v1/games/" + id + "/analysis/events\"")
                .contains("data-progress-text=\"{0} de {1} posiciones\"")
                .contains("<noscript>", "/js/review.js")
                .doesNotContain("id=\"board\"");
    }

    @Test
    void aFailedAnalysisSaysWhyAndCanBeTriedAgain() {
        var id = finishedGame();
        jobs.put(new AnalysisJob(1, gameId(id), AnalysisJob.State.FAILED, 3, Optional.of("Stockfish is not installed"),
                START));

        assertThat(page("/games/{id}/analysis?lang=en", id))
                .contains("The analysis could not be completed: Stockfish is not installed", "Try again");

        var retry = mvc.post().uri("/games/{id}/analysis", id).exchange();

        assertThat(retry).hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/games/" + id + "/analysis");
        assertThat(jobs.findByGameId(gameId(id))).map(AnalysisJob::state).hasValue(AnalysisJob.State.QUEUED);
    }

    @Test
    void aGameInProgressGoesBackToItsBoard() {
        var id = newGame();

        assertThat(mvc.get().uri("/games/{id}/analysis", id)).hasStatus(HttpStatus.FOUND)
                .hasRedirectedUrl("/games/" + id);
    }

    @Test
    void aGameFinishedWithoutMovesHasNothingToReview() {
        var id = newGame();
        mvc.post().uri("/games/{id}/resign", id).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("version", "0").exchange();

        assertThat(page("/games/{id}/analysis?lang=en", id)).contains("there is nothing to review");
    }

    @Test
    void anOlderGameNeverAnalysedIsQueuedWhenItsReviewIsOpened() {
        var id = finishedGame();
        jobs.clear(); // as if it had finished before analyses existed

        assertThat(page("/games/{id}/analysis?lang=en", id)).contains("Waiting for its turn…");
        assertThat(jobs.findByGameId(gameId(id))).isPresent();
    }

    @Test
    void onceDoneItShowsTheWholeReview() {
        var id = finishedGame();
        analysed(id);

        var html = page("/games/{id}/analysis?lang=es", id);

        // The board and what it steps through: the start, then each move,
        // with the engine's choice from there.
        assertThat(html).contains("id=\"board\"", "data-orientation=\"white\"")
                .contains("&quot;fen&quot;:&quot;rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1&quot;")
                .contains("&quot;lastMove&quot;:&quot;e2e4&quot;,&quot;bestMove&quot;:&quot;d8h4&quot;");
        // The summary: opening, accuracy in the visitor's format (a decimal
        // comma in Spanish; White fell from 52.8 % to a lost game: 7.2), the counts.
        assertThat(html).contains("B00 · Ware Defense", "fórmula de Lichess", "7,2 %")
                .contains("Error grave", "Mate perdido");
        // The verdicts, in Spanish, with figurines in the engine's line.
        assertThat(html).contains("1. e4", "1… a5", "Teoría")
                .contains("El motor encontró mate en 2: ♕h4 g3 ♕xe4#")
                .contains("Evaluación: +1.20");
        // The graph: a line and a dot on the missed mate.
        assertThat(html).contains("class=\"eval-graph-line\"", "eval-graph-marker cat-MISSED_MATE");
        // Moves to click, and the engine that judged them.
        assertThat(html).contains("class=\"review-move\"", "data-ply=\"2\"", "Analizada con Stockfish 19.");
    }
}
