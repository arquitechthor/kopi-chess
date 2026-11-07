package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.application.online.OnlineGames;
import com.arquitechthor.kopi.domain.game.GameMode;
import java.time.Clock;
import java.util.Objects;

/// [ResignUseCase]: the person at this computer resigns (in a game between
/// two people here, whoever is to move); the other side wins, and the
/// game's analysis is queued.
public final class Resign implements ResignUseCase {

    private final GameRepository repository;
    private final AnalysisJobQueue analyses;
    private final Clock clock;
    /// `null` without online play: an online game is then refused.
    private final OnlineGames online;

    public Resign(GameRepository repository, AnalysisJobQueue analyses, Clock clock) {
        this(repository, analyses, clock, null);
    }

    /// With online play: in an online game the relay is told first.
    public Resign(GameRepository repository, AnalysisJobQueue analyses, Clock clock, OnlineGames online) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.analyses = Objects.requireNonNull(analyses, "analyses");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.online = online;
    }

    @Override
    public GameView resign(ResignCommand command) {
        var loaded = Games.loadAtVersion(repository, command.gameId(), command.expectedVersion());
        if (loaded.mode() == GameMode.ONLINE) {
            if (online == null) {
                throw new IllegalStateException("Online play is not available");
            }
            return GameView.of(online.resign(loaded));
        }
        var game = loaded.resign(Games.localColor(loaded), clock.instant());
        repository.update(game, loaded.version());
        AnalysisOnFinish.request(analyses, game, clock.instant());
        return GameView.of(game);
    }
}
