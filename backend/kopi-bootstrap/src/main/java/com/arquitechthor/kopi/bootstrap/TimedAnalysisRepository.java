package com.arquitechthor.kopi.bootstrap;

import com.arquitechthor.kopi.application.port.AnalysisRepository;
import com.arquitechthor.kopi.domain.analysis.GameAnalysis;
import com.arquitechthor.kopi.domain.game.GameId;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/// An [AnalysisRepository] that records, for every analysis it saves, how
/// long it took: `kopi.analysis.duration` (design §14).
///
/// The analysis carries its own `startedAt` and `completedAt`, so nothing
/// has to be timed around the use case; and only analyses that finished are
/// counted, never an attempt that failed half-way.
final class TimedAnalysisRepository implements AnalysisRepository {

    static final String METRIC = "kopi.analysis.duration";

    private final AnalysisRepository analyses;
    private final Timer duration;

    TimedAnalysisRepository(AnalysisRepository analyses, MeterRegistry registry) {
        this.analyses = Objects.requireNonNull(analyses, "analyses");
        this.duration = Timer.builder(METRIC)
                .description("Time to analyse a whole game, from the first position to the last")
                .publishPercentileHistogram()
                // About 1.5 min for 40 moves on a full core, 3 on half (docs/notas/presupuesto-analisis.md).
                .maximumExpectedValue(Duration.ofMinutes(15))
                .register(registry);
    }

    @Override
    public Optional<GameAnalysis> findByGameId(GameId id) {
        return analyses.findByGameId(id);
    }

    @Override
    public void save(GameAnalysis analysis) {
        analyses.save(analysis);
        duration.record(Duration.between(analysis.startedAt(), analysis.completedAt()));
    }
}
