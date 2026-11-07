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

/// Two people at this computer through the API (ADR-018).
@WebMvcTest
class LocalGamesApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    RestAdapterTestApplication.InMemoryGames games;

    @BeforeEach
    void noGames() {
        games.clear();
    }

    private MvcTestResult createLocal(String body) {
        return mvc.post().uri("/api/v1/games/local").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    @Test
    void createsAGameBetweenTwoNamedPeople() {
        var result = createLocal("{\"whiteName\": \"Ana\", \"blackName\": \"Luis\"}");

        assertThat(result).hasStatus(HttpStatus.CREATED).bodyJson()
                .hasPathSatisfying("$.mode", mode -> assertThat(mode).asString().isEqualTo("LOCAL"))
                .hasPathSatisfying("$.white.kind", kind -> assertThat(kind).asString().isEqualTo("LOCAL"))
                .hasPathSatisfying("$.white.name", name -> assertThat(name).asString().isEqualTo("Ana"))
                .hasPathSatisfying("$.black.name", name -> assertThat(name).asString().isEqualTo("Luis"))
                .hasPathSatisfying("$.humanToMove", toMove -> assertThat(toMove).asBoolean().isTrue());
    }

    @Test
    void aMissingNameIsABadRequest() {
        assertThat(createLocal("{\"whiteName\": \"Ana\"}")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aGameAgainstABotDescribesBothPlayers() {
        mvc.post().uri("/api/v1/games").contentType(MediaType.APPLICATION_JSON)
                .content("{\"humanColor\": \"BLACK\", \"botProfile\": \"PAWN\"}").exchange();
        var id = games.findRecent(0, 1).getFirst().id();

        assertThat(mvc.get().uri("/api/v1/games/{id}", id)).bodyJson()
                .hasPathSatisfying("$.mode", mode -> assertThat(mode).asString().isEqualTo("AGAINST_BOT"))
                .hasPathSatisfying("$.white.bot", bot -> assertThat(bot).asString().isEqualTo("PAWN"))
                .hasPathSatisfying("$.black.kind", kind -> assertThat(kind).asString().isEqualTo("LOCAL"));
    }

    @Test
    void twoPeopleAgreeToADraw() {
        createLocal("{\"whiteName\": \"Ana\", \"blackName\": \"Luis\"}");
        var id = games.findRecent(0, 1).getFirst().id();

        assertThat(mvc.post().uri("/api/v1/games/{id}/draw", id).header(HttpHeaders.IF_MATCH, "\"0\""))
                .hasStatusOk().bodyJson()
                .hasPathSatisfying("$.outcome.termination", t -> assertThat(t).asString().isEqualTo("DRAW_AGREEMENT"))
                .hasPathSatisfying("$.result", r -> assertThat(r).asString().isEqualTo("DRAW"));
    }

    @Test
    void aBotNeverAgreesToADraw() {
        mvc.post().uri("/api/v1/games").contentType(MediaType.APPLICATION_JSON)
                .content("{\"humanColor\": \"WHITE\", \"botProfile\": \"PAWN\"}").exchange();
        var id = games.findRecent(0, 1).getFirst().id();

        assertThat(mvc.post().uri("/api/v1/games/{id}/draw", id).header(HttpHeaders.IF_MATCH, "\"0\""))
                .hasStatus(HttpStatus.CONFLICT).bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("NOT_A_LOCAL_GAME"));
    }
}
