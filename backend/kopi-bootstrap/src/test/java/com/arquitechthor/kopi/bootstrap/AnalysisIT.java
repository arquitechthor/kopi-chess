package com.arquitechthor.kopi.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.application.analysis.RequestAnalysisUseCase;
import com.arquitechthor.kopi.application.port.AnalysisRepository;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.domain.analysis.MoveCategory;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

/// Plan §8.3's acceptance, through the whole application: a game finishes,
/// its analysis starts by itself, and the background worker judges it with
/// the real engine and stores it in the real database. Needs Stockfish
/// (`KOPI_STOCKFISH`; CI has it), like [GameApiIT].
@SpringBootTest(properties = {AnalysisIT.FAST_NODES, AnalysisIT.FAST_POLL})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AnalysisIT {

    /// A light search, so the bootstrap tests stay quick. Shared by every
    /// test of this module, so they share one application context.
    static final String FAST_NODES = "kopi.analysis.nodes=20000";
    static final String FAST_POLL = "kopi.analysis.poll-interval=100ms";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    GameRepository games;

    @Autowired
    RequestAnalysisUseCase requests;

    @Autowired
    AnalysisRepository analyses;

    @Test
    void aFinishedGameIsAnalysedInTheBackground() throws Exception {
        var created = mvc.post().uri("/api/v1/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"humanColor\": \"WHITE\", \"botProfile\": \"PAWN\"}")
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mvc.post().uri("/api/v1/games/{id}/moves", id).header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"uci\": \"e2e4\"}").exchange();

        // Resigning finishes the game: nothing else is asked for.
        assertThat(mvc.post().uri("/api/v1/games/{id}/resign", id).header(HttpHeaders.IF_MATCH, "\"2\""))
                .hasStatusOk();

        assertThat(jobStateWithin(id, Duration.ofSeconds(60))).isEqualTo("DONE");
        // e4 and the bot's reply, each judged, after one search per position.
        assertThat(jdbc.sql("select engine from analysis where game_id = cast(:id as uuid)")
                .param("id", id).query(String.class).single()).startsWith("Stockfish");
        List<String> evaluations = jdbc.sql(
                        "select eval_before from move_analysis where game_id = cast(:id as uuid) order by ply")
                .param("id", id).query(String.class).list();
        assertThat(evaluations).hasSize(2).allMatch(text -> text.startsWith("cp ") || text.startsWith("mate "));
        assertThat(jdbc.sql("select opening_eco from analysis where game_id = cast(:id as uuid)")
                .param("id", id).query(String.class).single()).isNotNull();
    }

    @Test
    void aMissedMateIsFoundWithItsLine() throws Exception {
        // 1.e4 e5 2.Bc4 Nc6 3.Qh5 Nf6?? 4.d3?, and White resigns: 4.Qxf7# was there.
        var game = Game.start(new GameId(UUID.randomUUID()), Color.WHITE, BotProfile.PAWN, Position.initial(),
                Instant.now());
        for (var uci : "e2e4 e7e5 f1c4 b8c6 d1h5 g8f6 d2d3".split(" ")) {
            game = game.play(uci, game.currentPosition().sideToMove(), Instant.now());
        }
        games.insert(game.resign(Color.WHITE, Instant.now()));

        requests.request(game.id());

        assertThat(jobStateWithin(game.id().value().toString(), Duration.ofSeconds(60))).isEqualTo("DONE");
        var missed = analyses.findByGameId(game.id()).orElseThrow().moves().get(6);
        assertThat(missed.classification().category()).isEqualTo(MoveCategory.MISSED_MATE);
        assertThat(missed.classification().missedMate()).hasValue(1);
        assertThat(missed.missedMateLine()).hasValue(List.of("Qxf7#"));
    }

    /// The job's state once it is `DONE` or `FAILED`, or as it is when the
    /// time is up.
    private String jobStateWithin(String gameId, Duration limit) throws InterruptedException {
        var deadline = System.nanoTime() + limit.toNanos();
        String state = null;
        while (System.nanoTime() < deadline) {
            state = jdbc.sql("select state from analysis_job where game_id = cast(:id as uuid)")
                    .param("id", gameId).query(String.class).optional().orElse(null);
            if ("DONE".equals(state) || "FAILED".equals(state)) {
                return state;
            }
            Thread.sleep(200);
        }
        return state;
    }
}
