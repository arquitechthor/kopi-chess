package com.arquitechthor.kopi.adapter.stockfish;

import com.arquitechthor.kopi.application.port.ChessEngine;
import com.arquitechthor.kopi.application.port.EngineAnalysis;
import com.arquitechthor.kopi.application.port.EngineUnavailableException;
import com.arquitechthor.kopi.domain.analysis.Evaluation;
import com.arquitechthor.kopi.domain.analysis.SearchLimits;
import com.arquitechthor.kopi.domain.chess.Move;
import com.arquitechthor.kopi.domain.chess.Position;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// The [ChessEngine] port: Stockfish at full strength, judging positions
/// for post-game analysis (design §8).
///
/// It has **its own pool of engines**, apart from the bots': an analysis
/// searches dozens of positions in a row, and a person's game must not
/// wait behind it.
///
/// Engines are configured on every loan (full strength, `MultiPV`), a few
/// lines of UCI next to a deep search. Every position starts from a clean
/// engine (`ucinewgame`): nothing searched before, in this game or another,
/// on this engine or another of the pool, changes its result.
///
/// A position where the game is already over is not sent to the engine at
/// all: Stockfish would answer `mate 0` with no move, and the rules
/// already know the answer.
public final class StockfishAnalysis implements ChessEngine, AutoCloseable {

    private final EnginePool<UciEngine> pool;
    private final Duration positionTimeout;

    /// @param pool            the analysis engines
    /// @param positionTimeout how long one position may take before the
    ///                        search is given up
    StockfishAnalysis(EnginePool<UciEngine> pool, Duration positionTimeout) {
        this.pool = Objects.requireNonNull(pool, "pool");
        this.positionTimeout = Objects.requireNonNull(positionTimeout, "positionTimeout");
    }

    /// Ends the engine processes.
    @Override
    public void close() {
        pool.close();
    }

    @Override
    public EngineAnalysis analyse(Position position, SearchLimits limits, int multiPv) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(limits, "limits");
        if (multiPv < 1) {
            throw new IllegalArgumentException("MultiPV must be at least 1: " + multiPv);
        }
        try {
            return withOneRetry(position, limits, multiPv);
        } catch (UciException | IllegalArgumentException e) {
            throw new EngineUnavailableException("Cannot analyse " + position.toFen(), e);
        }
    }

    /// As the bots do: an engine that dies or misbehaves is replaced by the
    /// pool, and the position is tried once more on a fresh one.
    private EngineAnalysis withOneRetry(Position position, SearchLimits limits, int multiPv) {
        try {
            return pool.withEngine(engine -> analyse(engine, position, limits, multiPv));
        } catch (UciException firstFailure) {
            try {
                return pool.withEngine(engine -> analyse(engine, position, limits, multiPv));
            } catch (UciException e) {
                e.addSuppressed(firstFailure);
                throw e;
            }
        }
    }

    private EngineAnalysis analyse(UciEngine engine, Position position, SearchLimits limits, int multiPv) {
        if (position.legalMoves().isEmpty()) {
            return new EngineAnalysis(engine.name(), gameOver(position), List.of());
        }
        engine.newGame();
        engine.setOption("Skill Level", 20);
        engine.setOption("UCI_LimitStrength", false);
        engine.setOption("MultiPV", multiPv);
        engine.waitUntilReady();
        var go = switch (limits) {
            case SearchLimits.Depth(var plies) -> "depth " + plies;
            case SearchLimits.Nodes(var count) -> "nodes " + count;
        };
        var result = engine.search(position.toFen(), go, positionTimeout);
        var lines = new ArrayList<EngineAnalysis.Line>();
        for (var info : result.lines()) {
            lines.add(line(position, info));
        }
        if (lines.isEmpty()) {
            throw new UciException("No scored line for " + position.toFen());
        }
        return new EngineAnalysis(engine.name(), lines.getFirst().evaluation(), lines);
    }

    /// Checkmate or stalemate: the side to move has no legal move.
    private static Evaluation gameOver(Position position) {
        return position.isInCheck()
                ? new Evaluation.Checkmate(position.sideToMove().opposite())
                : new Evaluation.Centipawns(0);
    }

    /// One candidate line, its moves matched against the legal moves of
    /// each position along it (text from the engine never becomes a `Move`
    /// on its own) and its score turned to White's point of view.
    private static EngineAnalysis.Line line(Position position, InfoLine info) {
        var moves = new ArrayList<Move>();
        var current = position;
        for (var uci : info.pv()) {
            var move = current.legalMoves().stream()
                    .filter(candidate -> candidate.toUci().equals(uci))
                    .findFirst()
                    .orElseThrow(() -> new UciException("The engine's line plays " + uci
                            + ", not a legal move, in " + position.toFen()));
            moves.add(move);
            current = current.apply(move);
        }
        if (moves.isEmpty()) {
            throw new UciException("A line without moves for " + position.toFen());
        }
        var evaluation = switch (info.score().forWhite(position.sideToMove())) {
            case InfoLine.Score.Centipawns(var cp) -> new Evaluation.Centipawns(cp);
            case InfoLine.Score.MateIn(var mateIn) when mateIn != 0 -> new Evaluation.MateIn(mateIn);
            case InfoLine.Score.MateIn _ ->
                throw new UciException("mate 0 with moves to play in " + position.toFen());
        };
        return new EngineAnalysis.Line(evaluation, moves);
    }
}
