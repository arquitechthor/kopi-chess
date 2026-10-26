package com.arquitechthor.kopi.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/// `POST /games/import`: a PGN in, a finished imported game out, or a
/// problem that says where the PGN goes wrong.
@WebMvcTest
class ImportApiTest {

    private static final String FOOLS_MATE = """
            [Event "Casual"]
            [White "Anon"]
            [Black "Someone"]
            [Result "0-1"]

            1. f3 e5 2. g4 Qh4# 0-1
            """;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    RestAdapterTestApplication.InMemoryGames games;

    @Autowired
    RestAdapterTestApplication.QueuedAnalyses jobs;

    @BeforeEach
    void empty() {
        games.clear();
        jobs.clear();
    }

    private MvcTestResult importText(String pgn, String language) {
        return mvc.post().uri("/api/v1/games/import").contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.ACCEPT_LANGUAGE, language).content(pgn).exchange();
    }

    @Test
    void aPgnAsPlainTextBecomesAFinishedImportedGame() {
        var result = importText(FOOLS_MATE, "en");

        var id = games.findRecent(0, 1).getFirst().id().toString();
        assertThat(result).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION, "/api/v1/games/" + id);
        assertThat(result).bodyJson().extractingPath("$.origin").isEqualTo("IMPORTED");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("FINISHED");
        assertThat(result).bodyJson().extractingPath("$.humanColor").isNull();
        assertThat(result).bodyJson().extractingPath("$.botProfile").isNull();
        assertThat(result).bodyJson().extractingPath("$.outcome.termination").isEqualTo("CHECKMATE");
        assertThat(result).bodyJson().extractingPath("$.pgnTags.White").isEqualTo("Anon");
        assertThat(result).bodyJson().extractingPath("$.moves.length()").isEqualTo(4);
        // Its analysis is queued, as for a game finished here.
        assertThat(jobs.findByGameId(games.findRecent(0, 1).getFirst().id())).isPresent();
    }

    @Test
    void aPgnInJson() {
        var result = mvc.post().uri("/api/v1/games/import").contentType(MediaType.APPLICATION_JSON)
                .content("{\"pgn\": \"1. e4 e5 2. Nf3 Nc6 1-0\"}").exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        // White won, but the board does not show why: as the PGN records it.
        assertThat(result).bodyJson().extractingPath("$.outcome.termination").isEqualTo("DECLARED");
        assertThat(result).bodyJson().extractingPath("$.outcome.winner").isEqualTo("WHITE");
    }

    @Test
    void anIllegalMoveSaysExactlyWhere() {
        var result = importText("[Event \"x\"]\n\n1. e4 e5 2. Nf3 Nc6\n3. Bb5 Nxe5 *", "es");

        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_PGN");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("PGN no válido");
        assertThat(result).bodyJson().extractingPath("$.detail")
                .isEqualTo("Línea 4, jugada 3… Nxe5: no es una jugada legal en esa posición.");
        assertThat(result).bodyJson().extractingPath("$.pgnProblem").isEqualTo("ILLEGAL_MOVE");
        assertThat(result).bodyJson().extractingPath("$.line").isEqualTo(4);
        assertThat(result).bodyJson().extractingPath("$.moveNumber").isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.side").isEqualTo("BLACK");
        assertThat(result).bodyJson().extractingPath("$.text").isEqualTo("Nxe5");
        assertThat(games.count()).isZero();
    }

    @Test
    void aProblemOutsideTheMovesSaysTheLine() {
        var result = importText("[Event \"x\"\n\n1. e4 *", "en");

        assertThat(result).bodyJson().extractingPath("$.detail")
                .isEqualTo("Line 1: a tag is not written as [Name \"value\"].");
        assertThat(result).bodyJson().extractingPath("$.moveNumber").isNull();
    }

    @Test
    void anUnfinishedGameIsRefused() {
        assertThat(importText("1. e4 e5 2. Nf3 *", "en")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("UNFINISHED_IMPORT");
    }

    @Test
    void aResultThatContradictsTheBoardIsRefused() {
        assertThat(importText("1. f3 e5 2. g4 Qh4# 1-0", "en")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("RESULT_CONTRADICTS_BOARD");
    }

    @Test
    void aTextOverTheLimitIsRefused() {
        var huge = "{" + "x".repeat(RestAdapterTestApplication.PGN_LIMIT_BYTES) + "} 1. e4 e5 1-0";

        assertThat(importText(huge, "en")).hasStatus(HttpStatus.CONTENT_TOO_LARGE)
                .bodyJson().extractingPath("$.code").isEqualTo("PGN_TOO_LARGE");
    }

    @Test
    void anEmptyBodyIsABadRequest() {
        assertThat(importText(" ", "en")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void theImportedGameExportsWithItsOwnTags() throws Exception {
        importText(FOOLS_MATE, "en");
        var id = games.findRecent(0, 1).getFirst().id().toString();

        var pgn = mvc.get().uri("/api/v1/games/{id}/pgn", id).exchange().getResponse().getContentAsString();

        assertThat(pgn).startsWith("[Event \"Casual\"]\n[White \"Anon\"]").endsWith("1. f3 e5 2. g4 Qh4# 0-1\n");
    }
}
