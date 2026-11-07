package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.application.port.BotPlayer;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.application.online.OnlineGames;
import com.arquitechthor.kopi.domain.game.GameMode;
import java.time.Clock;
import java.util.Objects;

/// [PlayMoveUseCase]: load, check the version, play the move of the person
/// at this computer (in a game between two people here, whoever is to
/// move), let the bot reply if the game goes on, save once. If that finished the
/// game, its analysis is queued.
///
/// Saving once at the end means a failure anywhere (an illegal move, the
/// bot being unavailable, someone else saving first) leaves the stored game
/// exactly as it was.
public final class PlayMove implements PlayMoveUseCase {

    private final GameRepository repository;
    private final BotPlayer bot;
    private final AnalysisJobQueue analyses;
    private final Clock clock;
    /// `null` without online play: an online game is then refused.
    private final OnlineGames online;

    public PlayMove(GameRepository repository, BotPlayer bot, AnalysisJobQueue analyses, Clock clock) {
        this(repository, bot, analyses, clock, null);
    }

    /// With online play: in an online game the move goes to the relay first
    /// (ADR-020), and is saved here only once the relay accepts it.
    public PlayMove(GameRepository repository, BotPlayer bot, AnalysisJobQueue analyses, Clock clock,
            OnlineGames online) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.bot = Objects.requireNonNull(bot, "bot");
        this.analyses = Objects.requireNonNull(analyses, "analyses");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.online = online;
    }

    @Override
    public GameView play(PlayMoveCommand command) {
        var loaded = Games.loadAtVersion(repository, command.gameId(), command.expectedVersion());
        if (loaded.mode() == GameMode.ONLINE) {
            if (online == null) {
                throw new IllegalStateException("Online play is not available");
            }
            return GameView.of(online.play(loaded, command.uci()));
        }
        var game = loaded.play(command.uci(), Games.localColor(loaded), clock.instant());
        game = BotTurn.playIfBotToMove(game, bot, clock);
        repository.update(game, loaded.version());
        AnalysisOnFinish.request(analyses, game, clock.instant());
        return GameView.of(game);
    }
}
