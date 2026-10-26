package com.arquitechthor.kopi.domain.analysis;

import java.util.Objects;

/// How a game was analysed, kept with the result (design §8.2): with other
/// settings the same game can be judged differently, so the numbers only
/// mean something next to the settings that produced them.
///
/// @param limits          how far the engine searched each position
/// @param multiPv         how many candidate lines it was asked for
/// @param missedMateLimit the longest mate whose loss is a `MISSED_MATE`
///                        (see [MoveClassifier])
public record AnalysisSettings(SearchLimits limits, int multiPv, int missedMateLimit) {

    public AnalysisSettings {
        Objects.requireNonNull(limits, "limits");
        if (multiPv < 1) {
            throw new IllegalArgumentException("MultiPV must be at least 1: " + multiPv);
        }
        if (missedMateLimit < 1) {
            throw new IllegalArgumentException("The missed mate limit must be at least 1: " + missedMateLimit);
        }
    }
}
