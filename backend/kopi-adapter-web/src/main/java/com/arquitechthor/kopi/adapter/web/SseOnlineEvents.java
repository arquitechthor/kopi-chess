package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.application.port.OnlineEvents;
import com.arquitechthor.kopi.domain.game.GameId;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/// [OnlineEvents] for the pages, with Server-Sent Events: a game page or
/// the online page subscribes, and is told `changed` when the relay brings
/// news (a friend's move, an invitation). The page then asks for what
/// changed, as when a move is played on it. The event carries no data.
///
/// A connection lasts half an hour; the browser's `EventSource` reconnects
/// by itself.
@Component
class SseOnlineEvents implements OnlineEvents {

    private static final long TIMEOUT = Duration.ofMinutes(30).toMillis();

    private final Map<GameId, List<SseEmitter>> games = new ConcurrentHashMap<>();
    private final List<SseEmitter> lobby = new CopyOnWriteArrayList<>();

    SseEmitter subscribe(GameId gameId) {
        return track(games.computeIfAbsent(gameId, _ -> new CopyOnWriteArrayList<>()));
    }

    SseEmitter subscribeLobby() {
        return track(lobby);
    }

    private static SseEmitter track(List<SseEmitter> list) {
        var emitter = new SseEmitter(TIMEOUT);
        list.add(emitter);
        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> list.remove(emitter));
        emitter.onError(_ -> list.remove(emitter));
        return emitter;
    }

    @Override
    public void gameChanged(GameId gameId) {
        send(games.getOrDefault(gameId, List.of()));
        send(lobby); // the list of games shows whose turn it is
    }

    @Override
    public void lobbyChanged() {
        send(lobby);
    }

    private static void send(List<SseEmitter> emitters) {
        for (var emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("changed").data("changed"));
            } catch (IOException | IllegalStateException _) {
                emitters.remove(emitter); // the page is gone
            }
        }
    }
}
