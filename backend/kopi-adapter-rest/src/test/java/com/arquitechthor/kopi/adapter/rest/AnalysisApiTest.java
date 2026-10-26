package com.arquitechthor.kopi.adapter.rest;

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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/// `/games/{id}/analysis`: request, read and follow, in front of the real
/// use cases (the analysis itself is the worker's, not this adapter's).
@WebMvcTest
class AnalysisApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    RestAdapterTestApplication.InMemoryGames games;

    @Autowired
    RestAdapterTestApplication.QueuedAnalyses jobs;

    @Autowired
    RestAdapterTestApplication.SavedAnalyses analyses;

    @Autowired
    SseAnalysisEvents events;

    @BeforeEach
    void empty() {
        games.clear();
        jobs.clear();
        analyses.clear();
    }

    /// A game against `PAWN`: the person plays e4, the bot replies.
    private String gameAfterOneMove() {
        mvc.post().uri("/api/v1/games").contentType(MediaType.APPLICATION_JSON)
                .content("{\"humanColor\": \"WHITE\", \"botProfile\": \"PAWN\"}").exchange();
        var id = games.findRecent(0, 1).getFirst().id().toString();
        mvc.post().uri("/api/v1/games/{id}/moves", id).header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"uci\": \"e2e4\"}").exchange();
        return id;
    }

    /// The same, then resigned: finished, and its analysis queued by itself.
    private String finishedGame() {
        var id = gameAfterOneMove();
        mvc.post().uri("/api/v1/games/{id}/resign", id).header(HttpHeaders.IF_MATCH, "\"2\"").exchange();
        return id;
    }

    /// A saved analysis of [#finishedGame] and its job, done.
    private void analysed(String id) {
        var gameId = new GameId(UUID.fromString(id));
        var started = Instant.parse("2026-10-26T12:00:00Z");
        analyses.save(new GameAnalysis(gameId, "Stockfish 19",
                new AnalysisSettings(new SearchLimits.Nodes(1_000_000), 2, 5), Color.WHITE,
                Optional.of(new Opening("B00", "Ware Defense")),
                List.of(new MoveAnalysis(1, new Evaluation.Centipawns(30), new Evaluation.Centipawns(35), "e2e4",
                                List.of("e4", "e5"), new Classification(MoveCategory.BOOK, -0.01, OptionalInt.empty())),
                        new MoveAnalysis(2, new Evaluation.Centipawns(35), new Evaluation.MateIn(-3), "e7e5",
                                List.of("e5"), new Classification(MoveCategory.BLUNDER, 0.9, OptionalInt.empty()))),
                started, started.plusSeconds(3)));
        jobs.put(new AnalysisJob(1, gameId, AnalysisJob.State.DONE, 1, Optional.empty(), started));
    }

    @Test
    void askingQueuesItAndAnswersAtOnce() {
        var id = gameAfterOneMove();
        mvc.post().uri("/api/v1/games/{id}/resign", id).header(HttpHeaders.IF_MATCH, "\"2\"").exchange();
        jobs.clear(); // as if finishing had not queued it

        var result = mvc.post().uri("/api/v1/games/{id}/analysis", id).exchange();

        assertThat(result).hasStatus(HttpStatus.ACCEPTED)
                .hasHeader(HttpHeaders.LOCATION, "/api/v1/games/" + id + "/analysis");
        assertThat(result).bodyJson().extractingPath("$.state").isEqualTo("QUEUED");
        assertThat(result).bodyJson().extractingPath("$.engine").isNull();
    }

    @Test
    void askingTwiceIsHarmless() {
        var id = finishedGame();

        var job = jobs.findByGameId(new GameId(UUID.fromString(id))).orElseThrow();

        var again = mvc.post().uri("/api/v1/games/{id}/analysis", id).exchange();

        // Finishing the game had queued it; asking keeps that very job.
        assertThat(again).hasStatus(HttpStatus.ACCEPTED).bodyJson().extractingPath("$.state").isEqualTo("QUEUED");
        assertThat(jobs.findByGameId(new GameId(UUID.fromString(id)))).hasValue(job);
    }

    @Test
    void aGameInProgressCannotBeAnalysed() {
        var id = gameAfterOneMove();

        var result = mvc.post().uri("/api/v1/games/{id}/analysis", id).header(HttpHeaders.ACCEPT_LANGUAGE, "es")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("GAME_NOT_FINISHED");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Partida sin terminar");
    }

    @Test
    void aGameWithoutMovesHasNothingToAnalyse() {
        mvc.post().uri("/api/v1/games").contentType(MediaType.APPLICATION_JSON)
                .content("{\"humanColor\": \"WHITE\", \"botProfile\": \"PAWN\"}").exchange();
        var id = games.findRecent(0, 1).getFirst().id().toString();
        mvc.post().uri("/api/v1/games/{id}/resign", id).header(HttpHeaders.IF_MATCH, "\"0\"").exchange();

        assertThat(mvc.post().uri("/api/v1/games/{id}/analysis", id)).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("NOTHING_TO_ANALYSE");
    }

    @Test
    void anUnknownGameIsNotFound() {
        assertThat(mvc.post().uri("/api/v1/games/{id}/analysis", "11111111-1111-1111-1111-111111111111"))
                .hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code").isEqualTo("GAME_NOT_FOUND");
    }

    @Test
    void anAnalysisNeverAskedForIsNotFound() {
        var id = gameAfterOneMove();

        assertThat(mvc.get().uri("/api/v1/games/{id}/analysis", id)).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("ANALYSIS_NOT_FOUND");
    }

    @Test
    void untilItIsDoneItSaysHowItIsGoing() {
        var id = finishedGame();

        var result = mvc.get().uri("/api/v1/games/{id}/analysis", id).exchange();

        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.state").isEqualTo("QUEUED");
        assertThat(result).bodyJson().extractingPath("$.moves").isNull();
    }

    @Test
    void onceDoneItGivesTheWholeAnalysis() {
        var id = finishedGame();
        analysed(id);

        var json = mvc.get().uri("/api/v1/games/{id}/analysis", id).exchange();

        assertThat(json).hasStatusOk();
        assertThat(json).bodyJson().extractingPath("$.state").isEqualTo("DONE");
        assertThat(json).bodyJson().extractingPath("$.engine").isEqualTo("Stockfish 19");
        assertThat(json).bodyJson().extractingPath("$.settings.limit").isEqualTo("NODES");
        assertThat(json).bodyJson().extractingPath("$.settings.limitValue").isEqualTo(1_000_000);
        assertThat(json).bodyJson().extractingPath("$.opening.eco").isEqualTo("B00");
        assertThat(json).bodyJson().extractingPath("$.summary.black.counts.BLUNDER").isEqualTo(1);
        assertThat(json).bodyJson().extractingPath("$.summary.white.accuracy").isEqualTo(100.0);
        assertThat(json).bodyJson().extractingPath("$.moves[0].san").isEqualTo("e4");
        assertThat(json).bodyJson().extractingPath("$.moves[0].category").isEqualTo("BOOK");
        assertThat(json).bodyJson().extractingPath("$.moves[0].before.cp").isEqualTo(30);
        assertThat(json).bodyJson().extractingPath("$.moves[0].bestLine").isEqualTo(List.of("e4", "e5"));
        // Black mates in three: from White's point of view, a negative mate.
        assertThat(json).bodyJson().extractingPath("$.moves[1].after.mate").isEqualTo(-3);
        assertThat(json).bodyJson().extractingPath("$.moves[1].after.cp").isNull();
    }

    @Test
    void followingAFinishedAnalysisEndsAtOnce() {
        var id = finishedGame();
        analysed(id);

        var result = mvc.get().uri("/api/v1/games/{id}/analysis/events", id).accept(MediaType.TEXT_EVENT_STREAM)
                .exchange(Duration.ofSeconds(5));

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
        assertThat(result).bodyText().contains("event:completed");
    }

    @Test
    void followingAFailedAnalysisSaysWhy() {
        var id = finishedGame();
        jobs.put(new AnalysisJob(9, new GameId(UUID.fromString(id)), AnalysisJob.State.FAILED, 3,
                Optional.of("Stockfish is not installed"), Instant.parse("2026-10-26T12:00:00Z")));

        var result = mvc.get().uri("/api/v1/games/{id}/analysis/events", id).accept(MediaType.TEXT_EVENT_STREAM)
                .exchange(Duration.ofSeconds(5));

        assertThat(result).bodyText().contains("event:failed", "Stockfish is not installed");
    }

    @Test
    void followingARunningAnalysisStreamsItsProgress() throws Exception {
        var id = finishedGame();
        var gameId = new GameId(UUID.fromString(id));
        // The worker's side: once the client listens, publish as an analysis would.
        var worker = CompletableFuture.runAsync(() -> {
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (events.listeners(gameId) == 0 && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            events.progress(gameId, 1, 3);
            events.progress(gameId, 2, 3);
            events.progress(gameId, 3, 3);
            events.completed(gameId);
        });

        var result = mvc.get().uri("/api/v1/games/{id}/analysis/events", id).accept(MediaType.TEXT_EVENT_STREAM)
                .exchange(Duration.ofSeconds(10));
        worker.get(5, TimeUnit.SECONDS);

        assertThat(result).bodyText()
                .contains("event:progress", "{\"analysedPositions\":1,\"totalPositions\":3}",
                        "{\"analysedPositions\":3,\"totalPositions\":3}", "event:completed");
        assertThat(events.listeners(gameId)).isZero();
    }

    @Test
    void anAnalysisNeverAskedForCannotBeFollowed() {
        var id = gameAfterOneMove();

        assertThat(mvc.get().uri("/api/v1/games/{id}/analysis/events", id).accept(MediaType.TEXT_EVENT_STREAM))
                .hasStatus(HttpStatus.NOT_FOUND);
    }
}
