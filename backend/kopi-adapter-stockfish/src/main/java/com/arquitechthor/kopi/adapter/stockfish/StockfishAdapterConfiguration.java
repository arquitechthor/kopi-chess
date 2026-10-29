package com.arquitechthor.kopi.adapter.stockfish;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.nio.file.Path;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// Spring configuration of the Stockfish adapter, imported explicitly by
/// `kopi-bootstrap`.
///
/// Nothing starts here: the pool starts engines when the first Stockfish
/// move is asked for, so the application starts without Stockfish and the
/// own bots still play.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StockfishProperties.class)
public class StockfishAdapterConfiguration {

    /// Closed on shutdown (`close()` is inferred), which ends every engine
    /// process with `quit`.
    @Bean
    EnginePool<UciEngine> stockfishPool(StockfishProperties properties) {
        var binary = Path.of(properties.path());
        return new EnginePool<>(properties.poolSize(),
                () -> UciEngine.start(UciProcess.start(binary), properties.startTimeout()),
                UciEngine::isAlive, properties.waitTimeout());
    }

    /// The `stockfish` component of `/actuator/health`.
    @Bean
    StockfishHealthIndicator stockfishHealthIndicator(EnginePool<UciEngine> stockfishPool) {
        return new StockfishHealthIndicator(stockfishPool);
    }

    @Bean
    StockfishBots stockfishBots(EnginePool<UciEngine> stockfishPool, StockfishProperties properties) {
        return new StockfishBots(stockfishPool, properties.profiles(), properties.moveMargin());
    }

    /// The analysis engines, apart from the bots'. `Threads` and `Hash` are
    /// set once, when each engine starts: changing `Hash` clears it.
    /// Closed on shutdown, like the bots' pool.
    @Bean
    StockfishAnalysis stockfishAnalysis(StockfishProperties properties) {
        var binary = Path.of(properties.path());
        var analysis = properties.analysis();
        var pool = new EnginePool<>(analysis.poolSize(), () -> {
            var engine = UciEngine.start(UciProcess.start(binary), properties.startTimeout());
            try {
                engine.setOption("Threads", analysis.threads());
                engine.setOption("Hash", analysis.hashMb());
                engine.waitUntilReady();
                return engine;
            } catch (RuntimeException e) {
                engine.close();
                throw e instanceof UciException uci ? uci : new UciException("Cannot configure the engine", e);
            }
        }, UciEngine::isAlive, analysis.waitTimeout());
        return new StockfishAnalysis(pool, analysis.positionTimeout());
    }

    /// `kopi.engine.restarts{pool}` (design §14): engines that crashed, hung
    /// or died and were replaced, for the bots' pool and the analysis one.
    /// Read from the pools when the metrics are scraped.
    @Bean
    MeterBinder stockfishMetrics(EnginePool<UciEngine> stockfishPool, StockfishAnalysis stockfishAnalysis) {
        return registry -> {
            FunctionCounter.builder("kopi.engine.restarts", stockfishPool, EnginePool::discarded)
                    .description("Stockfish engines discarded after failing or dying, each replaced by a new one")
                    .tag("pool", "bots")
                    .register(registry);
            FunctionCounter.builder("kopi.engine.restarts", stockfishAnalysis, StockfishAnalysis::engineRestarts)
                    .description("Stockfish engines discarded after failing or dying, each replaced by a new one")
                    .tag("pool", "analysis")
                    .register(registry);
        };
    }
}
