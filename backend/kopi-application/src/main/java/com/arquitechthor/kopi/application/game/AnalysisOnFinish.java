package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameStatus;
import java.time.Instant;

/// Finishing a game queues its analysis (plan §8.3: "the analysis starts by
/// itself").
///
/// Called after the game is saved. If queuing fails, the game stays saved
/// and the request still succeeds: the move or the resignation happened,
/// and the analysis can be asked for again. The failure is only logged.
final class AnalysisOnFinish {

    private static final System.Logger log = System.getLogger(AnalysisOnFinish.class.getName());

    private AnalysisOnFinish() {}

    static void request(AnalysisJobQueue queue, Game game, Instant now) {
        if (game.status() != GameStatus.FINISHED || game.moves().isEmpty()) {
            return;
        }
        try {
            queue.enqueue(game.id(), now);
        } catch (RuntimeException e) {
            log.log(System.Logger.Level.WARNING, "Game " + game.id() + " finished but its analysis was not queued", e);
        }
    }
}
