package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.application.port.IdGenerator;
import com.arquitechthor.kopi.domain.chess.Pgn;
import com.arquitechthor.kopi.domain.game.Game;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;

/// [ImportPgnUseCase]: read, check, store, and queue the analysis.
///
/// The PGN is read by the domain ([Pgn#parse]: every move legal) and the
/// game built by it ([Game#imported]: how it ended). Like finishing a game
/// here, importing one queues its analysis; if queuing fails, the game is
/// still imported.
public final class ImportPgn implements ImportPgnUseCase {

    /// One game is a few kilobytes; design §12 suggests 100 KB at most.
    public static final int DEFAULT_LIMIT_BYTES = 100 * 1024;

    private final GameRepository repository;
    private final IdGenerator ids;
    private final AnalysisJobQueue analyses;
    private final Clock clock;
    private final int limitBytes;

    public ImportPgn(GameRepository repository, IdGenerator ids, AnalysisJobQueue analyses, Clock clock,
            int limitBytes) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.analyses = Objects.requireNonNull(analyses, "analyses");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (limitBytes < 1) {
            throw new IllegalArgumentException("The limit must be positive: " + limitBytes);
        }
        this.limitBytes = limitBytes;
    }

    @Override
    public GameView importPgn(String pgn) {
        Objects.requireNonNull(pgn, "pgn");
        // Counted before reading anything: a huge text is refused unread.
        if (pgn.length() > limitBytes || pgn.getBytes(StandardCharsets.UTF_8).length > limitBytes) {
            throw new PgnTooLargeException(limitBytes);
        }
        var game = Game.imported(ids.newGameId(), Pgn.parse(pgn), clock.instant());
        repository.insert(game);
        AnalysisOnFinish.request(analyses, game, clock.instant());
        return GameView.of(game);
    }
}
