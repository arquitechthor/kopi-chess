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

/// `GET /games/{id}/pgn`: the game as a file to save.
@WebMvcTest
class PgnApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    RestAdapterTestApplication.InMemoryGames games;

    @BeforeEach
    void noGames() {
        games.clear();
    }

    @Test
    void aGameIsDownloadedAsAPgnFile() throws Exception {
        mvc.post().uri("/api/v1/games").contentType(MediaType.APPLICATION_JSON)
                .content("{\"humanColor\": \"WHITE\", \"botProfile\": \"PAWN\"}").exchange();
        var id = games.findRecent(0, 1).getFirst().id().toString();
        mvc.post().uri("/api/v1/games/{id}/moves", id).header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"uci\": \"e2e4\"}").exchange();

        var result = mvc.get().uri("/api/v1/games/{id}/pgn", id).exchange();

        assertThat(result).hasStatusOk()
                .hasContentTypeCompatibleWith("application/x-chess-pgn")
                .hasHeader(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"kopi-chess-" + id.substring(0, 8) + ".pgn\"");
        // The person's e4 and the test bot's reply (its first legal move, a5).
        assertThat(result.getResponse().getContentAsString())
                .startsWith("[Event \"Kopi Chess\"]\n")
                .contains("[White \"Player\"]", "[Black \"Kopi Pawn\"]", "[Result \"*\"]")
                .endsWith("1. e4 a5 *\n");
    }

    @Test
    void anUnknownGameIsNotFound() {
        assertThat(mvc.get().uri("/api/v1/games/{id}/pgn", "11111111-1111-1111-1111-111111111111"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }
}
