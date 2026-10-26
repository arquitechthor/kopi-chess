package com.arquitechthor.kopi.application.port;

import com.arquitechthor.kopi.domain.analysis.Evaluation;
import com.arquitechthor.kopi.domain.chess.Move;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// What the engine said about one position.
///
/// @param engine     the engine's name and version (`Stockfish 19`), kept
///                   with the analysis (design §8.2)
/// @param evaluation the position's evaluation, from White's point of
///                   view: the best line's
/// @param lines      the candidate lines, best first; empty when the game
///                   is over in this position
public record EngineAnalysis(String engine, Evaluation evaluation, List<Line> lines) {

    public EngineAnalysis {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(evaluation, "evaluation");
        lines = List.copyOf(lines);
    }

    /// One candidate line.
    ///
    /// @param evaluation where it leads, from White's point of view
    /// @param moves      the moves, the first one being the candidate;
    ///                   never empty
    public record Line(Evaluation evaluation, List<Move> moves) {

        public Line {
            Objects.requireNonNull(evaluation, "evaluation");
            moves = List.copyOf(moves);
            if (moves.isEmpty()) {
                throw new IllegalArgumentException("A line has at least one move");
            }
        }
    }

    /// The engine's best move; empty when the game is over.
    public Optional<Move> bestMove() {
        return lines.stream().findFirst().map(line -> line.moves().getFirst());
    }
}
