package com.arquitechthor.kopi.bootstrap;

import com.arquitechthor.kopi.domain.analysis.AnalysisSettings;
import com.arquitechthor.kopi.domain.analysis.SearchLimits;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// `kopi.analysis.*` in `application.yml`: how games are analysed, and how
/// the background worker runs (design §8.2, ADR-007).
///
/// @param nodes           nodes searched per position (the default: they
///                        bound the time, a depth does not; see
///                        docs/notas/presupuesto-analisis.md)
/// @param depth           a fixed depth instead of nodes, if set
/// @param multiPv         candidate lines asked for per position
/// @param missedMateLimit the longest mate whose loss is a `MISSED_MATE`
/// @param maxAttempts     how many times a job is tried before it fails
/// @param workers         how many games are analysed at once
/// @param pollInterval    how long a worker waits when the queue is empty
/// @param enabled         whether workers run at all (the jobs are queued
///                        anyway)
@ConfigurationProperties("kopi.analysis")
record AnalysisProperties(
        @DefaultValue("1000000") long nodes,
        Integer depth,
        @DefaultValue("2") int multiPv,
        @DefaultValue("5") int missedMateLimit,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("1") int workers,
        @DefaultValue("2s") Duration pollInterval,
        @DefaultValue("true") boolean enabled) {

    /// The settings every analysis is run and stored with.
    AnalysisSettings settings() {
        SearchLimits limits = depth == null ? new SearchLimits.Nodes(nodes) : new SearchLimits.Depth(depth);
        return new AnalysisSettings(limits, multiPv, missedMateLimit);
    }
}
