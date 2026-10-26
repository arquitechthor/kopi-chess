package com.arquitechthor.kopi.adapter.rest;

import static com.arquitechthor.kopi.adapter.rest.RestAdapterConfiguration.API;

import com.arquitechthor.kopi.adapter.rest.ApiModel.AnalysisResponse;
import com.arquitechthor.kopi.application.analysis.AnalysisView;
import com.arquitechthor.kopi.application.analysis.GetAnalysisUseCase;
import com.arquitechthor.kopi.application.analysis.RequestAnalysisUseCase;
import com.arquitechthor.kopi.application.port.AnalysisJob;
import com.arquitechthor.kopi.domain.game.GameId;
import java.io.Serial;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/// `/api/v1/games/{id}/analysis` (design §10): ask for it, read it, and
/// follow it live.
///
/// Analysing is slow and happens in the background (ADR-007): `POST`
/// answers `202 Accepted` at once, `GET` gives the result or how it is
/// going, and `/events` streams its progress (Server-Sent Events, §2.6).
@RestController
@RequestMapping(API + "/games/{id}/analysis")
class AnalysisController {

    private final RequestAnalysisUseCase requestAnalysis;
    private final GetAnalysisUseCase getAnalysis;
    private final SseAnalysisEvents events;

    AnalysisController(RequestAnalysisUseCase requestAnalysis, GetAnalysisUseCase getAnalysis,
            SseAnalysisEvents events) {
        this.requestAnalysis = requestAnalysis;
        this.getAnalysis = getAnalysis;
        this.events = events;
    }

    /// Queues the analysis: `202 Accepted`. Idempotent: asking again
    /// returns the same job, wherever it is.
    @PostMapping
    ResponseEntity<AnalysisResponse> request(@PathVariable UUID id) {
        var job = requestAnalysis.request(new GameId(id));
        return ResponseEntity.accepted().location(URI.create(API + "/games/" + id + "/analysis"))
                .body(AnalysisResponse.of(job));
    }

    /// The result once `DONE`; until then, the job's state.
    ///
    /// @throws AnalysisNotFoundException if it was never asked for (`404`)
    @GetMapping
    AnalysisResponse get(@PathVariable UUID id) {
        return AnalysisResponse.of(requested(new GameId(id)));
    }

    /// The analysis as it happens: `progress` events, then `completed` or
    /// `failed`, and the stream ends. If it has already ended, that last
    /// event comes at once.
    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter follow(@PathVariable UUID id) {
        var gameId = new GameId(id);
        requested(gameId);
        var emitter = events.subscribe(gameId);
        // Read again after subscribing: an analysis that ended in between
        // must not leave the client waiting for an event already sent.
        var now = getAnalysis.get(gameId);
        if (now.isDone()) {
            SseAnalysisEvents.sendFinal(emitter, "completed", new SseAnalysisEvents.Done());
        } else if (now.job().map(AnalysisJob::state).orElse(null) == AnalysisJob.State.FAILED) {
            SseAnalysisEvents.sendFinal(emitter, "failed",
                    new SseAnalysisEvents.Failure(now.job().flatMap(AnalysisJob::lastError).orElse("")));
        }
        return emitter;
    }

    /// The game's analysis view, if its analysis was ever asked for.
    private AnalysisView requested(GameId gameId) {
        var view = getAnalysis.get(gameId);
        if (view.job().isEmpty() && view.analysis().isEmpty()) {
            throw new AnalysisNotFoundException(gameId);
        }
        return view;
    }

    /// The game exists, but nobody asked for its analysis (REST: `404`).
    static final class AnalysisNotFoundException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        private final transient GameId gameId;

        AnalysisNotFoundException(GameId gameId) {
            super("No analysis of game " + gameId);
            this.gameId = gameId;
        }

        GameId gameId() {
            return gameId;
        }
    }
}
