package com.arquitechthor.kopi.domain.analysis;

import java.util.List;

/// Accuracy, from 0 to 100: how close a player stayed to the engine's play
/// (design §8.4). The formulas are Lichess's public ones, so a Kopi
/// accuracy reads like the number players already know.
public final class Accuracy {

    /// An accuracy below 1 counts as 1 in a harmonic mean: a single 0 would
    /// otherwise divide by zero and wipe out the whole game.
    private static final double HARMONIC_FLOOR = 1;

    private Accuracy() {}

    /// The accuracy of one move, from the mover's win chance before and
    /// after it: `103.1668 · exp(-0.04354 · (Win% before - Win% after)) -
    /// 3.1669`, kept within 0–100. A move that loses nothing scores 100.
    public static double ofMove(WinChance before, WinChance after) {
        double dropped = before.percent() - after.percent();
        if (dropped <= 0) {
            return 100;
        }
        return Math.clamp(103.1668 * Math.exp(-0.04354 * dropped) - 3.1669, 0, 100);
    }

    /// The harmonic mean of some accuracies: it pulls towards the worst
    /// moves, so a few blunders are not hidden by many easy moves.
    static double harmonicMean(List<Double> accuracies) {
        if (accuracies.isEmpty()) {
            throw new IllegalArgumentException("No accuracies to average");
        }
        double inverses = accuracies.stream().mapToDouble(a -> 1 / Math.max(a, HARMONIC_FLOOR)).sum();
        return accuracies.size() / inverses;
    }

    /// The mean of `accuracies` weighted by `weights`, position by position.
    static double weightedMean(List<Double> accuracies, List<Double> weights) {
        if (accuracies.isEmpty() || accuracies.size() != weights.size()) {
            throw new IllegalArgumentException(
                    "One weight per accuracy: " + accuracies.size() + " and " + weights.size());
        }
        double sum = 0;
        double totalWeight = 0;
        for (int i = 0; i < accuracies.size(); i++) {
            sum += accuracies.get(i) * weights.get(i);
            totalWeight += weights.get(i);
        }
        return sum / totalWeight;
    }
}
