package com.arquitechthor.kopi.adapter.persistence;

import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.application.port.AnalysisRepository;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.application.port.HistorySyncStore;
import com.arquitechthor.kopi.application.port.KopiSessionStore;
import com.arquitechthor.kopi.application.port.OnlineGameStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/// Spring configuration of the persistence adapter, imported explicitly by
/// `kopi-bootstrap`. The datasource, `JdbcClient`, the transaction manager
/// and Flyway come from Spring Boot's auto-configuration.
@Configuration(proxyBeanMethods = false)
public class PersistenceAdapterConfiguration {

    @Bean
    GameRepository gameRepository(JdbcClient jdbc, PlatformTransactionManager transactionManager) {
        return new JdbcGameRepository(jdbc, new TransactionTemplate(transactionManager));
    }

    @Bean
    AnalysisRepository analysisRepository(JdbcClient jdbc, PlatformTransactionManager transactionManager) {
        return new JdbcAnalysisRepository(jdbc, new TransactionTemplate(transactionManager));
    }

    @Bean
    OnlineGameStore onlineGameStore(JdbcClient jdbc) {
        return new JdbcOnlineGameStore(jdbc);
    }

    @Bean
    KopiSessionStore kopiSessionStore(JdbcClient jdbc) {
        return new JdbcKopiSessionStore(jdbc);
    }

    @Bean
    HistorySyncStore historySyncStore(JdbcClient jdbc) {
        return new JdbcHistorySyncStore(jdbc);
    }

    @Bean
    AnalysisJobQueue analysisJobQueue(JdbcClient jdbc) {
        return new JdbcAnalysisJobQueue(jdbc);
    }
}
