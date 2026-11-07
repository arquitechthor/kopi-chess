package com.arquitechthor.kopi.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.application.port.KopiSessionStore;
import com.arquitechthor.kopi.application.port.KopiSessionStore.StoredSession;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/// The Kopi session against a real PostgreSQL (V6): one row at most.
@SpringBootTest(classes = JdbcGameRepositoryIT.PersistenceTestApplication.class)
class JdbcKopiSessionStoreIT {

    private static final Instant EXPIRES = Instant.parse("2026-11-07T13:00:00Z");

    @Autowired
    KopiSessionStore store;

    @BeforeEach
    void noSession() {
        store.clear();
    }

    @Test
    void thereIsNoSessionAtFirst() {
        assertThat(store.load()).isEmpty();
    }

    @Test
    void aSavedSessionComesBack() {
        var session = new StoredSession("access-1", "refresh-1", EXPIRES, "sub-1", "ana@example.com");

        store.save(session);

        assertThat(store.load()).hasValue(session);
    }

    @Test
    void savingAgainReplacesIt() {
        store.save(new StoredSession("access-1", "refresh-1", EXPIRES, "sub-1", "ana@example.com"));
        var renewed = new StoredSession("access-2", "refresh-1", EXPIRES.plusSeconds(3600), "sub-1", "ana@example.com");

        store.save(renewed);

        assertThat(store.load()).hasValue(renewed);
    }

    @Test
    void clearingForgetsIt() {
        store.save(new StoredSession("access-1", "refresh-1", EXPIRES, "sub-1", ""));

        store.clear();

        assertThat(store.load()).isEmpty();
    }
}
