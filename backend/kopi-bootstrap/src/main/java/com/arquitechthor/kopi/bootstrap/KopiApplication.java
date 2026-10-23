package com.arquitechthor.kopi.bootstrap;

import com.arquitechthor.kopi.adapter.persistence.PersistenceAdapterConfiguration;
import com.arquitechthor.kopi.adapter.rest.RestAdapterConfiguration;
import com.arquitechthor.kopi.adapter.stockfish.StockfishAdapterConfiguration;
import com.arquitechthor.kopi.adapter.web.WebAdapterConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/// Entry point of Kopi Chess.
///
/// Component scanning is limited to this package on purpose: the domain and
/// application rings contain no Spring annotations, so their classes are
/// registered as beans explicitly with `@Configuration` classes in this
/// module (see ADR-002). Each adapter's configuration is imported here, so
/// the list below is the full picture of what the application is made of.
@SpringBootApplication
@Import({
        WebAdapterConfiguration.class,
        RestAdapterConfiguration.class,
        PersistenceAdapterConfiguration.class,
        StockfishAdapterConfiguration.class,
        UseCaseConfiguration.class,
})
public class KopiApplication {

    static void main(String[] args) {
        SpringApplication.run(KopiApplication.class, args);
    }
}
