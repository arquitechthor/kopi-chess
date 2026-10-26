package com.arquitechthor.kopi.domain.analysis;

import com.arquitechthor.kopi.domain.chess.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.stream.Gatherers;
import java.util.stream.IntStream;

/// A whole game in a few numbers: how many moves of each category each
/// side played, and each side's accuracy (design §8.4).
///
/// @param white what White played
/// @param black what Black played
public record AnalysisSummary(Side white, Side black) {

    public AnalysisSummary {
        Objects.requireNonNull(white, "white");
        Objects.requireNonNull(black, "black");
    }

    /// One side's part of the summary.
    ///
    /// @param accuracy from 0 to 100; empty if the side never moved
    /// @param counts   how many moves of each category it played
    public record Side(OptionalDouble accuracy, Map<MoveCategory, Integer> counts) {

        public Side {
            Objects.requireNonNull(accuracy, "accuracy");
            var all = new EnumMap<MoveCategory, Integer>(MoveCategory.class);
            for (var category : MoveCategory.values()) {
                all.put(category, counts.getOrDefault(category, 0));
            }
            counts = Collections.unmodifiableMap(all);
        }

        /// How many moves of `category` this side played.
        public int count(MoveCategory category) {
            return counts.get(category);
        }
    }

    /// The summary of one side.
    public Side of(Color color) {
        return color == Color.WHITE ? white : black;
    }

    /// Sums up an analysed game.
    ///
    /// The accuracy of a side is Lichess's: the average of two means of its
    /// move accuracies. One is harmonic, so blunders weigh heavily; the
    /// other is weighted by how volatile the game was around each move (the
    /// standard deviation of the Win % in a window of positions, kept
    /// within 0.5–12), so moves in sharp positions count more than moves in
    /// quiet ones. Windows hold a tenth of the game, between 2 and 8
    /// positions.
    ///
    /// @param firstMover who played the first move (Black, in a game set up
    ///                   from a position with Black to move)
    /// @param positions  the evaluation of every position of the game, the
    ///                   starting one included: one more than the moves
    /// @param categories the category of every move, in order
    public static AnalysisSummary of(Color firstMover, List<Evaluation> positions, List<MoveCategory> categories) {
        Objects.requireNonNull(firstMover, "firstMover");
        if (positions.size() != categories.size() + 1) {
            throw new IllegalArgumentException("One more position than moves: "
                    + positions.size() + " positions, " + categories.size() + " moves");
        }
        // Each move is the step from one position to the next.
        var steps = positions.stream().gather(Gatherers.windowSliding(2)).toList();
        var weights = volatility(positions);

        var accuracies = new EnumMap<Color, List<Double>>(Color.class);
        var weightsBySide = new EnumMap<Color, List<Double>>(Color.class);
        var counts = new EnumMap<Color, Map<MoveCategory, Integer>>(Color.class);
        for (var color : Color.values()) {
            accuracies.put(color, new ArrayList<>());
            weightsBySide.put(color, new ArrayList<>());
            counts.put(color, new EnumMap<>(MoveCategory.class));
        }
        for (int ply = 0; ply < categories.size(); ply++) {
            var mover = ply % 2 == 0 ? firstMover : firstMover.opposite();
            var step = steps.get(ply);
            accuracies.get(mover).add(Accuracy.ofMove(step.get(0).winChanceFor(mover), step.get(1).winChanceFor(mover)));
            weightsBySide.get(mover).add(weights.get(ply));
            counts.get(mover).merge(categories.get(ply), 1, Integer::sum);
        }
        return new AnalysisSummary(
                side(accuracies.get(Color.WHITE), weightsBySide.get(Color.WHITE), counts.get(Color.WHITE)),
                side(accuracies.get(Color.BLACK), weightsBySide.get(Color.BLACK), counts.get(Color.BLACK)));
    }

    private static Side side(List<Double> accuracies, List<Double> weights, Map<MoveCategory, Integer> counts) {
        if (accuracies.isEmpty()) {
            return new Side(OptionalDouble.empty(), counts);
        }
        double accuracy = (Accuracy.weightedMean(accuracies, weights) + Accuracy.harmonicMean(accuracies)) / 2;
        return new Side(OptionalDouble.of(accuracy), counts);
    }

    /// One weight per move: how much White's Win % moves around in a window
    /// of positions. The first windows repeat the opening one, so there are
    /// exactly as many windows as moves.
    private static List<Double> volatility(List<Evaluation> positions) {
        var winPercents = positions.stream().map(e -> e.winChanceFor(Color.WHITE).percent()).toList();
        int moves = positions.size() - 1;
        if (moves == 0) {
            return List.of();
        }
        int size = Math.clamp(moves / 10, 2, 8);
        var first = winPercents.subList(0, size);
        var windows = new ArrayList<List<Double>>();
        IntStream.range(0, size - 2).forEach(_ -> windows.add(first));
        windows.addAll(winPercents.stream().gather(Gatherers.windowSliding(size)).toList());
        return windows.stream().map(window -> Math.clamp(standardDeviation(window), 0.5, 12)).toList();
    }

    private static double standardDeviation(List<Double> values) {
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double variance = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElseThrow();
        return Math.sqrt(variance);
    }
}
