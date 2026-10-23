package com.arquitechthor.kopi.adapter.stockfish;

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
}
