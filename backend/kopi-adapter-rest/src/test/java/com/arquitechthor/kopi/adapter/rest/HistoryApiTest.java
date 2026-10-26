package com.arquitechthor.kopi.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/// `GET /games` with the history's filters (plan §9).
@WebMvcTest
class HistoryApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    RestAdapterTestApplication.InMemoryGames games;

    @BeforeEach
    void twoGames() {
        games.clear();
        // One played here, still in progress; one imported, won by White.
        mvc.post().uri("/api/v1/games").contentType(MediaType.APPLICATION_JSON)
                .content("{\"humanColor\": \"WHITE\", \"botProfile\": \"PAWN\"}").exchange();
        mvc.post().uri("/api/v1/games/import").contentType(MediaType.TEXT_PLAIN).content("1. e4 e5 1-0").exchange();
    }

    @Test
    void filtersByOrigin() {
        var imported = mvc.get().uri("/api/v1/games?origin=IMPORTED").exchange();

        assertThat(imported).bodyJson().extractingPath("$.total").isEqualTo(1);
        assertThat(imported).bodyJson().extractingPath("$.games[0].origin").isEqualTo("IMPORTED");
        assertThat(imported).bodyJson().extractingPath("$.games[0].botProfile").isNull();
    }

    @Test
    void filtersByResult() {
        assertThat(mvc.get().uri("/api/v1/games?result=IN_PROGRESS")).bodyJson()
                .extractingPath("$.games[0].origin").isEqualTo("PLAYED");
        assertThat(mvc.get().uri("/api/v1/games?result=DRAW")).bodyJson().extractingPath("$.total").isEqualTo(0);
        assertThat(mvc.get().uri("/api/v1/games")).bodyJson().extractingPath("$.total").isEqualTo(2);
    }

    @Test
    void anUnknownFilterIsABadRequest() {
        assertThat(mvc.get().uri("/api/v1/games?result=MAYBE")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_REQUEST");
    }
}
