package com.arquitechthor.kopi.application.analysis;

import com.arquitechthor.kopi.application.port.AnalysisEvents;
import com.arquitechthor.kopi.application.port.AnalysisJob;
import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.application.port.AnalysisRepository;
import com.arquitechthor.kopi.application.port.ChessEngine;
import com.arquitechthor.kopi.application.port.EngineAnalysis;
import com.arquitechthor.kopi.application.port.EngineUnavailableException;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.application.port.OpeningBook;
import com.arquitechthor.kopi.domain.analysis.AnalysisSettings;
import com.arquitechthor.kopi.domain.analysis.GameAnalysis;
import com.arquitechthor.kopi.domain.analysis.MoveAnalysis;
import com.arquitechthor.kopi.domain.analysis.MoveClassifier;
import com.arquitechthor.kopi.domain.chess.Move;
import com.arquitechthor.kopi.domain.chess.Opening;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.chess.San;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameStatus;
import com.arquitechthor.kopi.domain.game.PlayedMove;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// [RunAnalysisJobUseCase]: the analysis of one game (design §8.1).
///
/// 1. Claim the oldest queued job.
/// 2. Search **each position once**, the starting one included: N moves,
///    N + 1 searches. What the engine says after a move is what it says
///    before the next one.
/// 3. Judge every move with [MoveClassifier]. A move is `BOOK` while every
///    position so far, its own included, has a name in the opening book;
///    the first one that has none ends the book for the rest of the game.
///    The game's opening is the last named position it went through.
/// 4. Save the [GameAnalysis] and mark the job `DONE`.
///
/// Along the way it tells [AnalysisEvents] how far it got after every
/// position, and how it ended. Publishing is best effort: a listener that
/// fails never fails the analysis.
///
/// If the engine cannot answer, the job goes back to the queue until it has
/// been tried `maxAttempts` times, then `FAILED`. Anything else (the game
/// is gone, a bug) fails it at once: trying again would fail the same way.
public final class RunAnalysisJob implements RunAnalysisJobUseCase {

    private final AnalysisJobQueue queue;
    private final GameRepository games;
    private final ChessEngine engine;
    private final OpeningBook book;
    private final AnalysisRepository analyses;
    private final AnalysisEvents events;
    private final AnalysisSettings settings;
    private final int maxAttempts;
    private final Clock clock;

    /// @param settings    how every game is analysed
    /// @param maxAttempts how many times a job is tried before it fails
    public RunAnalysisJob(AnalysisJobQueue queue, GameRepository games, ChessEngine engine, OpeningBook book,
            AnalysisRepository analyses, AnalysisEvents events, AnalysisSettings settings, int maxAttempts,
            Clock clock) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.games = Objects.requireNonNull(games, "games");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.book = Objects.requireNonNull(book, "book");
        this.analyses = Objects.requireNonNull(analyses, "analyses");
        this.events = Objects.requireNonNull(events, "events");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("At least one attempt: " + maxAttempts);
        }
        this.maxAttempts = maxAttempts;
    }

    @Override
    public boolean runNext() {
        var claimed = queue.claimNext(clock.instant());
        if (claimed.isEmpty()) {
            return false;
        }
        var job = claimed.get();
        try {
            analyses.save(analyse(job));
            queue.complete(job.id());
            publish(() -> events.completed(job.gameId()));
        } catch (EngineUnavailableException e) {
            if (job.attempts() < maxAttempts) {
                queue.retry(job.id(), describe(e));
            } else {
                fail(job, describe(e));
            }
        } catch (RuntimeException e) {
            fail(job, describe(e));
        }
        return true;
    }

    private void fail(AnalysisJob job, String reason) {
        queue.fail(job.id(), reason);
        publish(() -> events.failed(job.gameId(), reason));
    }

    /// Tells the listeners, whatever they do: the analysis is what matters.
    private static void publish(Runnable event) {
        try {
            event.run();
        } catch (RuntimeException _) {
            // AnalysisEvents promises not to throw; this keeps the promise for it
        }
    }

    private GameAnalysis analyse(AnalysisJob job) {
        var startedAt = clock.instant();
        var game = games.findById(job.gameId())
                .orElseThrow(() -> new IllegalStateException("The game no longer exists"));
        if (game.status() != GameStatus.FINISHED || game.moves().isEmpty()) {
            throw new IllegalStateException("Only a finished game with moves can be analysed");
        }

        var positions = positions(game);
        var verdicts = new ArrayList<EngineAnalysis>();
        for (var position : positions) {
            verdicts.add(engine.analyse(position, settings.limits(), settings.multiPv()));
            var analysed = verdicts.size();
            publish(() -> events.progress(game.id(), analysed, positions.size()));
        }

        var classifier = new MoveClassifier(settings.missedMateLimit());
        var moves = new ArrayList<MoveAnalysis>();
        Optional<Opening> opening = Optional.empty();
        var stillInBook = true;
        for (int i = 0; i < game.moves().size(); i++) {
            var played = game.moves().get(i);
            var position = positions.get(i);
            var before = verdicts.get(i);
            var after = verdicts.get(i + 1);

            var named = book.lookup(played.positionAfter());
            if (named.isPresent()) {
                opening = named;
            }
            stillInBook = stillInBook && named.isPresent();

            var bestLine = before.lines().getFirst().moves();
            var classification = classifier.classify(new MoveClassifier.Facts(position.sideToMove(),
                    played.move(), bestLine.getFirst(), before.evaluation(), after.evaluation(), stillInBook,
                    position.legalMoves().size()));
            moves.add(new MoveAnalysis(played.ply(), before.evaluation(), after.evaluation(),
                    bestLine.getFirst().toUci(), san(position, bestLine), classification));
        }

        return new GameAnalysis(game.id(), verdicts.getFirst().engine(), settings,
                game.initialPosition().sideToMove(), opening, moves, startedAt, clock.instant());
    }

    /// The starting position and the one after each move.
    private static List<Position> positions(Game game) {
        var positions = new ArrayList<Position>();
        positions.add(game.initialPosition());
        game.moves().stream().map(PlayedMove::positionAfter).forEach(positions::add);
        return positions;
    }

    /// `line` in SAN, played from `position`.
    private static List<String> san(Position position, List<Move> line) {
        var san = new ArrayList<String>();
        var current = position;
        for (var move : line) {
            san.add(San.format(current, move));
            current = current.apply(move);
        }
        return san;
    }

    /// What goes in the job's `last_error`: the exception and its cause.
    private static String describe(RuntimeException e) {
        var message = Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName());
        var cause = e.getCause();
        return cause == null || cause.getMessage() == null ? message : message + ": " + cause.getMessage();
    }
}
