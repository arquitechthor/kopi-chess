package com.arquitechthor.kopi.domain.analysis;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Opening;
import com.arquitechthor.kopi.domain.game.GameId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// A finished game, judged move by move: the result of an analysis
/// (design §8).
///
/// The [#summary] is not kept apart: it is worked out from the moves, so it
/// can never disagree with them.
///
/// @param gameId      the game analysed
/// @param engine      the engine's name and version (`Stockfish 19`)
/// @param settings    how it was analysed
/// @param firstMover  who played the first move (Black in a game set up
///                    with Black to move)
/// @param opening     the last named opening the game went through, if any
///                    (design §9)
/// @param moves       every move of the game, in order, from ply 1
/// @param startedAt   when the analysis started
/// @param completedAt when it finished
public record GameAnalysis(GameId gameId, String engine, AnalysisSettings settings, Color firstMover,
                           Optional<Opening> opening, List<MoveAnalysis> moves, Instant startedAt,
                           Instant completedAt) {

    public GameAnalysis {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(firstMover, "firstMover");
        Objects.requireNonNull(opening, "opening");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(completedAt, "completedAt");
        moves = List.copyOf(moves);
        if (moves.isEmpty()) {
            throw new IllegalArgumentException("A game without moves has nothing to analyse");
        }
        for (int i = 0; i < moves.size(); i++) {
            var move = moves.get(i);
            if (move.ply() != i + 1) {
                throw new IllegalArgumentException("Move " + (i + 1) + " is ply " + move.ply());
            }
            // One search per position (design §8.1): what follows a move is
            // what precedes the next one.
            if (i > 0 && !move.before().equals(moves.get(i - 1).after())) {
                throw new IllegalArgumentException("Ply " + move.ply() + " does not start where ply "
                        + (i) + " ended: " + move.before() + " after " + moves.get(i - 1).after());
            }
        }
        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("Completed before it started: " + startedAt + " > " + completedAt);
        }
    }

    /// Counts and accuracy of each side.
    public AnalysisSummary summary() {
        var positions = new ArrayList<Evaluation>();
        positions.add(moves.getFirst().before());
        moves.forEach(move -> positions.add(move.after()));
        return AnalysisSummary.of(firstMover, positions,
                moves.stream().map(move -> move.classification().category()).toList());
    }
}
