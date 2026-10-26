package com.arquitechthor.kopi.domain.analysis;

import com.arquitechthor.kopi.domain.chess.Color;

/// How likely one side is to win, from `-1` (lost) through `0` (even) to
/// `1` (won). It is what moves are judged by, instead of raw centipawns
/// (design §8.3, ADR-005): dropping two pawns matters a lot at 0.00 and
/// hardly at all at +9.00, and the curve below says exactly that.
///
/// The curve is the one Lichess publishes, fitted on its own games:
/// `2 / (1 + exp(-0.00368208 · cp)) - 1`, with the centipawns capped at
/// ±1000 first. A forced mate counts as a certain result.
///
/// @param value from `-1` to `1`
public record WinChance(double value) {

    /// Beyond a thousand centipawns (ten pawns), more is not more winning.
    static final int CENTIPAWN_CAP = 1000;

    private static final double SLOPE = 0.00368208;

    public WinChance {
        if (!(value >= -1 && value <= 1)) {
            throw new IllegalArgumentException("A win chance goes from -1 to 1: " + value);
        }
    }

    /// The win chance of `side` in a position the engine scored `evaluation`.
    public static WinChance of(Evaluation evaluation, Color side) {
        double forWhite = switch (evaluation) {
            case Evaluation.Centipawns(var cp) -> {
                int capped = Math.clamp(cp, -CENTIPAWN_CAP, CENTIPAWN_CAP);
                yield 2 / (1 + Math.exp(-SLOPE * capped)) - 1;
            }
            case Evaluation.MateIn(var moves) -> moves > 0 ? 1 : -1;
            case Evaluation.Checkmate(var winner) -> winner == Color.WHITE ? 1 : -1;
        };
        return new WinChance(side == Color.WHITE ? forWhite : -forWhite);
    }

    /// The same chance as a percentage, Lichess's "Win %": `0` to `100`.
    public double percent() {
        return 50 + 50 * value;
    }

    /// How much was given away going from this chance to `after`; negative
    /// if `after` is better.
    public double lossTo(WinChance after) {
        return value - after.value;
    }
}
