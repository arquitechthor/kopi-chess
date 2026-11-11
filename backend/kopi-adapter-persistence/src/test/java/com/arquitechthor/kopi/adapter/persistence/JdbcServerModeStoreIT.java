package com.arquitechthor.kopi.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.application.port.ServerModeStore;
import com.arquitechthor.kopi.application.port.ServerModeStore.Settings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/// Server mode against a real PostgreSQL (V9).
@SpringBootTest(classes = JdbcGameRepositoryIT.PersistenceTestApplication.class)
class JdbcServerModeStoreIT {

    @Autowired
    ServerModeStore store;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void empty() {
        jdbc.sql("delete from server_mode").update();
    }

    @Test
    void itIsOffAtFirst() {
        assertThat(store.settings()).isEqualTo(Settings.OFF);
    }

    @Test
    void theSettingsComeBackAndAreReplaced() {
        var on = new Settings(true, "sub-1", "PC de casa");
        store.save(on);
        assertThat(store.settings()).isEqualTo(on);

        var off = new Settings(false, "sub-1", "Portátil");
        store.save(off);
        assertThat(store.settings()).isEqualTo(off);
        assertThat(jdbc.sql("select count(*) from server_mode").query(Long.class).single()).isEqualTo(1);
    }
}
