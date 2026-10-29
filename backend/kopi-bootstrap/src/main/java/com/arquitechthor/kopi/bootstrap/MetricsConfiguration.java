package com.arquitechthor.kopi.bootstrap;

import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// Kopi's own metrics (design §14), next to the ones Spring Boot already
/// gives (HTTP, JVM, connection pool…), at `/actuator/prometheus`.
///
/// Where each one is measured:
///
/// - `kopi.bot.move.duration` and `kopi.analysis.duration`: decorators of
///   the ports ([TimedBotPlayer], [TimedAnalysisRepository]), put in place
///   by [UseCaseConfiguration].
/// - `kopi.analysis.queue.size`: here, asking the queue port.
/// - `kopi.engine.restarts`: in the Stockfish adapter, the only one that
///   knows when an engine is replaced.
///
/// Never in the use cases: the application ring stays free of Micrometer.
@Configuration(proxyBeanMethods = false)
class MetricsConfiguration {

    /// Counted in the database when Prometheus scrapes, every 15 s or so:
    /// a `count(*)` on a small table. If the database is down, the gauge
    /// shows no value rather than a stale one.
    @Bean
    MeterBinder analysisQueueMetrics(AnalysisJobQueue queue) {
        return registry -> Gauge.builder("kopi.analysis.queue.size", queue, AnalysisJobQueue::countQueued)
                .description("Analyses waiting for a worker")
                .register(registry);
    }
}
