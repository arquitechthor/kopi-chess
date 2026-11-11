package com.arquitechthor.kopi.adapter.persistence;

import com.arquitechthor.kopi.application.port.ServerModeStore;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;

/// [ServerModeStore] on PostgreSQL: the single row of `server_mode` (V9).
public final class JdbcServerModeStore implements ServerModeStore {

    private final JdbcClient jdbc;

    public JdbcServerModeStore(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Settings settings() {
        return jdbc.sql("select enabled, account_sub, name from server_mode where id = 1")
                .query((rs, _) -> new Settings(rs.getBoolean("enabled"), rs.getString("account_sub"),
                        rs.getString("name")))
                .optional()
                .orElse(Settings.OFF);
    }

    @Override
    public void save(Settings settings) {
        jdbc.sql("""
                insert into server_mode (id, enabled, account_sub, name) values (1, :enabled, :sub, :name)
                on conflict (id) do update
                   set enabled = excluded.enabled, account_sub = excluded.account_sub, name = excluded.name""")
                .param("enabled", settings.enabled())
                .param("sub", settings.accountSub())
                .param("name", settings.name())
                .update();
    }
}
