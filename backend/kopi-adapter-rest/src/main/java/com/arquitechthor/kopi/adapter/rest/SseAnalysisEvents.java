package com.arquitechthor.kopi.adapter.rest;

import com.arquitechthor.kopi.application.port.AnalysisEvents;
import com.arquitechthor.kopi.domain.game.GameId;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/// The [AnalysisEvents] port as Server-Sent Events (design §2.6): whoever
/// follows `GET /games/{id}/analysis/events` gets each event of that game's
/// analysis as it happens.
///
/// ```
/// event: progress
/// data: {"analysedPositions":12,"totalPositions":81}
///
/// event: completed
/// data: {}
/// ```
///
/// A registry of open streams by game. An analysis that ends (`completed`,
/// `failed`) closes them; so do a client that goes away and the timeout.
/// Publishing never throws: a dead stream is just dropped.
@Component
class SseAnalysisEvents implements AnalysisEvents {

    /// How long a stream stays open without the analysis ending: longer
    /// than any analysis should take (about two minutes for 40 moves).
    static final Duration TIMEOUT = Duration.ofMinutes(15);

    /// `progress` data.
    record Progress(int analysedPositions, int totalPositions) {}

    /// `failed` data.
    record Failure(String reason) {}

    /// `completed` data: nothing to say but that it is done.
    record Done() {}

    private final Map<GameId, List<SseEmitter>> streams = new ConcurrentHashMap<>();

    /// A new stream for this game's events.
    SseEmitter subscribe(GameId gameId) {
        var emitter = new SseEmitter(TIMEOUT.toMillis());
        streams.computeIfAbsent(gameId, _ -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable forget = () -> forget(gameId, emitter);
        emitter.onCompletion(forget);
        emitter.onTimeout(forget);
        emitter.onError(_ -> forget.run());
        return emitter;
    }

    /// Sends one event to a single stream and ends it: for a client that
    /// arrives after the analysis ended.
    static void sendFinal(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
        }
    }

    @Override
    public void progress(GameId gameId, int analysedPositions, int totalPositions) {
        send(gameId, "progress", new Progress(analysedPositions, totalPositions), false);
    }

    @Override
    public void completed(GameId gameId) {
        send(gameId, "completed", new Done(), true);
    }

    @Override
    public void failed(GameId gameId, String reason) {
        send(gameId, "failed", new Failure(reason), true);
    }

    private void send(GameId gameId, String name, Object data, boolean last) {
        for (var emitter : streams.getOrDefault(gameId, List.of())) {
            try {
                emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
                if (last) {
                    emitter.complete();
                }
            } catch (IOException | IllegalStateException _) {
                // The client went away; Spring calls onError/onCompletion, which forget it.
                forget(gameId, emitter);
            }
        }
        if (last) {
            streams.remove(gameId);
        }
    }

    private void forget(GameId gameId, SseEmitter emitter) {
        streams.computeIfPresent(gameId, (_, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    /// How many streams are open for this game (for tests).
    int listeners(GameId gameId) {
        return streams.getOrDefault(gameId, List.of()).size();
    }
}
