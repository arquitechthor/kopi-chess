package com.arquitechthor.kopi.domain.analysis;

import com.arquitechthor.kopi.domain.analysis.MoveClassifier.Classification;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// The verdict on one move of an analysed game.
///
/// The engine's suggestion is kept as text, ready to show: the best move in
/// UCI (to draw it on the board) and its line in SAN (to read it, design
/// §8.6).
///
/// @param ply            which move: 1 for the game's first
/// @param before         the evaluation of the position before the move
/// @param after          the evaluation of the position after it
/// @param bestMove       the engine's move in the position before, in UCI
/// @param bestLine       the engine's line from there, in SAN, starting with
///                       the best move
/// @param classification the move's category and why
public record MoveAnalysis(int ply, Evaluation before, Evaluation after, String bestMove, List<String> bestLine,
                           Classification classification) {

    public MoveAnalysis {
        if (ply < 1) {
            throw new IllegalArgumentException("Plies start at 1: " + ply);
        }
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(bestMove, "bestMove");
        Objects.requireNonNull(classification, "classification");
        bestLine = List.copyOf(bestLine);
        if (bestLine.isEmpty()) {
            throw new IllegalArgumentException("The best line has at least the best move");
        }
    }

    /// The engine's mate that was missed, with its line, if this move was a
    /// `MISSED_MATE`: "the engine found mate in n" (design §8.6).
    public Optional<List<String>> missedMateLine() {
        return classification.category() == MoveCategory.MISSED_MATE ? Optional.of(bestLine) : Optional.empty();
    }
}
