package com.arquitechthor.kopi.adapter.persistence;

import com.arquitechthor.kopi.domain.analysis.Evaluation;
import com.arquitechthor.kopi.domain.chess.Color;

/// How an [Evaluation] is stored: text in the engine's own words, from
/// White's point of view (`V3__analysis.sql` checks the format).
///
/// ```
/// Centipawns(34)        ↔ "cp 34"
/// MateIn(-3)            ↔ "mate -3"
/// Checkmate(WHITE)      ↔ "checkmate WHITE"
/// ```
final class EvaluationText {

    private EvaluationText() {}

    static String format(Evaluation evaluation) {
        return switch (evaluation) {
            case Evaluation.Centipawns(var cp) -> "cp " + cp;
            case Evaluation.MateIn(var moves) -> "mate " + moves;
            case Evaluation.Checkmate(var winner) -> "checkmate " + winner;
        };
    }

    /// @throws IllegalArgumentException if `text` is not one of the three
    ///                                  forms (the table forbids it)
    static Evaluation parse(String text) {
        var parts = text.split(" ");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Not a stored evaluation: " + text);
        }
        return switch (parts[0]) {
            case "cp" -> new Evaluation.Centipawns(Integer.parseInt(parts[1]));
            case "mate" -> new Evaluation.MateIn(Integer.parseInt(parts[1]));
            case "checkmate" -> new Evaluation.Checkmate(Color.valueOf(parts[1]));
            default -> throw new IllegalArgumentException("Not a stored evaluation: " + text);
        };
    }
}
