package com.arquitechthor.kopi.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/// The whole application, from the REST API to PostgreSQL: the use cases
/// wired in [UseCaseConfiguration], the `PAWN` bot and the real database.
@SpringBootTest(properties = {AnalysisIT.FAST_NODES, AnalysisIT.FAST_POLL})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GameApiIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    private String createGame(String humanColor) throws Exception {
        var result = mvc.post().uri("/api/v1/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"humanColor\": \"" + humanColor + "\", \"botProfile\": \"PAWN\"}")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void aGameIsPlayedThroughTheApiAndStored() throws Exception {
        var id = createGame("WHITE");

        var afterMove = mvc.post().uri("/api/v1/games/{id}/moves", id)
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uci\": \"e2e4\"}")
                .exchange();

        // The person's move and the bot's reply.
        assertThat(afterMove).hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"2\"");
        assertThat(afterMove).bodyJson().extractingPath("$.moves.length()").isEqualTo(2);

        // Read back from the database: same version, same moves.
        assertThat(mvc.get().uri("/api/v1/games/{id}", id)).hasStatusOk()
                .hasHeader(HttpHeaders.ETAG, "\"2\"")
                .bodyJson().extractingPath("$.moves[0].san").isEqualTo("e4");
        assertThat(jdbc.sql("select count(*) from game_move where game_id = cast(:id as uuid)")
                .param("id", id).query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    void theBotOpensWhenItHasWhite() throws Exception {
        var id = createGame("BLACK");

        assertThat(mvc.get().uri("/api/v1/games/{id}", id)).hasStatusOk()
                .bodyJson().extractingPath("$.sideToMove").isEqualTo("BLACK");
    }

    @Test
    void aStockfishBotOpensThroughTheWholeApplication() {
        // CI provides the engine (KOPI_STOCKFISH); QUEEN with White moves first.
        var result = mvc.post().uri("/api/v1/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"humanColor\": \"BLACK\", \"botProfile\": \"QUEEN\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.moves.length()").isEqualTo(1);
    }

    @Test
    void aResignationIsStored() throws Exception {
        var id = createGame("WHITE");

        mvc.post().uri("/api/v1/games/{id}/resign", id).header(HttpHeaders.IF_MATCH, "\"0\"").exchange();

        assertThat(jdbc.sql("select termination from game where id = cast(:id as uuid)")
                .param("id", id).query(String.class).single()).isEqualTo("RESIGNATION");
    }

    @Test
    void problemsAreTranslated() {
        assertThat(mvc.get().uri("/api/v1/games/{id}", "11111111-1111-1111-1111-111111111111")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "es"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.title").isEqualTo("Partida no encontrada");
    }

    @Test
    void theHomePageStillUsesItsOwnTexts() {
        // Both bundles share one MessageSource; the web keys must still resolve.
        assertThat(mvc.get().uri("/?lang=es")).hasStatusOk().bodyText().contains("Juega al ajedrez.");
    }
}
